import { NextRequest, NextResponse } from 'next/server';
import { adminClient } from '@/lib/supabaseAdmin';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

/**
 * Students can log in with register number / email / phone; staff and admin with
 * email / phone. Supabase Auth needs an email, so this server route (service role)
 * resolves identifier → email for the selected role. The role is re-verified after
 * login from the profiles table — the UI selection is never trusted.
 */
export async function POST(req: NextRequest) {
  try {
    const { identifier, role } = (await req.json()) as { identifier?: string; role?: string };
    const id = (identifier || '').trim();
    if (!id || !role || !['student', 'staff', 'admin'].includes(role)) {
      return NextResponse.json({ error: 'Missing identifier or role.' }, { status: 400 });
    }
    if (id.includes('@')) return NextResponse.json({ email: id.toLowerCase() });

    const sb = adminClient();

    if (role === 'student') {
      const { data } = await sb
        .from('students')
        .select('profiles(email)')
        .ilike('register_number', id)
        .maybeSingle();
      const email = (data as any)?.profiles?.email;
      if (email) return NextResponse.json({ email });
    }

    const { data: byPhone } = await sb
      .from('profiles')
      .select('email')
      .eq('phone', id)
      .eq('role', role)
      .maybeSingle();
    if (byPhone?.email) return NextResponse.json({ email: byPhone.email });

    return NextResponse.json({ error: `No ${role} account found for "${id}".` }, { status: 404 });
  } catch (e: any) {
    return NextResponse.json({ error: e?.message || 'Lookup failed.' }, { status: 500 });
  }
}
