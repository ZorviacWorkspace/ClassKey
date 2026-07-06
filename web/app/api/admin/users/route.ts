import { NextRequest, NextResponse } from 'next/server';
import { adminClient } from '@/lib/supabaseAdmin';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

/**
 * Admin-only account management (needs the service role to create auth users).
 * The caller must send their own access token; we verify their profile is admin.
 * Actions: create (student/staff/admin), reset_password, delete.
 */
async function requireAdmin(req: NextRequest) {
  const token = req.headers.get('authorization')?.replace(/^Bearer /, '');
  if (!token) return { error: 'Not signed in.', status: 401 as const };
  const sb = adminClient();
  const { data: userData, error } = await sb.auth.getUser(token);
  if (error || !userData.user) return { error: 'Session invalid.', status: 401 as const };
  const { data: prof } = await sb.from('profiles').select('role').eq('id', userData.user.id).single();
  if (prof?.role !== 'admin') return { error: 'Admin only.', status: 403 as const };
  return { sb, adminId: userData.user.id };
}

export async function POST(req: NextRequest) {
  try {
    const auth = await requireAdmin(req);
    if ('error' in auth) return NextResponse.json({ error: auth.error }, { status: auth.status });
    const { sb, adminId } = auth;
    const body = await req.json();

    if (body.action === 'create') {
      const { role, full_name, email, phone, register_number, department_id, year, section, staff_code, designation } = body;
      if (!role || !full_name || !email) return NextResponse.json({ error: 'Role, name and email are required.' }, { status: 400 });

      const { data: created, error } = await sb.auth.admin.createUser({
        email,
        password: 'ChangeMe123!',
        email_confirm: true,
        user_metadata: { role, full_name, phone },
      });
      if (error) return NextResponse.json({ error: error.message }, { status: 409 });
      const uid = created.user.id;

      await sb.from('profiles').upsert(
        { id: uid, role, full_name, email: email.toLowerCase(), phone: phone || null, department_id: department_id || null, forced_password_change: true },
        { onConflict: 'id' }
      );

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

      await sb.from('audit_logs').insert({ actor_id: adminId, action: 'ACCOUNT_CREATED', entity_type: 'profile', entity_id: uid, new_value: { role, email } });
      return NextResponse.json({ ok: true, message: `${role} account created. Password: ChangeMe123!` });
    }

    if (body.action === 'reset_password') {
      const { profile_id } = body;
      if (!profile_id) return NextResponse.json({ error: 'Missing profile_id.' }, { status: 400 });
      const { error } = await sb.auth.admin.updateUserById(profile_id, { password: 'ChangeMe123!' });
      if (error) return NextResponse.json({ error: error.message }, { status: 400 });
      await sb.from('profiles').update({ forced_password_change: true }).eq('id', profile_id);
      await sb.from('audit_logs').insert({ actor_id: adminId, action: 'PASSWORD_RESET', entity_type: 'profile', entity_id: profile_id });
      return NextResponse.json({ ok: true, message: 'Password reset to ChangeMe123!' });
    }

    if (body.action === 'delete') {
      const { profile_id } = body;
      if (!profile_id) return NextResponse.json({ error: 'Missing profile_id.' }, { status: 400 });
      if (profile_id === adminId) return NextResponse.json({ error: 'You cannot delete your own account.' }, { status: 400 });
      const { error } = await sb.auth.admin.deleteUser(profile_id); // cascades to profiles → students/staff
      if (error) return NextResponse.json({ error: error.message }, { status: 400 });
      await sb.from('audit_logs').insert({ actor_id: adminId, action: 'ACCOUNT_DELETED', entity_type: 'profile', entity_id: profile_id });
      return NextResponse.json({ ok: true, message: 'Account deleted.' });
    }

    return NextResponse.json({ error: 'Unknown action.' }, { status: 400 });
  } catch (e: any) {
    return NextResponse.json({ error: e?.message || 'Server error.' }, { status: 500 });
  }
}
