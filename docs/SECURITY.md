# Security notes and limitations

This is a security-conscious Phase 1 foundation, not a completed security audit. Review the SQL and test it against a real Supabase project before using real member data.

## Secrets and build configuration

- `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY` are public client configuration. The app may include them in `BuildConfig` and in a debug APK.
- The Gradle build rejects recognizable `sb_secret_…` and legacy JWTs with a `role: service_role` claim. Do not try to bypass this check. Never add database passwords, service-role/service-account keys, signing passwords, or private signing files to the repository, APK, workflow variables, or project ZIP.
- `config/supabase.public.properties` intentionally contains only the active project's public URL and publishable Android key so CI can produce a connected build without privileged secrets. `local.properties` is ignored by Git and can override it; use `local.properties.example` as a template. The CI built-config check fails before upload if the generated debug configuration is blank or privileged.
- GitHub Actions stores only the APK build artifact; it does not publish a release or sign a production APK.

## Auth, session storage, and deep links

- The Supabase Auth client uses PKCE and the redirect `smarttransparentmess://auth`.
- Session and PKCE verifier bytes are encrypted with AES-GCM using a key held by Android Keystore. The encryption is app-local; it does not protect a rooted/unlocked device, instrumentation, a compromised OS, or a user who can access the app process. Hardware-backed Keystore availability is not assumed.
- `android:allowBackup="false"` is set. A reinstall or device migration may require signing in again.
- Never log passwords, access/refresh tokens, authorization headers, full Auth session objects, or sensitive profile data.

### SDK log risk — unresolved verification item

A source review of the pinned `supabase-kt` 3.8.0 Auth code found an error path that can interpolate an access token into a log message. The client config sets `defaultLogLevel = LogLevel.NONE` to reduce that risk. This exact path has **not** been exercised in a compiled Android app, so do not treat the mitigation as proven. Before production use, verify with a test token and captured device logs that token-bearing messages are suppressed, or upgrade/review the SDK source and add a regression test. Do not enable SDK verbose logging as a workaround.

## Backend authorization

- RLS is enabled on every Phase 1 application table. The anonymous role receives no table/function grants.
- A signed-in client can update only `profiles.full_name` and `profiles.phone_number` for its own active profile. Role, status, manager flag, mess association, and assignment fields are not client-writable.
- RLS functions use `SECURITY DEFINER` with `search_path` fixed to `public, pg_temp`; execution is revoked from `PUBLIC`/`anon` and granted narrowly to `authenticated` where needed.
- Role/status/provisioning/transfer operations validate the active Main Admin on the server and record a reason and audit row. The Main Admin cannot self-promote through the profile endpoint; `MAIN_ADMIN` is derived from the singleton assignment.
- The assigned Main Admin must be active. Assignment deletion and suspension of the assigned account are blocked. Transfer updates the row atomically and requires an active target in the same mess.
- Main Admin recovery after loss of credentials is **not** available in the app. The migration includes `recover_main_admin_owner_only`, whose `EXECUTE` privilege is revoked from `PUBLIC`, `anon`, `authenticated`, and `service_role`; use it only from a project-owner SQL session after out-of-band authority/approval checks. The template suspends the old account and writes an audit entry with an external approval reference. This owner-only path has not been exercised or independently reviewed; do not casually edit or delete the assignment row.
- Phase 1 has no in-app member invitation/provisioning screen. An authorized Main Admin RPC can link an already-created, confirmed Auth user to a profile, but the RPC is not exposed as a management UI yet.

## App behavior

- The dashboard is gated on a matching Auth user ID and an active profile from the backend. Missing, malformed, or unsupported roles fail closed.
- The hidden Admin entry is seven taps on the App information mark within five seconds. It only prompts a separate administrator sign-in; it does not alter authorization.
- The app does not display fake balances, payments, meal counts, or operational success states.

## Before real deployment

1. Review `supabase/migrations/0001_phase1_foundation.sql` with a PostgreSQL/Supabase reviewer.
2. Execute positive and negative RLS/RPC tests using member, Admin, Main Admin, inactive, and anonymous identities.
3. Verify token redaction in Android logs, deep-link recovery, session refresh, offline sign-out, and Keystore-loss behavior.
4. Build and test the debug APK in CI; review the artifact and dependencies. Do a separate threat review before production signing or Play deployment.
