'use client';

import { useCallback, useEffect, useState } from 'react';
import { supabase } from '@/lib/supabaseClient';
import { useProfile } from '@/lib/useProfile';
import { deviceId, hhmm, prettyDate, statusInfo, todayISO } from '@/lib/client';
import { Avatar, Chip, Loading, Modal, Spinner, StatCard, Toast } from '../ui/parts';

export default function StudentPage() {
  const { profile, loading, signOut } = useProfile(['student']);
  const [tab, setTab] = useState<'home' | 'history' | 'requests'>('home');
  const [toast, setToast] = useState<string | null>(null);

  if (loading || !profile) return <div className="center-screen center"><Spinner blue /></div>;

  return (
    <div className="app">
      <div className="scroll">
        <div className="between">
          <div>
            <div className="muted small">{greeting()},</div>
            <div className="h2">{profile.full_name} 👋</div>
          </div>
          <div className="row gap8">
            <button className="pill" onClick={signOut}>Sign out</button>
            <Avatar name={profile.full_name} />
          </div>
        </div>
        <div className="tabbar mt12">
          {(['home', 'history', 'requests'] as const).map((t) => (
            <button key={t} className={`pill${tab === t ? ' active' : ''}`} onClick={() => setTab(t)}>
              {t === 'home' ? 'Mark' : t[0].toUpperCase() + t.slice(1)}
            </button>
          ))}
        </div>
        {tab === 'home' && <Home toast={setToast} />}
        {tab === 'history' && <History />}
        {tab === 'requests' && <Requests toast={setToast} />}
      </div>
      {toast && <Toast text={toast} onClose={() => setToast(null)} />}
    </div>
  );
}

function Home({ toast }: { toast: (s: string) => void }) {
  const [campus, setCampus] = useState<any>(null);
  const [today, setToday] = useState<any>(null);
  const [loaded, setLoaded] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<any>(null);

  const load = useCallback(async () => {
    const { data: cfg } = await supabase.from('campus_config').select('*').limit(1).maybeSingle();
    setCampus(cfg);
    const { data: sess } = await supabase.auth.getSession();
    const uid = sess.session?.user.id;
    if (uid) {
      const { data: stu } = await supabase.from('students').select('id').eq('profile_id', uid).maybeSingle();
      if (stu) {
        const { data: rec } = await supabase
          .from('attendance').select('*').eq('student_id', stu.id).eq('attendance_date', todayISO()).maybeSingle();
        setToday(rec);
      }
    }
    setLoaded(true);
  }, []);
  useEffect(() => { load(); }, [load]);

  async function mark() {
    setBusy(true);
    setError(null);
    try {
      const pos = await new Promise<GeolocationPosition>((res, rej) =>
        navigator.geolocation.getCurrentPosition(res, (e) => rej(new Error(
          e.code === 1 ? 'Location permission denied. Allow location and retry.' :
          e.code === 2 ? 'Location unavailable. Turn on GPS and retry.' : 'Location timed out. Retry.'
        )), { enableHighAccuracy: true, timeout: 15000, maximumAge: 0 })
      );
      const { data, error: rpcErr } = await supabase.rpc('mark_attendance', {
        p_latitude: pos.coords.latitude,
        p_longitude: pos.coords.longitude,
        p_accuracy: pos.coords.accuracy,
        p_device_id: deviceId(),
        p_biometric_verified: true, // web identity = Supabase login; the Android app adds a real fingerprint check
      });
      if (rpcErr) throw new Error(rpcErr.message);
      const r = data as any;
      if (!r.ok) throw new Error(r.message);
      setSuccess(r);
      toast(r.message);
      load();
    } catch (e: any) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }

  if (!loaded) return <Loading />;

  return (
    <>
      <div className="hero mt12">
        <div className="small" style={{ opacity: 0.85 }}>Today&apos;s entry attendance</div>
        <div style={{ fontSize: 20, fontWeight: 800 }}>
          {new Date().toLocaleDateString(undefined, { weekday: 'long', day: 'numeric', month: 'long' })}
        </div>
        <div className="small mt8" style={{ opacity: 0.9 }}>
          {today
            ? `Marked ${statusInfo(today.status).label}${today.marked_at ? ' · ' + new Date(today.marked_at).toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' }) : ''}`
            : campus
              ? `Present till ${hhmm(campus.present_until)} · Late till ${hhmm(campus.late_until)}`
              : 'Campus not configured yet'}
        </div>
      </div>

      <div className="card mt12">
        {today ? (
          <div className="row gap14">
            <div className="fp" style={{ width: 52, height: 52, fontSize: 24, background: 'var(--soft-green)', color: 'var(--success)' }}>✓</div>
            <div>
              <div className="h2">You&apos;re {statusInfo(today.status).label} today</div>
              <div className="muted small">Synced to the college database — staff can see it live.</div>
            </div>
          </div>
        ) : (
          <>
            <div className="h2 mb8">Mark attendance</div>
            <div className="muted small mb12">
              Your live location is verified on the server against the campus geofence
              {campus ? ` (${campus.allowed_radius_meters} m radius)` : ''}. One record per day.
            </div>
            {error && <div className="error-box mb12">{error}</div>}
            <button className="btn btn-primary" disabled={busy} onClick={mark}>
              {busy ? <span className="row gap8" style={{ justifyContent: 'center' }}><Spinner /> Verifying location…</span> : '📍 Mark Attendance'}
            </button>
          </>
        )}
      </div>

      {success && (
        <Modal title="Attendance marked" onClose={() => setSuccess(null)}>
          <div className="center">
            <div className="fp" style={{ background: 'var(--success)' }}>✓</div>
            <div className="h1 mt12">Attendance Marked!</div>
            <div className="muted">
              You are <b style={{ color: 'var(--success)' }}>{statusInfo(success.status).label}</b>
              {success.distance != null ? ` · ${success.distance} m from gate` : ''}
            </div>
          </div>
          <button className="btn btn-primary mt16" onClick={() => setSuccess(null)}>Done</button>
        </Modal>
      )}
    </>
  );
}

function History() {
  const [rows, setRows] = useState<any[] | null>(null);
  useEffect(() => {
    (async () => {
      const { data } = await supabase.from('attendance').select('*').order('attendance_date', { ascending: false }).limit(60);
      setRows(data ?? []);
    })();
  }, []);
  if (!rows) return <Loading />;
  const c = (s: string) => rows.filter((r) => r.status === s).length;
  return (
    <>
      <div className="row gap8 mt12">
        <StatCard label="Present" value={c('present')} color="var(--success)" />
        <StatCard label="Late" value={c('late')} color="var(--warning)" />
        <StatCard label="Absent" value={c('absent')} color="var(--error)" />
      </div>
      <div className="card mt12">
        {rows.length === 0 && <div className="muted small center">No records yet.</div>}
        {rows.map((r) => (
          <div className="list-row" key={r.id}>
            <div className="grow">
              <div style={{ fontWeight: 600 }}>{prettyDate(r.attendance_date)}</div>
              <div className="muted tiny">{r.verification_method}{r.manual_reason ? ` · ${r.manual_reason}` : ''}</div>
            </div>
            <Chip status={r.status} />
          </div>
        ))}
      </div>
    </>
  );
}

function Requests({ toast }: { toast: (s: string) => void }) {
  const [rows, setRows] = useState<any[] | null>(null);
  const [studentId, setStudentId] = useState<string | null>(null);
  const [type, setType] = useState('od');
  const [offset, setOffset] = useState(0);
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    const { data: sess } = await supabase.auth.getSession();
    const uid = sess.session?.user.id;
    if (!uid) return;
    const { data: stu } = await supabase.from('students').select('id').eq('profile_id', uid).maybeSingle();
    setStudentId(stu?.id ?? null);
    const { data } = await supabase.from('attendance_requests').select('*').order('created_at', { ascending: false }).limit(30);
    setRows(data ?? []);
  }, []);
  useEffect(() => { load(); }, [load]);

  async function submit() {
    setError(null);
    if (!studentId) { setError('Student record not found.'); return; }
    if (reason.trim().length < 4) { setError('Describe the reason (a few words).'); return; }
    const { error: e } = await supabase.from('attendance_requests').insert({
      student_id: studentId, request_type: type, request_date: todayISO(offset), reason: reason.trim(),
    });
    if (e) { setError(e.message); return; }
    setReason('');
    toast('Request submitted for approval.');
    load();
  }

  return (
    <>
      <div className="card mt12">
        <div className="section-title" style={{ marginTop: 0 }}>New request</div>
        <div className="row gap6 wrap">
          {['od', 'half_day', 'full_day_leave', 'permission', 'early_leave'].map((t) => (
            <button key={t} className={`pill${type === t ? ' active' : ''}`} onClick={() => setType(t)}>
              {t.replace(/_/g, ' ')}
            </button>
          ))}
        </div>
        <div className="row gap6 wrap mt12">
          {[0, 1, 2].map((o) => (
            <button key={o} className={`pill${offset === o ? ' active' : ''}`} onClick={() => setOffset(o)}>
              {o === 0 ? 'Today' : o === 1 ? 'Tomorrow' : '+2 days'}
            </button>
          ))}
        </div>
        <label className="field">Reason</label>
        <textarea className="input" rows={3} value={reason} onChange={(e) => setReason(e.target.value)} />
        {error && <div className="error-box mt8">{error}</div>}
        <button className="btn btn-primary mt12" onClick={submit}>Submit Request</button>
      </div>
      {rows === null ? <Loading /> : rows.map((r) => (
        <div className="card mt12" key={r.id}>
          <div className="between">
            <div className="grow">
              <span className="chip st-blue">{r.request_type.replace(/_/g, ' ')}</span>{' '}
              <span className="muted small">{prettyDate(r.request_date)}</span>
              <div className="small mt8">{r.reason}</div>
              {r.review_note && <div className="muted tiny mt8">Note: {r.review_note}</div>}
            </div>
            <Chip status={r.status} />
          </div>
        </div>
      ))}
    </>
  );
}

function greeting() {
  const h = new Date().getHours();
  return h < 12 ? 'Good morning' : h < 17 ? 'Good afternoon' : 'Good evening';
}
