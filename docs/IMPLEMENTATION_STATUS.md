# Implementation status

Last updated: **2026-10-25** (Asia/Dhaka). This file distinguishes source presence from executed verification. A migration, test, workflow, or APK is not described as successful until its result has been observed.

Status vocabulary: **COMPLETE**, **IN_PROGRESS**, **READY_FOR_TESTING**, **BLOCKED**, **DEFERRED**.

## Environment and safety boundary

- Repository branch: `arena/34fa0299-mymess-x1`.
- Active Supabase project: `mymess-x1-phase1` / `iwoiwgkaevobxqojyphb` (`ap-south-1`, PostgreSQL 17).
- Existing project `mymess` / `jiiexmjczpzxikhmlgbh` is outside the implementation boundary and has not been modified.
- Only the Android publishable key is permitted in app configuration. No service-role key, database password, signing key, or other privileged credential is stored here.
- Backend changes are additive. RLS remains enabled; no production data deletion or security-control bypass is part of the plan.

## Phase register

| Phase | Status | Verified state | Next action |
| --- | --- | --- | --- |
| 0 — repository, native Android, CI | **COMPLETE** | Main commit `5aaf473f733c412aaf22113b71531a7f7ad84a01`; Android workflow run `40153010838` completed tests/build and uploaded an unconfigured debug APK. | Keep static checks and unit tests in every subsequent workflow. |
| 1 — Auth, profiles, roles, RLS, Main Admin | **COMPLETE** | New project is `ACTIVE_HEALTHY`; migration `20261025023033_phase1_foundation` is applied. Verified one mess, one active profile, one singleton Main Admin assignment, and one bootstrap audit row. | Exercise installed-app Auth/profile flows after configured APK delivery. |
| 2 — billing, charges, payments, receipts | **IN_PROGRESS** | Migrations `20261025030611_phase2_billing_payments` and `20261025030642_phase2_view_grants` are applied to the new project only. Schema/RLS/grants were inspected and the rollback-only finance smoke transaction passed. Android minor-unit logic, member repository/UI, and JVM tests passed workflow run `40157238078`; no installed-device verification has occurred. | Test real Member/Admin/anon/cross-mess JWTs and the installed app; implement Admin entry UI. |
| 3 — meals, rice, fines, expenses, cash book | **DEFERRED** | No operational source is claimed. | Implement after the Phase 2 ledger checks are green. |
| 4 — duties and notices | **DEFERRED** | No operational source is claimed. | Implement additive tables/RPCs and Android workflows. |
| 5 — suggestions, complaints, messaging | **DEFERRED** | No operational source is claimed. | Implement abuse controls, RLS, and audit-aware workflows. |
| 6 — reports, backup, audit, hardening | **IN_PROGRESS** | Phase 1 audit foundation and Phase 2 audited finance RPC source exist; report/backup/CSV modules do not. | Extend integration/security matrix and add export/backup only after operational data paths stabilize. |

## Phase 2 implementation checklist

| Item | Status | Notes |
| --- | --- | --- |
| Integer minor-unit money parser/formatter | **COMPLETE** | JVM tests passed in run `40157238078`, including precision, invalid input, and `Long` boundaries. |
| Due/advance calculation edge cases | **COMPLETE** | JVM tests passed for opening due, partial payment, overpayment, credits, reversal visibility, backend consistency, and overflow rejection. |
| Billing periods and non-overlap RPC | **READY_FOR_TESTING** | Applied remotely; period create/close and closed-period rejection passed rollback smoke checks. Real-JWT API tests pending. |
| Charges and opening/credit/debit adjustments | **READY_FOR_TESTING** | Audited RPCs applied; charge calculation and carry-forward smoke checks passed. No Admin entry UI yet. |
| Payments, unique client request IDs, receipts | **READY_FOR_TESTING** | Payment/receipt/audit path and duplicate-request rejection passed rollback smoke checks; no Admin entry UI yet. |
| Reversal/correction history | **READY_FOR_TESTING** | Reversal, double-reversal rejection, receipt voiding, and valid-payment exclusion passed rollback smoke checks. |
| Carry-forward | **READY_FOR_TESTING** | Due and advance carry-forward plus duplicate rejection passed; no financial rows persisted by the test. |
| Member finance dashboard | **READY_FOR_TESTING** | Compiled successfully in run `40157238078`; real backend views and empty states only. Installed configured-app verification remains blocked. |
| Admin finance entry UI | **DEFERRED** | Backend RPCs are being established first; the UI must not be marked complete before it exists. |

## Delivery blockers

### BLOCKED — GitHub Actions repository Variables

- Observed result: GitHub repository Variables list/create APIs returned **HTTP 403 `Resource not accessible by integration`**.
- Required permission: repository **Variables: read/write** for the GitHub App installation token. Workflow dispatch may separately require **Actions: write**.
- Current effect: `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY` were **not written** to Actions Variables. The denied API has not been retried.
- Independent resolution: the same non-privileged Android URL/publishable key is now intentionally bundled in `config/supabase.public.properties`; blank Variables fall back to it. Variables permission repair is optional for future overrides and no longer blocks a configured debug APK.

### IN_PROGRESS — configured installable APK

- The screenshot-supplied older artifact was correctly identified as unconfigured: it lacked public Supabase configuration.
- Bundled public configuration, blank-Variable fallback, and a generated-`BuildConfig` CI verification step are now in source. A new workflow and artifact inspection are pending.
- Delivery requires a new successful workflow followed by installation of that new artifact. No installation or device verification is claimed yet.

## Immediate verification queue

1. **COMPLETE:** `python3 scripts/verify_project.py` and `git diff --check` passed locally before migration review; rerun after final edits.
2. **COMPLETE:** Applied `0002` and `0003` only to `iwoiwgkaevobxqojyphb`; inspected table/view/function grants and RLS. A default ALL view grant was found and tightened to SELECT-only by `0003`.
3. **COMPLETE:** Rollback-only smoke script passed; post-rollback smoke periods and ledger rows are both zero. See `docs/PHASE2_FINANCE.md` and `supabase/tests/phase2_smoke_transaction.sql`.
4. **COMPLETE:** Run `40157238078` passed static checks, JVM tests, `assembleDebug`, and artifact upload for commit `35119bb131a5df5cb41d3079850c80a8c30112a0`. Artifact ID `12585146867` is 21,705,454 bytes and expires `2026-11-08T03:11:52Z`; it is unconfigured because Variables remain blocked.
5. **READY_FOR_TESTING:** Exercise real anonymous/Member/Admin/suspended/cross-mess JWTs before real financial data.
6. **IN_PROGRESS:** Build and inspect a publicly configured debug APK without relying on the denied Variables API; then provide the new artifact for installation.
7. **DEFERRED:** Continue Admin finance entry UI and then Phase 3.
