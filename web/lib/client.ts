'use client';

export const STATUS: Record<string, { label: string; cls: string }> = {
  present: { label: 'Present', cls: 'st-present' },
  late: { label: 'Late', cls: 'st-late' },
  absent: { label: 'Absent', cls: 'st-absent' },
  od: { label: 'On Duty', cls: 'st-od' },
  half_day: { label: 'Half Day', cls: 'st-half' },
  leave: { label: 'Leave', cls: 'st-leave' },
  permission: { label: 'Permission', cls: 'st-permission' },
  early_leave: { label: 'Early Leave', cls: 'st-permission' },
  not_marked: { label: 'Not Marked', cls: 'st-none' },
  pending: { label: 'Pending', cls: 'st-late' },
  approved: { label: 'Approved', cls: 'st-present' },
  rejected: { label: 'Rejected', cls: 'st-absent' },
};

export function statusInfo(status?: string | null) {
  return (status && STATUS[status]) || STATUS.not_marked;
}

export function initials(name: string): string {
  return (
    name
      .split(' ')
      .filter(Boolean)
      .slice(0, 2)
      .map((w) => w[0]?.toUpperCase())
      .join('') || 'CK'
  );
}

export function hhmm(t: unknown): string {
  return String(t ?? '').slice(0, 5);
}

export function todayISO(offset = 0): string {
  const d = new Date();
  d.setDate(d.getDate() + offset);
  return d.toISOString().slice(0, 10);
}

export function shiftDate(iso: string, days: number): string {
  const d = new Date(iso + 'T00:00:00');
  d.setDate(d.getDate() + days);
  return d.toISOString().slice(0, 10);
}

export function prettyDate(iso: string): string {
  const d = new Date(iso + 'T00:00:00');
  return d.toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short' });
}

/** Stable per-browser device id (used for device binding on the web client). */
export function deviceId(): string {
  if (typeof window === 'undefined') return 'server';
  let id = localStorage.getItem('ck_device_id');
  if (!id) {
    id = 'web-' + crypto.randomUUID();
    localStorage.setItem('ck_device_id', id);
  }
  return id;
}

export function downloadCSV(filename: string, header: string[], rows: (string | number | null | undefined)[][]) {
  const esc = (v: unknown) => String(v ?? '').replace(/"/g, '""');
  const text =
    header.join(',') +
    '\n' +
    rows.map((r) => r.map((c) => `"${esc(c)}"`).join(',')).join('\n');
  const blob = new Blob([text], { type: 'text/csv' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}

export function rangeFor(kind: 'day' | 'week' | 'month', anchor: string): { from: string; to: string } {
  if (kind === 'day') return { from: anchor, to: anchor };
  const d = new Date(anchor + 'T00:00:00');
  if (kind === 'week') {
    const day = (d.getDay() + 6) % 7; // Monday=0
    const from = new Date(d);
    from.setDate(d.getDate() - day);
    const to = new Date(from);
    to.setDate(from.getDate() + 6);
    return { from: from.toISOString().slice(0, 10), to: to.toISOString().slice(0, 10) };
  }
  const from = new Date(d.getFullYear(), d.getMonth(), 1);
  const to = new Date(d.getFullYear(), d.getMonth() + 1, 0);
  const iso = (x: Date) => `${x.getFullYear()}-${String(x.getMonth() + 1).padStart(2, '0')}-${String(x.getDate()).padStart(2, '0')}`;
  return { from: iso(from), to: iso(to) };
}
