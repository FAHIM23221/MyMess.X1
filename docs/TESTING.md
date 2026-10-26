# Testing and verification plan

## Checks included in the source

- `app/src/test/java/.../ProfileValidatorTest.kt`, `AppRoleTest.kt`, `ProfileDtoTest.kt`, and `SupabaseConfigTest.kt`: profile/role/config validation and fail-closed parsing.
- `MoneyTest.kt`, `FinanceModelsTest.kt`, `FinanceDtosTest.kt`, `AdminFinanceCalculatorTest.kt`, and `FinanceEntryValidatorTest.kt`: exact BDT arithmetic, balances, receipts, filters, dates and finance input boundaries.
- `OperationsCalculatorTest.kt` and `OperationsValidationTest.kt`: meal/rice/expense calculations, half-meal increments, stock bounds, references and reversal validation.
- `MemberManagementModelsTest.kt` and `MemberManagementValidationTest.kt`: member search/filter/sort, departed derivation, capacity and onboarding/edit validation.
- `ExtendedErpModelsTest.kt`: fines, payment review, meal billing and record projection validation.
- `ExtendedErpOperationsValidationTest.kt`: schedules, rice purchases, cash accounts/locks and mess settings validation.
- `ExtendedErpDutiesValidationTest.kt`: duty swap and scheduled-notice validation.
- `ExtendedErpCommunityValidationTest.kt`: private-message recipient, moderation/suggestion reason and complaint-response validation.
- `ExtendedErpNotificationValidationTest.kt`: direct/all/Admin audience validation, payload bounds and removal of stale hidden recipient IDs from effective retry payloads.
- `ExtendedErpDtosTest.kt`: Admin notification batch-history DTO/domain mapping.
- `supabase/tests/phase2_smoke_transaction.sql`: rollback-only finance RPC, duplicate, reversal, closed-period, carry-forward and authorization checks.
- `supabase/tests/phase3_smoke_transaction.sql`: rollback-only meal/rice/expense/cash-book duplicate, reversal, stock, trigger and authorization checks.
- `supabase/tests/phase_a_member_management_smoke.sql`: rollback-only member visibility, invitation/idempotency, duplicate/capacity, lifecycle, audit, direct-write and missing-profile checks.
- `supabase/tests/phase_a_business_timezone_smoke.sql`: UTC-outer-session regression for Asia/Dhaka join-date boundaries.
- `supabase/tests/phase_b_finance_extensions_smoke.sql`: fines, payment verification, meal-billing calculation/idempotency and privilege checks.
- `supabase/tests/phase_c_operations_extensions_smoke.sql`: schedules, rice metadata/reversals, alerts, expense approvals, cash source-date locks, settings and notification-write checks.
- `supabase/tests/phase_d_duties_notices_smoke.sql`: duty/request/rotation/notice workflows, scheduled-due publication, read state, notification deduplication and privilege checks.
- `supabase/tests/phase_e_community_smoke.sql`: group message idempotency/read/report/review/moderation, self-private denial, separated suggestion identity/publication, long complaint/notice payloads, direct-write and missing-profile checks.
- `supabase/tests/phase_admin_member_notifications_smoke.sql`: rollback-only direct/all/Admin fan-out, stable idempotency, changed-payload rejection, Main Admin inclusion, audit/history, invalid-recipient/missing-profile denial and direct-write denial.
- `supabase/tests/phase_f_reports_backup_smoke.sql`: rollback-only monthly report scope/range, independent `extensions.digest` SHA-256 verification, logical-only flags, audit/current snapshot inclusion, privileged-marker absence and missing-profile denial.
- `scripts/verify_project.py`: dependency-free XML/resource checks, bilingual parity, selected SQL invariants, public-config validation, workflow presence and privileged-key guard.
- `scripts/verify_built_config.py`: reads generated debug `BuildConfig` without printing values and fails unless a valid HTTPS Supabase URL and publishable key were compiled.
- `.github/workflows/android-debug.yml`: runs static checks, `testDebugUnitTest`, `assembleDebug`, built-config verification and debug APK upload.

Run locally where JDK 17, Android SDK Platform 36 and a working Gradle wrapper are available:

```sh
python3 scripts/verify_project.py
./gradlew testDebugUnitTest
./gradlew assembleDebug
python3 scripts/verify_built_config.py
```

The static script is not a SQL parser, penetration test or proof of RLS behavior. SQL smoke uses injected claims in an owner session and rolls back; it is not a real-JWT test. This sandbox lacks a reliable full local Android/Gradle runtime, so build evidence comes only from observed GitHub Actions runs. Current source is version `0.4.2-phase3` / versionCode `7`; use the latest green current-head artifact, not an older phase artifact.

## Observed exact-head Android runs

| Head | Runs | Result |
| --- | --- | --- |
| `1f6876b` Phase A | `40188681745`, `40188685214` | Passed static checks, JVM tests, debug build and config verification. |
| `1eaa68f` Phase B | `40189238379`, `40189241738` | Passed. |
| `04a9681` Phase C | `40189784504`, `40189787023` | Passed. |
| `5abbb87` Phase D | `40190110513`, `40190114805` | Passed. |
| `4f376d7` Phase E | `40190673127`, `40190677111` | Passed static checks, JVM tests, debug build and config verification. |
| `12cbf4d` invitation hotfix | `40200572153`, `40200576638` | Passed static checks, JVM tests, debug build and config verification. |
| `ad6b0ba` hotfix documentation | `40200974769`, `40200978038` | Passed at the exact documentation head and uploaded an artifact. |
| `ac79a09` Admin notifications + Phase F backend | `40203387352`, `40203392928` | Passed static checks, JVM tests, debug build, config verification and artifact upload. |
| `e7ded35` invitation Edge boot fix | `40286053939`, `40286058434` | Passed static guardrail/JVM/build/config checks; live OPTIONS startup separately passed. |

## Observed live invitation runtime diagnosis — 2026-10-26

- Unauthenticated and structurally invalid/invalid-signature Bearer calls to `member-invitation` returned the expected `401`; this did not execute an invitation or use a real credential.
- A temporary non-privileged diagnostic function returned `200 RUNTIME_OK` without dependencies, `503 BOOT_ERROR` after adding `jsr:@supabase/supabase-js@2`, and `200 NPM_IMPORT_OK` with `npm:@supabase/supabase-js@2`.
- Pinned `npm:@supabase/supabase-js@2.118.0` plus an anon Auth-client invalid-token negative check returned `200 PINNED_IMPORT_AND_AUTH_CLIENT_OK`. No service-role operation, Auth user creation, invitation, secret value or database mutation was part of the diagnostic.
- The diagnostic function was deleted. The transactional HTTP extension was rolled back; read-back found only the pre-existing `pgcrypto` extension.
- Actual `member-invitation` ACTIVE v3 has the committed pinned import and `verify_jwt=true`; its OPTIONS request returned `200 ok`, directly confirming runtime startup after the repair.
- These checks isolate and verify the boot defect. They do not prove a real-JWT invitation, Auth Admin invite, SMTP delivery, resend or acceptance.

## Supabase integration test matrix before real data

Use separate test Auth accounts and a disposable project. Test allowed and denied outcomes as the actual Supabase `anon`/`authenticated` roles with real JWTs, not only as the Dashboard owner.

| Identity / action | Expected result |
| --- | --- |
| Anonymous selects protected profiles, finance, operations, community or audit tables | Denied / no rows; no anonymous table grant |
| Active Member reads own profile/finance/meals | Exactly authorized own rows |
| Active Member reads another Member's private profile/payment/meal/community identity | No row / denied by RLS or snapshot predicate |
| Member updates `role`, `account_status`, `mess_id`, `is_current_manager` or another profile | Denied; no client self-elevation |
| Admin reads same-mess management/finance/operations rows | Allowed only within the defined Admin projection |
| Admin reads another mess or calls Main Admin-only role/moderation RPC | Denied |
| Suspended/departed user reads or mutates protected data | Denied / no active rows |
| Main Admin transfers role or provisions a valid target | Only the permitted audited path succeeds; singleton/capacity invariants remain |
| Main Admin/Admin exceeds 20 members or 5 Admins, including concurrent requests | Rejected under the backend capacity lock; verify with real concurrent clients |
| Duplicate invitation/payment/meal/rice/duty/message/suggestion request ID | Idempotent return or defined duplicate rejection; no duplicate financial/notification effect |
| Member sends private message to another ordinary Member | Denied; only authorized member-to-Admin private path |
| Non-participant reads a private message | No row, including ordinary Admin without Main Admin moderation visibility |
| Ordinary Member/Admin reads suggestion identity | No author identity; only active Main Admin identity policy/projection |
| Non-Main Admin publishes/rejects a suggestion or moderates a message | Denied |
| Member creates complaint; authorized Admin responds | Own complaint plus authorized Admin response visible; audit/notification rules hold |
| Non-Admin writes complaints/messages/suggestions/notifications directly | Denied by grants/RLS; use RPCs |
| Admin invokes `publish_due_notices` | Due scheduled notices publish once; no automatic scheduler is implied |
| Admin sends `MEMBER`/`ALL`/`ADMINS` notification; Member or cross-mess actor attempts it | Only active same-mess Admin/Main Admin succeeds; each intended active recipient gets one durable row and one batch audit event |
| Same notification request is retried; hidden member selection changes under non-member audience | Same effective request ID returns the original batch without duplicate recipient rows; stale hidden selection is excluded |
| Recipient reads an Admin notification | Only the recipient can mark their own row read; Admin history remains same-mess/admin-only; no external push is inferred |
| Member/Main Admin requests monthly report or logical export | Role-authorized projection only; digest matches payload; `restore_supported=false`; no cross-mess/physical-restore claim |
| Valid long notice/message/complaint payload | Source and bounded durable notification succeed without constraint failure |
| Payment/rice/expense source is reversed twice or enters a locked period | First valid reversal/entry follows the rules; repeat/locked mutation is rejected |
| Cash-book row is inserted/updated/deleted directly | Denied; source triggers/RPCs only |

Also test password recovery redirect, PKCE callback, refresh after process restart, local/remote sign-out, invalid Auth logs, real invitation/resend/acceptance, and external delivery configuration. On Android verify valid/reversed PDF rendering, Bengali text, document-picker cancellation/success, share-sheet permissions, light/dark layouts, rotation/process restoration and that no arbitrary cache file is exposed through the receipt `FileProvider`.

The hotfix adds parser tests for structured non-2xx, authentication, configuration, request-unconfirmed and safe-detail handling. Live Edge ACTIVE v3 deployment/source/JWT-flag read-back and OPTIONS startup verification are complete, but real JWT/device invitation, SMTP, FCM, physical backup/restore and production-release verification remain open. Notification and Phase F SQL smoke uses injected claims and is not a substitute for those checks. See `FEATURE_AUDIT.md`, `IMPLEMENTATION_STATUS.md` and `SECURITY.md` for the current evidence boundaries.
