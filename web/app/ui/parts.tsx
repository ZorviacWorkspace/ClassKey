'use client';

import { initials, statusInfo } from '@/lib/client';

export function Chip({ status }: { status?: string | null }) {
  const s = statusInfo(status);
  return (
    <span className={`chip ${s.cls}`}>
      <span className="dot" />
      {s.label}
    </span>
  );
}

export function Avatar({ name, size = 42 }: { name: string; size?: number }) {
  return (
    <div className="avatar" style={{ width: size, height: size, fontSize: size / 2.6 }}>
      {initials(name)}
    </div>
  );
}

export function Spinner({ blue = false }: { blue?: boolean }) {
  return <span className={`spinner${blue ? ' blue' : ''}`} />;
}

export function Toast({ text, onClose }: { text: string; onClose: () => void }) {
  return (
    <div className="toast">
      <span className="grow">{text}</span>
      <button onClick={onClose}>OK</button>
    </div>
  );
}

export function StatCard({ label, value, color }: { label: string; value: number | string; color: string }) {
  return (
    <div className="stat">
      <div className="n" style={{ color }}>
        {value}
      </div>
      <div className="l">{label}</div>
    </div>
  );
}

export function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: React.ReactNode }) {
  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <div className="between mb12">
          <div className="h2">{title}</div>
          <button className="pill" onClick={onClose}>
            Close
          </button>
        </div>
        {children}
      </div>
    </div>
  );
}

export function Loading() {
  return (
    <div className="card mt12 center">
      <Spinner blue />
    </div>
  );
}

export function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <label className="field">{label}</label>
      {children}
    </div>
  );
}
