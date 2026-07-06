// ClassKey — seed demo auth users + profiles + students/staff via the Supabase Admin API.
//
//   1) In Supabase SQL editor run: migrations 0001..0004, then seed_data.sql
//   2) cp .env.example .env  (set SUPABASE_URL + SUPABASE_SERVICE_ROLE_KEY)
//   3) npm install && npm run seed
//
// The service role key is admin-level — keep it secret, never ship it to a client.

import 'dotenv/config';
import { createClient } from '@supabase/supabase-js';

const url = process.env.SUPABASE_URL;
const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
if (!url || !serviceKey) {
  console.error('Set SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY in supabase/seed/.env');
  process.exit(1);
}

const sb = createClient(url, serviceKey, { auth: { autoRefreshToken: false, persistSession: false } });
const PASSWORD = 'ChangeMe123!';

const DEMO = [
  { role: 'admin', full_name: 'Dr. Anand Kumar', email: 'admin@classkey.local', phone: '9990001000' },
  { role: 'staff', full_name: 'Dr. Rajesh Kumar', email: 'staff@classkey.local', phone: '9990003000', staff_code: 'STF001', dept: 'CSE', designation: 'Associate Professor' },
  { role: 'student', full_name: 'Priya Sharma', email: 'priya.sharma@classkey.local', phone: '9990002000', register_number: 'CS21001', dept: 'CSE', year: 3, section: 'A' },
  { role: 'student', full_name: 'Arjun Mehta', email: 'arjun.mehta@classkey.local', phone: '9990002001', register_number: 'CS21002', dept: 'CSE', year: 3, section: 'A' },
  { role: 'student', full_name: 'Riya Patel', email: 'riya.patel@classkey.local', phone: '9990002002', register_number: 'CS21003', dept: 'CSE', year: 3, section: 'A' },
];

async function findUserByEmail(email) {
  // paginate through users (fine for a demo project)
  for (let page = 1; page <= 20; page++) {
    const { data, error } = await sb.auth.admin.listUsers({ page, perPage: 200 });
    if (error) throw error;
    const hit = data.users.find((u) => u.email?.toLowerCase() === email.toLowerCase());
    if (hit) return hit;
    if (data.users.length < 200) break;
  }
  return null;
}

async function ensureAuthUser(u) {
  const { data, error } = await sb.auth.admin.createUser({
    email: u.email,
    password: PASSWORD,
    email_confirm: true,
    user_metadata: { role: u.role, full_name: u.full_name, phone: u.phone },
  });
  if (error) {
    if (/already/i.test(error.message)) {
      const existing = await findUserByEmail(u.email);
      if (existing) return existing.id;
    }
    throw error;
  }
  return data.user.id;
}

async function main() {
  // department ids
  const { data: depts, error: dErr } = await sb.from('departments').select('id, code');
  if (dErr) throw dErr;
  const deptId = (code) => depts.find((d) => d.code === code)?.id ?? null;
  if (!deptId('CSE')) {
    console.error('Run seed_data.sql first (departments/campus missing).');
    process.exit(1);
  }

  for (const u of DEMO) {
    const id = await ensureAuthUser(u);

    // profile (trigger may have created it; upsert to be sure)
    const { error: pErr } = await sb.from('profiles').upsert(
      {
        id,
        role: u.role,
        full_name: u.full_name,
        email: u.email,
        phone: u.phone,
        department_id: u.dept ? deptId(u.dept) : null,
        forced_password_change: false,
      },
      { onConflict: 'id' }
    );
    if (pErr) throw pErr;

    if (u.role === 'student') {
      const { error } = await sb.from('students').upsert(
        {
          profile_id: id,
          register_number: u.register_number,
          department_id: deptId(u.dept),
          year: u.year,
          section: u.section,
          device_status: 'not_bound',
        },
        { onConflict: 'register_number' }
      );
      if (error) throw error;
    } else if (u.role === 'staff') {
      const { error } = await sb.from('staff').upsert(
        { profile_id: id, staff_code: u.staff_code, department_id: deptId(u.dept), designation: u.designation },
        { onConflict: 'staff_code' }
      );
      if (error) throw error;
    }

    console.log(`✓ ${u.role.padEnd(7)} ${u.email}`);
  }

  console.log('\nDone. Demo password for all accounts: ChangeMe123!');
}

main().catch((e) => {
  console.error('Seed failed:', e.message || e);
  process.exit(1);
});
