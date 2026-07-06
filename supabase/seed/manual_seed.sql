-- ClassKey — manual seed (no Node needed).
-- FIRST: Supabase Dashboard → Authentication → Users → "Add user" → create these three,
-- ticking "Auto Confirm User", all with password: ChangeMe123!
--   admin@classkey.local
--   staff@classkey.local
--   priya.sharma@classkey.local
-- THEN run this whole file in the SQL Editor. (Profiles are auto-created by trigger;
-- this sets their roles and creates the student/staff rows.)

-- roles + names + phones + department
update public.profiles p set
  role = 'admin', full_name = 'Dr. Anand Kumar', phone = '9990001000', forced_password_change = false
where p.email = 'admin@classkey.local';

update public.profiles p set
  role = 'staff', full_name = 'Dr. Rajesh Kumar', phone = '9990003000', forced_password_change = false,
  department_id = (select id from public.departments where code = 'CSE')
where p.email = 'staff@classkey.local';

update public.profiles p set
  role = 'student', full_name = 'Priya Sharma', phone = '9990002000', forced_password_change = false,
  department_id = (select id from public.departments where code = 'CSE')
where p.email = 'priya.sharma@classkey.local';

-- staff row
insert into public.staff (profile_id, staff_code, department_id, designation)
select p.id, 'STF001', (select id from public.departments where code='CSE'), 'Associate Professor'
from public.profiles p where p.email = 'staff@classkey.local'
on conflict (staff_code) do nothing;

-- student row
insert into public.students (profile_id, register_number, department_id, year, section)
select p.id, 'CS21001', (select id from public.departments where code='CSE'), 3, 'A'
from public.profiles p where p.email = 'priya.sharma@classkey.local'
on conflict (register_number) do nothing;

-- sanity check: should list 3 rows with correct roles
select p.email, p.role, s.register_number, st.staff_code
from public.profiles p
left join public.students s on s.profile_id = p.id
left join public.staff st on st.profile_id = p.id
where p.email in ('admin@classkey.local','staff@classkey.local','priya.sharma@classkey.local');
