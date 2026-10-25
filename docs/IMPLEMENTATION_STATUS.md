# Implementation status

Last updated: **2026-10-25** (Asia/Dhaka). Branch `arena/34fa0299-mymess-x1`; PR `#2` remains open. Current source head is `4f376d7c15d0886098a9866b5d1d6c28e281e3a2`.

Status vocabulary: **COMPLETE** means the named slice has the evidence listed, not production readiness; **READY_FOR_TESTING** means source/backend checks exist but real JWT/device or external delivery is still required; **IN_PROGRESS** means adjacent requested behavior remains; **BLOCKED** identifies an external/configuration dependency; **DEFERRED** means no implementation is claimed.

## Environment and safety boundary

- Active Supabase project: `mymess-x1-phase1` / `iwoiwgkaevobxqojyphb` (`ap-south-1`, PostgreSQL 17). Existing `mymess` / `jiiexmjczpzxikhmlgbh` was not modified.
- Only the Android publishable key is used by the app. No service-role key, database password, signing key, or privileged credential is stored in the repository, APK configuration, logs, Variables, or audit records.
- Backend changes are non-destructive and additive where possible. RLS remains enabled; no production data was erased and no security control was disabled to make a test pass.
- App version remains `0.4.0-phase3`, versionCode `5`. Use only an artifact from a green run of the current head; the version string is not evidence that a later phase is device-verified.

## Phase register

| Phase | Status | Verified state | Next action |
| --- | --- | --- | --- |
| 0 — repository, native Android, CI | **COMPLETE** | GitHub Actions performs static/security checks, JVM tests, debug build, public-config verification and artifact upload. | Keep exact-head checks required for every slice. |
| 1 — Auth, profiles, roles, RLS, Main Admin | **COMPLETE / READY_FOR_TESTING** | Foundation migration and one-time Main Admin bootstrap are applied/read back. Auth/profile/role source is wired. | Run installed Auth, deep-link, role and session tests. |
| 2 — billing, charges, payments, receipts | **READY_FOR_TESTING** | Finance migrations, rollback smoke, member/Admin finance UI, filters, balances and PDF source are implemented; Phase B extensions add fines, verification and meal billing. | Real-JWT financial matrix and device/PDF workflows. |
| 3 — meals, rice, expenses, cash book | **READY_FOR_TESTING** | Phase C schedules, purchase metadata, linked corrections, alerts, approvals, accounts and locks are applied; rollback smoke and exact-head CI passed. | Real-JWT/device operations and monthly reporting. |
| 4 — duties, rotation, notices | **READY_FOR_TESTING** | Phase D backend, Android workspace, bilingual resources, validation tests, rollback smoke and exact-head CI passed. | Real-JWT/device checks; configure a scheduler only through an approved path if automatic publication is required. |
| 5 — messages, suggestions, complaints | **READY_FOR_TESTING** | Phase E backend and Community UI are committed; rollback smoke passed message/suggestion/complaint/moderation and long-payload cases. Exact-head CI runs `40190673127` and `40190677111` passed. | Run two-account real-JWT privacy and installed-device tests. |
| 6 — reports, backup, audit, hardening | **IN_PROGRESS** | Audit foundation, per-module settings, receipt PDF source and active-read hardening exist. Unified reports, CSV, backup/restore and final security matrix do not. | Implement reports/backup/settings without weakening RLS; retain all blockers below. |

## Delivered implementation checklist

### Phase A — member management

- `ui/members/MemberManagementScreen.kt`, `MemberDetailSection.kt`, `presentation/MemberManagementViewModel.kt`, `data/members/MemberManagementRepository.kt`: searchable/sortable/filterable members, invitation/lifecycle/role/audit workspaces.
- `0007_member_management.sql` and `0008_member_management_type_fix.sql`: `member_invitations`, departure metadata, capacity helper, provisioning/acceptance/resend/cancel/edit/lifecycle RPCs, audited role/status transitions, and member-management audit policy.
- `supabase/functions/member-invitation/index.ts`: deployed invitation function with JWT verification; delivery state is explicit and no success is inferred without provider confirmation.
- `phase_a_member_management_smoke.sql`: passed visibility, invitation idempotency, duplicate/capacity, cancellation, audit, direct-write denial, helper privilege and missing-profile checks with zero retained smoke rows.
- `0022_member_join_date_business_timezone.sql` plus `phase_a_business_timezone_smoke.sql`: passed Asia/Dhaka join-date boundary regression in a UTC outer session.
- **Open:** SMTP delivery, real invitation acceptance/resend, concurrent real-JWT capacity/transfer tests and installed-device checks.

### Phase B — finance extensions

- `0009_phase_b_finance_extensions.sql`, `0010_phase_b_finance_request_id_fix.sql`, `0011_phase_b_extended_snapshot.sql`: `fines`, `payment_reviews`, `meal_billing_policies`, `meal_billing_runs`, audited RPCs and snapshot projections.
- `ExtendedErpModels.kt`, `ExtendedErpDtos.kt`, `ExtendedErpRepository.kt`, `ExtendedErpScreen.kt`: Billing workspace and validated fine/payment-review/policy/bill actions.
- `phase_b_finance_extensions_smoke.sql`: passed fine/payment/billing rules, `750` from three half-meal units at `250`, idempotency, reversed/closed-period rejection, direct-write denial and missing-profile denial. Snapshot shape checks used injected claims.
- **Open:** real JWT/device verification, general monthly statements, overdue automation and external reminder delivery.

### Phase C — operations controls

- `0012_phase_c_operations_extensions.sql`, `0013_phase_c_cash_business_date_fix.sql`, `0014_phase_c_extended_snapshot.sql`: meal schedules, rice purchase metadata, linked reversals, expense approvals, settings, alerts, cash accounts/locks and source-date cash locking.
- Operations and Extended ERP Android sources expose schedules, rice purchases/reversals, expense review, cash assignment/close, settings and notifications with EN/BN resources.
- `phase_c_operations_extensions_smoke.sql`: passed schedules, rice total `5000`, stock alerts, linked/double reversal, expense approval, cash assignment, source-date locking, notification-write denial and missing-profile denial. Snapshot/settings checks used injected claims.
- Commit `04a9681`; exact-head push/PR runs `40189784504` and `40189787023` passed.
- **Open:** real JWT/device operations, monthly reports and actual file attachments (only receipt references currently exist).

### Phase D — duties and notices

- `0015_phase_d_duties_notices.sql`, `0016_phase_d_extended_snapshot.sql`: `duties`, `duty_requests`, `manager_rotations`, `notices`, `notice_reads`, notification link/deduplication and snapshot wrapper.
- `ExtendedErpDutiesValidationTest.kt`, Community-independent Duties & notices tab and EN/BN resources cover assignment, cannot-do/swap, completion, rotation, notice publication/read actions.
- `phase_d_duties_notices_smoke.sql`: passed duty idempotency, cannot-do approval, completion, rotation activation, notice read/publish, scheduled-due publication, duplicate-notification prevention, direct-write denial and missing-profile denial. No smoke rows were retained.
- Commit `5abbb87`; exact-head push/PR runs `40190110513` and `40190114805` passed.
- **Open:** real JWT/device verification; `publish_due_notices` requires an Admin RPC call because no automatic scheduler or external push delivery is configured.

### Phase E — community and moderation

- `0017_phase_e_community.sql`: `messages`, `message_reads`, `message_reports`, `suggestions`, separate `suggestion_identities`, `complaints`, RLS and authenticated RPCs.
- `0018_phase_e_extended_snapshot.sql`: authorized messages/suggestions/complaints/report projections. Public suggestion rows contain no author; identity is conditionally projected only for active Main Admin.
- `0019_phase_e_message_report_review.sql`: report `REVIEWED`/`DISMISSED` lifecycle.
- `0020_phase_e_active_moderation_fix.sql`: explicit active-Main-Admin checks and pending-suggestion row lock.
- `0021_notification_payload_bounds.sql`: bounded notification title/body compatibility for valid long source payloads; long-input rollback smoke passed.
- `ExtendedErpCommunityValidationTest.kt`, Community tab, DTO/repository RPC mappings and EN/BN resources: group/private messages, unread/read, reporting/moderation, anonymous suggestion review, complaints and Admin responses.
- `phase_e_community_smoke.sql`: passed message idempotency/read/report/review/moderation, self-private denial, suggestion identity separation/publication, long complaint response, long published notice, direct-write denial and missing-profile denial; rollback retained no rows.
- Commit `4f376d7`; exact-head push/PR runs `40190673127` and `40190677111` passed static checks, JVM tests, `assembleDebug`, public-config verification and artifact upload.
- **Open:** real two-account JWT privacy (especially anonymous identity and private messages), cross-mess/suspended/departed tests, concurrent submissions and installed-device UX.

## Executed Android verification

| Source head | Workflow runs | Observed result |
| --- | --- | --- |
| `1f6876b` Phase A | `40188681745`, `40188685214` | Static checks, JVM tests, `assembleDebug`, public-config verification and artifact upload passed. |
| `1eaa68f` Phase B | `40189238379`, `40189241738` | Same workflow completed successfully. |
| `04a9681` Phase C | `40189784504`, `40189787023` | Same workflow completed successfully. |
| `5abbb87` Phase D | `40190110513`, `40190114805` | Same workflow completed successfully. |
| `4f376d7` Phase E | `40190673127`, `40190677111` | Static checks, JVM tests, debug build, public-config verification and artifact upload passed. |

The sandbox lacks a reliable full local Android/Gradle runtime. Compilation is claimed only for the exact observed GitHub Actions heads above, never from a fictitious local build. No APK installation or production release is claimed.

## Live backend verification

Applied only to `iwoiwgkaevobxqojyphb`:

1. Phase 1–3 foundation/finance/operations migrations `0001`–`0006` and their earlier rollback/grant/RLS evidence.
2. Phase A `0007`–`0008` and invitation Edge Function version 1.
3. Phase B `0009`–`0011`.
4. Phase C `0012`–`0014`.
5. Phase D `0015`–`0016`.
6. Phase E `0017`–`0022`, including active moderation, notification payload and join-date timezone corrections.

Post-smoke checks found no retained smoke operational rows. New tables have RLS enabled and authenticated SELECT-only table grants; mutations are intended through audited RPCs. Anonymous EXECUTE is revoked for the new public mutation functions. These are live read-back/rollback observations, not a real-JWT penetration test.

Auth remains signup-disabled with `smarttransparentmess://auth`, refresh rotation enabled, email rate limit `2`, password minimum `6`, and SMTP unconfigured. Existing `mymess` remains untouched.

## Blocker and verification register

### READY_FOR_TESTING — real PostgREST/JWT authorization matrix

Rollback SQL used injected claims, not real PostgREST tokens. Test anonymous, Member, Admin, Main Admin, suspended, departed and cross-mess JWTs for every table/view/RPC. Include concurrent capacity/duplicate submissions, role transfer/recovery, private-message participant isolation, suggestion identity non-disclosure, complaint visibility, notice audience/expiry and notification read/write denial.

### READY_FOR_TESTING — installed Android verification

Install only the latest green current-head artifact and verify Auth/deep links, profile, member management, finance/receipt PDF, operations, duties/notices, Community privacy, Bengali/English, dark/light, rotation/process restoration, loading/error/empty states and duplicate-submit behavior. No installed-device result is currently claimed.

### BLOCKED — outbound Auth/invitation email

SMTP is unconfigured. `member-invitation` is deployed, but no real invitation, resend, recovery email or acceptance delivery is verified. The app must continue to expose the exact delivery status rather than report a fake success.

### BLOCKED — external push and automatic due publication

FCM credentials, token registration and a push worker are absent. Durable notifications are in-app only. Scheduled notices become due only when authorized `publish_due_notices` is invoked; no scheduler is configured or claimed.

### BLOCKED — Auth password minimum

The Auth connector does not expose a writable `password_min_length`; read-back remains `6`. Do not claim a stronger minimum without an approved supported configuration path and read-back.

### NOT IMPLEMENTED — reports, CSV and backup/restore

Unified monthly reports, CSV export, secure ERP backup/history, restore preview/integrity/failure recovery and comprehensive audit reporting remain open. Receipt PDF source is not a general report/backup solution.

### ENVIRONMENT NOTE — GitHub and local tooling

GitHub Variables, branch-protection and Actions-permissions writes previously returned integration 403; denied writes were not repeatedly retried. Bundled non-privileged configuration remains the debug build path. Signed-blob artifact downloads and local Gradle setup are unreliable; use observed run metadata and exact-head artifacts only.

## Immediate queue

1. Observe Phase E exact-head CI; if it fails, fix only the reported source issue and rerun the normal workflow.
2. Update this register and `FEATURE_AUDIT.md` with the observed Phase E run result; do not promote it to real-JWT/device verification.
3. Implement Phase F reports/exports and settings only after preserving the Phase A–E authorization boundaries; design backup/restore separately with explicit integrity and failure tests.
4. Keep SMTP, FCM, real-JWT, concurrency, installed-device and production-release blockers visible until independently verified.
