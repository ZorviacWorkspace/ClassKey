'use client';

import { useCallback, useEffect, useState } from 'react';
import { supabase } from '@/lib/supabaseClient';
import { useProfile } from '@/lib/useProfile';
import { prettyDate, statusInfo, todayISO } from '@/lib/client';
import { Avatar, Chip, Loading, Spinner, StatCard, Toast } from '../ui/parts';
import Reports from '../ui/Reports';
import OverrideModal from '../ui/OverrideModal';

const TABS = ['today', 'students', 'approvals', 'suspicious', 'reports'] as const;
type Tab = (typeof TABS)[number];

export default function StaffPage() {
  const { profile, loading, signOut } = useProfile(['staff']);
  const [tab, setTab] = useState<Tab>('today');
  const [toast, setToast] = useState<string | null>(null);

  if (loading || !profile) return <div className="center-screen center"><Spinner blue /></div>;

  return (
    <div className="wide">
      <div className="topbar">
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img src="/logo.png" alt="" width={40} height={40} />
        <div className="grow">
          <div className="h2">ClassKey · Staff</div>
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
      {tab === 'today' && <Today />}
      {tab === 'students' && <Students toast={setToast} />}
      {tab === 'approvals' && <Approvals toast={setToast} />}
      {tab === 'suspicious' && <Suspicious />}
      {tab === 'reports' && <Reports />}
      {toast && <Toast text={toast} onClose={() => setToast(null)} />}
    </div>
  );
}

/** Live view of today's attendance — realtime via Supabase channels. */
function Today() {
  const [rows, setRows] = useState<any[] | null>(null);
  const [total, setTotal] = useState(0);
  const [live, setLive] = useState(false);

  const load = useCallback(async () => {
    const { data } = await supabase
      .from('attendance')
      .select('*, students(register_number, profiles(full_name))')
      .eq('attendance_date', todayISO())
      .order('marked_at', { ascending: false });
    setRows(data ?? []);
    const { count } = await supabase.from('students').select('*', { count: 'exact', head: true });
    setTotal(count ?? 0);
  }, []);

  useEffect(() => {
    load();
    const ch = supabase
      .channel('staff-live')
      .on('postgres_changes', { event: '*', schema: 'public', table: 'attendance' }, load)
      .on('postgres_changes', { event: '*', schema: 'public', table: 'attendance_requests' }, load)
      .subscribe((status) => setLive(status === 'SUBSCRIBED'));
    return () => { supabase.removeChannel(ch); };
  }, [load]);

  if (rows === null) return <Loading />;
  const count = (s: string) => rows.filter((r) => r.status === s).length;

  return (
    <>
      <div className="hero mt8">
        <div className="row gap8">
          <span className="live-dot" />
          <span className="small" style={{ opacity: 0.9 }}>{live ? 'LIVE — updates instantly when students mark' : 'Connecting live updates…'}</span>
        </div>
        <div style={{ fontSize: 19, fontWeight: 800, marginTop: 4 }}>
          {new Date().toLocaleDateString(undefined, { weekday: 'long', day: 'numeric', month: 'long' })}
        </div>
        <div className="small" style={{ opacity: 0.9 }}>{rows.length} of {total} students marked</div>
      </div>
      <div className="row gap8 mt12">
        <StatCard label="Present" value={count('present')} color="var(--success)" />
        <StatCard label="Late" value={count('late')} color="var(--warning)" />
        <StatCard label="Unmarked" value={Math.max(0, total - rows.length)} color="var(--error)" />
        <StatCard label="OD/Leave" value={count('od') + count('half_day') + count('leave') + count('permission') + count('early_leave')} color="var(--primary)" />
      </div>
      <div className="card mt12">
        {rows.length === 0 && <div className="muted small center">No attendance yet today.</div>}
        {rows.map((r) => (
          <div className="list-row" key={r.id}>
            <Avatar name={r.students?.profiles?.full_name ?? '?'} size={36} />
            <div className="grow">
              <div style={{ fontWeight: 600, fontSize: 14 }}>{r.students?.profiles?.full_name}</div>
              <div className="muted tiny">
                {r.students?.register_number} · {r.session}
                {r.marked_at ? ' · ' + new Date(r.marked_at).toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' }) : ''}
              </div>
            </div>
            <Chip status={r.status} />
          </div>
        ))}
      </div>
    </>
  );
}

function Students({ toast }: { toast: (s: string) => void }) {
  const [rows, setRows] = useState<any[] | null>(null);
  const [search, setSearch] = useState('');
  const [target, setTarget] = useState<any>(null);

  const load = useCallback(async () => {
    const { data: students } = await supabase
      .from('students')
      .select('*, profiles(full_name, email, phone)')
      .order('register_number');
    const { data: att } = await supabase
      .from('attendance').select('student_id, status').eq('attendance_date', todayISO());
    const map = new Map((att ?? []).map((a) => [a.student_id, a.status]));
    setRows((students ?? []).map((s) => ({ ...s, today_status: map.get(s.id) ?? null })));
  }, []);
  useEffect(() => { load(); }, [load]);

  if (rows === null) return <Loading />;
  const shown = rows.filter(
    (s) => !search ||
      s.profiles?.full_name?.toLowerCase().includes(search.toLowerCase()) ||
      s.register_number?.toLowerCase().includes(search.toLowerCase())
  );

  return (
    <>
      <input className="input mt8" placeholder="Search name or register no…" value={search} onChange={(e) => setSearch(e.target.value)} />
      <div className="card mt12">
        {shown.map((s) => (
          <button key={s.id} className="list-row" style={{ width: '100%', background: 'none', border: 'none', textAlign: 'left', borderBottom: '1px solid var(--border)' }} onClick={() => setTarget(s)}>
            <Avatar name={s.profiles?.full_name ?? '?'} size={36} />
            <div className="grow">
              <div style={{ fontWeight: 600, fontSize: 14 }}>{s.profiles?.full_name}</div>
              <div className="muted tiny">{s.register_number} · device {s.device_status}</div>
            </div>
            <Chip status={s.today_status} />
          </button>
        ))}
        {shown.length === 0 && <div className="muted small center">No students found.</div>}
      </div>
      {target && <OverrideModal student={target} onClose={() => setTarget(null)} onDone={() => { setTarget(null); load(); }} toast={toast} />}
    </>
  );
}

function Approvals({ toast }: { toast: (s: string) => void }) {
  const [rows, setRows] = useState<any[] | null>(null);
  const load = useCallback(async () => {
    const { data } = await supabase
      .from('attendance_requests')
      .select('*, students(register_number, profiles(full_name))')
      .order('created_at', { ascending: false })
      .limit(50);
    setRows(data ?? []);
  }, []);
  useEffect(() => {
    load();
    const ch = supabase.channel('staff-approvals')
      .on('postgres_changes', { event: '*', schema: 'public', table: 'attendance_requests' }, load)
      .subscribe();
    return () => { supabase.removeChannel(ch); };
  }, [load]);

  async function decide(id: string, decision: 'approved' | 'rejected') {
    const { data, error } = await supabase.rpc('approve_attendance_request', {
      p_request_id: id, p_decision: decision, p_review_note: null,
    });
    if (error) { toast(error.message); return; }
    const r = data as any;
    toast(r.message);
    load();
  }

  if (rows === null) return <Loading />;
  const pending = rows.filter((r) => r.status === 'pending');
  const decided = rows.filter((r) => r.status !== 'pending');

  return (
    <>
      {pending.length === 0 && <div className="card mt8 muted small center">All caught up — no pending requests.</div>}
      {pending.map((r) => (
        <div className="card mt12" key={r.id}>
          <div className="between">
            <div className="grow">
              <span className="chip st-blue">{r.request_type.replace(/_/g, ' ')}</span>{' '}
              <span className="muted small">{prettyDate(r.request_date)}</span>
              <div style={{ fontWeight: 700, marginTop: 6 }}>{r.students?.profiles?.full_name} · {r.students?.register_number}</div>
              <div className="small mt8">{r.reason}</div>
            </div>
            <Chip status={r.status} />
          </div>
          <div className="row gap10 mt12">
            <button className="btn btn-ghost btn-sm grow" onClick={() => decide(r.id, 'rejected')}>Reject</button>
            <button className="btn btn-primary btn-sm grow" onClick={() => decide(r.id, 'approved')}>Approve</button>
          </div>
        </div>
      ))}
      {decided.length > 0 && <div className="section-title">Decided</div>}
      {decided.slice(0, 10).map((r) => (
        <div className="card mt8" key={r.id}>
          <div className="between">
            <div className="grow">
              <b>{r.students?.profiles?.full_name}</b> <span className="muted small">{r.request_type.replace(/_/g, ' ')} · {prettyDate(r.request_date)}</span>
            </div>
            <Chip status={r.status} />
          </div>
        </div>
      ))}
    </>
  );
}

function Suspicious() {
  const [rows, setRows] = useState<any[] | null>(null);
  useEffect(() => {
    (async () => {
      const { data } = await supabase
        .from('suspicious_attempts')
        .select('*, students(register_number, profiles(full_name))')
        .order('created_at', { ascending: false })
        .limit(50);
      setRows(data ?? []);
    })();
  }, []);
  if (rows === null) return <Loading />;
  return (
    <div className="card mt8">
      {rows.length === 0 && <div className="muted small center">No suspicious attempts recorded.</div>}
      {rows.map((r) => (
        <div className="list-row" key={r.id}>
          <span style={{ fontSize: 18 }}>⚠️</span>
          <div className="grow">
            <div style={{ fontWeight: 600, fontSize: 14 }}>
              {r.students?.profiles?.full_name ?? 'Unknown'} <span className="muted tiny">{r.students?.register_number}</span>
            </div>
            <div className="muted tiny">{r.reason} · {new Date(r.created_at).toLocaleString()}</div>
          </div>
        </div>
      ))}
    </div>
  );
}
