# SMART TRANSPARENT MESS ERP — Phase 1

A new Android-only Phase 1 source bundle for the SMART TRANSPARENT MESS ERP. It uses Kotlin, Jetpack Compose, Supabase Auth/Postgres, and server-side row-level security (RLS).

> **Verification status:** This workspace does not have Gradle or the Android SDK, so the app has **not** been built or tested here. No APK is included or claimed. The GitHub Actions workflow in `.github/workflows/android-debug.yml` is configured to run the unit tests, build a debug APK, and upload the APK as a 14-day artifact when the source is in GitHub. That workflow has not been run yet.

## Phase 1 scope

- Email/password sign-in, password recovery, and password update.
- Encrypted local Supabase session and PKCE verifier storage using Android Keystore AES-GCM.
- Member profile read/update for the permitted fields `full_name` and `phone_number` only.
- Role-specific member, Admin/Manager, and Main Admin landing screens using backend-provided active profiles.
- Hidden Admin entry: tap the app mark seven times within five seconds on App information. This is only a navigation convenience; backend role checks and RLS are authoritative.
- Supabase schema, RLS policies, protected role/status/profile provisioning RPCs, audited Main Admin transfer, owner-only audited recovery template, and audit log foundation.
- English and Bengali resources, unit tests, security/architecture notes, and a manual debug-APK Actions workflow.

This phase does **not** implement balances, payments, meals, rice, duties, reports, notifications, or Phases 2–6. It uses no fake authentication or demo financial figures. Member/Auth account creation is not exposed as a public in-app signup screen.

## Quick start

1. Create a Supabase project and follow [`docs/SETUP_SUPABASE.md`](docs/SETUP_SUPABASE.md). Apply the migration at `supabase/migrations/0001_phase1_foundation.sql` and perform the one-time Main Admin bootstrap.
2. Copy `local.properties.example` to `local.properties` and fill in the project URL and **publishable/anon client key**. The Gradle build rejects Supabase `sb_secret_` and legacy `service_role` keys. Never place a privileged key in the app, repository, APK, or GitHub Variables.
3. On a machine with JDK 17, Android SDK Platform 36, and network access, run:

   ```sh
   ./gradlew testDebugUnitTest assembleDebug
   ```

   The debug APK output is `app/build/outputs/apk/debug/app-debug.apk`.
4. Or add the source to GitHub and run **Android debug APK** from the Actions tab. Read [`docs/GITHUB_PHONE_GUIDE.md`](docs/GITHUB_PHONE_GUIDE.md) for Android-phone download steps.

An unconfigured build deliberately opens a “Setup required” screen rather than pretending that login or data access succeeded.

## Configure GitHub Actions

The workflow builds without Supabase variables, but the app will remain unconfigured. To create a configured debug artifact, add these repository **Actions Variables** (not service-role secrets):

- `SUPABASE_URL`
- `SUPABASE_PUBLISHABLE_KEY` — a Supabase publishable key (`sb_publishable_…`) or legacy anon JWT, never a `service_role`/`sb_secret_…` key.

Then run **Actions → Android debug APK → Run workflow**. The workflow uploads `smart-transparent-mess-debug-apk-<commit>`; it does not publish a production release. Debug APKs are for testing and are not Play Store releases.

## Project layout

```text
app/                 Android app, Compose UI, repositories, domain, and tests
supabase/migrations/ Postgres schema, RLS, and protected functions
supabase/bootstrap/  One-time Main Admin bootstrap SQL
.github/workflows/   Manual/CI debug APK build and artifact upload
docs/                Setup, architecture, security, testing, and phone guide
scripts/             Dependency-free static resource/security contract check
```

## Security boundaries

- The Android client contains only a public Supabase client key. A Gradle guard rejects recognizable privileged Supabase service-role keys before creating `BuildConfig`.
- UI role checks are not authorization. Protected tables use RLS; role, account status, Main Admin transfer, profile provisioning, and audit writes are server-side.
- `MAIN_ADMIN` is derived from a protected one-row assignment table, not writable through the profile API. The migration requires one active assignment per mess, blocks assignment deletion, and preserves the current Main Admin until transfer.
- The app disables Android backup and stores Auth session/PKCE data encrypted with a key held by Android Keystore.
- SDK logging is configured to `LogLevel.NONE` because the pinned SDK source includes an error path that may interpolate an access token. This mitigation has **not** been verified in a built Android app; see [`docs/SECURITY.md`](docs/SECURITY.md).

## License / project status

No production license or deployment is implied. This is a Phase 1 source project awaiting Supabase setup, GitHub Actions execution, build/test review, and security verification.
