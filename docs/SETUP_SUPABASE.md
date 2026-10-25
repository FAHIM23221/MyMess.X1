# Supabase setup — Phases 1–2

This guide is for a new Supabase project or an optional project override. The repository bundles only the active project's public Android URL and publishable key in `config/supabase.public.properties`; it contains no privileged backend credential. That public configuration is sufficient for the app to authenticate against the already migrated active project.

## 1. Create the project and configure Auth

1. Create a Supabase project. Choose the nearest region that is available and appropriate for your members.
2. In **Authentication → Providers**, enable Email/password and disable public sign-ups. Account creation is intentionally operator-controlled in Phase 1.
3. In **Authentication → URL Configuration → Redirect URLs**, add this exact callback:

   ```text
   smarttransparentmess://auth
   ```

4. Configure email delivery for password recovery before real use. A development/default mailer may be rate-limited or deliver only to approved addresses.
5. In **Authentication → Users**, create the designated Main Admin Auth user and confirm its email. Use a strong unique password and protect the mailbox/account with MFA where available.

## 2. Apply the database migration

For a fresh project, open **SQL Editor**, review each complete file, and apply them once in this order:

1. `supabase/migrations/0001_phase1_foundation.sql` — schema, constraints, RLS, helpers, and protected profile/Main Admin RPCs.
2. `supabase/migrations/0002_phase2_billing_payments.sql` — additive finance ledger, member views, and audited finance RPCs.
3. `supabase/migrations/0003_phase2_view_grants.sql` — required SELECT-only tightening for Supabase's default view grants.
4. `supabase/migrations/0004_phase2_admin_finance_methods.sql` — explicit wallet methods, expanded charge categories, and strengthened payment/charge RPC validation.

These migrations are intended for a new project. On the active new project, all four were applied and inspected on 2026-10-25; do not apply them again there. If a fresh application fails, stop and review the error rather than partially editing tables or disabling RLS. Do not rerun policy/table creation as a substitute for fixing a failed migration.

## 3. Bootstrap exactly one Main Admin

1. Open `supabase/bootstrap/001_bootstrap_main_admin.sql` in a text editor.
2. Replace the three `REPLACE_*` placeholders with the confirmed Auth email, display name, and mess name.
3. Paste the edited SQL into SQL Editor and run it once. The block fails if the account is missing/unconfirmed or a Main Admin assignment already exists.
4. Do not commit or share a modified copy containing a real person's name or email. Keep the checked-in file as the placeholder template.
5. Check the resulting `profiles` and `main_admin_assignments` rows using the Dashboard's table viewer. The Main Admin role is derived from the assignment; the profile's stored `role` remains `MEMBER` intentionally.

Do not manually grant a client role, create a second assignment, or delete the assignment. Use the audited `transfer_main_admin` RPC for a planned transfer. If the current Main Admin is genuinely lost or compromised, follow the controlled owner-only recovery path in `supabase/bootstrap/002_recover_main_admin.sql` after external identity/approval checks. That function is revoked from all app/API roles, is not an app feature, and must be reviewed and tested before use.

## 4. Obtain public app configuration

In **Project Settings → API**, copy:

- Project URL → `SUPABASE_URL`
- Publishable key (or legacy anon public key) → `SUPABASE_PUBLISHABLE_KEY`

These values are public client configuration. Never use a `service_role` JWT, `sb_secret_…` key, database password, or project-owner credential. The Gradle build contains a check that stops when it detects recognizable service-role key formats.

### Local build

The bundled public file is used automatically. To override it for another project, copy `local.properties.example` to `local.properties` and fill the two values; blank values fall back to the bundled public file. `local.properties` is ignored by Git. On a machine with JDK 17, Android SDK Platform 36, and Gradle network access:

```sh
./gradlew testDebugUnitTest assembleDebug
python3 scripts/verify_built_config.py
```

### GitHub Actions build

No Actions Variables are required for the active public project configuration. To override it, open **Settings → Secrets and variables → Actions → Variables** and create `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY`. The publishable key is intentionally not treated as a privileged secret. Then run **Actions → Android debug APK → Run workflow**.

Blank Variables fall back to `config/supabase.public.properties`. After the build, `scripts/verify_built_config.py` checks generated `BuildConfig` before artifact upload. If all public/override configuration is removed, the app shows **Setup required** and does not fake a sign-in.

## 5. Provisioning and current limitations

- Public signup and member invitation screens are not present.
- The database RPC `provision_member_profile` links an already-created, email-confirmed Auth user to an active Member profile when called by the active Main Admin. It is not yet wired to an Android management screen.
- `set_profile_role`, `set_profile_status`, and `transfer_main_admin` are likewise server-side functions only; there is no role-management UI yet.
- Phase 2 includes a read-only member finance dashboard and an Admin/Main Admin finance workspace. Real-JWT authorization and installed-device mutation/PDF tests are still required before real financial data.
- Test with separate Member, Admin, Main Admin, suspended, cross-mess, and anonymous identities before storing any real member or financial data.

See [`SECURITY.md`](SECURITY.md) for the current open security verification items and [`ARCHITECTURE.md`](ARCHITECTURE.md) for the authorization model.
