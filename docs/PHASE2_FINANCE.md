# Phase 2 finance design and verification

## Scope currently implemented

Phase 2 now includes the member finance dashboard and an Admin/Main Admin finance workspace. It does not claim that the whole ERP or installed-device verification is complete.

### Backend ledger

- Billing periods with validated date ranges and an overlap guard.
- Charges, opening due, debit adjustments, and credit adjustments.
- Explicit payment methods: `CASH`, `BKASH`, `NAGAD`, `BANK`, `MOBILE_BANKING`, and `OTHER`.
- Detailed charge categories including seat rent, meals, electricity, Wi-Fi, gas, water, fines, utilities, and services.
- Admin/Main Admin mutations with per-mess unique client request IDs where applicable.
- One immutable receipt record per payment.
- Compensating payment reversal: the original payment remains visible, its receipt is voided, and a separate reversal plus audit entry is written.
- Closed-period mutation rejection and due/advance carry-forward into an open period.
- `security_invoker` views for the signed-in member's per-period summary and recent receipts.

### Native Android

- Exact minor-unit parsing/formatting and overflow-safe domain calculations; no floating-point ledger arithmetic.
- Member dashboard with loading, error, empty, due/advance, and reversed-receipt states.
- Role-gated Admin/Main Admin finance tab; members do not receive the Admin navigation item.
- Overview, payment/receipt, charge/adjustment, and billing-period/carry-forward sections.
- Per-member overview aggregation and member-wise balance list: one member's advance does not cancel another member's outstanding due; zero-balance and historical members remain visible.
- Payment search plus method/status filters and charge search plus category filtering operate on the loaded server snapshot.
- Bilingual English/Bengali labels and validation feedback.
- Duplicate-submit prevention and stable request-ID reuse after a failed mutation attempt.
- Post-mutation snapshot refresh; a refresh failure is shown separately and is never hidden by a success notice.
- PDF receipt download through the Android document picker and PDF sharing through a non-exported `FileProvider`.
- Reversed receipts remain identifiable as reversed. PDFs include the real immutable receipt record ID; no nonexistent cryptographic receipt hash is claimed.

Source presence and compilation are not represented as installed-device correctness. Real login, mutation, PDF rendering, and sharing still require device verification.

## Balance invariant

All amounts are non-negative `bigint` minor units (BDT poisha). The sign is carried by the entry kind or resulting balance:

```text
net_due = opening_due + charges + debit_adjustments
          - valid_payments - credit_adjustments

outstanding_due = max(net_due, 0)
advance_credit  = max(-net_due, 0)
```

A reversed payment is excluded from `valid_payments` but remains visible with its reversal reason and voided receipt. Zero due and zero advance mean settled. Carry-forward writes `OPENING_DUE` for a positive net or `CREDIT` for a negative net. A partial unique index prevents duplicate source/member/target carry-forward rows.

The Admin overview first calculates each member's period balance and then sums non-negative dues and advances. It therefore does not net one member's advance against another member's due.

## Mutation boundary

Authenticated roles receive only `SELECT` on ledger tables and the two member views. There are no direct client `INSERT`/`UPDATE`/`DELETE` policies. The following `SECURITY DEFINER` RPCs each require an active same-mess Admin or Main Admin:

| RPC | Purpose |
| --- | --- |
| `create_billing_period` | Open a non-overlapping period and audit it |
| `close_billing_period` | Close an open period and audit it |
| `create_charge` | Add a charge to an open period with a unique request ID |
| `add_billing_adjustment` | Add opening due/credit/debit with a reason and unique request ID |
| `record_payment` | Insert payment + receipt + audit atomically |
| `reverse_payment` | Preserve payment, add reversal, void receipt, audit reason |
| `carry_forward_balance` | Carry a closed period's net into an open target once |

UI role checks are not authorization. The RPCs repeat server-side role/mess checks, and member views use `auth.uid()` rather than a client-supplied member ID.

## Live migration and smoke result

On 2026-10-25, the following migrations were applied only to project `iwoiwgkaevobxqojyphb`:

| Recorded migration | Source |
| --- | --- |
| `20261025030611_phase2_billing_payments` | `0002_phase2_billing_payments.sql` |
| `20261025030642_phase2_view_grants` | `0003_phase2_view_grants.sql` |
| `20261025033247_phase2_admin_finance_methods` | `0004_phase2_admin_finance_methods.sql` |
| `20261025041631_phase2_active_read_hardening` | `0005_phase2_active_read_hardening.sql` |

Observed backend checks after the final rollback smoke test:

- Six ledger tables have RLS enabled and exactly one SELECT policy each for `authenticated`; after `0005`, every policy also requires `is_active_user()`.
- Both member views have `security_invoker=true` and authenticated SELECT-only grants.
- Anonymous EXECUTE is false for all seven finance RPCs; authenticated EXECUTE is true, with internal active-Admin/mess authorization still required.
- Live payment constraints accept `BKASH` and `NAGAD`; live charge constraints accept the expanded category set.
- The rollback-only transaction passed partial payment, duplicate request rejection, reversal/double-reversal behavior, receipt voiding, closed-period rejection, due/advance carry-forward, duplicate carry-forward rejection, missing-profile authorization rejection, `ELECTRICITY`, `BKASH`, `NAGAD`, and future-payment rejection.
- Post-rollback counts were zero for billing periods, charges, adjustments, payments, receipts, and reversals. Profiles `1`, Main Admin assignments `1`, and the original audit row `1` remained unchanged.

`0003` is intentionally separate: live inspection showed Supabase default privileges had granted `ALL` on the new views to `authenticated`. The follow-up migration revoked those defaults and granted only SELECT. Do not omit it in a fresh setup.

## Android verification result

For Phase 3-era code commit `6be5cd91969570d56cd3355003b7738027180fd1`, GitHub Actions run `40161969261` completed successfully (including the Phase 2 finance source/tests):

- static XML/resource/security contract checks;
- all JVM unit tests, including Admin overview separation and finance-entry validation;
- `assembleDebug`;
- generated public `BuildConfig` verification;
- debug APK artifact upload.

The run verifies compilation and JVM behavior, not Android device rendering, Supabase JWT interactions, or user acceptance.

## Verification limits and next tests

The smoke transaction injected `request.jwt.claims` in a project-owner SQL session and rolled back. It is useful RPC/calculation verification, but it is **not** a substitute for PostgREST tests using real anonymous, Member, Admin, suspended, and cross-mess JWTs.

Run the checked-in smoke script only in a disposable/test project:

```text
supabase/tests/phase2_smoke_transaction.sql
```

Next required integration cases:

1. Anonymous cannot select tables/views or execute finance RPCs.
2. Member sees only their own rows and cannot call any finance mutation RPC.
3. Admin can mutate only their own mess; cross-mess target IDs are rejected.
4. Suspended users receive no finance rows and cannot mutate.
5. Concurrent duplicate submissions and carry-forwards preserve uniqueness.
6. Installed Android app decodes real snapshots and shows loading/error/empty/stale states correctly.
7. Admin creates and closes periods, records charges/payments, reverses a payment, carries a balance forward, and downloads/shares both valid and reversed PDF receipts on a device.
