-- ClassKey — 0001 schema
-- Run in Supabase → SQL Editor. Safe to re-run.

create extension if not exists pgcrypto;

-- ── helper: updated_at ────────────────────────────────────────────────
create or replace function public.set_updated_at()
returns trigger language plpgsql as $$
begin new.updated_at = now(); return new; end $$;

-- ── departments ───────────────────────────────────────────────────────
create table if not exists public.departments (
  id         uuid primary key default gen_random_uuid(),
  name       text not null,
  code       text unique not null,
  created_at timestamptz not null default now()
);

-- ── profiles (1:1 with auth.users) ────────────────────────────────────
create table if not exists public.profiles (
  id                     uuid primary key references auth.users(id) on delete cascade,
  role                   text not null default 'student' check (role in ('student','staff','admin')),
  full_name              text not null default '',
  email                  text,
  phone                  text,
  avatar_url             text,
  department_id          uuid references public.departments(id),
  is_active              boolean not null default true,
  forced_password_change boolean not null default true,
  biometric_enabled      boolean not null default false,
  created_at             timestamptz not null default now(),
  updated_at             timestamptz not null default now()
);
create trigger trg_profiles_updated before update on public.profiles
  for each row execute function public.set_updated_at();

-- ── students ──────────────────────────────────────────────────────────
create table if not exists public.students (
  id              uuid primary key default gen_random_uuid(),
  profile_id      uuid unique not null references public.profiles(id) on delete cascade,
  register_number text unique not null,
  department_id   uuid references public.departments(id),
  year            integer,
  section         text,
  device_id       text,
  device_status   text not null default 'not_bound'
                    check (device_status in ('not_bound','approved','pending_replacement','blocked')),
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now()
);
create trigger trg_students_updated before update on public.students
  for each row execute function public.set_updated_at();
create index if not exists idx_students_department on public.students(department_id);

-- ── staff ─────────────────────────────────────────────────────────────
create table if not exists public.staff (
  id            uuid primary key default gen_random_uuid(),
  profile_id    uuid unique not null references public.profiles(id) on delete cascade,
  staff_code    text unique not null,
  department_id uuid references public.departments(id),
  designation   text,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now()
);
create trigger trg_staff_updated before update on public.staff
  for each row execute function public.set_updated_at();

-- ── campus_config (single row) ────────────────────────────────────────
create table if not exists public.campus_config (
  id                    uuid primary key default gen_random_uuid(),
  college_name          text not null default 'ClassKey College',
  latitude              double precision not null default 11.01830,
  longitude             double precision not null default 76.97250,
  allowed_radius_meters integer not null default 300,
  present_until         time not null default '09:30',
  late_until            time not null default '13:00',
  absent_after          time not null default '18:00',
  min_accuracy_meters   integer not null default 100,
  timezone              text not null default 'Asia/Kolkata',
  updated_by            uuid references public.profiles(id),
  updated_at            timestamptz not null default now()
);

-- ── attendance ────────────────────────────────────────────────────────
create table if not exists public.attendance (
  id                  uuid primary key default gen_random_uuid(),
  student_id          uuid not null references public.students(id) on delete cascade,
  attendance_date     date not null default current_date,
  status              text not null
                        check (status in ('present','late','absent','od','half_day','leave','permission','early_leave')),
  marked_at           timestamptz,
  latitude            double precision,
  longitude           double precision,
  accuracy            double precision,
  device_id           text,
  verification_method text not null default 'location_biometric',
  marked_by           uuid references public.profiles(id),
  manual_reason       text,
  request_id          uuid,
  created_at          timestamptz not null default now(),
  updated_at          timestamptz not null default now(),
  unique (student_id, attendance_date)
);
create trigger trg_attendance_updated before update on public.attendance
  for each row execute function public.set_updated_at();
create index if not exists idx_attendance_date on public.attendance(attendance_date);

-- ── attendance_requests ───────────────────────────────────────────────
create table if not exists public.attendance_requests (
  id           uuid primary key default gen_random_uuid(),
  student_id   uuid not null references public.students(id) on delete cascade,
  request_type text not null check (request_type in ('od','half_day','full_day_leave','permission','early_leave')),
  request_date date not null,
  from_time    time,
  to_time      time,
  reason       text not null,
  proof_url    text,
  status       text not null default 'pending' check (status in ('pending','approved','rejected')),
  reviewed_by  uuid references public.profiles(id),
  review_note  text,
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now()
);
create trigger trg_requests_updated before update on public.attendance_requests
  for each row execute function public.set_updated_at();
create index if not exists idx_requests_status on public.attendance_requests(status);

-- ── device_approvals ──────────────────────────────────────────────────
create table if not exists public.device_approvals (
  id            uuid primary key default gen_random_uuid(),
  student_id    uuid not null references public.students(id) on delete cascade,
  old_device_id text,
  new_device_id text not null,
  status        text not null default 'pending' check (status in ('pending','approved','rejected')),
  requested_at  timestamptz not null default now(),
  reviewed_by   uuid references public.profiles(id),
  reviewed_at   timestamptz
);

-- ── suspicious_attempts ───────────────────────────────────────────────
create table if not exists public.suspicious_attempts (
  id         uuid primary key default gen_random_uuid(),
  student_id uuid references public.students(id) on delete set null,
  profile_id uuid references public.profiles(id) on delete set null,
  reason     text not null,
  latitude   double precision,
  longitude  double precision,
  accuracy   double precision,
  device_id  text,
  created_at timestamptz not null default now()
);

-- ── notifications ─────────────────────────────────────────────────────
create table if not exists public.notifications (
  id         uuid primary key default gen_random_uuid(),
  profile_id uuid not null references public.profiles(id) on delete cascade,
  title      text not null,
  body       text not null default '',
  is_read    boolean not null default false,
  created_at timestamptz not null default now()
);
create index if not exists idx_notifications_profile on public.notifications(profile_id);

-- ── audit_logs ────────────────────────────────────────────────────────
create table if not exists public.audit_logs (
  id          uuid primary key default gen_random_uuid(),
  actor_id    uuid references public.profiles(id),
  action      text not null,
  entity_type text not null,
  entity_id   uuid,
  old_value   jsonb,
  new_value   jsonb,
  created_at  timestamptz not null default now()
);

-- ── role/department helpers (security definer → no RLS recursion) ─────
create or replace function public.jwt_role()
returns text language sql stable security definer set search_path = public as $$
  select role from public.profiles where id = auth.uid();
$$;

create or replace function public.jwt_department()
returns uuid language sql stable security definer set search_path = public as $$
  select department_id from public.profiles where id = auth.uid();
$$;

create or replace function public.jwt_student_id()
returns uuid language sql stable security definer set search_path = public as $$
  select s.id from public.students s join public.profiles p on p.id = s.profile_id
  where p.id = auth.uid();
$$;

-- Haversine distance in meters
create or replace function public.haversine_m(lat1 double precision, lng1 double precision, lat2 double precision, lng2 double precision)
returns double precision language sql immutable as $$
  select 2 * 6371000 * asin(sqrt(
    power(sin(radians(lat2 - lat1) / 2), 2) +
    cos(radians(lat1)) * cos(radians(lat2)) * power(sin(radians(lng2 - lng1) / 2), 2)
  ));
$$;

-- ── auto-create a profile when an auth user is created ────────────────
-- Reads role/full_name/phone from the sign-up metadata; defaults to student.
create or replace function public.handle_new_user()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  insert into public.profiles (id, role, full_name, email, phone)
  values (
    new.id,
    coalesce(new.raw_user_meta_data->>'role', 'student'),
    coalesce(new.raw_user_meta_data->>'full_name', ''),
    new.email,
    new.raw_user_meta_data->>'phone'
  )
  on conflict (id) do nothing;
  return new;
end $$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

-- ── lock role/is_active changes to admins (defense in depth) ──────────
create or replace function public.guard_profile_privileged_columns()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is null then           -- service role / SQL editor: allow
    return new;
  end if;
  if public.jwt_role() = 'admin' then  -- admins: allow
    return new;
  end if;
  if new.role is distinct from old.role
     or new.is_active is distinct from old.is_active
     or new.department_id is distinct from old.department_id then
    raise exception 'Not allowed to change role/is_active/department.';
  end if;
  return new;
end $$;

drop trigger if exists trg_profiles_guard on public.profiles;
create trigger trg_profiles_guard before update on public.profiles
  for each row execute function public.guard_profile_privileged_columns();
