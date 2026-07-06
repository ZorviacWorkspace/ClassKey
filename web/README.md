# ClassKey Web — Supabase + Vercel dashboard & student app

The deployable web client for ClassKey. One URL, three role logins:

- **/login** — role cards: Student · Staff · Admin (role re-verified server-side after login)
- **/student** — mark attendance (live GPS → server-side geofence + time-window check via the
  `mark_attendance` RPC), history, OD/leave/permission requests
- **/staff** — **live** today view (Supabase Realtime), students with manual override
  (audit reason required), approvals, suspicious attempts, reports + CSV
- **/admin** — overview, manage students/staff (creates real Supabase Auth accounts),
  departments, campus geofence & timings, device approvals, audit logs, reports

Stack: Next.js 14 + `@supabase/supabase-js`. Auth, data, RLS, RPC and Realtime all come from
the Supabase project defined in [`../supabase/`](../supabase/).

## Deploy

Follow the step-by-step guide: **[../DEPLOYMENT.md](../DEPLOYMENT.md)** (~15 min, free).

Environment variables (local `.env.local` and Vercel):

| Name | Where to find |
|---|---|
| `NEXT_PUBLIC_SUPABASE_URL` | Supabase → Project Settings → API → Project URL |
| `NEXT_PUBLIC_SUPABASE_ANON_KEY` | same page → anon public key |
| `SUPABASE_SERVICE_ROLE_KEY` | same page → service_role (server-only: used by `/api/admin/users` to create accounts and `/api/resolve-identifier` for register-no/phone login) |

## Run locally

```bash
cd web
npm install
cp .env.example .env.local   # fill in the 3 values
npm run dev                  # http://localhost:3000
npm run build && npm run typecheck   # what Vercel runs
```

## Notes

- The service role key is used **only** inside the two server routes; it is never exposed to
  the browser (no `NEXT_PUBLIC_` prefix).
- All authority lives in the database: RLS scopes reads/writes per role, and attendance can
  only be inserted through the `mark_attendance` security-definer function which re-checks
  geofence, GPS accuracy, time window, device binding and duplicates.
- Browsers cannot use Android's fingerprint API, so web identity = Supabase login + live
  location. The Android app (this repo, `app/`) adds a real BiometricPrompt on top of the
  same RPC.
