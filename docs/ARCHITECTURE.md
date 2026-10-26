# Architecture — Phases 1–3 vertical slices

## Scope and data flow

```text
Jetpack Compose / Material 3 screens
        │ user actions / StateFlow state
        ▼
AuthViewModel / FinanceViewModel / AdminFinanceViewModel / OperationsViewModel
        │ typed validation, role-gated navigation, loading/error/success state
        ▼
Auth / Profile / Finance / AdminFinance / Operations repositories
        │ DTO mapping; no privileged client credential
        └── supabase-kt Auth + PostgREST ── HTTPS ── Supabase
                                                         │
                                  Auth + Postgres grants / RLS / RPC / triggers / audit
```

The app is Android-only. `MainActivity` creates the application container, handles the configured Auth deep link, and hosts Compose. Repositories isolate Supabase SDK calls; domain models use exact integer units. Unknown role/status values and profile/session identity mismatches fail closed. UI role checks are navigation aids, never authorization.

## Authentication and session flow

1. Supabase Auth issues an email/password session. Public sign-up is not offered in the app.
2. The SDK uses PKCE and the custom callback `smarttransparentmess://auth`.
3. `AndroidEncryptedAuthStorage` implements session and PKCE-verifier storage with AES-GCM and an Android Keystore-held key. Android backup is disabled.
4. The app fetches `public.my_profile`, requires the Auth user ID to match, and requires `account_status = ACTIVE` before showing a dashboard.
5. Sign-out attempts server revocation and clears local session state if the network operation fails. An unconfirmed remote sign-out is surfaced rather than hidden.

The app never stores a user's password. Password-reset responses are generic. SMTP delivery and installed-app deep-link behavior remain integration-test items.

## Authorization model

- **MEMBER**: reads/updates the permitted fields of their own profile; reads their own finance summary/receipts and meal history; reads same-mess rice, expense, expense-reversal, and cash-book transparency rows. Member mutation RPC calls are denied.
- **ADMIN**: the manager/admin profile role. The Android app exposes the Finance and Operations workspaces. Every mutation still requires the server-side active same-mess Admin check.
- **MAIN_ADMIN**: derived from the singleton `main_admin_assignments` row, not a client-writable profile role. It has the same Android finance/operations workspace plus server-side profile/role/status/transfer capabilities. Dedicated role-management screens are not yet implemented.

Postgres grants, RLS, constraints, triggers, and reviewed `SECURITY DEFINER` functions with fixed search paths enforce the boundary. The hidden seven-tap Admin entry cannot grant a role.

The one-mess foundation prevents multiple Main Admin assignments, assignment deletion, and suspension of the assigned Main Admin. Audited transfer and owner-only recovery remain separate operator workflows; recovery is revoked from app/API roles.

## Phase 1–2 data surface

The foundation defines `messes`, `profiles`, `main_admin_assignments`, and `audit_logs`, plus helpers and `my_profile`. Profile update is limited to `full_name` and `phone_number`.

Phase 2 adds `billing_periods`, `charges`, `billing_adjustments`, `payments`, `receipts`, and `payment_reversals`. Authenticated clients receive SELECT-only ledger/view grants. Mutations use active same-mess Admin/Main Admin RPCs and write audit entries. The `0005` hardening migration makes every Phase 2 member/admin SELECT policy require an active profile as well as the same-mess/member predicate.

`FinanceViewModel` loads the member's `security_invoker` summary/receipt views. `AdminFinanceViewModel` loads the Admin snapshot. `AdminFinanceCalculator` computes each member's period balance before aggregating non-negative dues and advances, so one member's advance cannot erase another's due. The UI retains all four original tabs and adds member-wise balances plus payment/charge filters.

All money is BDT integer minor units. Reversed payments remain in history but are excluded from valid-payment totals. Receipt voiding and carry-forward are compensating/audited operations, not destructive edits.

## Phase 3 operations data surface

`OperationsViewModel` and `SupabaseOperationsRepository` load one role-filtered snapshot from these additive tables:

| Table | Purpose | Client access |
| --- | --- | --- |
| `meal_entries` / `meal_reversals` | Half-meal-unit member entries and one reasoned reversal per entry | Active Member: own rows; Admin: same mess |
| `rice_movements` | Append-only whole-gram stock-in/stock-out | Active same-mess read; Admin RPC write |
| `expenses` / `expense_reversals` | BDT-minor-unit expenses and one reasoned reversal | Active same-mess read; Admin RPC write |
| `cash_book_entries` | Trigger-generated inflow/outflow events | Active same-mess SELECT only |

The five mutation RPCs are `record_meal_entry`, `reverse_meal_entry`, `record_rice_movement`, `record_expense`, and `reverse_expense`. Meal, rice, and expense creation accepts a per-mess unique client request ID. Rice stock-out is checked under a per-mess advisory transaction lock. Reversal uniqueness and all check constraints are enforced again by Postgres.

Cash-book triggers run after payment, payment reversal, expense, and expense-reversal inserts. `(source_type, source_id)` is unique. Migration `0006` backfills existing payment/payment-reversal events with conflict protection; it does not invent opening balances or meal charges.

Meal and expense reversals retain the original row and add a separate record. Rice corrections currently require an offsetting stock movement with a clear note; a linked rice-reversal workflow is not yet implemented. Meal entries do **not** automatically create billing charges, and fines are a separate incomplete Phase 3 item.

## Open integration and scale work

- Exercise PostgREST with real anonymous, Member, Admin, suspended, and cross-mess JWTs; injected-claims rollback smoke is not enough.
- Verify installed-app Auth, Finance, Operations, PDF, Bengali rendering, and process-restoration flows on a device.
- Add paging/incremental queries before large operational histories; the current vertical slice loads the readable snapshot without claiming large-data readiness.
- Add fines, explicit meal-billing rules, linked rice correction, duties/notices, messaging, general reports/backup, and broader audit/settings screens without weakening existing RLS.
