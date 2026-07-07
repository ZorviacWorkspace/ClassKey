-- ClassKey — 0006 production upgrade
--  • Morning + afternoon attendance sessions
--  • Username login + resolve_login_email() (no service key needed for identifier login)
--  • Session-aware RPCs (mark / approve / override / auto-absent)
--  • Notification rows written by the RPCs

-- ── campus_config: session windows ────────────────────────────────────
alter table public.campus_config add column if not exists morning_open            time not null default '08:00';
alter table public.campus_config add column if not exists afternoon_open          time not null default '13:00';
alter table public.campus_config add column if not exists afternoon_present_until time not null default '14:00';
alter table public.campus_config add column if not exists afternoon_late_until    time not null default '15:30';
-- existing columns keep their meaning for the MORNING session:
--   present_until = morning present cutoff, late_until = morning late cutoff

-- ── profiles: username login ──────────────────────────────────────────
alter table public.profiles add column if not exists username text;
create unique index if not exists idx_profiles_username on public.profiles(lower(username)) where username is not null;

-- ── attendance: per-session records ───────────────────────────────────
alter table public.attendance add column if not exists session text not null default 'morning'
  check (session in ('morning','afternoon'));
alter table public.attendance drop constraint if exists attendance_student_id_attendance_date_key;
create unique index if not exists idx_attendance_unique_session
  on public.attendance(student_id, attendance_date, session);

-- ── requests: which half they apply to ────────────────────────────────
alter table public.attendance_requests add column if not exists session text not null default 'full_day'
  check (session in ('morning','afternoon','full_day'));

-- ── identifier → email lookup (SECURITY DEFINER, safe for anon) ──────
create or replace function public.resolve_login_email(p_identifier text)
returns text language plpgsql stable security definer set search_path = public as $$
declare
  v_id text := trim(p_identifier);
  v_email text;
begin
  if v_id = '' then return null; end if;
  if position('@' in v_id) > 0 then return lower(v_id); end if;

  select email into v_email from public.profiles
   where lower(username) = lower(v_id) or phone = v_id limit 1;
  if v_email is not null then return v_email; end if;

  select p.email into v_email
    from public.students s join public.profiles p on p.id = s.profile_id
   where upper(s.register_number) = upper(v_id) limit 1;
  return v_email;
end $$;

grant execute on function public.resolve_login_email(text) to anon, authenticated;

-- ── mark_attendance: session aware + notifications ───────────────────
create or replace function public.mark_attendance(
  p_latitude double precision,
  p_longitude double precision,
  p_accuracy double precision,
  p_device_id text,
  p_biometric_verified boolean
) returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_uid uuid := auth.uid();
  v_student public.students;
  v_cfg public.campus_config;
  v_dist double precision;
  v_now time;
  v_session text;
  v_status text;
begin
  if v_uid is null then
    return jsonb_build_object('ok', false, 'code', 'NO_AUTH', 'message', 'Please log in.');
  end if;

  select s.* into v_student from public.students s
    join public.profiles p on p.id = s.profile_id
   where p.id = v_uid and p.role = 'student' and p.is_active;
  if not found then
    return jsonb_build_object('ok', false, 'code', 'NOT_STUDENT', 'message', 'Only active students can mark attendance.');
  end if;

  if not coalesce(p_biometric_verified, false) then
    return jsonb_build_object('ok', false, 'code', 'BIOMETRIC_REQUIRED', 'message', 'Fingerprint verification is required.');
  end if;

  -- device binding
  if v_student.device_id is null or v_student.device_status = 'not_bound' then
    update public.students set device_id = p_device_id, device_status = 'approved', updated_at = now()
     where id = v_student.id;
  elsif v_student.device_status = 'blocked' then
    return jsonb_build_object('ok', false, 'code', 'DEVICE_BLOCKED', 'message', 'This account is blocked on this device. Contact admin.');
  elsif v_student.device_id is distinct from p_device_id then
    insert into public.device_approvals (student_id, old_device_id, new_device_id, status)
      values (v_student.id, v_student.device_id, p_device_id, 'pending');
    insert into public.suspicious_attempts (student_id, reason, latitude, longitude, accuracy, device_id)
      values (v_student.id, 'New/unapproved device', p_latitude, p_longitude, p_accuracy, p_device_id);
    return jsonb_build_object('ok', false, 'code', 'DEVICE_NOT_APPROVED',
      'message', 'New device detected. A replacement request was sent for approval.');
  end if;

  select * into v_cfg from public.campus_config order by updated_at desc limit 1;
  if not found then
    return jsonb_build_object('ok', false, 'code', 'NO_CAMPUS', 'message', 'Campus is not configured yet.');
  end if;

  if p_accuracy is not null and p_accuracy > v_cfg.min_accuracy_meters then
    insert into public.suspicious_attempts (student_id, reason, latitude, longitude, accuracy, device_id)
      values (v_student.id, 'Low GPS accuracy: '||round(p_accuracy)::text||'m', p_latitude, p_longitude, p_accuracy, p_device_id);
    return jsonb_build_object('ok', false, 'code', 'LOW_ACCURACY',
      'message', 'GPS accuracy too low ('||round(p_accuracy)::text||'m). Move to open sky and retry.');
  end if;

  v_dist := public.haversine_m(p_latitude, p_longitude, v_cfg.latitude, v_cfg.longitude);
  if v_dist > v_cfg.allowed_radius_meters then
    insert into public.suspicious_attempts (student_id, reason, latitude, longitude, accuracy, device_id)
      values (v_student.id, 'Outside geofence: '||round(v_dist)::text||'m', p_latitude, p_longitude, p_accuracy, p_device_id);
    return jsonb_build_object('ok', false, 'code', 'OUT_OF_RANGE',
      'message', 'You are '||round(v_dist)::text||'m away — outside the '||v_cfg.allowed_radius_meters::text||'m campus radius.',
      'distance', round(v_dist));
  end if;

  -- which session is open right now?
  v_now := (now() at time zone v_cfg.timezone)::time;
  if v_now >= v_cfg.morning_open and v_now <= v_cfg.late_until then
    v_session := 'morning';
    v_status := case when v_now <= v_cfg.present_until then 'present' else 'late' end;
  elsif v_now >= v_cfg.afternoon_open and v_now <= v_cfg.afternoon_late_until then
    v_session := 'afternoon';
    v_status := case when v_now <= v_cfg.afternoon_present_until then 'present' else 'late' end;
  else
    return jsonb_build_object('ok', false, 'code', 'WINDOW_CLOSED',
      'message', 'No attendance session is open now. Morning '||v_cfg.morning_open||'–'||v_cfg.late_until||
                 ', afternoon '||v_cfg.afternoon_open||'–'||v_cfg.afternoon_late_until||'.');
  end if;

  if exists (select 1 from public.attendance
              where student_id = v_student.id and attendance_date = current_date and session = v_session) then
    return jsonb_build_object('ok', false, 'code', 'DUPLICATE',
      'message', initcap(v_session)||' attendance is already marked today.');
  end if;

  insert into public.attendance (student_id, attendance_date, session, status, marked_at,
                                 latitude, longitude, accuracy, device_id, verification_method)
    values (v_student.id, current_date, v_session, v_status, now(),
            p_latitude, p_longitude, p_accuracy, p_device_id, 'location_biometric');

  insert into public.audit_logs (actor_id, action, entity_type, entity_id, new_value)
    values (v_uid, 'ATTENDANCE_MARKED', 'attendance', v_student.id,
            jsonb_build_object('session', v_session, 'status', v_status, 'distance', round(v_dist)));

  insert into public.notifications (profile_id, title, body)
    values (v_uid, 'Attendance marked',
            initcap(v_session)||' session: '||v_status||' ('||round(v_dist)::text||'m from gate).');

  return jsonb_build_object('ok', true, 'session', v_session, 'status', v_status,
    'distance', round(v_dist), 'message', initcap(v_session)||' attendance marked: '||v_status||'.');
end $$;

grant execute on function public.mark_attendance(double precision, double precision, double precision, text, boolean) to authenticated;

-- ── approve_attendance_request: session aware + notify ────────────────
create or replace function public.approve_attendance_request(
  p_request_id uuid, p_decision text, p_review_note text default null
) returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_uid uuid := auth.uid();
  v_role text := public.jwt_role();
  v_req public.attendance_requests;
  v_status text;
  v_sess text;
  v_stu_profile uuid;
begin
  if v_role not in ('staff','admin') then
    return jsonb_build_object('ok', false, 'message', 'Only staff/admin can review requests.');
  end if;
  if p_decision not in ('approved','rejected') then
    return jsonb_build_object('ok', false, 'message', 'Decision must be approved or rejected.');
  end if;

  select * into v_req from public.attendance_requests where id = p_request_id;
  if not found then
    return jsonb_build_object('ok', false, 'message', 'Request not found.');
  end if;

  if v_role = 'staff' and not exists (
    select 1 from public.students s where s.id = v_req.student_id and s.department_id = public.jwt_department()
  ) then
    return jsonb_build_object('ok', false, 'message', 'This student is outside your department.');
  end if;

  update public.attendance_requests
     set status = p_decision, reviewed_by = v_uid, review_note = p_review_note, updated_at = now()
   where id = p_request_id;

  if p_decision = 'approved' then
    v_status := case v_req.request_type
      when 'od' then 'od'
      when 'half_day' then 'half_day'
      when 'full_day_leave' then 'leave'
      when 'permission' then 'permission'
      when 'early_leave' then 'early_leave' end;

    for v_sess in
      select unnest(case when v_req.session = 'full_day' then array['morning','afternoon'] else array[v_req.session] end)
    loop
      insert into public.attendance (student_id, attendance_date, session, status, marked_at,
                                     verification_method, marked_by, manual_reason, request_id)
        values (v_req.student_id, v_req.request_date, v_sess, v_status, now(),
                'approved_request', v_uid, v_req.reason, v_req.id)
      on conflict (student_id, attendance_date, session)
        do update set status = excluded.status, marked_by = excluded.marked_by,
                      verification_method = 'approved_request', request_id = excluded.request_id, updated_at = now();
    end loop;
  end if;

  select profile_id into v_stu_profile from public.students where id = v_req.student_id;
  if v_stu_profile is not null then
    insert into public.notifications (profile_id, title, body)
      values (v_stu_profile, 'Request '||p_decision,
              'Your '||replace(v_req.request_type,'_',' ')||' request for '||v_req.request_date||' was '||p_decision||
              coalesce('. Note: '||p_review_note, '.'));
  end if;

  insert into public.audit_logs (actor_id, action, entity_type, entity_id, new_value)
    values (v_uid, 'REQUEST_'||upper(p_decision), 'attendance_request', p_request_id,
            jsonb_build_object('note', p_review_note));

  return jsonb_build_object('ok', true, 'message', 'Request '||p_decision||'.');
end $$;

grant execute on function public.approve_attendance_request(uuid, text, text) to authenticated;

-- ── manual_attendance_override: session aware ─────────────────────────
create or replace function public.manual_attendance_override(
  p_student_id uuid, p_date date, p_status text, p_reason text, p_session text default 'full_day'
) returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_uid uuid := auth.uid();
  v_role text := public.jwt_role();
  v_sess text;
  v_stu_profile uuid;
begin
  if v_role not in ('staff','admin') then
    return jsonb_build_object('ok', false, 'message', 'Only staff/admin can override attendance.');
  end if;
  if coalesce(trim(p_reason),'') = '' then
    return jsonb_build_object('ok', false, 'message', 'An audit reason is required.');
  end if;
  if p_session not in ('morning','afternoon','full_day') then
    return jsonb_build_object('ok', false, 'message', 'Session must be morning, afternoon or full_day.');
  end if;
  if v_role = 'staff' and not exists (
    select 1 from public.students s where s.id = p_student_id and s.department_id = public.jwt_department()
  ) then
    return jsonb_build_object('ok', false, 'message', 'This student is outside your department.');
  end if;

  for v_sess in
    select unnest(case when p_session = 'full_day' then array['morning','afternoon'] else array[p_session] end)
  loop
    insert into public.attendance (student_id, attendance_date, session, status, marked_at,
                                   verification_method, marked_by, manual_reason)
      values (p_student_id, p_date, v_sess, p_status, now(), 'manual_override', v_uid, p_reason)
    on conflict (student_id, attendance_date, session)
      do update set status = excluded.status, marked_by = excluded.marked_by,
                    manual_reason = excluded.manual_reason, verification_method = 'manual_override', updated_at = now();
  end loop;

  select profile_id into v_stu_profile from public.students where id = p_student_id;
  if v_stu_profile is not null then
    insert into public.notifications (profile_id, title, body)
      values (v_stu_profile, 'Attendance updated by staff',
              p_date||' ('||p_session||'): '||p_status||'. Reason: '||p_reason);
  end if;

  insert into public.audit_logs (actor_id, action, entity_type, entity_id, new_value)
    values (v_uid, 'MANUAL_OVERRIDE', 'attendance', p_student_id,
            jsonb_build_object('date', p_date, 'session', p_session, 'status', p_status, 'reason', p_reason));

  return jsonb_build_object('ok', true, 'message', 'Attendance updated.');
end $$;

grant execute on function public.manual_attendance_override(uuid, date, text, text, text) to authenticated;
-- keep the old 4-arg signature working (defaults to full day)
drop function if exists public.manual_attendance_override(uuid, date, text, text);

-- ── auto_mark_absentees: per session ──────────────────────────────────
create or replace function public.auto_mark_absentees(p_date date default current_date)
returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_role text := public.jwt_role();
  v_count integer := 0;
  v_c integer;
  v_sess text;
begin
  if auth.uid() is not null and v_role <> 'admin' then
    return jsonb_build_object('ok', false, 'message', 'Only admin can run this.');
  end if;

  foreach v_sess in array array['morning','afternoon'] loop
    insert into public.attendance (student_id, attendance_date, session, status, verification_method)
    select s.id, p_date, v_sess, 'absent', 'auto_absent'
      from public.students s
      join public.profiles p on p.id = s.profile_id and p.is_active
     where not exists (
       select 1 from public.attendance a
        where a.student_id = s.id and a.attendance_date = p_date and a.session = v_sess
     );
    get diagnostics v_c = row_count;
    v_count := v_count + v_c;
  end loop;

  insert into public.audit_logs (actor_id, action, entity_type, new_value)
    values (auth.uid(), 'AUTO_ABSENT', 'attendance', jsonb_build_object('date', p_date, 'count', v_count));

  return jsonb_build_object('ok', true, 'marked_absent', v_count);
end $$;

grant execute on function public.auto_mark_absentees(date) to authenticated;
