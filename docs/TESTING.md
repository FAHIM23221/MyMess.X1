# Testing and verification plan

## Checks included in the source

- `app/src/test/java/.../ProfileValidatorTest.kt`: name and phone validation boundaries.
- `AppRoleTest.kt`: case-insensitive role parsing and fail-closed unknown roles.
- `ProfileDtoTest.kt`: profile mapping, nullable fields, and rejection of an unknown role.
- `SupabaseConfigTest.kt`: HTTPS requirement and project URL normalization.
- `MoneyTest.kt`: exact BDT minor-unit parsing/formatting, excess precision, invalid input, and full `Long` formatting boundaries.
- `FinanceModelsTest.kt`: opening due, partial payment, overpayment/advance, credit offset, reversal visibility, and overflow rejection.
- `FinanceDtosTest.kt`: exact backend/domain balance consistency, unknown enum rejection, and payment/receipt mapping.
- `AdminFinanceCalculatorTest.kt`: per-member due/advance separation, zero/historical member balances, reversed-payment exclusion, adjustment totals, missing periods, and aggregate overflow.
- `FinanceEntryValidatorTest.kt`: Dhaka payment-date conversion, future-date rejection, amount limits, optional text, and charge/adjustment/period validation.
- `OperationsCalculatorTest.kt`: reversed-row exclusion, exact rice/cash totals, half-meal formatting, and overflow rejection.
- `OperationsValidationTest.kt`: half-meal increments, whole-gram bounds, exact expense minor units, references, future dates, descriptions, and reversal reasons.
- `supabase/tests/phase2_smoke_transaction.sql`: rollback-only RPC, duplicate, reversal, closed-period, due/advance carry-forward, expanded method/category, future-date, and authorization smoke coverage.
- `supabase/tests/phase3_smoke_transaction.sql`: rollback-only meal/rice/expense duplicates and reversals, stock guard, four cash-book triggers, direct-write denial, and authorization smoke coverage.
- `scripts/verify_project.py`: dependency-free XML/resource checks, bilingual parity, selected SQL invariants, public-config validation, workflow presence, and privileged-key guard.
- `scripts/verify_built_config.py`: reads generated debug `BuildConfig` without printing values and fails unless a valid HTTPS Supabase URL and publishable key were actually compiled.
- `.github/workflows/android-debug.yml`: runs static checks, `testDebugUnitTest`, `assembleDebug`, and built-config verification before uploading only the debug APK artifact.

Run locally (with Python 3, JDK 17, Android SDK Platform 36, and a working Gradle wrapper):

```sh
python3 scripts/verify_project.py
./gradlew testDebugUnitTest
./gradlew assembleDebug
python3 scripts/verify_built_config.py
```

The static script is not a SQL parser, penetration test, or proof that RLS behaves correctly. This sandbox lacks a complete local Android/Gradle runtime and cannot reliably download signed artifacts, so Android build results are taken only from observed GitHub Actions runs. Phase 3 implementation commit `6be5cd91969570d56cd3355003b7738027180fd1` passed static checks, all JVM tests, `assembleDebug`, generated-`BuildConfig` verification, and artifact upload in run `40161969261`. Six migrations, grant/RLS/trigger inspection, and both rollback-only SQL smoke transactions have run. Current source is version `0.4.0-phase3` / versionCode `5`; use the artifact from the latest green current-head run rather than an older versioned artifact. Real-JWT API and installed-device tests remain open.

## Supabase integration test matrix before real data

Use separate test Auth accounts and a disposable project. Test both allowed and denied outcomes; run queries as the actual Supabase `anon`/`authenticated` roles with real JWTs, not as the Dashboard's owner role alone.

| Identity / action | Expected result |
| --- | --- |
| Anonymous request selects `profiles`, `messes`, assignment table, or audit logs | Denied / no rows; no anonymous table grant |
| Active Member reads `my_profile` | Exactly own profile |
| Active Member selects another Member's profile | No row / denied by RLS |
| Active Member updates own `full_name` or `phone_number` within constraints | Allowed |
| Active Member updates `role`, `account_status`, `mess_id`, `is_current_manager`, or another user's profile | Denied |
| Active Admin reads same-mess profiles | Allowed by RLS |
| Admin reads a different mess or tries role/status RPC | Denied |
| Active Main Admin calls role/status/profile-provisioning RPC with a valid reason | Allowed only for permitted target; audit row written |
| Member or Admin calls Main Admin RPC | Denied |
| Main Admin tries to assign `MAIN_ADMIN` through `set_profile_role` | Denied; use transfer RPC |
| Main Admin transfers to an active same-mess profile | One assignment remains; old user becomes Admin; audit row exists |
| Main Admin transfers to inactive/wrong-mess/self target | Denied; old assignment remains |
| Client attempts to delete the assignment or suspend its current user | Denied by grants/trigger |
| Authenticated or service-role API calls `recover_main_admin_owner_only` | Denied; only project-owner SQL session may use it |
| Owner-only recovery uses a wrong expected old ID or inactive target | Denied; assignment remains unchanged |
| Owner-only recovery uses verified approval and valid active target | Assignment transfers atomically, old account is suspended, and a `PROJECT_OWNER_RECOVERY` audit entry is written |
| New mess is committed without an active Main Admin assignment | Transaction rejected by deferred constraint trigger |
| Suspended user signs in or calls a protected RPC | No dashboard/protected data; operation denied |
| Anonymous/Member selects finance tables or views | Anonymous denied; Member only own summary/receipts |
| Member calls any finance mutation RPC | Permission denied; no ledger/audit mutation |
| Admin records a duplicate payment request ID | Unique violation; no duplicate payment/receipt |
| Admin reverses a payment twice | First adds reversal and voids receipt; second rejected |
| Charge/payment/adjustment targets a closed period | Rejected; closed ledger remains unchanged |
| Carry-forward is repeated for the same member/source/target | Rejected by the partial unique index |
| Payment exceeds charges/opening due | Advance credit, not a negative outstanding due |
| Admin targets another mess or suspended identity calls RPC | Rejected by server-side active same-mess checks |
| Suspended Member selects any Phase 2 ledger/period row | No rows; every Member/Admin SELECT branch requires an active profile |
| Member selects meal rows | Only own active same-mess entries/reversals; no other member's meals |
| Member selects rice/expense/cash-book rows | Same-mess transparency rows only; no cross-mess rows |
| Member or anonymous identity calls an operations mutation RPC | Denied; no operational/audit/cash-book mutation |
| Admin repeats a meal/rice/expense client request ID | Unique violation; no duplicate entry or cash event |
| Admin records rice stock-out above current stock | Rejected under the per-mess transaction lock |
| Meal or expense is reversed twice | First adds a reasoned reversal; second rejected |
| Client inserts/updates/deletes a cash-book row directly | Denied by grants/RLS; only source triggers create rows |
| Existing Finance payment or reversal is recorded | Exactly one corresponding cash-book event is created |

Also test password recovery redirect, PKCE callback, refresh after process restart, local and remote sign-out, and logs after a deliberately invalid Auth request. On Android, verify valid and reversed PDF rendering, Bengali text, document-picker cancellation/success, share-sheet permission grants, and that no arbitrary cache file is exposed through the receipt `FileProvider`. See `SECURITY.md` for unresolved token-log verification and `PHASE2_FINANCE.md` for the executed rollback-only smoke scope.
