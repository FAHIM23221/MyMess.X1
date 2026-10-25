# Phase 3 operations design and verification

## Implemented vertical slice

This slice covers meals, rice stock, expenses, and the cash book. It does not claim that fines, automatic meal billing, every Phase 3 report, real-JWT authorization, or installed-device behavior is complete.

### Exact units and corrections

- Meal quantities are integer **half-meal units**: database `3` means `1.5` meals. Valid input is `0.5` through `10` in half-meal increments.
- Rice quantities are positive whole **grams**. Stock is the exact sum of stock-in minus stock-out. The server rejects stock-out above the current balance under a per-mess transaction lock.
- Expense and cash-book amounts are positive integer **BDT minor units**. Direction is explicit (`INFLOW`/`OUTFLOW`); no floating-point money is used.
- Meal/expense reversal preserves the original row and adds one reasoned reversal. Reversed rows remain visible and are excluded from active meal/expense totals.
- Rice has no linked reversal RPC in this slice. An offsetting movement with a clear note is the available correction mechanism; no delete or silent overwrite is offered.

Meal entries intentionally do not generate `MEAL_CHARGE` rows. Linking meal activity to billing requires separately reviewed pricing and period rules.

## Backend contract

Migration `0006_phase3_meals_rice_expenses_cashbook.sql` adds:

- `meal_entries`, `meal_reversals`, `rice_movements`, `expenses`, `expense_reversals`, and `cash_book_entries`;
- RLS with active same-mess SELECT policies and member-only meal visibility;
- authenticated SELECT grants only, with anonymous/default privileges revoked;
- audited `record_meal_entry`, `reverse_meal_entry`, `record_rice_movement`, `record_expense`, and `reverse_expense` RPCs;
- unique per-mess client request IDs for meal/rice/expense creation and unique source reversal rows;
- cash-book triggers for payment, payment reversal, expense, and expense reversal events;
- non-duplicating backfill for existing payment/payment-reversal cash events.

Cash-book rows have no direct client mutation RPC. A payment creates an inflow; its reversal creates an outflow. An expense creates an outflow; its reversal creates an inflow. `(source_type, source_id)` uniqueness prevents duplicate cash events.

Migration `0005_phase2_active_read_hardening.sql` separately tightens all six Phase 2 SELECT policies so a suspended profile does not retain ledger/period reads through a Member policy branch.

## Android workflow

The existing bottom navigation now includes **Operations** for every active role. Admin/Main Admin keeps Home, Finance, Operations, and Profile; Member keeps Home, Operations, and Profile. The four Finance tabs are unchanged.

Operations contains Meals, Rice, Expenses, and Cash book tabs with:

- server-backed loading, refresh, empty, error, validation, and success states;
- role-gated mutation forms and read-only Member behavior;
- active-member, date, slot, quantity, category, note, reference, and reversal-reason validation;
- stable request IDs during a failed mutation retry and disabled duplicate submission while work is running;
- server snapshot refresh after mutation, with refresh failure surfaced separately;
- active meal/rice/expense and cash-balance summaries;
- original rows, reversed status, and reversal reasons retained in history.

The current repository reads the full readable snapshot. Add pagination/incremental loading before presenting it as suitable for very large histories.

## Executed verification

Applied only to `iwoiwgkaevobxqojyphb`:

| Recorded migration | Source |
| --- | --- |
| `20261025041631_phase2_active_read_hardening` | `0005_phase2_active_read_hardening.sql` |
| `20261025041711_phase3_meals_rice_expenses_cashbook` | `0006_phase3_meals_rice_expenses_cashbook.sql` |

`supabase/tests/phase3_smoke_transaction.sql` was executed successfully and rolled back. It covered:

- meal creation, duplicate request rejection, reversal, and double-reversal rejection;
- rice stock-in/out, exact balance, duplicate request rejection, and insufficient-stock rejection;
- payment plus reversal cash-book trigger behavior;
- expense creation, duplicate request rejection, future-date rejection, reversal, and double-reversal rejection;
- four cash events with equal inflow/outflow (`1500` minor units each in the isolated fixture);
- direct authenticated cash-book INSERT denial and missing-profile RPC denial.

Post-rollback read-back found zero rows in every new operational table and zero billing-period/payment rows; the original single audit row remained. The six new tables expose only SELECT to `authenticated`; anonymous EXECUTE is false for all five new RPCs; all four cash-book triggers are present.

Android implementation commit `6be5cd91969570d56cd3355003b7738027180fd1` passed run `40161969261`: static/security checks, JVM tests, `assembleDebug`, generated-public-config verification, and APK upload. This proves compilation/JVM behavior, not connected device correctness.

## Required next verification

1. Use real anonymous, Member, Admin, suspended, and cross-mess JWTs against every new table and RPC.
2. Test concurrent stock-outs and duplicate request IDs through PostgREST, not only injected claims.
3. On Android, test Admin meal/rice/expense entry and reversal, Member read restrictions, cash-book updates after existing Finance payment/reversal, rotation/process restoration, and Bengali/dark-mode layouts.
4. Add linked rice correction, fines, explicit meal-billing rules, and operational exports as separate reviewed slices.
