# Testing and verification plan

## Checks included in the source

- `app/src/test/java/.../ProfileValidatorTest.kt`: name and phone validation boundaries.
- `AppRoleTest.kt`: case-insensitive role parsing and fail-closed unknown roles.
- `ProfileDtoTest.kt`: profile mapping, nullable fields, and rejection of an unknown role.
- `SupabaseConfigTest.kt`: HTTPS requirement and project URL normalization.
- `MoneyTest.kt`: exact BDT minor-unit parsing/formatting, excess precision, invalid input, and full `Long` formatting boundaries.
- `FinanceModelsTest.kt`: opening due, partial payment, overpayment/advance, credit offset, reversal visibility, and overflow rejection.
- `FinanceDtosTest.kt`: exact backend/domain balance consistency, unknown enum rejection, and payment/receipt mapping.
- `AdminFinanceCalculatorTest.kt`: per-member due/advance separation, reversed-payment exclusion, adjustment totals, missing periods, and aggregate overflow.
- `FinanceEntryValidatorTest.kt`: Dhaka payment-date conversion, future-date rejection, amount limits, optional text, and charge/adjustment/period validation.
- `supabase/tests/phase2_smoke_transaction.sql`: rollback-only RPC, duplicate, reversal, closed-period, due/advance carry-forward, expanded method/category, future-date, and authorization smoke coverage.
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

The static script is not a SQL parser, penetration test, or proof that RLS behaves correctly. This sandbox lacks a complete local Android/Gradle runtime and cannot reliably download signed artifacts, so Android build results are taken only from observed GitHub Actions runs. Current code commit `42c196cac4e21ab1282094624b0baa922faf0c6f` passed static checks, all JVM tests, `assembleDebug`, generated-`BuildConfig` verification, and artifact upload in run `40159571594`; artifact `12586620126` is version `0.3.0-phase2` / versionCode `4`. Phase 2 migrations, grant/RLS/constraint inspection, and the expanded rollback-only SQL smoke transaction have also run. Real-JWT API and installed-device tests remain open.

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

Also test password recovery redirect, PKCE callback, refresh after process restart, local and remote sign-out, and logs after a deliberately invalid Auth request. On Android, verify valid and reversed PDF rendering, Bengali text, document-picker cancellation/success, share-sheet permission grants, and that no arbitrary cache file is exposed through the receipt `FileProvider`. See `SECURITY.md` for unresolved token-log verification and `PHASE2_FINANCE.md` for the executed rollback-only smoke scope.
