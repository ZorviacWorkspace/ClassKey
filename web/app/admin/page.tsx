'use client';

import { useCallback, useEffect, useState } from 'react';
import { supabase } from '@/lib/supabaseClient';
import { useProfile } from '@/lib/useProfile';
import { hhmm, prettyDate, todayISO } from '@/lib/client';
import { Avatar, Chip, Field, Loading, Modal, Spinner, StatCard, Toast } from '../ui/parts';
import Reports from '../ui/Reports';
import OverrideModal from '../ui/OverrideModal';

const TABS = ['overview', 'students', 'staff', 'departments', 'campus', 'devices', 'audit', 'reports'] as const;
type Tab = (typeof TABS)[number];

async function adminApi(body: any): Promise<{ ok?: boolean; message?: string; error?: string }> {
  const { data: sess } = await supabase.auth.getSession();
  const res = await fetch('/api/admin/users', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${sess.session?.access_token}` },
    body: JSON.stringify(body),
  });
  return res.json();
}

export default function AdminPage() {
  const { profile, loading, signOut } = useProfile(['admin']);
  const [tab, setTab] = useState<Tab>('overview');
  const [toast, setToast] = useState<string | null>(null);

  if (loading || !profile) return <div className="center-screen center"><Spinner blue /></div>;

  return (
    <div className="wide">
      <div className="topbar">
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img src="/logo.png" alt="" width={40} height={40} />
        <div className="grow">
          <div className="h2">ClassKey · Admin</div>
          <div className="muted small">{profile.full_name}</div>
        </div>
        <button className="pill" onClick={signOut}>Sign out</button>
      </div>
      <div className="tabbar">
        {TABS.map((t) => (
          <button key={t} className={`pill${tab === t ? ' active' : ''}`} onClick={() => setTab(t)}>
            {t[0].toUpperCase() + t.slice(1)}
          </button>
        ))}
      </div>
      {tab === 'overview' && <Overview toast={setToast} />}
      {tab === 'students' && <Students toast={setToast} />}
      {tab === 'staff' && <StaffTab toast={setToast} />}
      {tab === 'departments' && <Departments toast={setToast} />}
      {tab === 'campus' && <Campus toast={setToast} />}
      {tab === 'devices' && <Devices toast={setToast} />}
      {tab === 'audit' && <Audit />}
      {tab === 'reports' && <Reports showDepartmentFilter />}
      {toast && <Toast text={toast} onClose={() => setToast(null)} />}
    </div>
  );
}

function Overview({ toast }: { toast: (s: string) => void }) {
  const [rows, setRows] = useState<any[] | null>(null);
  const [total, setTotal] = useState(0);
  const [pending, setPending] = useState(0);

  const load = useCallback(async () => {
    const { data } = await supabase.from('attendance').select('status').eq('attendance_date', todayISO());
    setRows(data ?? []);
    const { count } = await supabase.from('students').select('*', { count: 'exact', head: true });
    setTotal(count ?? 0);
    const { count: p } = await supabase.from('attendance_requests').select('*', { count: 'exact', head: true }).eq('status', 'pending');
    setPending(p ?? 0);
  }, []);
  useEffect(() => {
    load();
    const ch = supabase.channel('admin-live')
      .on('postgres_changes', { event: '*', schema: 'public', table: 'attendance' }, load)
      .on('postgres_changes', { event: '*', schema: 'public', table: 'attendance_requests' }, load)
      .subscribe();
    return () => { supabase.removeChannel(ch); };
  }, [load]);

  async function runAutoAbsent() {
    const { data, error } = await supabase.rpc('auto_mark_absentees', { p_date: todayISO() });
    if (error) { toast(error.message); return; }
    const r = data as any;
    toast(r.ok ? `Marked ${r.marked_absent} students absent.` : r.message);
    load();
  }

  if (rows === null) return <Loading />;
  const c = (s: string) => rows.filter((r) => r.status === s).length;

  return (
    <>
      <div className="hero mt8">
        <div className="row gap8"><span className="live-dot" /><span className="small" style={{ opacity: 0.9 }}>LIVE college overview</span></div>
        <div style={{ fontSize: 19, fontWeight: 800, marginTop: 4 }}>
          {new Date().toLocaleDateString(undefined, { weekday: 'long', day: 'numeric', month: 'long' })}
        </div>
        <div className="small" style={{ opacity: 0.9 }}>{rows.length} of {total} students marked · {pending} pending requests</div>
      </div>
      <div className="row gap8 mt12">
        <StatCard label="Present" value={c('present')} color="var(--success)" />
        <StatCard label="Late" value={c('late')} color="var(--warning)" />
        <StatCard label="Absent" value={c('absent')} color="var(--error)" />
        <StatCard label="Unmarked" value={Math.max(0, total - rows.length)} color="var(--muted)" />
      </div>
      <div className="card mt12">
        <div className="h2 mb8">End of day</div>
        <div className="muted small mb12">After the cutoff, mark every student without a record as Absent for today.</div>
        <button className="btn btn-ghost" onClick={runAutoAbsent}>Run auto-absent for today</button>
      </div>
    </>
  );
}

function Students({ toast }: { toast: (s: string) => void }) {
  const [rows, setRows] = useState<any[] | null>(null);
  const [depts, setDepts] = useState<any[]>([]);
  const [search, setSearch] = useState('');
  const [addOpen, setAddOpen] = useState(false);
  const [target, setTarget] = useState<any>(null);
  const [override, setOverride] = useState<any>(null);

  const load = useCallback(async () => {
    const { data } = await supabase.from('students').select('*, profiles(id, full_name, email, phone, is_active)').order('register_number');
    setRows(data ?? []);
    const { data: d } = await supabase.from('departments').select('*');
    setDepts(d ?? []);
  }, []);
  useEffect(() => { load(); }, [load]);

  if (rows === null) return <Loading />;
  const shown = rows.filter((s) => !search || s.profiles?.full_name?.toLowerCase().includes(search.toLowerCase()) || s.register_number?.toLowerCase().includes(search.toLowerCase()));

  return (
    <>
      <div className="between mt8">
        <input className="input grow" placeholder="Search students…" value={search} onChange={(e) => setSearch(e.target.value)} />
        <button className="btn btn-primary btn-sm" style={{ marginLeft: 8 }} onClick={() => setAddOpen(true)}>+ Student</button>
      </div>
      <div className="card mt12" style={{ overflowX: 'auto' }}>
        <table className="tbl">
          <thead><tr><th>Student</th><th className="hide-sm">Contact</th><th>Device</th><th></th></tr></thead>
          <tbody>
            {shown.map((s) => (
              <tr key={s.id}>
                <td>
                  <div className="row gap8">
                    <Avatar name={s.profiles?.full_name ?? '?'} size={32} />
                    <div>
                      <div style={{ fontWeight: 600 }}>{s.profiles?.full_name}</div>
                      <div className="muted tiny">{s.register_number} · Yr {s.year ?? '-'} {s.section ?? ''}</div>
                    </div>
                  </div>
                </td>
                <td className="hide-sm muted tiny">{s.profiles?.email}<br />{s.profiles?.phone}</td>
                <td><span className={`chip ${s.device_status === 'approved' ? 'st-present' : s.device_status === 'pending_replacement' ? 'st-late' : 'st-none'}`}>{s.device_status.replace(/_/g, ' ')}</span></td>
                <td><button className="pill" onClick={() => setTarget(s)}>Manage</button></td>
              </tr>
            ))}
            {shown.length === 0 && <tr><td colSpan={4} className="muted center">No students.</td></tr>}
          </tbody>
        </table>
      </div>

      {addOpen && <AddUserModal role="student" depts={depts} onClose={() => setAddOpen(false)} onDone={() => { setAddOpen(false); load(); }} toast={toast} />}
      {target && (
        <Modal title={target.profiles?.full_name ?? 'Student'} onClose={() => setTarget(null)}>
          <div className="muted small">{target.register_number} · {target.profiles?.email}</div>
          <div className="col gap8 mt12">
            <button className="btn btn-ghost" onClick={() => { setOverride(target); setTarget(null); }}>Manual attendance override</button>
            <button className="btn btn-ghost" onClick={async () => { const r = await adminApi({ action: 'reset_password', profile_id: target.profiles?.id }); toast(r.message || r.error || ''); }}>Reset password to ChangeMe123!</button>
            <button className="btn btn-danger" onClick={async () => {
              const r = await adminApi({ action: 'delete', profile_id: target.profiles?.id });
              toast(r.message || r.error || '');
              setTarget(null); load();
            }}>Delete account</button>
          </div>
        </Modal>
      )}
      {override && <OverrideModal student={override} onClose={() => setOverride(null)} onDone={() => { setOverride(null); load(); }} toast={toast} />}
    </>
  );
}

function StaffTab({ toast }: { toast: (s: string) => void }) {
  const [rows, setRows] = useState<any[] | null>(null);
  const [depts, setDepts] = useState<any[]>([]);
  const [addOpen, setAddOpen] = useState(false);

  const load = useCallback(async () => {
    const { data } = await supabase.from('staff').select('*, profiles(id, full_name, email, phone, role)').order('staff_code');
    setRows(data ?? []);
    const { data: d } = await supabase.from('departments').select('*');
    setDepts(d ?? []);
  }, []);
  useEffect(() => { load(); }, [load]);

  if (rows === null) return <Loading />;
  return (
    <>
      <div className="between mt8">
        <div className="h2">Staff & admins</div>
        <button className="btn btn-primary btn-sm" onClick={() => setAddOpen(true)}>+ Staff / Admin</button>
      </div>
      <div className="card mt12">
        {rows.map((s) => (
          <div className="list-row" key={s.id}>
            <Avatar name={s.profiles?.full_name ?? '?'} size={36} />
            <div className="grow">
              <div style={{ fontWeight: 600 }}>{s.profiles?.full_name}</div>
              <div className="muted tiny">{s.staff_code} · {s.designation} · {s.profiles?.email}</div>
            </div>
            <button className="pill" onClick={async () => { const r = await adminApi({ action: 'reset_password', profile_id: s.profiles?.id }); toast(r.message || r.error || ''); }}>Reset pwd</button>
          </div>
        ))}
        {rows.length === 0 && <div className="muted small center">No staff yet.</div>}
      </div>
      {addOpen && <AddUserModal role="staff" depts={depts} onClose={() => setAddOpen(false)} onDone={() => { setAddOpen(false); load(); }} toast={toast} />}
    </>
  );
}

function AddUserModal({ role: initialRole, depts, onClose, onDone, toast }: { role: 'student' | 'staff'; depts: any[]; onClose: () => void; onDone: () => void; toast: (s: string) => void }) {
  const [role, setRole] = useState<'student' | 'staff' | 'admin'>(initialRole);
  const [f, setF] = useState({ full_name: '', email: '', phone: '', register_number: '', department_id: '', year: '3', section: 'A', designation: 'Staff' });
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function add() {
    setBusy(true);
    setError(null);
    const r = await adminApi({
      action: 'create',
      role,
      full_name: f.full_name,
      email: f.email,
      phone: f.phone,
      register_number: f.register_number,
      department_id: f.department_id || null,
      year: Number(f.year) || null,
      section: f.section,
      designation: f.designation,
    });
    setBusy(false);
    if (r.error) { setError(r.error); return; }
    toast(r.message || 'Created.');
    onDone();
  }

  return (
    <Modal title="Add account" onClose={onClose}>
      <div className="row gap6">
        {(['student', 'staff', 'admin'] as const).map((x) => (
          <button key={x} className={`pill${role === x ? ' active' : ''}`} onClick={() => setRole(x)}>{x}</button>
        ))}
      </div>
      <Field label="Full name"><input className="input" value={f.full_name} onChange={(e) => setF({ ...f, full_name: e.target.value })} /></Field>
      <Field label="Email"><input className="input" value={f.email} onChange={(e) => setF({ ...f, email: e.target.value })} /></Field>
      <Field label="Phone"><input className="input" value={f.phone} onChange={(e) => setF({ ...f, phone: e.target.value.replace(/\D/g, '') })} /></Field>
      {role !== 'admin' && (
        <Field label="Department">
          <select className="input" value={f.department_id} onChange={(e) => setF({ ...f, department_id: e.target.value })}>
            <option value="">— select —</option>
            {depts.map((d) => <option key={d.id} value={d.id}>{d.code} — {d.name}</option>)}
          </select>
        </Field>
      )}
      {role === 'student' && (
        <>
          <Field label="Register number"><input className="input" value={f.register_number} onChange={(e) => setF({ ...f, register_number: e.target.value })} /></Field>
          <div className="grid2">
            <Field label="Year"><input className="input" value={f.year} onChange={(e) => setF({ ...f, year: e.target.value.replace(/\D/g, '') })} /></Field>
            <Field label="Section"><input className="input" value={f.section} onChange={(e) => setF({ ...f, section: e.target.value })} /></Field>
          </div>
        </>
      )}
      {role === 'staff' && (
        <Field label="Designation"><input className="input" value={f.designation} onChange={(e) => setF({ ...f, designation: e.target.value })} /></Field>
      )}
      <div className="muted tiny mt8">Login password will be ChangeMe123! (user is asked to change it).</div>
      {error && <div className="error-box mt8">{error}</div>}
      <button className="btn btn-primary mt12" disabled={busy} onClick={add}>{busy ? <Spinner /> : 'Create account'}</button>
    </Modal>
  );
}

function Departments({ toast }: { toast: (s: string) => void }) {
  const [rows, setRows] = useState<any[] | null>(null);
  const [name, setName] = useState('');
  const [code, setCode] = useState('');
  const load = useCallback(async () => {
    const { data } = await supabase.from('departments').select('*').order('code');
    setRows(data ?? []);
  }, []);
  useEffect(() => { load(); }, [load]);
  async function add() {
    if (!name || !code) { toast('Name and code required.'); return; }
    const { error } = await supabase.from('departments').insert({ name, code: code.toUpperCase() });
    if (error) { toast(error.message); return; }
    setName(''); setCode('');
    toast('Department added.');
    load();
  }
  if (rows === null) return <Loading />;
  return (
    <>
      <div className="card mt8">
        <div className="h2 mb8">Add department</div>
        <div className="grid2">
          <Field label="Name"><input className="input" value={name} onChange={(e) => setName(e.target.value)} /></Field>
          <Field label="Code"><input className="input" value={code} onChange={(e) => setCode(e.target.value)} /></Field>
        </div>
        <button className="btn btn-primary mt12" onClick={add}>Add</button>
      </div>
      <div className="card mt12">
        {rows.map((d) => (
          <div className="list-row" key={d.id}>
            <span className="chip st-blue">{d.code}</span>
            <div className="grow" style={{ fontWeight: 600 }}>{d.name}</div>
          </div>
        ))}
      </div>
    </>
  );
}

function Campus({ toast }: { toast: (s: string) => void }) {
  const [cfg, setCfg] = useState<any>(null);
  const [f, setF] = useState<any>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    (async () => {
      const { data } = await supabase.from('campus_config').select('*').limit(1).maybeSingle();
      setCfg(data);
      setF({
        college_name: data?.college_name ?? 'ClassKey College',
        latitude: String(data?.latitude ?? ''),
        longitude: String(data?.longitude ?? ''),
        allowed_radius_meters: String(data?.allowed_radius_meters ?? 300),
        morning_open: hhmm(data?.morning_open ?? '08:00'),
        present_until: hhmm(data?.present_until ?? '09:30'),
        late_until: hhmm(data?.late_until ?? '13:00'),
        afternoon_open: hhmm(data?.afternoon_open ?? '13:00'),
        afternoon_present_until: hhmm(data?.afternoon_present_until ?? '14:00'),
        afternoon_late_until: hhmm(data?.afternoon_late_until ?? '15:30'),
        absent_after: hhmm(data?.absent_after ?? '18:00'),
        min_accuracy_meters: String(data?.min_accuracy_meters ?? 100),
      });
    })();
  }, []);

  if (!f) return <Loading />;

  function useMyLocation() {
    setBusy(true);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setF((p: any) => ({ ...p, latitude: pos.coords.latitude.toFixed(5), longitude: pos.coords.longitude.toFixed(5) }));
        setBusy(false);
        toast('Coordinates captured — press Save.');
      },
      () => { setBusy(false); setError('Could not get location. Allow location and retry.'); },
      { enableHighAccuracy: true, timeout: 15000 }
    );
  }

  async function save() {
    setError(null);
    const patch = {
      college_name: f.college_name,
      latitude: Number(f.latitude),
      longitude: Number(f.longitude),
      allowed_radius_meters: Number(f.allowed_radius_meters),
      morning_open: f.morning_open,
      present_until: f.present_until,
      late_until: f.late_until,
      afternoon_open: f.afternoon_open,
      afternoon_present_until: f.afternoon_present_until,
      afternoon_late_until: f.afternoon_late_until,
      absent_after: f.absent_after,
      min_accuracy_meters: Number(f.min_accuracy_meters),
      updated_at: new Date().toISOString(),
    };
    if (Number.isNaN(patch.latitude) || Number.isNaN(patch.longitude)) { setError('Latitude/longitude must be numbers.'); return; }
    if (!(patch.morning_open < patch.present_until && patch.present_until < patch.late_until)) { setError('Morning: need open < present-until < late-until.'); return; }
    if (!(patch.afternoon_open < patch.afternoon_present_until && patch.afternoon_present_until < patch.afternoon_late_until)) { setError('Afternoon: need open < present-until < late-until.'); return; }
    const { error: e } = cfg
      ? await supabase.from('campus_config').update(patch).eq('id', cfg.id)
      : await supabase.from('campus_config').insert(patch);
    if (e) { setError(e.message); return; }
    toast('Campus settings saved.');
  }

  return (
    <div className="card mt8">
      <div className="h2 mb8">Campus geofence & timing</div>
      <Field label="College name"><input className="input" value={f.college_name} onChange={(e) => setF({ ...f, college_name: e.target.value })} /></Field>
      <div className="grid2">
        <Field label="Latitude"><input className="input" value={f.latitude} onChange={(e) => setF({ ...f, latitude: e.target.value })} /></Field>
        <Field label="Longitude"><input className="input" value={f.longitude} onChange={(e) => setF({ ...f, longitude: e.target.value })} /></Field>
      </div>
      <div className="grid2">
        <Field label="Allowed radius (m)"><input className="input" value={f.allowed_radius_meters} onChange={(e) => setF({ ...f, allowed_radius_meters: e.target.value.replace(/\D/g, '') })} /></Field>
        <Field label="Min GPS accuracy (m)"><input className="input" value={f.min_accuracy_meters} onChange={(e) => setF({ ...f, min_accuracy_meters: e.target.value.replace(/\D/g, '') })} /></Field>
      </div>
      <div className="section-title">Morning session</div>
      <div className="grid2">
        <Field label="Opens (HH:mm)"><input className="input" value={f.morning_open} onChange={(e) => setF({ ...f, morning_open: e.target.value })} /></Field>
        <Field label="Present until"><input className="input" value={f.present_until} onChange={(e) => setF({ ...f, present_until: e.target.value })} /></Field>
      </div>
      <Field label="Late until (morning closes)"><input className="input" value={f.late_until} onChange={(e) => setF({ ...f, late_until: e.target.value })} /></Field>
      <div className="section-title">Afternoon session</div>
      <div className="grid2">
        <Field label="Opens (HH:mm)"><input className="input" value={f.afternoon_open} onChange={(e) => setF({ ...f, afternoon_open: e.target.value })} /></Field>
        <Field label="Present until"><input className="input" value={f.afternoon_present_until} onChange={(e) => setF({ ...f, afternoon_present_until: e.target.value })} /></Field>
      </div>
      <Field label="Late until (afternoon closes)"><input className="input" value={f.afternoon_late_until} onChange={(e) => setF({ ...f, afternoon_late_until: e.target.value })} /></Field>
      <Field label="Absent after (HH:mm) — used by auto-absent"><input className="input" value={f.absent_after} onChange={(e) => setF({ ...f, absent_after: e.target.value })} /></Field>
      <button className="btn btn-ghost mt12" disabled={busy} onClick={useMyLocation}>{busy ? <Spinner blue /> : '◎ Set to my current location'}</button>
      {error && <div className="error-box mt12">{error}</div>}
      <button className="btn btn-primary mt12" onClick={save}>Save Campus Settings</button>
      <div className="muted tiny mt8">Marking before &quot;Present until&quot; = Present · until &quot;Late until&quot; = Late · after that self-marking closes.</div>
    </div>
  );
}

function Devices({ toast }: { toast: (s: string) => void }) {
  const [rows, setRows] = useState<any[] | null>(null);
  const load = useCallback(async () => {
    const { data } = await supabase
      .from('device_approvals')
      .select('*, students(register_number, profiles(full_name))')
      .order('requested_at', { ascending: false })
      .limit(50);
    setRows(data ?? []);
  }, []);
  useEffect(() => {
    load();
    const ch = supabase.channel('admin-devices')
      .on('postgres_changes', { event: '*', schema: 'public', table: 'device_approvals' }, load)
      .subscribe();
    return () => { supabase.removeChannel(ch); };
  }, [load]);

  async function decide(id: string, decision: 'approved' | 'rejected') {
    const { data, error } = await supabase.rpc('approve_device', { p_approval_id: id, p_decision: decision });
    if (error) { toast(error.message); return; }
    toast((data as any).message);
    load();
  }

  if (rows === null) return <Loading />;
  const pending = rows.filter((r) => r.status === 'pending');

  return (
    <>
      {pending.length === 0 && <div className="card mt8 muted small center">No pending device replacements.</div>}
      {pending.map((r) => (
        <div className="card mt12" key={r.id}>
          <div style={{ fontWeight: 700 }}>{r.students?.profiles?.full_name} · {r.students?.register_number}</div>
          <div className="muted tiny mt8">Old: {r.old_device_id ?? '—'}<br />New: {r.new_device_id}</div>
          <div className="row gap10 mt12">
            <button className="btn btn-ghost btn-sm grow" onClick={() => decide(r.id, 'rejected')}>Reject</button>
            <button className="btn btn-primary btn-sm grow" onClick={() => decide(r.id, 'approved')}>Approve device</button>
          </div>
        </div>
      ))}
      {rows.filter((r) => r.status !== 'pending').slice(0, 10).map((r) => (
        <div className="card mt8" key={r.id}>
          <div className="between">
            <div className="grow small"><b>{r.students?.profiles?.full_name}</b> · {r.new_device_id.slice(0, 18)}…</div>
            <Chip status={r.status} />
          </div>
        </div>
      ))}
    </>
  );
}

function Audit() {
  const [rows, setRows] = useState<any[] | null>(null);
  useEffect(() => {
    (async () => {
      const { data } = await supabase
        .from('audit_logs')
        .select('*, profiles(full_name)')
        .order('created_at', { ascending: false })
        .limit(100);
      setRows(data ?? []);
    })();
  }, []);
  if (rows === null) return <Loading />;
  return (
    <div className="card mt8">
      {rows.length === 0 && <div className="muted small center">No audit entries yet.</div>}
      {rows.map((r) => (
        <div className="list-row" key={r.id}>
          <div className="grow">
            <div style={{ fontWeight: 600, fontSize: 13 }}>{r.action.replace(/_/g, ' ')}</div>
            <div className="muted tiny">
              by {r.profiles?.full_name ?? 'system'} · {new Date(r.created_at).toLocaleString()}
              {r.new_value ? ' · ' + JSON.stringify(r.new_value) : ''}
            </div>
          </div>
        </div>
      ))}
    </div>
  );
}
