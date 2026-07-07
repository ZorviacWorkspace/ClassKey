import { NextRequest, NextResponse } from 'next/server';
import { adminClient } from '@/lib/supabaseAdmin';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

/**
 * Secure account management (service role lives ONLY here / in the Edge Function).
 * - ADMIN: create students/staff/admins, reset passwords, delete accounts.
 * - STAFF: create students in their own department only.
 * The caller's access token is verified and the role comes from the profiles table.
 */
async function requireStaffOrAdmin(req: NextRequest) {
  const token = req.headers.get('authorization')?.replace(/^Bearer /, '');
  if (!token) return { error: 'Not signed in.', status: 401 as const };
  const sb = adminClient();
  const { data: userData, error } = await sb.auth.getUser(token);
  if (error || !userData.user) return { error: 'Session invalid.', status: 401 as const };
  const { data: prof } = await sb.from('profiles').select('role, department_id').eq('id', userData.user.id).single();
  if (!prof || !['admin', 'staff'].includes(prof.role)) return { error: 'Only admin or staff can manage accounts.', status: 403 as const };
  return { sb, callerId: userData.user.id, callerRole: prof.role as 'admin' | 'staff', callerDept: prof.department_id as string | null };
}

export async function POST(req: NextRequest) {
  try {
    const auth = await requireStaffOrAdmin(req);
    if ('error' in auth) return NextResponse.json({ error: auth.error }, { status: auth.status });
    const { sb, callerId, callerRole, callerDept } = auth;
    const body = await req.json();

    if (body.action === 'create') {
      let { role, full_name, email, phone, username, temp_password, register_number, department_id, year, section, staff_code, designation } = body;
      if (!role || !full_name || !email) return NextResponse.json({ error: 'Role, name and email are required.' }, { status: 400 });

      if (callerRole === 'staff') {
        if (role !== 'student') return NextResponse.json({ error: 'Staff can only create student accounts.' }, { status: 403 });
        department_id = callerDept;
      }

      const password = temp_password && String(temp_password).length >= 8 ? String(temp_password) : 'ChangeMe123!';
      const { data: created, error } = await sb.auth.admin.createUser({
        email,
        password,
        email_confirm: true,
        user_metadata: { role, full_name, phone },
      });
      if (error) return NextResponse.json({ error: error.message }, { status: 409 });
      const uid = created.user.id;

      const { error: pErr } = await sb.from('profiles').upsert(
        {
          id: uid, role, full_name, email: String(email).toLowerCase(),
          phone: phone || null, username: username || null,
          department_id: department_id || null, forced_password_change: true,
        },
        { onConflict: 'id' }
      );
      if (pErr) return NextResponse.json({ error: pErr.message }, { status: 409 });

      if (role === 'student') {
        if (!register_number) return NextResponse.json({ error: 'Register number required for students.' }, { status: 400 });
        const { error: sErr } = await sb.from('students').insert({
          profile_id: uid,
          register_number: String(register_number).toUpperCase(),
          department_id: department_id || null,
          year: year || null,
          section: section || null,
        });
        if (sErr) return NextResponse.json({ error: sErr.message }, { status: 409 });
      } else if (role === 'staff') {
        const code = staff_code || 'STF' + Math.floor(1000 + Math.random() * 9000);
        const { error: fErr } = await sb.from('staff').insert({
          profile_id: uid,
          staff_code: code,
          department_id: department_id || null,
          designation: designation || 'Staff',
        });
        if (fErr) return NextResponse.json({ error: fErr.message }, { status: 409 });
      }

      await sb.from('audit_logs').insert({ actor_id: callerId, action: 'ACCOUNT_CREATED', entity_type: 'profile', entity_id: uid, new_value: { role, email } });
      return NextResponse.json({ ok: true, message: `${role} account created. Temporary password set.` });
    }

    if (callerRole !== 'admin') return NextResponse.json({ error: 'Admin only.' }, { status: 403 });

    if (body.action === 'reset_password') {
      const { profile_id } = body;
      if (!profile_id) return NextResponse.json({ error: 'Missing profile_id.' }, { status: 400 });
      const { error } = await sb.auth.admin.updateUserById(profile_id, { password: 'ChangeMe123!' });
      if (error) return NextResponse.json({ error: error.message }, { status: 400 });
      await sb.from('profiles').update({ forced_password_change: true }).eq('id', profile_id);
      await sb.from('audit_logs').insert({ actor_id: callerId, action: 'PASSWORD_RESET', entity_type: 'profile', entity_id: profile_id });
      return NextResponse.json({ ok: true, message: 'Password reset to ChangeMe123!' });
    }

    if (body.action === 'delete') {
      const { profile_id } = body;
      if (!profile_id) return NextResponse.json({ error: 'Missing profile_id.' }, { status: 400 });
      if (profile_id === callerId) return NextResponse.json({ error: 'You cannot delete your own account.' }, { status: 400 });
      const { error } = await sb.auth.admin.deleteUser(profile_id);
      if (error) return NextResponse.json({ error: error.message }, { status: 400 });
      await sb.from('audit_logs').insert({ actor_id: callerId, action: 'ACCOUNT_DELETED', entity_type: 'profile', entity_id: profile_id });
      return NextResponse.json({ ok: true, message: 'Account deleted.' });
    }

    return NextResponse.json({ error: 'Unknown action.' }, { status: 400 });
  } catch (e: any) {
    return NextResponse.json({ error: e?.message || 'Server error.' }, { status: 500 });
  }
}
