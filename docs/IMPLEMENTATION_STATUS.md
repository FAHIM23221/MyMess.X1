# Implementation status

Last updated: **2026-10-25** (Asia/Dhaka). This register distinguishes source presence, executed backend checks, CI compilation/JVM tests, and still-open real-JWT/device verification.

Status vocabulary: **COMPLETE**, **IN_PROGRESS**, **READY_FOR_TESTING**, **BLOCKED**, **DEFERRED**.

## Environment and safety boundary

- Repository branch: `arena/34fa0299-mymess-x1`; PR `#2` remains open.
- Phase 3 implementation commit with observed green Android verification: `6be5cd91969570d56cd3355003b7738027180fd1`, run `40161969261`.
- Current source version after the Phase 3 version update: `0.4.0-phase3`, versionCode `5`. Install only an artifact from a green run of the current PR head; do not treat an older artifact as the current source.
- Active Supabase project: `mymess-x1-phase1` / `iwoiwgkaevobxqojyphb` (`ap-south-1`, PostgreSQL 17).
- Existing project `mymess` / `jiiexmjczpzxikhmlgbh` is outside the implementation boundary and has not been modified.
- Only the Android publishable key is present in app configuration. No service-role key, database password, signing key, or other privileged credential is stored here.
- Backend work is non-destructive and additive where possible. Migration `0005` tightens existing SELECT policies in place; RLS was not disabled and no data was deleted.

## Phase register

| Phase | Status | Verified state | Next action |
| --- | --- | --- | --- |
| 0 — repository, native Android, CI | **COMPLETE** | CI completes static/security checks, JVM tests, debug build, built-config verification, and artifact upload. | Keep checks mandatory for subsequent slices. |
| 1 — Auth, profiles, roles, RLS, Main Admin | **COMPLETE** | Foundation migration and one-time singleton Main Admin bootstrap are applied and read back. | Exercise installed-app Auth/profile and role-management requirements. |
| 2 — billing, charges, payments, receipts | **IN_PROGRESS** | Four finance migrations plus active-read hardening are applied. Rollback smoke and live RLS/grant checks passed. Member/Admin Android finance, filters, member-wise balances, and PDF source compile and pass JVM tests. | Run real-JWT authorization and installed-device Member/Admin/PDF workflows before real financial data. |
| 3 — meals, rice, fines, expenses, cash book | **IN_PROGRESS** | Meal/rice/expense/cash-book migration, Android workflow, JVM tests, and rollback smoke are implemented and verified at source/backend-smoke level. | Test real JWT/device behavior; implement fines, linked rice correction, and explicit meal-billing rules. |
| 4 — duties and notices | **DEFERRED** | No operational source is claimed. | Implement additive tables/RPCs and Android workflows. |
| 5 — suggestions, complaints, messaging | **DEFERRED** | No operational source is claimed. | Implement abuse controls, RLS, and audit-aware workflows. |
| 6 — reports, backup, audit, hardening | **IN_PROGRESS** | Audit foundation, audited finance/operations RPCs, receipt PDF export, and active-read hardening exist. General reports, backup/restore, audit UI, and broader hardening do not. | Expand integration/security matrix and add broader exports/settings without weakening RLS. |

## Phase 2 implementation checklist

| Item | Status | Evidence and remaining boundary |
| --- | --- | --- |
| Integer minor-unit money and due/advance arithmetic | **COMPLETE** | JVM tests cover precision, opening due, partial payment, overpayment, credits, reversal visibility, member separation, and overflow rejection. |
| Billing periods and non-overlap RPC | **READY_FOR_TESTING** | Applied; create/close and closed-period rejection passed rollback smoke. Real-JWT/device tests remain. |
| Charges and opening/credit/debit adjustments | **READY_FOR_TESTING** | Audited RPCs, entry/history UI, search, and category filtering are compiled; charge/carry-forward smoke passed. |
| Payments, request IDs, and receipts | **READY_FOR_TESTING** | Payment/receipt/audit and duplicate rejection passed smoke. UI includes member/receipt/period/reference search, method/status filters, retry request-ID retention, and reversal. |
| Member-wise due monitoring | **READY_FOR_TESTING** | Calculator retains zero-balance and historical members, isolates each member's advance/due, and has JVM coverage. Device rendering remains open. |
| Carry-forward | **READY_FOR_TESTING** | Due/advance carry-forward and duplicate rejection passed rollback checks; Admin source/target UI is compiled. |
| Member finance dashboard | **READY_FOR_TESTING** | Compiled and JVM-tested. Real backend decoding and empty/loading/error behavior need device verification. |
| PDF download/share | **READY_FOR_TESTING** | Android `PdfDocument`, document picker, and non-exported `FileProvider` compiled. Rendering, Bengali fonts, and share targets need device verification. |

## Phase 3 implementation checklist

| Item | Status | Evidence and remaining boundary |
| --- | --- | --- |
| Meal entry and reversal | **READY_FOR_TESTING** | Exact half-meal validation, active-member selection, duplicate request IDs, reasoned one-time reversal, audited RPC, history UI, and rollback smoke exist. No automatic meal charge is generated. |
| Rice stock movements | **READY_FOR_TESTING** | Whole-gram validation, exact stock summary, per-mess locked insufficient-stock guard, duplicate request IDs, and rollback smoke exist. Linked rice reversal is not implemented. |
| Expense entry and reversal | **READY_FOR_TESTING** | Exact minor-unit/category/date/reference validation, duplicate request IDs, reasoned one-time reversal, cash-book integration, and rollback smoke exist. |
| Cash book | **READY_FOR_TESTING** | Four source triggers, unique source IDs, payment/reversal backfill, read-only Android history/totals, direct-write denial smoke, and zero post-rollback rows verified. |
| Operations navigation and bilingual UI | **READY_FOR_TESTING** | Admin/Member role-appropriate navigation and Meals/Rice/Expenses/Cash book tabs compiled in run `40161969261`; EN/BN resource parity and JVM tests passed. Device accessibility/responsiveness remains open. |
| Fines and automatic meal billing | **DEFERRED** | No complete workflow is claimed. Charge category `FINE` alone is not a fines module. |

## Executed Android verification

For implementation commit `6be5cd91969570d56cd3355003b7738027180fd1`, push workflow `40161969261` completed:

- static XML/resource/security contract checks;
- all JVM unit tests, including new operations and member-balance tests;
- `assembleDebug`;
- generated public `BuildConfig` verification;
- debug APK artifact upload.

The first Phase 3 run `40161841254` found timestamp-serializer/payment-filter compile errors; commit `6be5cd9` corrected them and the subsequent run passed. No failed run is represented as a valid artifact.

No sandbox APK checksum or installation is claimed. Signed-blob artifact download previously failed with `EOF`. No connected login, financial/operational mutation from the app, PDF rendering, or production release is claimed.

## Live backend verification

Applied only to `iwoiwgkaevobxqojyphb`:

1. `20261025023033_phase1_foundation`
2. `20261025030611_phase2_billing_payments`
3. `20261025030642_phase2_view_grants`
4. `20261025033247_phase2_admin_finance_methods`
5. `20261025041631_phase2_active_read_hardening`
6. `20261025041711_phase3_meals_rice_expenses_cashbook`

Post-smoke read-only inspection found:

- Profiles `1`; Main Admin assignments `1`; audit rows `1`.
- Billing periods, charges, adjustments, payments, receipts, reversals, meals, meal reversals, rice movements, expenses, expense reversals, and cash-book entries each `0`; rollback tests left no operational/financial rows.
- All six hardened Phase 2 SELECT policies require `is_active_user()` and same-mess/member predicates.
- Six new Phase 3 tables have active same-mess SELECT policies and authenticated SELECT-only grants; anonymous table grants were not added.
- Five Phase 3 RPCs have anonymous EXECUTE `false` and authenticated EXECUTE `true`, with internal active same-mess Admin checks still mandatory.
- All four payment/payment-reversal/expense/expense-reversal cash-book triggers are present.

Auth remains signup-disabled with the Android deep link allowed, refresh-token rotation enabled, and email-send rate limit `2`. SMTP remains unconfigured. Auth password minimum remains `6`.

## Blocker and verification register

### READY_FOR_TESTING — installed Android verification

Install the APK from the latest green current-head PR run, sign in with the confirmed Main Admin account, and verify:

1. profile and Member finance empty states;
2. Admin-only Finance navigation and Member-safe Operations navigation;
3. period/charge/adjustment/payment/reversal/carry-forward workflows and filters;
4. member-wise due/advance and advance-credit preservation;
5. valid and reversed PDF download/share, including Bengali appearance;
6. meal/rice/expense entry, reversal, duplicate-submit behavior, and cash-book updates;
7. Member own-meal/read-only behavior, light/dark themes, Bengali layouts, rotation, and process restoration.

Installation and all connected device results remain unverified.

### READY_FOR_TESTING — real PostgREST/JWT authorization matrix

Rollback SQL smoke used injected claims, not real PostgREST tokens. Before real data, test anonymous, Member, Admin, suspended, and cross-mess JWTs for every table/view and mutation RPC. Include concurrent duplicate submissions, carry-forwards, rice stock-outs, and confirmation that suspended Members receive no Phase 2/3 rows.

### BLOCKED — Auth password minimum

The Auth connector schema does not expose a writable `password_min_length` field. Read-back confirms minimum `6`. Do not claim a stronger minimum until a supported approved path and read-back verify it.

### BLOCKED — outbound recovery email

SMTP is unconfigured, so production-like password-recovery delivery is not verified.

### OPTIONAL REPAIR — GitHub Actions repository Variables

Variables list/create APIs previously returned HTTP 403; no values were written and denied writes were not retried. Bundled non-privileged configuration removes this dependency for debug builds.

### ENVIRONMENT NOTE — local Gradle and artifact downloads

The sandbox lacks a reliable full local Android/Gradle runtime and signed-blob downloads have failed. Compilation is therefore claimed only from observed GitHub Actions runs, not from a fictitious local build.

## Immediate queue

1. **READY_FOR_TESTING:** execute the real-JWT/PostgREST matrix and current-head installed-device checklist before real member/financial data.
2. **IN_PROGRESS:** add Phase 3 fines, linked rice correction, and explicit meal-billing rules as separate reviewed slices.
3. **DEFERRED:** implement Phases 4–5 and the remaining Phase 6 reports/backup/audit/settings workflows; none is represented as complete.
4. **BLOCKED:** raise Auth password minimum only through a supported approved configuration path; current value is `6`.
