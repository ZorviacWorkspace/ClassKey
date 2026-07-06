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
4. Open these 5 files from the `supabase/migrations/` folder **one by one, in order**.
   For each: copy ALL the text → paste into the editor → press **Run** → you should see "Success".
   1. `0001_schema.sql`
   2. `0002_rls.sql`
   3. `0003_functions.sql`
   4. `0004_storage.sql`
   5. `0005_realtime_and_devices.sql`
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

## The Android app (optional companion)

The native Android app in `app/` adds a **real fingerprint check** on top of the same
rules and can connect to the same Supabase backend:

1. Open `local.properties` (created by Android Studio in the project root) and add:
   ```properties
   SUPABASE_URL=https://YOUR-PROJECT.supabase.co
   SUPABASE_ANON_KEY=your-anon-public-key
   ```
2. Build & run from Android Studio (`gradlew.bat assembleDebug`,
   APK at `app/build/outputs/apk/debug/app-debug.apk`).

Without those two lines the Android app runs in its offline demo mode (local SQLite).
For day-1 deployment, the **web app is the recommended client** for students and staff.

---

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
