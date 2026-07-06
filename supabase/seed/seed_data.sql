-- ClassKey — non-auth seed data (departments + campus).
-- Safe to run in the SQL editor. Auth users are created by seed.mjs (service role).

insert into public.departments (name, code) values
  ('Computer Science', 'CSE'),
  ('Electronics', 'ECE')
on conflict (code) do nothing;

insert into public.campus_config (college_name, latitude, longitude, allowed_radius_meters, present_until, late_until, absent_after)
select 'ClassKey College', 11.01830, 76.97250, 300, '09:30', '13:00', '18:00'
where not exists (select 1 from public.campus_config);
