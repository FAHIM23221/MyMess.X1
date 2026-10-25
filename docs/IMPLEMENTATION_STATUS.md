# Implementation status

Last updated: **2026-10-25** (Asia/Dhaka). Branch `arena/34fa0299-mymess-x1`; PR `#2` remains open. Current Android/backend source head is `ac79a09fdc543f63699b75bb09d5f458a38ce532`.

Status vocabulary: **COMPLETE** means the named slice has the evidence listed, not production readiness; **READY_FOR_TESTING** means source/backend checks exist but real JWT/device or external delivery is still required; **IN_PROGRESS** means adjacent requested behavior remains; **BLOCKED** identifies an external/configuration dependency; **DEFERRED** means no implementation is claimed.

## Environment and safety boundary

- Active Supabase project: `mymess-x1-phase1` / `iwoiwgkaevobxqojyphb` (`ap-south-1`, PostgreSQL 17). Existing `mymess` / `jiiexmjczpzxikhmlgbh` was not modified.
- Only the Android publishable key is used by the app. No service-role key, database password, signing key, or privileged credential is stored in the repository, APK configuration, logs, Variables, or audit records.
- Backend changes are non-destructive and additive where possible. RLS remains enabled; no production data was erased and no security control was disabled to make a test pass.
- App version is `0.4.2-phase3`, versionCode `7` for Admin-directed in-app notifications and retry-payload canonicalization. Use only an artifact from a green run of the current head; the version string is not evidence of real-JWT/device verification or Phase F UI completion.

## Phase register

| Phase | Status | Verified state | Next action |
| --- | --- | --- | --- |
| 0 — repository, native Android, CI | **COMPLETE** | GitHub Actions performs static/security checks, JVM tests, debug build, public-config verification and artifact upload. | Keep exact-head checks required for every slice. |
| 1 — Auth, profiles, roles, RLS, Main Admin | **COMPLETE / READY_FOR_TESTING** | Foundation migration and one-time Main Admin bootstrap are applied/read back. Auth/profile/role source is wired. | Run installed Auth, deep-link, role and session tests. |
| 2 — billing, charges, payments, receipts | **READY_FOR_TESTING** | Finance migrations, rollback smoke, member/Admin finance UI, filters, balances and PDF source are implemented; Phase B extensions add fines, verification and meal billing. | Real-JWT financial matrix and device/PDF workflows. |
| 3 — meals, rice, expenses, cash book | **READY_FOR_TESTING** | Phase C schedules, purchase metadata, linked corrections, alerts, approvals, accounts and locks are applied; rollback smoke and exact-head CI passed. | Real-JWT/device operations and monthly reporting. |
| 4 — duties, rotation, notices, in-app notifications | **READY_FOR_TESTING** | Phase D backend/Android workspace and the additive `0025` Admin-directed notification workflow are applied; rollback smoke passed. Current-head Android CI is recorded separately below. | Real-JWT recipient/read/device checks; no external push or automatic scheduler is implied. |
| 5 — messages, suggestions, complaints | **READY_FOR_TESTING** | Phase E backend and Community UI are committed; rollback smoke passed message/suggestion/complaint/moderation and long-payload cases. Exact-head CI runs `40190673127` and `40190677111` passed. | Run two-account real-JWT privacy and installed-device tests. |
| 6 — reports, backup, audit, hardening | **IN_PROGRESS / BACKEND SLICE READY_FOR_TESTING** | `0023`/`0024` add authorized monthly aggregates and logical SHA-256-stamped export; rollback smoke passed. Audit/settings/receipt source exist. Android unified reports, CSV, physical backup/restore and final security matrix remain incomplete. | Implement report/export UI without weakening RLS; design physical recovery separately and retain all blockers below. |

## Delivered implementation checklist

### Phase A — member management

- `ui/members/MemberManagementScreen.kt`, `MemberDetailSection.kt`, `presentation/MemberManagementViewModel.kt`, `data/members/MemberManagementRepository.kt`: searchable/sortable/filterable members, invitation/lifecycle/role/audit workspaces.
- `0007_member_management.sql` and `0008_member_management_type_fix.sql`: `member_invitations`, departure metadata, capacity helper, provisioning/acceptance/resend/cancel/edit/lifecycle RPCs, audited role/status transitions, and member-management audit policy.
- `supabase/functions/member-invitation/index.ts`: invitation function with JWT verification; delivery state is explicit and no success is inferred without provider confirmation. Source hotfix `12cbf4d` adds a safe thrown-error envelope; live deployment/read-back confirms **ACTIVE v2**, `verify_jwt=true`, with that safe-catch source.
- `phase_a_member_management_smoke.sql`: passed visibility, invitation idempotency, duplicate/capacity, cancellation, audit, direct-write denial, helper privilege and missing-profile checks with zero retained smoke rows.
- `0022_member_join_date_business_timezone.sql` plus `phase_a_business_timezone_smoke.sql`: passed Asia/Dhaka join-date boundary regression in a UTC outer session.
- `data/members/OnboardingResponseParser.kt` and `OnboardingResponseParserTest.kt`: non-2xx envelopes, authentication/configuration/request failures, safe code/dependency allowlisting and no raw provider body in UI state. Exact-head CI runs `40200572153` and `40200576638` passed.
- **Open:** real-JWT Edge invocation, persisted invitation creation, SMTP delivery, real invitation acceptance/resend, concurrent real-JWT capacity/transfer tests and installed-device checks. Deployment/read-back alone does not prove delivery.

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

### Admin-directed in-app notifications

- `0025_admin_member_notifications.sql`: `member_notification_batches`, nullable `notifications.batch_id`, per-recipient uniqueness, active same-mess Admin/Main Admin `send_member_notification`, request fingerprint/idempotency, `MEMBER`/`ALL`/`ADMINS` fan-out, admin-only batch-history snapshot projection and audit SELECT policy. Batch table RLS is enabled and direct authenticated SELECT/INSERT is revoked.
- `ExtendedErpModels.kt`, `ExtendedErpDtos.kt`, `ExtendedErpRepository.kt`, `ExtendedErpViewModel.kt` and `ExtendedErpScreen.kt`: validated bilingual composer, dynamic recipient selection, stable effective-payload retry IDs, durable inbox/read action and Admin history. Success text explicitly makes no external-push claim.
- `ExtendedErpNotificationValidationTest.kt` and `ExtendedErpDtosTest.kt`: recipient rules, payload bounds, hidden-selection canonicalization and batch DTO mapping.
- `phase_admin_member_notifications_smoke.sql`: passed direct/all/Admin fan-out, idempotent retry, changed-payload rejection, Main Admin inclusion, audit/history, invalid-recipient/missing-profile denial and direct-write denial; updated smoke also passed after making history assertions safe alongside pre-existing batches. Live read-back found zero retained batch rows and confirmed grants/RLS/security-definer wrappers.
- **Open:** real Admin/recipient JWT execution, installed-device composer/inbox/read verification and external push (not implemented).

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

### Phase F — report and logical-export backend slice

- `0023_phase_f_reports_backup.sql`: bounded `get_monthly_report(date,date)` with role-appropriate financial/member/meal/rice/expense/cash aggregates and `export_erp_backup()` with current authorized snapshot, Main Admin audit projection and explicit logical-only flags.
- `0024_phase_f_backup_digest_schema.sql`: pins the existing export function search path to the Supabase `extensions` schema for `pgcrypto` digest resolution.
- `phase_f_reports_backup_smoke.sql`: passed monthly scope/range checks, independent SHA-256 recomputation with `extensions.digest`, logical-only flags, Main Admin audit/current `notification_batches` snapshot inclusion, privileged-marker absence and missing-profile denial. No write rows are involved; rollback completed.
- **Open:** Android report/export UI, CSV/PDF statements, real-JWT role/cross-mess tests, physical backup/restore design, restore preview/failure recovery and installed-device verification. `restore_supported=false` remains intentional.

## Executed Android verification

| Source head | Workflow runs | Observed result |
| --- | --- | --- |
| `1f6876b` Phase A | `40188681745`, `40188685214` | Static checks, JVM tests, `assembleDebug`, public-config verification and artifact upload passed. |
| `1eaa68f` Phase B | `40189238379`, `40189241738` | Same workflow completed successfully. |
| `04a9681` Phase C | `40189784504`, `40189787023` | Same workflow completed successfully. |
| `5abbb87` Phase D | `40190110513`, `40190114805` | Same workflow completed successfully. |
| `4f376d7` Phase E | `40190673127`, `40190677111` | Static checks, JVM tests, debug build, public-config verification and artifact upload passed. |
| `12cbf4d` invitation hotfix | `40200572153`, `40200576638` | Static checks, JVM tests, debug build, public-config verification and artifact upload passed. |
| `ad6b0ba` hotfix documentation | `40200974769`, `40200978038` | Same Android source as the hotfix; exact documentation-head checks and artifact upload passed. |
| `ac79a09` Admin notifications + Phase F backend | `40203387352`, `40203392928` | Static checks, JVM tests (including notification validation/DTO tests), debug build, public-config verification and artifact upload passed. |

The sandbox lacks a reliable full local Android/Gradle runtime. Compilation is claimed only for the exact observed GitHub Actions heads above, never from a fictitious local build. No APK installation or production release is claimed.

## Live backend verification

Applied only to `iwoiwgkaevobxqojyphb`:

1. Phase 1–3 foundation/finance/operations migrations `0001`–`0006` and their earlier rollback/grant/RLS evidence.
2. Phase A `0007`–`0008`; invitation Edge `member-invitation` ACTIVE v2 with `verify_jwt=true` and committed safe-catch source was read back. This is not a real invitation/delivery result.
3. Phase B `0009`–`0011`.
4. Phase C `0012`–`0014`.
5. Phase D `0015`–`0016`.
6. Phase E `0017`–`0022`, including active moderation, notification payload and join-date timezone corrections.
7. Phase F `0023`–`0024`; post-digest-fix report/export rollback smoke passed.
8. Admin-directed notifications `0025`; rollback smoke, zero-retained-row check and schema/grant/RLS/function read-back passed.

Post-smoke checks found no retained smoke operational rows. New tables have RLS enabled and authenticated SELECT-only table grants; mutations are intended through audited RPCs. Anonymous EXECUTE is revoked for the new public mutation functions. These are live read-back/rollback observations, not a real-JWT penetration test.

Auth remains signup-disabled with `smarttransparentmess://auth`, refresh rotation enabled, email rate limit `2`, password minimum `6`, and SMTP unconfigured. Existing `mymess` remains untouched.

## Blocker and verification register

### READY_FOR_TESTING — real PostgREST/JWT authorization matrix

Rollback SQL used injected claims, not real PostgREST tokens. Test anonymous, Member, Admin, Main Admin, suspended, departed and cross-mess JWTs for every table/view/RPC. Include concurrent capacity/duplicate submissions, role transfer/recovery, private-message participant isolation, suggestion identity non-disclosure, complaint visibility, notice audience/expiry and notification read/write denial.

### READY_FOR_TESTING — installed Android verification

Install only the latest green current-head artifact and verify Auth/deep links, profile, member management, finance/receipt PDF, operations, duties/notices, Community privacy, Bengali/English, dark/light, rotation/process restoration, loading/error/empty states and duplicate-submit behavior. No installed-device result is currently claimed.

### BLOCKED — outbound Auth/invitation email

SMTP is unconfigured. `member-invitation` ACTIVE v2 is deployed/read back with JWT verification, but no real-JWT invocation, persisted invitation, resend, recovery email or acceptance delivery is verified. The screenshot incident had zero invitation rows on safe read-back; it is not evidence of Auth creation or delivery. The app must continue to expose the exact delivery status rather than report a fake success.

### BLOCKED — external push and automatic due publication

FCM credentials, token registration and a push worker are absent. Durable notifications are in-app only. Scheduled notices become due only when authorized `publish_due_notices` is invoked; no scheduler is configured or claimed.

### BLOCKED — Auth password minimum

The Auth connector does not expose a writable `password_min_length`; read-back remains `6`. Do not claim a stronger minimum without an approved supported configuration path and read-back.

### PARTIAL / NOT IMPLEMENTED — reports, CSV and physical recovery

Backend monthly aggregates and logical SHA-256-stamped export are applied and rollback-tested. Android unified reports, CSV export, secure physical ERP backup/history, restore preview/failure recovery and comprehensive audit reporting remain open. The logical export explicitly has `restore_supported=false`; receipt PDF source is not a general report/backup solution.

### ENVIRONMENT NOTE — GitHub and local tooling

GitHub Variables, branch-protection and Actions-permissions writes previously returned integration 403; denied writes were not repeatedly retried. Bundled non-privileged configuration remains the debug build path. Signed-blob artifact downloads and local Gradle setup are unreliable; use observed run metadata and exact-head artifacts only.

## Immediate queue

1. Observe the final documentation-head CI and supply only its green exact-head debug artifact; do not use the older hotfix APK as evidence for this feature.
2. Run real-JWT invitation and Admin/recipient notification tests, including duplicate retries, same-mess/cross-mess authorization and read state; keep SMTP/external-push claims closed.
3. Implement the Phase F Android report/logical-export UI and CSV only after preserving the verified backend boundaries; keep `restore_supported=false` until a separately approved, tested recovery design exists.
4. Keep real-JWT, concurrency, installed-device, PDF/device, physical recovery and production-release blockers visible until independently verified.
