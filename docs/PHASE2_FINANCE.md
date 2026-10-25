# Phase 2 finance design and verification

## Scope currently implemented

Phase 2 adds an append-oriented billing/payment foundation and a read-only member finance dashboard. It does not claim the whole ERP is complete.

- Billing periods with validated date ranges and an overlap guard.
- Charges, opening due, debit adjustments, and credit adjustments.
- Admin/Main Admin payment entry RPC with a per-mess unique `client_request_id` for duplicate-submission rejection.
- One unique receipt per payment.
- Compensating payment reversal: the original payment stays in history, its receipt is voided, and a separate reversal plus audit entry is written.
- Closed-period mutation rejection and due/advance carry-forward into an open period.
- `security_invoker` views for the signed-in member's per-period summary and recent receipts.
- Exact Android minor-unit parsing/formatting and domain calculations. No `Double`/`Float` ledger arithmetic.

The Admin Android entry screens are still deferred. Server RPC existence is not represented as a finished Admin UI.

## Balance invariant

All amounts are non-negative `bigint` minor units (BDT poisha). The sign is carried by the entry kind or the resulting balance:

```text
net_due = opening_due + charges + debit_adjustments
          - valid_payments - credit_adjustments

outstanding_due = max(net_due, 0)
advance_credit  = max(-net_due, 0)
```

A reversed payment is excluded from `valid_payments`, but remains visible with its reversal reason and voided receipt. Zero due and zero advance mean settled. Carry-forward writes `OPENING_DUE` for a positive net or `CREDIT` for a negative net. A partial unique index prevents the same source/member/target carry-forward from being inserted twice.

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

UI role checks are not authorization. The RPCs repeat the server-side role/mess checks, and views use `auth.uid()` rather than a client-supplied member ID.

## Live migration and smoke result

On 2026-10-25, migrations `0002_phase2_billing_payments.sql` and `0003_phase2_view_grants.sql` were applied only to project `iwoiwgkaevobxqojyphb`.

Observed checks:

- Six ledger tables exist with RLS enabled and SELECT-only policies.
- Both member views have `security_invoker=true`.
- Authenticated has SELECT-only table/view grants; anonymous has none in the inspected grant set.
- All seven finance RPCs are security-definer functions with authenticated EXECUTE privilege and internal authorization checks.
- The rollback-only smoke transaction passed partial payment, duplicate request rejection, reversal/double-reversal behavior, receipt voiding, closed-period rejection, due carry-forward, duplicate carry-forward rejection, overpayment/advance, advance carry-forward, and missing-profile authorization rejection.
- Post-rollback verification found zero smoke periods and zero ledger rows; the original foundation audit count remained one.

`0003` is intentionally separate: live inspection showed Supabase default privileges had granted `ALL` on the new views to `authenticated`. The follow-up migration revoked those defaults and granted only SELECT. Do not omit it in a fresh setup.

## Verification limits and next tests

The smoke transaction injected `request.jwt.claims` in a project-owner SQL session and rolled back. It is useful RPC/calculation verification, but it is **not** a substitute for PostgREST tests using real anonymous, Member, Admin, suspended, and cross-mess JWTs. Those tests remain required before real financial data.

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
6. Installed Android app decodes the exact views and shows empty/loading/stale-error states correctly.
