-- ClassKey — 0002 Row Level Security
-- Enable RLS everywhere and add role-scoped policies.
-- Reads use jwt_role()/jwt_department()/jwt_student_id() (security definer helpers).

alter table public.departments          enable row level security;
alter table public.profiles             enable row level security;
alter table public.students             enable row level security;
alter table public.staff                enable row level security;
alter table public.campus_config        enable row level security;
alter table public.attendance           enable row level security;
alter table public.attendance_requests  enable row level security;
alter table public.device_approvals     enable row level security;
alter table public.suspicious_attempts  enable row level security;
alter table public.notifications        enable row level security;
alter table public.audit_logs           enable row level security;

-- ── departments ── everyone signed in can read; only admin writes
drop policy if exists dep_read on public.departments;
create policy dep_read on public.departments for select to authenticated using (true);
drop policy if exists dep_admin on public.departments;
create policy dep_admin on public.departments for all to authenticated
  using (public.jwt_role() = 'admin') with check (public.jwt_role() = 'admin');

-- ── profiles ──
drop policy if exists prof_self_read on public.profiles;
create policy prof_self_read on public.profiles for select to authenticated
  using (id = auth.uid() or public.jwt_role() in ('staff','admin'));
drop policy if exists prof_self_update on public.profiles;
create policy prof_self_update on public.profiles for update to authenticated
  using (id = auth.uid() or public.jwt_role() = 'admin')
  with check (id = auth.uid() or public.jwt_role() = 'admin');
drop policy if exists prof_admin_insert on public.profiles;
create policy prof_admin_insert on public.profiles for insert to authenticated
  with check (public.jwt_role() = 'admin');

-- ── students ──
drop policy if exists stu_read on public.students;
create policy stu_read on public.students for select to authenticated using (
  profile_id = auth.uid()
  or public.jwt_role() = 'admin'
  or (public.jwt_role() = 'staff' and department_id = public.jwt_department())
);
drop policy if exists stu_self_update on public.students;
create policy stu_self_update on public.students for update to authenticated
  using (profile_id = auth.uid() or public.jwt_role() = 'admin')
  with check (profile_id = auth.uid() or public.jwt_role() = 'admin');
drop policy if exists stu_admin_write on public.students;
create policy stu_admin_write on public.students for insert to authenticated
  with check (public.jwt_role() = 'admin');
drop policy if exists stu_admin_delete on public.students;
create policy stu_admin_delete on public.students for delete to authenticated
  using (public.jwt_role() = 'admin');

-- ── staff ──
drop policy if exists staff_read on public.staff;
create policy staff_read on public.staff for select to authenticated
  using (profile_id = auth.uid() or public.jwt_role() in ('staff','admin'));
drop policy if exists staff_admin_write on public.staff;
create policy staff_admin_write on public.staff for all to authenticated
  using (public.jwt_role() = 'admin') with check (public.jwt_role() = 'admin');

-- ── campus_config ── all read; only admin writes
drop policy if exists campus_read on public.campus_config;
create policy campus_read on public.campus_config for select to authenticated using (true);
drop policy if exists campus_admin on public.campus_config;
create policy campus_admin on public.campus_config for all to authenticated
  using (public.jwt_role() = 'admin') with check (public.jwt_role() = 'admin');

-- ── attendance ── students read own; staff read their dept; admin all.
-- Inserts happen ONLY via the mark_attendance RPC (security definer), so no client insert policy.
drop policy if exists att_read on public.attendance;
create policy att_read on public.attendance for select to authenticated using (
  student_id = public.jwt_student_id()
  or public.jwt_role() = 'admin'
  or (public.jwt_role() = 'staff' and student_id in (
        select id from public.students where department_id = public.jwt_department()))
);
-- staff/admin manual override handled by RPC; allow admin/staff update for their scope as a fallback
drop policy if exists att_staff_update on public.attendance;
create policy att_staff_update on public.attendance for update to authenticated using (
  public.jwt_role() = 'admin'
  or (public.jwt_role() = 'staff' and student_id in (
        select id from public.students where department_id = public.jwt_department()))
) with check (true);

-- ── attendance_requests ──
drop policy if exists req_read on public.attendance_requests;
create policy req_read on public.attendance_requests for select to authenticated using (
  student_id = public.jwt_student_id()
  or public.jwt_role() = 'admin'
  or (public.jwt_role() = 'staff' and student_id in (
        select id from public.students where department_id = public.jwt_department()))
);
drop policy if exists req_student_insert on public.attendance_requests;
create policy req_student_insert on public.attendance_requests for insert to authenticated
  with check (student_id = public.jwt_student_id());
drop policy if exists req_staff_update on public.attendance_requests;
create policy req_staff_update on public.attendance_requests for update to authenticated using (
  public.jwt_role() = 'admin'
  or (public.jwt_role() = 'staff' and student_id in (
        select id from public.students where department_id = public.jwt_department()))
) with check (true);

-- ── device_approvals ──
drop policy if exists dev_read on public.device_approvals;
create policy dev_read on public.device_approvals for select to authenticated using (
  student_id = public.jwt_student_id()
  or public.jwt_role() in ('staff','admin')
);
drop policy if exists dev_student_insert on public.device_approvals;
create policy dev_student_insert on public.device_approvals for insert to authenticated
  with check (student_id = public.jwt_student_id());
drop policy if exists dev_staff_update on public.device_approvals;
create policy dev_staff_update on public.device_approvals for update to authenticated
  using (public.jwt_role() in ('staff','admin')) with check (true);

-- ── suspicious_attempts ── written by RPC; staff/admin read
drop policy if exists susp_read on public.suspicious_attempts;
create policy susp_read on public.suspicious_attempts for select to authenticated
  using (public.jwt_role() in ('staff','admin'));

-- ── notifications ── own only
drop policy if exists notif_own on public.notifications;
create policy notif_own on public.notifications for select to authenticated
  using (profile_id = auth.uid());
drop policy if exists notif_own_update on public.notifications;
create policy notif_own_update on public.notifications for update to authenticated
  using (profile_id = auth.uid()) with check (profile_id = auth.uid());

-- ── audit_logs ── admin read only
drop policy if exists audit_read on public.audit_logs;
create policy audit_read on public.audit_logs for select to authenticated
  using (public.jwt_role() = 'admin');
