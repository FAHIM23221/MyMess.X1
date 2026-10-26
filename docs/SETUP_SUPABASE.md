# Supabase setup — SMART TRANSPARENT MESS ERP

This guide covers the native Android + Supabase vertical slices through Phase E. The repository bundles only the active project's public Android URL and publishable key in `config/supabase.public.properties`; it contains no privileged backend credential. Public configuration is sufficient for the already migrated active project, not proof of production readiness.

## 1. Create/configure Auth

1. Create a Supabase project in an appropriate region.
2. In **Authentication → Providers**, enable Email/password and disable public sign-ups. Account creation is operator/invitation controlled.
3. In **Authentication → URL Configuration → Redirect URLs**, add exactly:

   ```text
   smarttransparentmess://auth
   ```

4. Configure SMTP/email delivery before real invitation or recovery use. Until delivery is configured and observed, the app must show an unconfirmed/failed status rather than claim email success.
5. Create and confirm the designated Main Admin Auth user through an approved operator flow. Use a strong unique password and protect the mailbox/account with MFA where available.

## 2. Apply migrations in order

For a fresh project, review each complete file and apply once in this order. Do not rerun already applied migrations on the active project.

1. `0001_phase1_foundation.sql` — schema, constraints, RLS, helpers and protected profile/Main Admin RPCs.
2. `0002_phase2_billing_payments.sql` — finance ledger, member views and audited RPCs.
3. `0003_phase2_view_grants.sql` — SELECT-only view grant tightening.
4. `0004_phase2_admin_finance_methods.sql` — wallet methods, categories and strengthened finance validation.
5. `0005_phase2_active_read_hardening.sql` — active-profile requirements for finance reads.
6. `0006_phase3_meals_rice_expenses_cashbook.sql` — operational ledgers, RLS/grants, RPCs, cash-book triggers/backfill.
7. `0007_member_management.sql` and `0008_member_management_type_fix.sql` — invitations, lifecycle, capacity, management RPCs and audit access.
8. `0009_phase_b_finance_extensions.sql`, `0010_phase_b_finance_request_id_fix.sql`, `0011_phase_b_extended_snapshot.sql` — fines, payment reviews, meal billing and snapshot.
9. `0012_phase_c_operations_extensions.sql`, `0013_phase_c_cash_business_date_fix.sql`, `0014_phase_c_extended_snapshot.sql` — schedules, purchase metadata, linked corrections, alerts, approvals, accounts/locks and snapshot.
10. `0015_phase_d_duties_notices.sql`, `0016_phase_d_extended_snapshot.sql` — duties, rotations, notices, read receipts and snapshot.
11. `0017_phase_e_community.sql`, `0018_phase_e_extended_snapshot.sql`, `0019_phase_e_message_report_review.sql`, `0020_phase_e_active_moderation_fix.sql`, `0021_notification_payload_bounds.sql` — messages, anonymous suggestions, complaints, moderation, report lifecycle and bounded notification payloads.
12. `0022_member_join_date_business_timezone.sql` — Asia/Dhaka join-date validation for both management RPCs.

On the active project `iwoiwgkaevobxqojyphb`, these slices were applied and inspected on 2026-10-25. Do not apply them again there. If a fresh application fails, stop and review the error; never disable RLS or partially hand-edit tables to force a pass.

## 3. Bootstrap exactly one Main Admin

1. Open `supabase/bootstrap/001_bootstrap_main_admin.sql` in a text editor.
2. Replace the `REPLACE_*` placeholders with the confirmed Auth email, display name and mess name.
3. Run the reviewed block once in SQL Editor. It fails if the account is missing/unconfirmed or an assignment already exists.
4. Do not commit or share a modified copy containing a real person's name/email. Keep the checked-in file as the placeholder template.
5. Read back `profiles` and `main_admin_assignments`. The Main Admin role is derived from the singleton assignment; the profile's stored role intentionally remains `MEMBER`.

Do not manually grant a client role, create a second assignment or delete the assignment. Use audited `transfer_main_admin` for a planned transfer. Owner-only recovery in `supabase/bootstrap/002_recover_main_admin.sql` requires external identity/approval checks and is revoked from app/API roles.

## 4. Invitation function and delivery status

`supabase/functions/member-invitation/index.ts` is the server-side invitation handler. It is deployed with JWT verification on the active project. Configure only approved server-side environment secrets through the Supabase connection; never place a service-role key, SMTP password or database credential in Android, Git, logs or audit records.

The Android management workspace calls the protected onboarding/resend paths and displays `PENDING`, `SENT`, `FAILED`, `UNCONFIRMED` or `NOT_REQUIRED` as returned by the backend. SMTP is currently unconfigured, so actual outbound delivery and acceptance remain unverified.

## 5. Obtain public app configuration

In **Project Settings → API**, copy only:

- Project URL → `SUPABASE_URL`
- Publishable key (or legacy anon public key) → `SUPABASE_PUBLISHABLE_KEY`

Never use a `service_role` JWT, `sb_secret_…` key, database password or project-owner credential in the app. The Gradle build checks for recognizable privileged key formats.

### Local build

The bundled public file is used automatically. To override it for another project, copy `local.properties.example` to ignored `local.properties` and fill the two public values; blank values fall back to the bundled file. With JDK 17, Android SDK Platform 36 and Gradle network access:

```sh
./gradlew testDebugUnitTest assembleDebug
python3 scripts/verify_built_config.py
```

### GitHub Actions build

No Actions Variables are required for the active public project configuration. To override it, use **Settings → Secrets and variables → Actions → Variables** for `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY`; the publishable key is not a privileged secret. Blank Variables fall back to `config/supabase.public.properties`. `scripts/verify_built_config.py` checks generated `BuildConfig` before artifact upload. If all public/override configuration is removed, the app shows **Setup required** and does not fake sign-in.

## 6. Current functional boundary

- Member management, finance, operations, duties/notices and Community screens are wired to repositories/RPCs; they are not disconnected demo screens.
- Members can read only authorized rows. Suggestions have separate Main Admin-only identity storage; private messages are participant-scoped with explicit Main Admin moderation visibility. RLS and RPC checks remain server-side.
- `publish_due_notices` is an Admin-invoked RPC. No automatic scheduler, FCM token registration or external push delivery is configured.
- Unified monthly reports, CSV export, secure ERP backup/restore and final real-JWT/device verification are still open.
- Test separate Member, Admin, Main Admin, suspended, departed, cross-mess and anonymous identities before storing real member, operational or financial data.

See [`FEATURE_AUDIT.md`](FEATURE_AUDIT.md), [`IMPLEMENTATION_STATUS.md`](IMPLEMENTATION_STATUS.md), [`TESTING.md`](TESTING.md) and [`SECURITY.md`](SECURITY.md) for evidence and blockers.
