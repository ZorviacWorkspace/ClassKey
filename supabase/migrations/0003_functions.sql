-- ClassKey — 0003 RPC functions (all SECURITY DEFINER; server-side authoritative)

-- ── mark_attendance ───────────────────────────────────────────────────
-- Called by the authenticated student. Validates role, biometric flag,
-- device binding, campus geofence (Haversine), accuracy, time window and
-- duplicates. Logs suspicious attempts. Returns a JSON result.
create or replace function public.mark_attendance(
  p_latitude          double precision,
  p_longitude         double precision,
  p_accuracy          double precision,
  p_device_id         text,
  p_biometric_verified boolean
) returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_uid      uuid := auth.uid();
  v_student  public.students;
  v_cfg      public.campus_config;
  v_dist     double precision;
  v_now      time;
  v_status   text;
begin
  if v_uid is null then
    return jsonb_build_object('ok', false, 'code', 'NO_AUTH', 'message', 'Please log in.');
  end if;

  select s.* into v_student from public.students s
    join public.profiles p on p.id = s.profile_id
   where p.id = v_uid and p.role = 'student';
  if not found then
    return jsonb_build_object('ok', false, 'code', 'NOT_STUDENT', 'message', 'Only students can mark attendance.');
  end if;

  if not coalesce(p_biometric_verified, false) then
    return jsonb_build_object('ok', false, 'code', 'BIOMETRIC_REQUIRED', 'message', 'Biometric verification is required.');
  end if;

  -- device binding: bind on first use, else require the same approved device
  if v_student.device_id is null or v_student.device_status = 'not_bound' then
    update public.students set device_id = p_device_id, device_status = 'approved', updated_at = now()
     where id = v_student.id;
  elsif v_student.device_status = 'blocked' then
    return jsonb_build_object('ok', false, 'code', 'DEVICE_BLOCKED', 'message', 'This account is blocked on this device. Contact admin.');
  elsif v_student.device_id is distinct from p_device_id then
    insert into public.device_approvals (student_id, old_device_id, new_device_id, status)
      values (v_student.id, v_student.device_id, p_device_id, 'pending')
      on conflict do nothing;
    insert into public.suspicious_attempts (student_id, reason, latitude, longitude, accuracy, device_id)
      values (v_student.id, 'New/unapproved device', p_latitude, p_longitude, p_accuracy, p_device_id);
    return jsonb_build_object('ok', false, 'code', 'DEVICE_NOT_APPROVED',
      'message', 'New device detected. A replacement request was sent for staff/admin approval.');
  end if;

  select * into v_cfg from public.campus_config order by updated_at desc limit 1;
  if not found then
    return jsonb_build_object('ok', false, 'code', 'NO_CAMPUS', 'message', 'Campus is not configured yet.');
  end if;

  -- accuracy gate (reject very low-accuracy fixes)
  if p_accuracy is not null and v_cfg.min_accuracy_meters is not null
     and p_accuracy > v_cfg.min_accuracy_meters then
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

  -- duplicate?
  if exists (select 1 from public.attendance where student_id = v_student.id and attendance_date = current_date) then
    return jsonb_build_object('ok', false, 'code', 'DUPLICATE', 'message', 'Attendance already marked today.');
  end if;

  -- time window (campus timezone)
  v_now := (now() at time zone v_cfg.timezone)::time;
  if v_now <= v_cfg.present_until then
    v_status := 'present';
  elsif v_now <= v_cfg.late_until then
    v_status := 'late';
  else
    return jsonb_build_object('ok', false, 'code', 'WINDOW_CLOSED',
      'message', 'Self-marking is closed for today. Please contact staff for assisted attendance.');
  end if;

  insert into public.attendance (student_id, attendance_date, status, marked_at, latitude, longitude, accuracy, device_id, verification_method)
    values (v_student.id, current_date, v_status, now(), p_latitude, p_longitude, p_accuracy, p_device_id, 'location_biometric');

  insert into public.audit_logs (actor_id, action, entity_type, entity_id, new_value)
    values (v_uid, 'ATTENDANCE_MARKED', 'attendance', v_student.id,
            jsonb_build_object('status', v_status, 'distance', round(v_dist)));

  return jsonb_build_object('ok', true, 'status', v_status, 'distance', round(v_dist),
    'message', 'Attendance marked: '|| v_status || '.');
end $$;

grant execute on function public.mark_attendance(double precision, double precision, double precision, text, boolean) to authenticated;

-- ── approve_attendance_request ────────────────────────────────────────
create or replace function public.approve_attendance_request(
  p_request_id uuid, p_decision text, p_review_note text default null
) returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_uid uuid := auth.uid();
  v_role text := public.jwt_role();
  v_req public.attendance_requests;
  v_status text;
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

  -- staff limited to their department
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
    insert into public.attendance (student_id, attendance_date, status, marked_at, verification_method, marked_by, manual_reason, request_id)
      values (v_req.student_id, v_req.request_date, v_status, now(), 'approved_request', v_uid, v_req.reason, v_req.id)
    on conflict (student_id, attendance_date)
      do update set status = excluded.status, marked_by = excluded.marked_by,
                    verification_method = 'approved_request', request_id = excluded.request_id, updated_at = now();
  end if;

  insert into public.audit_logs (actor_id, action, entity_type, entity_id, new_value)
    values (v_uid, 'REQUEST_'||upper(p_decision), 'attendance_request', p_request_id,
            jsonb_build_object('note', p_review_note));

  return jsonb_build_object('ok', true, 'message', 'Request '||p_decision||'.');
end $$;

grant execute on function public.approve_attendance_request(uuid, text, text) to authenticated;

-- ── manual_attendance_override ────────────────────────────────────────
create or replace function public.manual_attendance_override(
  p_student_id uuid, p_date date, p_status text, p_reason text
) returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_uid uuid := auth.uid();
  v_role text := public.jwt_role();
begin
  if v_role not in ('staff','admin') then
    return jsonb_build_object('ok', false, 'message', 'Only staff/admin can override attendance.');
  end if;
  if coalesce(trim(p_reason),'') = '' then
    return jsonb_build_object('ok', false, 'message', 'An audit reason is required.');
  end if;
  if v_role = 'staff' and not exists (
    select 1 from public.students s where s.id = p_student_id and s.department_id = public.jwt_department()
  ) then
    return jsonb_build_object('ok', false, 'message', 'This student is outside your department.');
  end if;

  insert into public.attendance (student_id, attendance_date, status, marked_at, verification_method, marked_by, manual_reason)
    values (p_student_id, p_date, p_status, now(), 'manual_override', v_uid, p_reason)
  on conflict (student_id, attendance_date)
    do update set status = excluded.status, marked_by = excluded.marked_by,
                  manual_reason = excluded.manual_reason, verification_method = 'manual_override', updated_at = now();

  insert into public.audit_logs (actor_id, action, entity_type, entity_id, new_value)
    values (v_uid, 'MANUAL_OVERRIDE', 'attendance', p_student_id,
            jsonb_build_object('date', p_date, 'status', p_status, 'reason', p_reason));

  return jsonb_build_object('ok', true, 'message', 'Attendance updated.');
end $$;

grant execute on function public.manual_attendance_override(uuid, date, text, text) to authenticated;

-- ── auto_mark_absentees ───────────────────────────────────────────────
create or replace function public.auto_mark_absentees(p_date date default current_date)
returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_role text := public.jwt_role();
  v_count integer;
begin
  if v_role not in ('admin') and auth.uid() is not null then
    return jsonb_build_object('ok', false, 'message', 'Only admin can run this.');
  end if;

  insert into public.attendance (student_id, attendance_date, status, verification_method, marked_by)
  select s.id, p_date, 'absent', 'auto_absent', null
    from public.students s
   where not exists (
     select 1 from public.attendance a where a.student_id = s.id and a.attendance_date = p_date
   );
  get diagnostics v_count = row_count;

  insert into public.audit_logs (actor_id, action, entity_type, new_value)
    values (auth.uid(), 'AUTO_ABSENT', 'attendance', jsonb_build_object('date', p_date, 'count', v_count));

  return jsonb_build_object('ok', true, 'marked_absent', v_count);
end $$;

grant execute on function public.auto_mark_absentees(date) to authenticated;
