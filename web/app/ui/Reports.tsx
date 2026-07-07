'use client';

import { useEffect, useState } from 'react';
import { supabase } from '@/lib/supabaseClient';
import { downloadCSV, prettyDate, rangeFor, shiftDate, statusInfo, todayISO } from '@/lib/client';
import { Chip, Loading, StatCard } from './parts';

export default function Reports({ showDepartmentFilter = false }: { showDepartmentFilter?: boolean }) {
  const [kind, setKind] = useState<'day' | 'week' | 'month'>('day');
  const [anchor, setAnchor] = useState(todayISO());
  const [statusFilter, setStatusFilter] = useState<string>('all');
  const [deptFilter, setDeptFilter] = useState<string>('all');
  const [departments, setDepartments] = useState<any[]>([]);
  const [rows, setRows] = useState<any[] | null>(null);

  useEffect(() => {
    if (showDepartmentFilter) {
      supabase.from('departments').select('*').then(({ data }) => setDepartments(data ?? []));
    }
  }, [showDepartmentFilter]);

  useEffect(() => {
    (async () => {
      setRows(null);
      const { from, to } = rangeFor(kind, anchor);
      const { data } = await supabase
        .from('attendance')
        .select('*, students(register_number, department_id, profiles(full_name))')
        .gte('attendance_date', from)
        .lte('attendance_date', to)
        .order('attendance_date', { ascending: false })
        .limit(2000);
      setRows(data ?? []);
    })();
  }, [kind, anchor]);

  const filtered = (rows ?? []).filter(
    (r) =>
      (statusFilter === 'all' || r.status === statusFilter) &&
      (deptFilter === 'all' || r.students?.department_id === deptFilter)
  );
  const count = (s: string) => filtered.filter((r) => r.status === s).length;
  const { from, to } = rangeFor(kind, anchor);

  function exportCsv() {
    downloadCSV(
      `classkey_${from}_${to}.csv`,
      ['date', 'session', 'register_no', 'name', 'status', 'marked_at', 'method', 'reason'],
      filtered.map((r) => [
        r.attendance_date,
        r.session,
        r.students?.register_number,
        r.students?.profiles?.full_name,
        statusInfo(r.status).label,
        r.marked_at ? new Date(r.marked_at).toLocaleString() : '',
        r.verification_method,
        r.manual_reason,
      ])
    );
  }

  return (
    <>
      <div className="card tight mt12">
        <div className="between">
          <button className="icon-btn" onClick={() => setAnchor(shiftDate(anchor, kind === 'day' ? -1 : kind === 'week' ? -7 : -30))}>‹</button>
          <div className="center">
            <div style={{ fontWeight: 700 }}>{kind === 'day' ? (anchor === todayISO() ? 'Today' : prettyDate(anchor)) : `${prettyDate(from)} → ${prettyDate(to)}`}</div>
            <div className="row gap6 mt8" style={{ justifyContent: 'center' }}>
              {(['day', 'week', 'month'] as const).map((k) => (
                <button key={k} className={`pill${kind === k ? ' active' : ''}`} onClick={() => setKind(k)}>{k}</button>
              ))}
            </div>
          </div>
          <button className="icon-btn" disabled={anchor >= todayISO() && kind === 'day'} onClick={() => setAnchor(shiftDate(anchor, kind === 'day' ? 1 : kind === 'week' ? 7 : 30))}>›</button>
        </div>
      </div>

      <div className="row gap8 mt12">
        <StatCard label="Present" value={count('present')} color="var(--success)" />
        <StatCard label="Late" value={count('late')} color="var(--warning)" />
        <StatCard label="Absent" value={count('absent')} color="var(--error)" />
        <StatCard label="OD/Leave" value={count('od') + count('half_day') + count('leave') + count('permission') + count('early_leave')} color="var(--primary)" />
      </div>

      <div className="row gap8 wrap mt12">
        <select className="input" style={{ width: 'auto' }} value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
          <option value="all">All statuses</option>
          {['present', 'late', 'absent', 'od', 'half_day', 'leave', 'permission', 'early_leave'].map((s) => (
            <option key={s} value={s}>{statusInfo(s).label}</option>
          ))}
        </select>
        {showDepartmentFilter && (
          <select className="input" style={{ width: 'auto' }} value={deptFilter} onChange={(e) => setDeptFilter(e.target.value)}>
            <option value="all">All departments</option>
            {departments.map((d) => <option key={d.id} value={d.id}>{d.code}</option>)}
          </select>
        )}
        <button className="btn btn-ghost btn-sm" onClick={exportCsv}>⬇ Export CSV</button>
      </div>

      {rows === null ? (
        <Loading />
      ) : (
        <div className="card mt12" style={{ overflowX: 'auto' }}>
          <table className="tbl">
            <thead>
              <tr><th>Date</th><th>Student</th><th>Status</th><th className="hide-sm">Method</th></tr>
            </thead>
            <tbody>
              {filtered.length === 0 && (
                <tr><td colSpan={4} className="muted center">No records in this range.</td></tr>
              )}
              {filtered.map((r) => (
                <tr key={r.id}>
                  <td>{prettyDate(r.attendance_date)}<div className="muted tiny">{r.session}</div></td>
                  <td>
                    <div style={{ fontWeight: 600 }}>{r.students?.profiles?.full_name}</div>
                    <div className="muted tiny">{r.students?.register_number}</div>
                  </td>
                  <td><Chip status={r.status} /></td>
                  <td className="hide-sm muted">{r.verification_method}{r.manual_reason ? ` · ${r.manual_reason}` : ''}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}
