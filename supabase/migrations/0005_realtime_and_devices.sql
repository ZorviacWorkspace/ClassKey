-- ClassKey — 0005 realtime publication + device approval RPC

-- Realtime: let dashboards receive live changes (RLS still applies per user).
do $$
begin
  begin
    alter publication supabase_realtime add table public.attendance;
  exception when duplicate_object then null;
  end;
  begin
    alter publication supabase_realtime add table public.attendance_requests;
  exception when duplicate_object then null;
  end;
  begin
    alter publication supabase_realtime add table public.suspicious_attempts;
  exception when duplicate_object then null;
  end;
  begin
    alter publication supabase_realtime add table public.device_approvals;
  exception when duplicate_object then null;
  end;
end $$;

-- ── approve_device ────────────────────────────────────────────────────
-- Staff (same department) or admin approves/rejects a device replacement.
create or replace function public.approve_device(
  p_approval_id uuid, p_decision text
) returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_uid uuid := auth.uid();
  v_role text := public.jwt_role();
  v_row public.device_approvals;
begin
  if v_role not in ('staff','admin') then
    return jsonb_build_object('ok', false, 'message', 'Only staff/admin can review devices.');
  end if;
  if p_decision not in ('approved','rejected') then
    return jsonb_build_object('ok', false, 'message', 'Decision must be approved or rejected.');
  end if;

  select * into v_row from public.device_approvals where id = p_approval_id;
  if not found then
    return jsonb_build_object('ok', false, 'message', 'Approval request not found.');
  end if;

  if v_role = 'staff' and not exists (
    select 1 from public.students s where s.id = v_row.student_id and s.department_id = public.jwt_department()
  ) then
    return jsonb_build_object('ok', false, 'message', 'This student is outside your department.');
  end if;

  update public.device_approvals
     set status = p_decision, reviewed_by = v_uid, reviewed_at = now()
   where id = p_approval_id;

  if p_decision = 'approved' then
    update public.students
       set device_id = v_row.new_device_id, device_status = 'approved', updated_at = now()
     where id = v_row.student_id;
  else
    update public.students
       set device_status = case when device_id is null then 'not_bound' else 'approved' end,
           updated_at = now()
     where id = v_row.student_id;
  end if;

  insert into public.audit_logs (actor_id, action, entity_type, entity_id, new_value)
    values (v_uid, 'DEVICE_'||upper(p_decision), 'device_approval', p_approval_id,
            jsonb_build_object('new_device', v_row.new_device_id));

  return jsonb_build_object('ok', true, 'message', 'Device '||p_decision||'.');
end $$;

grant execute on function public.approve_device(uuid, text) to authenticated;
