# 🚀 ClassKey — Deploy with Supabase + Vercel (step by step, all free)

Follow this top to bottom. **No credit card needed.** Total time: ~15 minutes.

When you're done:
- Students open **your URL** on their phone → login → mark attendance with live location.
- Staff/Admin open the same URL → **see attendance update live** (Supabase Realtime).
- All data lives in **one central Supabase database** — every phone sees the same data.

---

## Part 1 — Put the code on GitHub (3 min)

1. Go to <https://github.com/new> → name it (e.g. `classkey`) → **Create repository**.
2. On your computer, open a terminal in the `ClassKeyModernAttendance` folder and run:

```bash
git init
git add .
git commit -m "ClassKey"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/classkey.git
git push -u origin main
```

> Secrets are safe: `.gitignore` already excludes every `.env` file.

---

## Part 2 — Create the Supabase database (5 min)

1. Go to <https://supabase.com> → **Start your project** → sign in with GitHub.
2. **New project** → any name → set a **Database Password** (write it down) → pick the
   region closest to you → **Create new project**. Wait ~2 minutes.
3. In the left sidebar click **SQL Editor** → **New query**.
4. Open these 6 files from the `supabase/migrations/` folder **one by one, in order**.
   For each: copy ALL the text → paste into the editor → press **Run** → you should see "Success".
   1. `0001_schema.sql`
   2. `0002_rls.sql`
   3. `0003_functions.sql`
   4. `0004_storage.sql`
   5. `0005_realtime_and_devices.sql`
   6. `0006_sessions_username_notifications.sql`  ← morning/afternoon sessions + username login
5. Still in the SQL Editor, run `supabase/seed/seed_data.sql` (departments + campus row).

### Create the 3 demo users (easiest way — no coding)

6. Left sidebar → **Authentication** → **Users** → **Add user → Create new user**.
   Create these three (tick **Auto Confirm User** each time), password for all:
   **`ChangeMe123!`**

   | Email |
   |---|
   | `admin@classkey.local` |
   | `staff@classkey.local` |
   | `priya.sharma@classkey.local` |

7. Back to **SQL Editor** → run all of `supabase/seed/manual_seed.sql`.
   The query at the bottom should show 3 rows with roles **admin / staff / student**. Done!

   > Alternative for many users: `cd supabase/seed && npm install && cp .env.example .env`
   > (fill in URL + service key) `&& npm run seed` — creates everything automatically.

### Copy your two keys (you need them in Part 3)

8. Left sidebar → ⚙️ **Project Settings** → **API**:
   - **Project URL** → looks like `https://abcdefgh.supabase.co`
   - **anon public** key (long string)
   - **service_role** key (long string — keep secret!)

---

## Part 3 — Deploy to Vercel (4 min)

1. Go to <https://vercel.com> → **Sign up / Log in with GitHub**.
2. **Add New… → Project** → find your `classkey` repo → **Import**.
3. **IMPORTANT:** under *Root Directory* click **Edit** → select the **`web`** folder.
4. Open **Environment Variables** and add these three:

   | Name | Value |
   |---|---|
   | `NEXT_PUBLIC_SUPABASE_URL` | your Project URL from step 2.8 |
   | `NEXT_PUBLIC_SUPABASE_ANON_KEY` | the **anon public** key |
   | `SUPABASE_SERVICE_ROLE_KEY` | the **service_role** key |

5. Click **Deploy**. In ~1 minute you get your live URL: `https://classkey-xxxx.vercel.app` 🎉

---

## Part 4 — Test it end-to-end (3 min)

Open your Vercel URL on a phone (or two phones for the full effect):

1. **Admin first:** login as Admin (`admin@classkey.local` / `ChangeMe123!`)
   → **Campus** tab → tap **"Set to my current location"** → **Save Campus Settings**.
   Also check the times: *Present until* / *Late until* (24-h format). For a demo at any
   time of day, set *Present until* to something later than now (e.g. `21:00`).
2. **Student:** (other phone or a private window) → Student login → `CS21001` /
   `ChangeMe123!` → tap **Mark Attendance** → allow location → done. The first mark
   **binds this device** to the student.
3. **Staff:** login `staff@classkey.local` → **Today** tab → the student's mark is
   already there — and if you keep this open while another student marks, it
   **appears live without refreshing**.
4. Student → **Requests** → submit an OD → Staff → **Approvals** → Approve →
   the student's status for that date becomes *On Duty* automatically.
5. Admin → **Audit** tab → every action is logged. Admin → **Devices** shows any
   device-replacement requests (e.g. student switching phones).
6. On phones: browser menu → **Add to Home screen** → ClassKey opens like an app.

**That's the whole deployment.** Share the URL with your college.

---

## Demo logins (password for all: `ChangeMe123!`)

| Role | Login with |
|---|---|
| Admin | `admin@classkey.local` or phone `9990001000` |
| Staff | `staff@classkey.local` or phone `9990003000` |
| Student | register no `CS21001`, email `priya.sharma@classkey.local`, or phone `9990002000` |

Add real students/staff from **Admin → Students / Staff → + Add** (they get password
`ChangeMe123!` and are asked to change it).

---

## The Android app (the main product)

The native app in `app/` is the full client: role-card login (register no / username /
email / phone), **real fingerprint** via BiometricPrompt, native FusedLocation GPS,
morning + afternoon sessions, forced first-login password change, **in-app account
creation** (admin adds staff & students; staff adds students — nobody touches Supabase),
profile photo upload, notifications, campus settings, device approvals, audit logs,
suspicious attempts and CSV reports.

1. Open `local.properties` (created by Android Studio in the project root) and add:
   ```properties
   SUPABASE_URL=https://YOUR-PROJECT.supabase.co
   SUPABASE_ANON_KEY=your-anon-public-key
   ADMIN_API_URL=https://your-app.vercel.app/api/admin/users
   ```
   `ADMIN_API_URL` powers in-app account creation without shipping any secret in the APK.
   It can point at either:
   - your **Vercel** deployment route `/api/admin/users` (works as soon as Part 3 is done), or
   - a **Supabase Edge Function**: install the Supabase CLI, then
     `supabase functions deploy create-user` from the repo root and use
     `https://YOUR-PROJECT.supabase.co/functions/v1/create-user` (code in
     `supabase/functions/create-user/`).
2. Build & run from Android Studio (`gradlew.bat assembleDebug`,
   APK at `app/build/outputs/apk/debug/app-debug.apk`).

Without the keys the app falls back to a self-contained offline demo (local SQLite) —
with the keys set, Supabase is the only data source.

### Morning & afternoon sessions

Admin → More → **Campus settings** (or web Admin → Campus) sets both windows:
morning *opens / present-till / late-till* and the same for afternoon (24-h `HH:mm`).
Marking inside a window records **that session**; before/after → clear "closed" message;
present vs late is decided by the *present-till* time. `auto_mark_absentees` fills both
sessions with Absent for anyone unmarked.

---

## Final testing checklist (acceptance)

Run these on real phones after deploying:

1. **Admin login** (app or web) → More → Campus settings → *Set to this phone's location* → Save.
2. **Admin adds staff**: Students tab → *Add staff/admin* → fill name/email/temp password → Create.
3. **Admin/Staff adds student**: Students tab → **+** → fill details incl. register number → Create.
4. **New student logs in** with the register number + temporary password → app forces a
   **password change** → student sets their own.
5. Student **profile photo**: Profile → camera badge → pick image → circular avatar appears.
6. Student taps **Mark Attendance** → fingerprint prompt → GPS check → session recorded
   (morning or afternoon automatically).
7. Marking again in the same session → blocked as duplicate; in the other session → new record.
8. **Staff phone/web**: Today view shows the mark (web updates live via Realtime).
9. Student submits an **OD request** → staff **Approvals** → Approve → student's day updates
   and the student gets a **notification**.
10. Student logs in on a **second phone** and tries to mark → blocked, device request created →
    Admin → Device approvals → Approve → second phone works.
11. Admin → **Audit logs** shows every one of the above actions.
12. Kill and reopen the app → still signed in (session persists). Turn off internet →
    friendly error messages, no crash.

## Troubleshooting

| Problem | Fix |
|---|---|
| Vercel build fails: missing env | Add the 3 env vars (Part 3.4) → Deployments → Redeploy. |
| Login says "No student/staff account found" | You skipped `manual_seed.sql` (Part 2.7), or the email doesn't match exactly. |
| Login says "This is a student account…" | You picked the wrong role card — roles are verified server-side. |
| "You are X m away — outside radius" | Admin → Campus → *Set to my current location* → Save. Or increase the radius (e.g. 500). |
| "Self-marking is closed for today" | Admin → Campus → set *Late until* to a later time (24-h HH:mm). |
| "GPS accuracy too low" | Move near a window/open sky, or Admin → Campus → raise *Min GPS accuracy* (e.g. 200). |
| "New device detected…" | That student already marked from another phone/browser. Admin → **Devices** → Approve. |
| Location prompt never appears | Location needs **https** (Vercel is https) and browser permission — check the site permissions. |
| Staff sees no students | Staff sees only their **department**. Check the staff profile has department CSE (manual_seed sets it). |
| Realtime not updating | Make sure migration `0005_realtime_and_devices.sql` ran; refresh the page once after enabling. |
| Want to start clean | Supabase → SQL Editor: `truncate attendance, attendance_requests, suspicious_attempts, device_approvals, audit_logs;` |
