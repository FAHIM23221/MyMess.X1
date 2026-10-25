# Implementation status

Last updated: **2026-10-25** (Asia/Dhaka). This file distinguishes source presence from executed verification. A migration, test, workflow, APK, installation, or connected flow is not described as successful until its result has been observed.

Status vocabulary: **COMPLETE**, **IN_PROGRESS**, **READY_FOR_TESTING**, **BLOCKED**, **DEFERRED**.

## Environment and safety boundary

- Repository branch: `arena/34fa0299-mymess-x1`; PR `#2` remains open.
- Latest fully verified Android code commit: `42c196cac4e21ab1282094624b0baa922faf0c6f`.
- Active Supabase project: `mymess-x1-phase1` / `iwoiwgkaevobxqojyphb` (`ap-south-1`, PostgreSQL 17).
- Existing project `mymess` / `jiiexmjczpzxikhmlgbh` is outside the implementation boundary and has not been modified.
- Only the Android publishable key is permitted in app configuration. No service-role key, database password, signing key, or other privileged credential is stored here.
- Backend changes are additive. RLS remains enabled; no production-data deletion or security-control bypass is part of the plan.

## Phase register

| Phase | Status | Verified state | Next action |
| --- | --- | --- | --- |
| 0 — repository, native Android, CI | **COMPLETE** | Native Android CI repeatedly completes static checks, JVM tests, debug build, built-config verification, and artifact upload. | Keep the checks mandatory for subsequent slices. |
| 1 — Auth, profiles, roles, RLS, Main Admin | **COMPLETE** | Migration `20261025023033_phase1_foundation` is applied. One mess, one active profile, one singleton Main Admin assignment, and one bootstrap audit row were verified. | Exercise installed-app Auth/profile flows with the configured APK. |
| 2 — billing, charges, payments, receipts | **IN_PROGRESS** | Backend migrations `20261025030611`, `20261025030642`, and `20261025033247` are applied. Rollback smoke and live RLS/grant/constraint checks passed. Member and Admin Android finance code compiled and JVM tests passed in run `40159571594`. | Run real-JWT authorization tests and installed-device Member/Admin/PDF workflows before real financial data. |
| 3 — meals, rice, fines, expenses, cash book | **DEFERRED** | No operational source is claimed. | Implement the next additive ledger slice without weakening Phase 2 boundaries. |
| 4 — duties and notices | **DEFERRED** | No operational source is claimed. | Implement additive tables/RPCs and Android workflows. |
| 5 — suggestions, complaints, messaging | **DEFERRED** | No operational source is claimed. | Implement abuse controls, RLS, and audit-aware workflows. |
| 6 — reports, backup, audit, hardening | **IN_PROGRESS** | Phase 1 audit foundation, audited finance RPCs, and PDF receipt export exist. General reports, backup, and broader hardening do not. | Expand the integration/security matrix and verify receipt/device behavior before adding broader exports. |

## Phase 2 implementation checklist

| Item | Status | Evidence and remaining boundary |
| --- | --- | --- |
| Integer minor-unit money parser/formatter | **COMPLETE** | JVM tests passed in run `40159571594`, including precision, invalid input, exact aggregation, and `Long` boundaries. |
| Due/advance calculation edge cases | **COMPLETE** | JVM tests cover opening due, partial payment, overpayment, credits, reversal visibility, overflow rejection, and per-member Admin aggregation. |
| Billing periods and non-overlap RPC | **READY_FOR_TESTING** | Applied remotely; create/close and closed-period rejection passed rollback smoke checks. Admin period UI is compiled. Real-JWT/device tests remain. |
| Charges and opening/credit/debit adjustments | **READY_FOR_TESTING** | Audited RPCs and Admin entry/history UI are compiled; charge and carry-forward smoke checks passed. |
| Payments, request IDs, and receipts | **READY_FOR_TESTING** | Payment/receipt/audit and duplicate-request rejection passed smoke checks. Admin payment UI, stale-user protection, and retry request-ID retention are compiled. |
| Reversal/correction history | **READY_FOR_TESTING** | Reversal, double-reversal rejection, receipt voiding, and valid-payment exclusion passed smoke checks; reversal UI is compiled. |
| Carry-forward | **READY_FOR_TESTING** | Due/advance carry-forward and duplicate rejection passed rollback checks; Admin source/target UI is compiled. |
| Member finance dashboard | **READY_FOR_TESTING** | Compiled and JVM-tested. Real backend view decoding and empty/loading/error behavior still need device verification. |
| Admin finance workspace | **READY_FOR_TESTING** | Role-gated navigation, overview, payment, charge/adjustment, and period sections compiled in run `40159571594`. Members do not receive the Admin tab. |
| PDF download/share | **READY_FOR_TESTING** | Android `PdfDocument`, document-picker export, and non-exported `FileProvider` sharing compiled. Actual rendering, Bengali font appearance, and share targets need device verification. |

## Verified configured Android delivery

- Code commit: `42c196cac4e21ab1282094624b0baa922faf0c6f`.
- Successful push workflow: `40159571594`.
- App version: `0.3.0-phase2`, versionCode `4`.
- Artifact ID: `12586620126`.
- Artifact name: `smart-transparent-mess-debug-apk-42c196cac4e21ab1282094624b0baa922faf0c6f`.
- Observed ZIP size: `21,896,312` bytes; expiry: `2026-11-08T03:49:39Z`.
- Download: <https://github.com/FAHIM23221/MyMess.X1/actions/runs/40159571594/artifacts/12586620126>
- The workflow verified static checks, JVM tests, `assembleDebug`, generated public Supabase configuration, and artifact upload.
- No sandbox APK checksum or installation is claimed: signed-blob artifact download previously failed with `EOF`. No connected login, financial mutation, PDF rendering, or production release is claimed.

## Live backend verification

Applied only to `iwoiwgkaevobxqojyphb`:

1. `20261025023033_phase1_foundation`
2. `20261025030611_phase2_billing_payments`
3. `20261025030642_phase2_view_grants`
4. `20261025033247_phase2_admin_finance_methods`

Post-smoke read-only inspection found:

- Profiles `1`; Main Admin assignments `1`; audit rows `1`.
- Billing periods, charges, adjustments, payments, receipts, and reversals each `0`; the rollback test left no financial rows.
- Six RLS-enabled ledger tables, each with one authenticated SELECT policy.
- SELECT-only grants on those tables and both finance views; no anonymous grants in the inspected set.
- Seven finance RPCs with anonymous EXECUTE `false` and authenticated EXECUTE `true`; internal active same-mess Admin checks remain mandatory.
- Live `BKASH`/`NAGAD` payment methods and expanded charge categories match migration `0004`.

Auth configuration remains signup-disabled with the Android auth deep link allowed, refresh-token rotation enabled, and email-send rate limit `2`. SMTP remains unconfigured.

## Blocker and verification register

### READY_FOR_TESTING — installed Android verification

Install artifact `12586620126` on an Android device, sign in with the confirmed Main Admin account, and verify:

1. profile and member finance empty states;
2. Admin-only finance navigation;
3. period creation/closure and charge/adjustment entry;
4. payment/receipt entry, retry behavior, and reversal;
5. due/advance carry-forward;
6. valid and reversed PDF download/share, including Bengali text appearance.

Installation and all connected device results remain unverified.

### READY_FOR_TESTING — real PostgREST/JWT authorization matrix

The rollback SQL smoke test used injected claims, not real PostgREST tokens. Before real financial data, test anonymous, Member, Admin, suspended, and cross-mess JWTs for table/view reads and every mutation RPC. Include concurrent duplicate submissions and carry-forwards.

### BLOCKED — Auth password minimum

The current Auth connector schema does not expose a writable `password_min_length` field. Read-back confirms the minimum remains `6`. Do not claim a stronger minimum until a supported, approved configuration path is available and read-back verifies it.

### BLOCKED — outbound recovery email

SMTP is unconfigured, so production-like password-recovery email delivery is not verified. Configure an approved SMTP provider before claiming recovery delivery.

### OPTIONAL REPAIR — GitHub Actions repository Variables

Repository Variables list/create APIs previously returned HTTP 403 `Resource not accessible by integration`; no values were written and the denied write was not retried. Bundled non-privileged configuration in `config/supabase.public.properties` now removes this dependency for debug builds. Variables read/write permission is needed only for future overrides; workflow dispatch may separately require Actions write.

### ENVIRONMENT NOTE — local Gradle and artifact downloads

The sandbox initially lacked a JDK, and external Gradle/JDK signed downloads were network-blocked. Android compilation was therefore verified in GitHub Actions rather than claimed locally. This does not block the workflow-built artifact. Signed-blob artifact download also remains unverified locally.

## Immediate queue

1. **READY_FOR_TESTING:** install the version-4 artifact and execute the connected Main Admin finance checklist above.
2. **READY_FOR_TESTING:** execute the real-JWT/PostgREST authorization matrix before entering real financial data.
3. **BLOCKED:** raise the Auth password minimum only through a supported approved configuration path; current value is `6`.
4. **DEFERRED:** begin Phase 3 as a separate additive, tested slice; do not represent it as implemented now.
