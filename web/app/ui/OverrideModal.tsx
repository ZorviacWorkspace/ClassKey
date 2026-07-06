'use client';

import { useState } from 'react';
import { supabase } from '@/lib/supabaseClient';
import { statusInfo, todayISO } from '@/lib/client';
import { Modal } from './parts';

export default function OverrideModal({
  student,
  onClose,
  onDone,
  toast,
}: {
  student: any;
  onClose: () => void;
  onDone: () => void;
  toast: (s: string) => void;
}) {
  const [status, setStatus] = useState('present');
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(null);

  async function save() {
    setError(null);
    const { data, error: e } = await supabase.rpc('manual_attendance_override', {
      p_student_id: student.id,
      p_date: todayISO(),
      p_status: status,
      p_reason: reason,
    });
    if (e) {
      setError(e.message);
      return;
    }
    const r = data as any;
    if (!r.ok) {
      setError(r.message);
      return;
    }
    toast(`${student.register_number} set to ${statusInfo(status).label}.`);
    onDone();
  }

  return (
    <Modal title={`Manual override · ${student.profiles?.full_name ?? ''}`} onClose={onClose}>
      <div className="muted small mb12">Use only when phone verification fails. The reason is audit-logged.</div>
      <div className="row gap6 wrap">
        {['present', 'late', 'od', 'absent', 'half_day', 'permission'].map((s) => (
          <button key={s} className={`pill${status === s ? ' active' : ''}`} onClick={() => setStatus(s)}>
            {statusInfo(s).label}
          </button>
        ))}
      </div>
      <label className="field">Audit reason (required)</label>
      <input className="input" value={reason} onChange={(e) => setReason(e.target.value)} />
      {error && <div className="error-box mt8">{error}</div>}
      <button className="btn btn-primary mt12" onClick={save}>
        Save
      </button>
    </Modal>
  );
}
