# SMART TRANSPARENT MESS ERP — native Android

A native Android ERP using Kotlin, Jetpack Compose/Material 3, MVVM/repositories, Coroutines/Flow, Supabase Auth/Postgres, and server-side row-level security (RLS). It is not a web wrapper.

> **Verified baseline:** main commit `5aaf473f733c412aaf22113b71531a7f7ad84a01` passed Android workflow run `40153010838`, including JVM tests, debug build, and APK upload. Phase 1 Auth/profile/RLS and singleton Main Admin bootstrap are also applied and verified on the new Supabase project. Phase 2 commit `35119bb131a5df5cb41d3079850c80a8c30112a0` also passed run `40157238078`, including its new finance JVM tests and debug build. Configured commit `e01ffb7d8a11d8c7a0117c36606ad07af04213f8` passed run `40157837474`, including generated-`BuildConfig` verification and artifact upload. See [`docs/IMPLEMENTATION_STATUS.md`](docs/IMPLEMENTATION_STATUS.md) for exact current and blocked states.

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

The active project's public Android configuration is intentionally bundled in `config/supabase.public.properties`. It contains only the HTTPS project URL and publishable client key; Auth, RLS, grants, and RPC authorization protect the backend. No privileged credential is needed to build or run the app.

1. On a machine with JDK 17 and Android SDK Platform 36, run:

   ```sh
   python3 scripts/verify_project.py
   ./gradlew testDebugUnitTest assembleDebug
   python3 scripts/verify_built_config.py
   ```

   The debug APK output is `app/build/outputs/apk/debug/app-debug.apk`.
2. Or run **Android debug APK** from GitHub Actions and download the newly uploaded artifact. Read [`docs/GITHUB_PHONE_GUIDE.md`](docs/GITHUB_PHONE_GUIDE.md) for phone steps.
3. To use another Supabase project, follow [`docs/SETUP_SUPABASE.md`](docs/SETUP_SUPABASE.md), then override the bundled values through `local.properties` or nonblank Actions Variables. For a fresh backend, review/apply migrations `0001`, `0002`, and `0003` in order and perform the one-time Main Admin bootstrap once.

A build with no valid public or override configuration deliberately opens **Setup required**. Older artifacts without the bundled public configuration show that screen even though the backend exists.

## Configuration precedence and GitHub Actions

Gradle resolves each value in this order: nonblank Gradle property → nonblank environment variable → nonblank `local.properties` → bundled public config. Empty GitHub Variables therefore do not erase the working public defaults.

Optional repository **Actions Variables** remain available for overrides:

- `SUPABASE_URL`
- `SUPABASE_PUBLISHABLE_KEY` — publishable/anon client configuration only, never `service_role`/`sb_secret_…`.

After `assembleDebug`, the workflow runs `scripts/verify_built_config.py` against generated `BuildConfig`; the APK is uploaded only when a valid HTTPS project URL and publishable key are actually present. The workflow does not publish a production release.

The earlier Variables API returned HTTP 403. That isolated permission issue remains documented, but it no longer prevents a configured debug build because the publishable client configuration is bundled intentionally.

## Project layout

```text
app/                 Native Android app, Compose UI, repositories, domain, and tests
config/              Bundled public Android Supabase URL/publishable key only
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
