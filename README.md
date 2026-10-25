# SMART TRANSPARENT MESS ERP — native Android

A native Android ERP using Kotlin, Jetpack Compose/Material 3, MVVM/repositories, Coroutines/Flow, Supabase Auth/Postgres, and server-side row-level security (RLS). It is not a web wrapper.

> **Verified baseline:** main commit `5aaf473f733c412aaf22113b71531a7f7ad84a01` passed Android workflow run `40153010838`, including JVM tests, debug build, and APK upload. Phase 1 Auth/profile/RLS and singleton Main Admin bootstrap are also applied and verified on the new Supabase project. The Phase 2 Android changes on this branch still require their own workflow result; see [`docs/IMPLEMENTATION_STATUS.md`](docs/IMPLEMENTATION_STATUS.md) for exact current and blocked states.

## Implemented source scope

### Phase 1

- Email/password sign-in, password recovery, and password update.
- Encrypted local Supabase session and PKCE verifier storage using Android Keystore AES-GCM.
- Member profile read/update for `full_name` and `phone_number` only.
- Backend-derived Member, Admin/Manager, and Main Admin dashboard routing.
- Hidden Admin entry: tap the app mark seven times within five seconds on App information. This is navigation only; backend role checks and RLS are authoritative.
- Supabase schema, RLS, protected profile/role/status RPCs, audited Main Admin transfer, owner-only recovery template, and audit foundation.
- English/Bengali resources, JVM tests, static security checks, and a debug-APK Actions workflow.

### Phase 2 vertical slice

- Additive billing periods, charges, adjustments, payments, receipts, reversals, and audited carry-forward SQL.
- Integer BDT minor-unit arithmetic with opening-due, partial-payment, overpayment/advance, credit, and overflow tests.
- Duplicate-request protection, closed-period rejection, receipt voiding, and correction history.
- Member dashboard backed by `security_invoker` summary/receipt views; empty states are shown instead of demo balances.
- Remote schema/grant inspection and a rollback-only RPC/calculation smoke script. Details: [`docs/PHASE2_FINANCE.md`](docs/PHASE2_FINANCE.md).

Admin finance entry UI, meals/rice/fines/expenses/cash book, duties/notices, messaging, reports/backup, and the remaining Phases 3–6 workflows are not yet complete. No fake authentication, sample ledger, or demo financial figures are used.

## Quick start

1. Create a Supabase project and follow [`docs/SETUP_SUPABASE.md`](docs/SETUP_SUPABASE.md). For a fresh project, review and apply migrations in order:
   - `supabase/migrations/0001_phase1_foundation.sql`
   - `supabase/migrations/0002_phase2_billing_payments.sql`
   - `supabase/migrations/0003_phase2_view_grants.sql`
2. Perform the one-time Main Admin bootstrap only after the designated Auth user exists and is confirmed. Do not rerun an already completed bootstrap.
3. Copy `local.properties.example` to `local.properties` and fill in the project URL and **publishable/anon client key**. Gradle rejects recognizable `sb_secret_` and `service_role` keys. Never place a privileged key in the app, repository, APK, logs, or GitHub Variables.
4. On a machine with JDK 17 and Android SDK Platform 36, run:

   ```sh
   python3 scripts/verify_project.py
   ./gradlew testDebugUnitTest assembleDebug
   ```

   The debug APK output is `app/build/outputs/apk/debug/app-debug.apk`.
5. Or run **Android debug APK** from GitHub Actions. Read [`docs/GITHUB_PHONE_GUIDE.md`](docs/GITHUB_PHONE_GUIDE.md) for phone download steps.

An unconfigured build deliberately opens **Setup required** rather than pretending that login or data access succeeded.

## Configure GitHub Actions

The workflow builds without Supabase Variables, but that APK remains unconfigured. For a connected debug artifact, add these repository **Actions Variables**:

- `SUPABASE_URL`
- `SUPABASE_PUBLISHABLE_KEY` — a publishable key (`sb_publishable_…`) or legacy anon JWT, never a `service_role`/`sb_secret_…` key.

Then run **Actions → Android debug APK → Run workflow**. The workflow uploads `smart-transparent-mess-debug-apk-<commit>` for 14 days; it does not publish a production release.

The current integration received HTTP 403 for repository Variables, so no configured APK is claimed. This blocker and the required permission are recorded in [`docs/IMPLEMENTATION_STATUS.md`](docs/IMPLEMENTATION_STATUS.md).

## Project layout

```text
app/                 Native Android app, Compose UI, repositories, domain, and tests
supabase/migrations/ Ordered additive Postgres schema, RLS, grants, and protected RPCs
supabase/bootstrap/  One-time Main Admin bootstrap and controlled recovery templates
supabase/tests/      Rollback-only database smoke/integration SQL
.github/workflows/   Static checks, JVM tests, debug build, and APK artifact upload
docs/                Status, setup, architecture, security, testing, and phone guide
scripts/             Dependency-free static resource/security contract checks
```

## Security boundaries

- The Android client contains only public Supabase configuration. A Gradle guard rejects recognizable privileged service-role keys before creating `BuildConfig`.
- UI role checks are not authorization. Protected tables use RLS; role, status, Main Admin, and finance mutations are server-side and audited.
- `MAIN_ADMIN` is derived from a protected one-row assignment, not writable through profile updates. Assignment deletion and unsafe suspension are blocked.
- Finance tables/views are SELECT-only for authenticated clients. All finance writes require an active same-mess Admin/Main Admin RPC check. Original payments and reversals are preserved.
- The app disables Android backup and stores Auth session/PKCE data encrypted with an Android Keystore-held key.
- SDK logging is `LogLevel.NONE` because the pinned SDK source includes an error path that may interpolate an access token. Installed-app log verification remains open; see [`docs/SECURITY.md`](docs/SECURITY.md).

## Project status

No production license, Play Store release, production signing, or real-data readiness is implied. Use the implementation register and test matrix before entering real member or financial data.
