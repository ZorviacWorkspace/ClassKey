// ClassKey — Edge Function: secure in-app user creation.
// Deploy:  supabase functions deploy create-user
// The service_role key is available to Edge Functions automatically as an env var —
// it never ships inside the Android app.
//
// Caller must be a signed-in ADMIN (create anyone) or STAFF (create students in
// their own department only). Body shape matches the Vercel /api/admin/users route:
//   { action: 'create', role, full_name, email, phone?, username?, temp_password?,
//     register_number?, department_id?, year?, section?, staff_code?, designation? }
//   { action: 'reset_password', profile_id }
//   { action: 'delete', profile_id }

import { createClient } from 'npm:@supabase/supabase-js@2';

const cors = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, content-type, apikey',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
};

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...cors, 'Content-Type': 'application/json' },
  });
}

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers: cors });
  if (req.method !== 'POST') return json({ error: 'POST only.' }, 405);

  try {
    const sb = createClient(
      Deno.env.get('SUPABASE_URL')!,
      Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!,
      { auth: { autoRefreshToken: false, persistSession: false } }
    );

    // 1) verify caller
    const token = req.headers.get('authorization')?.replace(/^Bearer /i, '');
    if (!token) return json({ error: 'Not signed in.' }, 401);
    const { data: userData, error: uErr } = await sb.auth.getUser(token);
    if (uErr || !userData.user) return json({ error: 'Session invalid.' }, 401);

    const { data: caller } = await sb
      .from('profiles')
      .select('role, department_id')
      .eq('id', userData.user.id)
      .single();
    if (!caller || !['admin', 'staff'].includes(caller.role)) {
      return json({ error: 'Only admin or staff can manage accounts.' }, 403);
    }

    const body = await req.json();

    // ── create ──
    if (body.action === 'create') {
      let { role, full_name, email, phone, username, temp_password,
        register_number, department_id, year, section, staff_code, designation } = body;

      if (!role || !full_name || !email) return json({ error: 'Role, name and email are required.' }, 400);

      // staff may only create students, always inside their own department
      if (caller.role === 'staff') {
        if (role !== 'student') return json({ error: 'Staff can only create student accounts.' }, 403);
        department_id = caller.department_id;
      }

      const password = temp_password && String(temp_password).length >= 8 ? String(temp_password) : 'ChangeMe123!';

      const { data: created, error } = await sb.auth.admin.createUser({
        email,
        password,
        email_confirm: true,
        user_metadata: { role, full_name, phone },
      });
      if (error) return json({ error: error.message }, 409);
      const uid = created.user.id;

      const { error: pErr } = await sb.from('profiles').upsert(
        {
          id: uid, role, full_name, email: String(email).toLowerCase(),
          phone: phone || null, username: username || null,
          department_id: department_id || null, forced_password_change: true,
        },
        { onConflict: 'id' }
      );
      if (pErr) return json({ error: pErr.message }, 409);

      if (role === 'student') {
        if (!register_number) return json({ error: 'Register number is required for students.' }, 400);
        const { error: sErr } = await sb.from('students').insert({
          profile_id: uid,
          register_number: String(register_number).toUpperCase(),
          department_id: department_id || null,
          year: year || null,
          section: section || null,
        });
        if (sErr) return json({ error: sErr.message }, 409);
      } else if (role === 'staff') {
        const code = staff_code || 'STF' + Math.floor(1000 + Math.random() * 9000);
        const { error: fErr } = await sb.from('staff').insert({
          profile_id: uid, staff_code: code,
          department_id: department_id || null, designation: designation || 'Staff',
        });
        if (fErr) return json({ error: fErr.message }, 409);
      }

      await sb.from('audit_logs').insert({
        actor_id: userData.user.id, action: 'ACCOUNT_CREATED',
        entity_type: 'profile', entity_id: uid, new_value: { role, email },
      });
      return json({ ok: true, message: `${role} account created. Temporary password set.` });
    }

    // ── reset_password / delete: admin only ──
    if (caller.role !== 'admin') return json({ error: 'Admin only.' }, 403);

    if (body.action === 'reset_password') {
      if (!body.profile_id) return json({ error: 'Missing profile_id.' }, 400);
      const { error } = await sb.auth.admin.updateUserById(body.profile_id, { password: 'ChangeMe123!' });
      if (error) return json({ error: error.message }, 400);
      await sb.from('profiles').update({ forced_password_change: true }).eq('id', body.profile_id);
      await sb.from('audit_logs').insert({ actor_id: userData.user.id, action: 'PASSWORD_RESET', entity_type: 'profile', entity_id: body.profile_id });
      return json({ ok: true, message: 'Password reset to ChangeMe123!' });
    }

    if (body.action === 'delete') {
      if (!body.profile_id) return json({ error: 'Missing profile_id.' }, 400);
      if (body.profile_id === userData.user.id) return json({ error: 'You cannot delete your own account.' }, 400);
      const { error } = await sb.auth.admin.deleteUser(body.profile_id);
      if (error) return json({ error: error.message }, 400);
      await sb.from('audit_logs').insert({ actor_id: userData.user.id, action: 'ACCOUNT_DELETED', entity_type: 'profile', entity_id: body.profile_id });
      return json({ ok: true, message: 'Account deleted.' });
    }

    return json({ error: 'Unknown action.' }, 400);
  } catch (e) {
    return json({ error: (e as Error).message || 'Server error.' }, 500);
  }
});
