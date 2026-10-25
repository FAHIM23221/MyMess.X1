# Evidence-based feature audit

Last audited: **2026-10-25** (Asia/Dhaka), branch `arena/34fa0299-mymess-x1`, starting head `79b53ebbc8084fa294fa938cd6461871d3c705cd`. PR `#2` was the only open PR at audit start and its checks were green.

This audit distinguishes source presence from executed verification. **Implemented and verified** means the stated evidence was actually observed; it does not imply production readiness. **Requires real-device or security verification** means source/backend evidence exists but the named authorization or device matrix has not run.

## Verification legend

- **CI/JVM:** GitHub Actions static checks, unit tests, and `assembleDebug` observed through run `40162396363` / PR run `40162400125`.
- **DB smoke:** owner-session SQL with injected claims and `ROLLBACK`; useful RPC/constraint evidence, not a real-JWT test.
- **Live read-back:** schema, grants, policies, triggers, Auth configuration, or counts read from `iwoiwgkaevobxqojyphb`.
- **Real JWT/device:** not yet executed for the current source. No feature is promoted to production-ready on the basis of a screen, button, model, table, or compiled APK.

## Phase A — Admin user management

| Requested feature | Classification | Exact evidence and remaining gap |
| --- | --- | --- |
| `Members & User Management` workspace | **Not implemented** | `SmartMessApp.kt` has Home/Finance/Operations/Profile only. No member-management ViewModel, repository, or screen exists. |
| Members list, search, sorting, filters | **Not implemented** | `profiles` and `profiles_select_own_or_admin` in `0001_phase1_foundation.sql` provide an authorized data source, not the requested UI. |
| Add New Member form | **Backend exists but UI is missing** | `provision_member_profile(uuid,text,text,text,text,text,text,date)` exists in `0001`; it requires a pre-existing confirmed Auth user and has no Android caller or capacity guard. |
| Secure invitation delivery | **Blocked by configuration or external service** | Auth signup is disabled and `smtp_host`, `smtp_admin_email`, and sender settings read back as null. `list_edge_functions` returned no functions. No invitation table/delivery integration exists. No invitation may be reported as sent. |
| Pending invitation state | **Not implemented** | `PENDING` exists as a profile status, but there is no invitation entity, acceptance flow, delivery status, resend history, or duplicate-open-invite guard. |
| Active/suspended states | **Partially implemented** | `account_status` and audited `set_profile_status` exist in `0001`; UI and real-JWT/device checks are absent. |
| Departed/archive state with financial retention | **Not implemented** | Profiles have no departed metadata or lifecycle RPC. Financial foreign keys use `ON DELETE RESTRICT`, but that alone is not a departure workflow. |
| Member details and authorized Admin edit | **Partially implemented** | Own name/phone edit exists in `SupabaseMemberProfileRepository.kt` and `ProfileScreen.kt`. No Admin details/edit UI or protected Admin edit RPC exists. |
| Suspend/reactivate/departure actions | **Backend exists but UI is missing** | Main Admin-only `set_profile_status` supports the existing enum; there is no departure action, Admin member action UI, or capacity-aware reactivation. |
| Member financial summary, payments, receipts | **Partially implemented** | `AdminFinanceRepository.kt`, `AdminFinanceCalculator.kt`, `AdminPaymentSection.kt`, and `ReceiptDocument.kt` provide Admin finance data/PDF source. They are not connected to a member-details workspace and still require device verification. |
| Member meal history | **Partially implemented** | `meal_entries`, `meal_reversals`, `OperationsRepository.kt`, and `OperationsMealSection.kt` exist; no member-management detail view connects them. |
| Admin/Manager list and role management | **Backend exists but UI is missing** | `set_profile_role` and `transfer_main_admin` exist in `0001`; no role-management screen and no five-Admin capacity check exists. |
| Main Admin-only privileged actions | **Requires real-device or security verification** | Singleton assignment, role derivation, transfer/recovery functions, and Main Admin RLS exist in `0001`/`002_recover_main_admin.sql`. One assignment was read back. Real-JWT transfer/recovery tests remain. |
| Maximum 20 members / 5 Admins | **Not implemented** | Reviewed `0001`–`0006` contain no member/Admin capacity constraint or RPC guard. Existing provisioning/role/transfer paths therefore need hardening, not only a UI counter. |
| Exactly one active Main Admin | **Requires real-device or security verification** | Singleton PK/unique mess row, deferred mess constraint, active-profile trigger, and transfer logic exist in `0001`. One assignment is present; concurrent/real-JWT verification remains. |
| Duplicate account / concurrent over-capacity prevention | **Not implemented** | `profiles.user_id` and `(mess_id,member_id)` are unique, but there is no normalized-email invitation guard or capacity transaction lock. |
| User-management audit history UI | **Backend exists but UI is missing** | `audit_logs` and audited Phase 1 RPCs exist. `audit_logs_select_main_admin_only` is the only audit SELECT policy; no member-management audit screen or narrow Admin event policy exists. |

## Phase B — Finance and billing

| Requested feature | Classification | Exact evidence and remaining gap |
| --- | --- | --- |
| Exact BDT minor-unit arithmetic | **Implemented and verified** | `Money.kt`, `FinanceModels.kt`, `AdminFinanceCalculator.kt`; `MoneyTest.kt`, `FinanceModelsTest.kt`, and `AdminFinanceCalculatorTest.kt` passed CI. |
| Seat rent/Wi-Fi/electricity/water/gas/other charges | **Requires real-device or security verification** | `ChargeCategory`, `create_charge`, `AdminChargeSection.kt`, migrations `0002`/`0004`, and Phase 2 DB smoke exist. Device and real-JWT mutation tests remain. |
| Fines/reasoned penalties | **Partially implemented** | `FINE` is a charge category and descriptions/reasons are validated. No dedicated fines ledger, penalty workflow, notification, or fine report exists. |
| Periods, allocation, opening balance, carry-forward, advances | **Requires real-device or security verification** | `billing_periods`, `charges`, `billing_adjustments`, `carry_forward_balance`, Admin periods/charges UI, and rollback smoke exist. |
| Partial/overpayment and transaction references | **Requires real-device or security verification** | `payments`, exact due/advance calculations, `record_payment`, payment UI, and smoke/JVM tests exist. |
| Payment verification | **Not implemented** | Valid means not reversed; there is no separate unverified/verified/rejected payment state or verifier workflow. |
| Receipts, PDF download/share, void/reversal | **Requires real-device or security verification** | `receipts`, `payment_reversals`, `ReceiptDocument.kt`, FileProvider, Admin payment UI, and DB smoke exist. Rendering/Bengali/share targets need a device. |
| Monthly bill generation and due dates | **Partially implemented** | Periods, charges, and optional charge due dates exist. No one-click monthly bill run, generated statement, or consolidated monthly invoice exists. |
| Overdue status and reminders | **Not implemented** | No overdue calculator, reminder entity, scheduler, or notification delivery exists. |
| Automatic meal-to-charge policy | **Not implemented** | `OperationsMealSection.kt` explicitly says entries do not create charges. No pricing/policy table or billing RPC exists. |
| Safe close, duplicate protection, audit trail | **Requires real-device or security verification** | Closed-period RPC checks, request IDs, reversal/carry-forward uniqueness, and audit writes are present in `0002`/`0004` and smoke scripts. |
| Member-wise ledger/statements | **Partially implemented** | Member summaries and Admin member-wise balances exist; no member-details ledger or general statement export exists. |

## Phase C — Meals, rice, expenses, cash book

| Requested feature | Classification | Exact evidence and remaining gap |
| --- | --- | --- |
| Daily slots/member entries/half meals | **Requires real-device or security verification** | `meal_entries`, `record_meal_entry`, `OperationsMealSection.kt`, and Phase 3 smoke/JVM tests exist. |
| Meal ON/OFF and pause/resume dates | **Not implemented** | No meal-status schedule, pause range, or notification RPC/table exists. |
| Meal correction history | **Requires real-device or security verification** | `meal_reversals` and one-time reversal RPC/UI exist; real authorization/device tests remain. |
| Monthly meal totals/cost summary | **Partially implemented** | Active meal-unit total exists in `OperationsCalculator.kt`; no monthly grouping or cost total exists. |
| Meal notifications | **Not implemented** | No meal event recipient, notification table, or push integration exists. |
| Rice stock and negative-stock guard | **Requires real-device or security verification** | `rice_movements`, advisory-lock stock guard, UI, and rollback smoke exist. |
| Low-stock alerts | **Not implemented** | No threshold setting, alert state, or notification exists. |
| Rice purchase unit/price/vendor/receipt | **Not implemented** | Current rows contain only kind, grams, note, request ID, actor, timestamp. |
| Linked rice correction | **Not implemented** | Only an offsetting movement is possible; no linked reversal entity exists. |
| Expense categories/notes/reversals | **Requires real-device or security verification** | `expenses`, `expense_reversals`, protected RPCs, UI, and smoke tests exist. |
| Expense vendor/attachments/approval | **Not implemented** | No vendor, Storage attachment, approval status, or approver fields/workflow exists. |
| Monthly rice/expense summaries | **Not implemented** | Current summary is all loaded rows, not a monthly consumption/expense report. |
| Source-generated cash book | **Requires real-device or security verification** | `cash_book_entries`, four triggers, source uniqueness/backfill, read-only UI, and smoke evidence exist in `0006`. |
| Cash/bank/mobile-wallet balances | **Not implemented** | No account entity or per-account balance exists; current total is a single mess cash aggregate. |
| Opening/closing balance and reconciliation | **Not implemented** | No opening balance RPC, daily/monthly close, reconciliation, or lock entity exists. |
| Member contribution/expense breakdown | **Partially implemented** | Payment and expense history are visible; no grouped contribution/expense report exists. |
| Cash period locking | **Not implemented** | Finance billing-period rules do not lock every cash-book source operation. |

## Phase D — Duties, manager rotation, notices, notifications

| Requested feature | Classification | Exact evidence and remaining gap |
| --- | --- | --- |
| Duty calendar, assignments, deadlines/status | **Not implemented** | No duty table, RPC, repository, or screen exists. |
| Cannot-do requests, swaps, approval | **Not implemented** | No request/swap/approval workflow exists. |
| Completion and missed-duty handling | **Not implemented** | No completion/missed-duty tracking exists. |
| Manager rotation, next manager, handover | **Not implemented** | `is_current_manager` exists on profiles, but no rotation schedule/handover implementation exists. |
| Notice CRUD/publish/schedule/cancel/archive | **Not implemented** | No notice table, RPC, repository, or Admin screen exists. |
| Audience/expiration/read counts/reminders | **Not implemented** | No recipients, read receipts, audience policy, or scheduler exists. |
| Android push/token registration/deep links | **Blocked by configuration or external service** | No FCM configuration, device-token table, notification service, or secure delivery function exists. A Postgres notice alone would not prove push delivery. |

## Phase E — Discussion, private messages, suggestions, complaints

| Requested feature | Classification | Exact evidence and remaining gap |
| --- | --- | --- |
| Mess group discussion | **Not implemented** | No conversation/message tables, RLS, repositories, or UI exist. |
| Private member-to-Admin messaging | **Not implemented** | No participant privacy policy or private-message workflow exists. |
| Anonymous suggestions with Main Admin-only identity | **Not implemented** | No sender-identity separation or protected identity RPC exists. |
| Complaints/status/Admin response | **Not implemented** | No complaint/status/response workflow exists. |
| Main Admin publication approval | **Not implemented** | No publication/moderation state exists. |
| Unread/moderation/reporting | **Not implemented** | No read/moderation/report entities exist. |

## Phase F — Reports, backup, settings, security

| Requested feature | Classification | Exact evidence and remaining gap |
| --- | --- | --- |
| Monthly financial/member/meal/rice/expense/cash reports | **Not implemented** | Domain snapshots exist, but no report generator or monthly report screen exists. |
| Date/category/member filters | **Partially implemented** | Payment and charge filters exist in `AdminPaymentSection.kt`/`AdminChargeSection.kt`; no unified report filters exist. |
| PDF receipts | **Requires real-device or security verification** | `ReceiptDocument.kt` and FileProvider exist; device rendering/sharing is unverified. |
| CSV export | **Not implemented** | No CSV generator/export tests exist. |
| Secure backup/history | **Not implemented** | Android backup is intentionally disabled for encrypted Auth data; no ERP backup service/history exists. |
| Restore preview/integrity/failure recovery | **Not implemented** | No restore implementation or verified restore result exists. |
| Role-appropriate audit access | **Partially implemented** | Audit writes and Main Admin SELECT policy exist; no audit UI and no narrow Admin member-event read policy exists. |
| Member dashboard | **Requires real-device or security verification** | `DashboardScreen.kt` and `FinanceSection.kt` use backend finance state with empty/loading/error handling. |
| Admin dashboard members/dues/meals/expenses | **Partially implemented** | Finance and Operations workspaces expose these separately; no unified Admin dashboard with notices/pending tasks exists. |
| Main Admin system-management dashboard | **Backend exists but UI is missing** | Protected role/status/transfer/recovery functions exist; no dedicated system-management screen exists. |
| Profile/account settings | **Partially implemented** | Own name/phone edit and password recovery/update exist; broader account settings do not. |
| English/Bengali and dark/light | **Requires real-device or security verification** | `values`/`values-bn` resources and `ui/theme/Theme.kt` exist. Static parity passes; device typography/theme/layout checks remain. |
| Session expiry/logout/recovery/reauthentication | **Partially implemented** | Auth state events, encrypted storage, sign-out, recovery, and Admin re-entry exist in Auth sources. Sensitive-action reauthentication and installed-device session tests are incomplete. |

## Cross-cutting database and test assessment

- **Implemented and verified at source/DB-smoke level:** ordered migrations `0001`–`0006`, RLS enabled on reviewed tables, authenticated SELECT-only ledger/operations grants, active-read hardening in `0005`, audited finance/operations RPCs, and rollback scripts `phase2_smoke_transaction.sql` / `phase3_smoke_transaction.sql`.
- **Requires real-device or security verification:** anonymous, Member, Admin, suspended, departed, and cross-mess JWT behavior through PostgREST/Edge Functions; concurrent capacity and duplicate tests; installed Auth/deep-link/session behavior.
- **Blocked by configuration or external service:** SMTP invitation/recovery delivery and FCM push. Auth password minimum remains `6`; SMTP fields are null. No secure backup/restore service is configured.
- **CI/JVM only:** the current workflow proves static contracts, unit tests, compilation, public-config generation, and artifact upload. It does not prove real authorization, email/push delivery, PDF rendering, backup/restore, or user acceptance.

## Incremental implementation order

1. **Phase A:** close the user-management audit gaps with capacity-safe backend provisioning/invitation and the actual Android workspace.
2. **Phase B:** payment verification, fines, bill generation/overdue, and explicit meal-billing policy.
3. **Phase C:** meal schedules, purchase/attachment/approval metadata, alerts, monthly summaries, and reconciliation.
4. **Phase D:** duties, rotation, notices, and secure notification delivery.
5. **Phase E:** private/group communication with sender-privacy enforcement and moderation.
6. **Phase F:** reports/exports, verified backup/restore, settings, and final real-JWT/device security matrix.

Phase A status is updated after implementation; unresolved delivery and verification dependencies remain explicit rather than being converted into success claims.
