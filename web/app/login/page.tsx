'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { supabase } from '@/lib/supabaseClient';
import { Spinner } from '../ui/parts';

type Role = 'student' | 'staff' | 'admin';

const ROLE_META: Record<Role, { title: string; sub: string; icon: string; idLabel: string; demo: string }> = {
  student: { title: 'Student', sub: 'Mark attendance · history · requests', icon: '🎓', idLabel: 'Register number / Email / Phone', demo: 'CS21001' },
  staff: { title: 'Staff', sub: 'Live attendance · approvals · reports', icon: '🧑‍🏫', idLabel: 'Staff email / Phone', demo: 'staff@classkey.local' },
  admin: { title: 'Admin', sub: 'Manage everything · campus · audit', icon: '🛡️', idLabel: 'Admin email / Phone', demo: 'admin@classkey.local' },
};

export default function LoginPage() {
  const router = useRouter();
  const [role, setRole] = useState<Role | null>(null);
  const [identifier, setIdentifier] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function doLogin() {
    if (!role) return;
    setBusy(true);
    setError(null);
    try {
      let email = identifier.trim().toLowerCase();
      if (!email.includes('@')) {
        const r = await fetch('/api/resolve-identifier', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ identifier, role }),
        });
        const j = await r.json();
        if (!r.ok) throw new Error(j.error || 'Account lookup failed.');
        email = j.email;
      }
      const { data, error: authErr } = await supabase.auth.signInWithPassword({ email, password });
      if (authErr) throw new Error('Wrong password or account does not exist.');

      // Server-side truth: the profile row decides the role, not the button the user tapped.
      const { data: prof, error: pErr } = await supabase.from('profiles').select('role, is_active').eq('id', data.user.id).single();
      if (pErr || !prof) {
        await supabase.auth.signOut();
        throw new Error('Profile not found. Ask admin to set up your account.');
      }
      if (!prof.is_active) {
        await supabase.auth.signOut();
        throw new Error('This account is deactivated. Contact admin.');
      }
      if (prof.role !== role) {
        await supabase.auth.signOut();
        throw new Error(`This is a ${prof.role} account. Please use the ${prof.role} login.`);
      }
      router.replace(`/${prof.role}`);
    } catch (e: any) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="center-screen">
      <div className="center">
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img src="/logo.png" alt="ClassKey" width={74} height={74} />
        <div className="h1 mt8">ClassKey</div>
        <div className="muted small">Secure attendance. Verified presence.</div>
      </div>

      {!role ? (
        <div className="col gap10 mt20">
          <div className="section-title center" style={{ margin: '0 0 4px' }}>Sign in as</div>
          {(Object.keys(ROLE_META) as Role[]).map((r) => {
            const m = ROLE_META[r];
            return (
              <button key={r} className="role-card" onClick={() => { setRole(r); setIdentifier(''); setPassword(''); setError(null); }}>
                <span className="ic" style={{ background: r === 'student' ? 'var(--soft-blue)' : r === 'staff' ? 'var(--soft-teal)' : 'var(--soft-purple)' }}>{m.icon}</span>
                <span className="grow">
                  <b>{m.title}</b>
                  <div className="muted small">{m.sub}</div>
                </span>
                <span className="muted">›</span>
              </button>
            );
          })}
          <div className="center muted tiny mt8">Roles are verified by the server after login.</div>
        </div>
      ) : (
        <div className="card mt20">
          <div className="row gap10 mb12">
            <button className="pill" onClick={() => setRole(null)}>‹ Back</button>
            <div className="h2">{ROLE_META[role].icon} {ROLE_META[role].title} login</div>
          </div>
          <label className="field">{ROLE_META[role].idLabel}</label>
          <input className="input" value={identifier} onChange={(e) => setIdentifier(e.target.value)} autoFocus />
          <label className="field">Password</label>
          <input className="input" type="password" value={password} onChange={(e) => setPassword(e.target.value)} onKeyDown={(e) => e.key === 'Enter' && doLogin()} />
          {error && <div className="error-box mt12">{error}</div>}
          <button className="btn btn-primary mt16" disabled={busy || !identifier || !password} onClick={doLogin}>
            {busy ? <Spinner /> : 'Login'}
          </button>
          <button
            className="btn btn-ghost mt8"
            onClick={() => { setIdentifier(ROLE_META[role].demo); setPassword('ChangeMe123!'); }}
          >
            Fill demo {role} login
          </button>
        </div>
      )}
      <div className="center muted tiny mt16">Works on any phone browser · Add to Home screen for app mode</div>
    </div>
  );
}
