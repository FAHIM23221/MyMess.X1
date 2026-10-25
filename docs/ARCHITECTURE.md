# Architecture — Phases 1–2

## Scope and data flow

```text
Jetpack Compose screens
        │ user actions / StateFlow state
        ▼
AuthViewModel / FinanceViewModel ── validate input and gate UI states
        │                     │
        ▼                     ▼
SupabaseAuthRepository   SupabaseMemberProfileRepository / SupabaseFinanceRepository
        │                     │
        └── supabase-kt Auth / PostgREST client ── HTTPS ── Supabase
                                                              │
                                      Auth + Postgres RLS / RPC / audit log
```

The app is Android-only. `MainActivity` creates the application container, handles the configured Auth deep link, and hosts Compose. `AuthViewModel` is the presentation state machine; repositories isolate the Supabase SDK; DTOs map the `my_profile` view to domain models. Unknown role/status values and profile/session identity mismatches fail closed.

## Authentication and session flow

1. Supabase Auth issues an email/password session. Public sign-up is not offered in the app.
2. The SDK uses PKCE and the custom callback `smarttransparentmess://auth`.
3. `AndroidEncryptedAuthStorage` implements the SDK session and PKCE-verifier storage contracts. Values are AES-GCM encrypted in app-private preferences with an AES key held by Android Keystore. Android backup is disabled so a restored encrypted blob cannot be used without its key.
4. The app fetches `public.my_profile` and checks that `user_id` matches the Auth session and `account_status` is `ACTIVE` before showing a dashboard.
5. Sign-out attempts Supabase session revocation and clears local session storage if the network operation fails. A non-confirmed server sign-out is surfaced to the user.

The app never stores a user's password. Password-reset emails are generic to avoid disclosing whether an address exists. Set the Supabase redirect allowlist to the custom callback before testing recovery.

## Authorization model

- **MEMBER**: active profile; can read their own profile and edit only their own name and phone number.
- **ADMIN**: manager/admin role from the profile; can read profile rows within the mess under RLS. The current Phase 1 app does not expose operational or financial management features.
- **MAIN_ADMIN**: role derived from `main_admin_assignments`, never set through a profile update. It may call server-side profile provisioning, account-status, role, and transfer RPCs; the client does not yet expose management screens.

All protected decisions are enforced by Postgres grants, RLS, constraints, triggers, or reviewed `SECURITY DEFINER` functions with a fixed search path. The UI role is for routing/display only. The hidden seven-tap Admin entry is not a security boundary and cannot grant a role.

The one-mess Phase 1 schema uses a singleton assignment row. A deferred constraint trigger rejects a newly committed mess without one active assignment; a unique row design prevents multiple assignments; deletion is blocked; the assigned Main Admin cannot be suspended. The `transfer_main_admin` RPC locks and updates the assignment in one transaction, demotes the old Main Admin to Admin, requires an active target profile, and writes an audit entry. Only the active current Main Admin may invoke it.

`set_profile_role`, `set_profile_status`, and `provision_member_profile` also validate the active Main Admin on the server and write audit rows. The target Auth user must already exist and have a confirmed email before a profile can be provisioned. Auth account creation itself remains an Auth Dashboard/operator task; no service-role credential is put in the app.

For a lost/compromised Main Admin, the separate `recover_main_admin_owner_only` database function is not exposed to any PostgREST role, including `service_role`. Its SQL template requires an expected old user ID, an active confirmed same-mess target, a reason, and an external approval reference. It transfers the singleton assignment, suspends the old account, and records a `PROJECT_OWNER_RECOVERY` audit actor. It is an operator recovery path, not an app feature, and still requires independent review and integration testing.

## Phase 1–2 data surface

The initial migration defines `messes`, `profiles`, `main_admin_assignments`, and `audit_logs`, plus helper functions and `my_profile`. Authenticated clients receive narrow table grants: profile update is limited to `full_name` and `phone_number`; role, status, manager flag, and Main Admin assignment are not client-writable.

Phase 2 adds `billing_periods`, `charges`, `billing_adjustments`, `payments`, `receipts`, and `payment_reversals`. Authenticated clients receive SELECT-only ledger/view grants. Mutations go through active same-mess Admin/Main Admin RPCs, which write audit entries. `FinanceViewModel` loads `my_financial_summary` and `my_payment_receipts`; both are `security_invoker` views restricted by `auth.uid()` and underlying RLS. The member UI computes and cross-checks due/advance using integer minor units. The SQL formula and verification limits are in [`PHASE2_FINANCE.md`](PHASE2_FINANCE.md).

## Open integration work

- Exercise PostgREST with real anonymous, Member, Admin, suspended, and cross-mess JWTs; the owner-session rollback smoke test is not enough.
- Configure and test email delivery and installed-app Auth deep links on a device.
- Add Admin finance entry UI only after reviewing RPC behavior and authorization tests.
- Run the Phase 2 JVM tests/build in a new GitHub Actions workflow and install only the resulting artifact for device verification.
