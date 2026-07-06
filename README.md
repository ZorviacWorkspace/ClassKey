# ClassKey — Secure attendance. Verified presence.

A single **daily entry attendance** system for colleges, now **centralized on Supabase**:
one Postgres database + Auth + Row Level Security + Realtime. A student verifies
**live location** (server-side Haversine geofence) and identity, and is marked
**Present** or **Late**. When a student marks on their phone, **staff and admin see it
instantly** on their dashboard. OD / half-day / leave / permission are approval flows.

## 🚀 Deploy it → **[DEPLOYMENT.md](DEPLOYMENT.md)** (~15 min, 100% free)

| Piece | Folder | What it is |
|---|---|---|
| **Database & security** | [`supabase/`](supabase/) | SQL migrations (schema, RLS, RPC functions, storage, realtime) + demo seed |
| **Web app** (students + staff + admin) | [`web/`](web/README.md) | Next.js on Vercel — role logins, live dashboard, admin management, reports |
| **Android app** | `app/` | Kotlin + Compose. Connects to the **same Supabase** (adds a real fingerprint check); runs in offline SQLite demo mode if no keys are set |

**Key rules enforced in the database (not in any client):** one record per student per day,
Present/Late/cutoff time windows, geofence + GPS-accuracy checks, device binding with
approval flow, manual overrides require an audit reason, all sensitive writes go through
`security definer` RPCs, and roles are verified server-side on every request.

Demo logins (password `ChangeMe123!`): Admin `admin@classkey.local` · Staff
`staff@classkey.local` · Student `CS21001` / `priya.sharma@classkey.local`.

---

The rest of this file is the **Android app** guide. With `SUPABASE_URL` +
`SUPABASE_ANON_KEY` in `local.properties` it runs in **cloud mode** (same data as the web
dashboard); without them it falls back to the self-contained offline demo below.

---

## 1. What you need

- **Android Studio** (any recent version, e.g. Koala/Ladybug or newer)
- A **real Android phone** (Android 8.0+, so almost any phone) with:
  - a fingerprint sensor **or** a screen lock (PIN/pattern) set up
  - Location/GPS
- A USB cable

An emulator also works, but fingerprint and GPS are much easier on a real phone.

## 2. Open the project

1. Start Android Studio.
2. Click **File → Open** (or "Open" on the welcome screen).
3. Select this folder: **`ClassKeyModernAttendance`** (the folder containing this README —
   **not** the inner `app` folder).
4. Click **Trust Project** if asked.
5. Wait for **Gradle Sync** to finish (bottom status bar — first time can take several minutes
   while it downloads dependencies).

If sync fails with a network error, just retry: **File → Sync Project with Gradle Files**.

## 3. Run it on your phone

1. On the phone: **Settings → About phone → tap "Build number" 7 times** → Developer options unlock.
2. **Settings → Developer options → enable "USB debugging"**.
3. Plug the phone into the laptop. Tap **Allow** on the phone when asked about USB debugging.
4. In Android Studio, your phone appears in the device dropdown (top toolbar). Select it.
5. Press the green **Run ▶** button.
6. The app installs and opens on your phone.

APK is also produced at:

```
app/build/outputs/apk/debug/app-debug.apk
```

You can copy that file to any phone and install it directly (enable "Install unknown apps").

To build from a terminal instead of the Run button (Windows):

```bat
gradlew.bat assembleDebug
```

## 4. Demo logins

Password for **every** demo account: `ChangeMe123!`

| Role    | Login (any of these)                          |
|---------|-----------------------------------------------|
| Student | `CS21001` / `priya.sharma@classkey.local` / `9990002000` |
| Staff   | `staff@classkey.local` / `9990003000`          |
| Admin   | `admin@classkey.local` / `9990001000`          |

More seeded students: `CS21002` … `CS21005` (same password).
The login screen has one-tap chips that fill these in for you.

## 5. First full test (5 minutes)

1. **Login as Admin** (`9990001000` / `ChangeMe123!`).
2. Go to the **Campus** tab → tap **"Set to this phone's current location"** → allow location →
   tap **Save Campus Settings**. Now the geofence is wherever you are standing (default radius 300 m).
3. **Profile → Sign Out**, then **login as Student** (`CS21001`).
4. Optional first-time flow: create a brand-new student via **"New student? Create account"** —
   it walks through college-email signup → profile photo → biometric enable.
5. Tap **Mark Attendance** → **Verify Location** (allow permission) → when location turns green,
   tap the pulsing **fingerprint button** → Android's real fingerprint/PIN prompt appears →
   success screen shows Present/Late with distance.
6. Try marking again — the app blocks duplicates for the day.
7. Go to **Requests** → submit an OD request.
8. Sign out, **login as Staff** (`9990003000`) → dashboard shows live counts, the marked student,
   and any **suspicious attempts** (e.g. tries from outside the geofence) → **Approvals** tab →
   approve the OD → the student's attendance for that date becomes "On Duty" automatically.
9. **Students** tab → tap any student → the detail card shows their recent history and lets staff
   do **manual assist** (needs an audit reason). As **Admin** the same card also has **Edit**,
   **Reset password**, and **Delete**.
10. **Reports** tab → use the **◀ / ▶ arrows to view any past day**, see the weekly chart, and
    **Export CSV** for the selected day / last 7 / last 30 days (opens the Android share sheet).
11. As **Admin**, Home → **Audit Log** shows every action with who did it; the **+** on the
    Students tab adds a **Student or a Staff/Admin** account.

## 5a. Attendance window (start / present / late / cutoff)

Admin → **Campus** sets three times (24-hour `HH:mm`):

| Time | Meaning |
|---|---|
| **Opens at** | Before this, marking is blocked ("Attendance opens at HH:mm"). |
| **Present till** | From *Opens at* to here → marked **Present**. |
| **Cutoff** | From *Present till* to here → marked **Late**. After *Cutoff*, self-marking closes and unmarked students count **Absent**. |

Demo defaults are wide (opens `06:00`, present till `20:00`, cutoff `23:30`) so testing at any
normal hour marks Present. **To demo a "Late" mark**, set *Present till* to a time a minute ago.
**To demo "closed"**, set *Cutoff* to a past time.

**Holidays:** Admin → Campus → *Holidays* marks any day so it never counts as Absent and
attendance isn't required that day.

## 6. Testing location & biometric

**Location**
- Phone Location/GPS must be ON (quick settings tile).
- Grant the permission when asked ("While using the app" + Precise).
- If it says it can't get a fix: stand near a window / go outside, then retry.
- If you are "outside the allowed radius": you (as Admin) haven't set the campus to where you are —
  see step 5.2 above — or increase the radius (e.g. 500 m) for testing.
- Every rejected attempt is logged and shown to staff as a suspicious attempt.

**Biometric**
- Add a fingerprint in phone **Settings → Security** (or at least a PIN/pattern —
  the prompt falls back to device credentials automatically).
- The app handles: not enrolled, no hardware, hardware busy, wrong finger, cancel, and lockout —
  each shows a friendly message instead of crashing.
- ClassKey never stores raw fingerprint data. Android's secure hardware verifies it and the app
  only receives success/failure (this is the only way Android permits — by design).

**Emulator tips** (if you have no phone):
- Set location: emulator "…" menu → Location → set a point → also set campus there as Admin.
- Fingerprint: enroll one in the emulated device's Settings → Security, then use
  emulator "…" menu → Fingerprint → Touch sensor when the prompt is open.

## 7. Where data lives

Everything is stored on-device in SQLite (`classkey_v3.db`): users, attendance,
requests, notifications, audit log, suspicious attempts, and campus settings.
Passwords are stored as salted SHA-256 hashes, never in plain text.
Uninstalling the app (or clearing its storage) resets to the seeded demo data.

## 8. Troubleshooting

| Problem | Fix |
|---|---|
| "AndroidX dependencies…" error | Already handled in `gradle.properties` (`android.useAndroidX=true`). Do **File → Sync Project with Gradle Files**. |
| Run button greyed out | Wait for Gradle sync; make sure you opened the **root** folder, not `app`. |
| Sync fails / dependency download error | Check internet, then **File → Sync Project with Gradle Files** again. Corporate proxies may need Gradle proxy settings. |
| "SDK location not found" | Android Studio usually fixes `local.properties` automatically; otherwise **File → Project Structure → SDK Location**. |
| App installs but location always fails | GPS off, permission denied, or campus not set to your position (Admin → Campus). |
| Biometric button says nothing enrolled | Add fingerprint or PIN in phone settings, then retry. |
| Want a clean start (fresh demo data) | Long-press app icon → App info → Storage → **Clear storage**. |
| Build cache weirdness | `gradlew.bat clean` then rebuild, or **File → Invalidate Caches / Restart**. |

## 9. Deployable version staff & students can use (Supabase + Vercel)

This native app stores data per-phone. For a **shared** system that **anyone opens via a URL**
(no install), deploy the web app in **[`web/`](web/README.md)** — Supabase Postgres + Vercel,
both free:

1. Supabase → new project → SQL Editor → run `web/supabase/schema.sql` then `seed.sql`.
2. Vercel → import your GitHub repo → **Root Directory `web`** → add env vars `DATABASE_URL`
   (Supabase pooled URI, port 6543) and `JWT_SECRET` → **Deploy**.
3. Open `https://your-app.vercel.app/api/health` → should say `database: connected`, then share
   the URL. Students/staff can **Add to Home screen** to use it like an app.

Full step-by-step: **[web/README.md](web/README.md)**.

## 10. Project layout

```
app/src/main/java/com/classkey/modernattendance/
├── MainActivity.kt          # thin host: theme, tabs, overlays, permission/photo launchers
├── AppViewModel.kt          # session + reload state
├── data/                    # Models, SQLite (Db.kt), business logic (Repo.kt)
├── hw/                      # LocationClient (Fused provider), Biometric prompt wrapper
└── ui/                      # Theme tokens + Components + screens per feature
    ├── AuthScreens.kt       # splash, login, signup, profile setup, biometric setup
    ├── StudentScreens.kt    # home, mark-attendance flow, success, history, requests
    ├── StaffScreens.kt      # dashboard, student list + manual assist, approvals, reports, audit
    ├── AdminScreens.kt      # campus geofence + timing configuration
    └── CommonScreens.kt     # notifications, profile/settings
design-reference/            # the Figma design export this UI follows
```
