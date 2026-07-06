# ClassKey PRD — App Identity and Feature Scope

## 1. Product Identity

**Product name:** ClassKey  
**Tagline:** Secure attendance. Verified presence.  
**Category:** Single-entry attendance verification app for colleges, labs and controlled academic spaces.  
**Primary users:** Students, Staff, Admin/PT Sir.  
**Core promise:** A student enters once, verifies identity and location, and the day/session attendance is marked securely.

ClassKey is **not** a subject-wise, period-wise, or timetable attendance system. It is an entry-level attendance product.

## 2. Design Identity

ClassKey should visually match the provided Figma/React design reference:

- clean light-first UI with optional dark mode
- blue, navy, white and teal brand palette
- rounded cards and premium spacing
- academic + secure visual tone
- smooth login, profile, verification and success animations
- bottom navigation for core screens
- strong ClassKey logo usage on splash, login, app icon and dashboard

### Brand colors

| Token | Light | Dark |
|---|---:|---:|
| Background | `#F0F4F8` | `#0C1527` |
| Surface/Card | `#FFFFFF` | `#152035` |
| Primary | `#1B6FE4` | `#4A9EF5` |
| Accent | `#0DADA5` | `#48D597` |
| Text | `#0D1B3E` | `#E8EDF8` |

## 3. Correct Attendance Model

### Required workflow

```text
Student opens app
→ Login / biometric login
→ Tap Mark Attendance
→ Location permission and latitude/longitude capture
→ Distance checked against college/lab geofence
→ Biometric/device verification
→ Attendance marked Present or Late
```

### Rules

- One attendance record per student per daily entry sheet.
- No subject field.
- No period field.
- No timetable logic.
- Staff does not need to manually start a daily session every day.
- The daily attendance sheet should auto-exist for the date.
- Staff only updates location/radius/rules if needed.
- QR/fallback code is only an assisted/manual fallback, not the main flow.

## 4. Student Features

1. Login and signup.
2. Signup using college email only.
3. Profile setup:
   - name
   - register number
   - mobile
   - college email
   - profile photo
4. Enable biometric login.
5. Mark attendance:
   - location verification
   - biometric verification
   - fallback QR/manual code if normal verification fails
6. View own attendance history.
7. Request:
   - OD
   - half-day leave
   - full-day leave
   - permission / early leave
8. View request status and notifications.

## 5. Staff Features

1. Staff login / biometric login.
2. Daily attendance sheet auto-loaded.
3. View student list.
4. View live attendance list:
   - Present
   - Late
   - Absent
   - OD
   - Half-day
   - Permission
5. Approve/reject OD and leave requests.
6. Staff-assisted attendance fallback with audit reason.
7. View daily reports.
8. Export/report email support when backend is connected.

## 6. Admin Features

1. Manage students.
2. Manage staff.
3. Configure college/lab geofence.
4. View all daily attendance.
5. Approve/reject special cases.
6. Device approval/replacement management.
7. Audit logs.
8. Reports and email automation.

## 7. Biometric Requirements

Android does **not** allow apps to capture or store raw fingerprint data. ClassKey must use Android's secure biometric system.

Correct production flow:

```text
Student enables biometric
→ Android system verifies fingerprint/device lock
→ ClassKey stores only biometric-enabled flag + device binding key
→ Next login/attendance uses Android BiometricPrompt
→ App receives success/failure only
```

Required cases:

- biometric available
- fingerprint not enrolled
- device lock fallback
- unsupported phone
- biometric cancelled
- biometric failed
- retry state

## 8. Location Requirements

ClassKey should capture:

- latitude
- longitude
- accuracy if available
- timestamp
- distance from allowed geofence

The app must request location permission only when needed for attendance or geofence setup.

Backend should also re-check location distance using Haversine formula. Do not trust frontend-only location validation.

## 9. Anti-cheat and Security Requirements

- JWT backend authentication.
- Passwords hashed with bcrypt/argon2.
- Device binding.
- Refresh token storage.
- Backend-side location verification.
- Backend-side duplicate attendance blocking.
- Audit logs for manual attendance and suspicious attempts.
- Obfuscation/minification for release APK.
- No hardcoded production secrets.
- No frontend-only attendance success.
- Root/mock-location/tamper detection in production pass.

## 10. Acceptance Criteria

The system is complete when:

1. Android app opens in Android Studio without compile errors.
2. UI closely follows the Figma design.
3. Student signup/profile setup exists.
4. Biometric login works using Android BiometricPrompt.
5. Location permission asks correctly.
6. Latitude/longitude capture works on real phone.
7. Attendance marks Present/Late once per day.
8. Duplicate attendance is blocked.
9. Staff can see student list and daily attendance list.
10. OD/leave/permission approval works.
11. Reports are daily-entry based only.
12. Backend + PostgreSQL version persists real data.
13. No prototype placeholder buttons remain.
