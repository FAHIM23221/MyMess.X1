#!/usr/bin/env python3
"""Dependency-free static checks for the Android and Supabase project bundle."""
from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
errors: list[str] = []


def require(condition: bool, message: str) -> None:
    if not condition:
        errors.append(message)


def read(path: str) -> str:
    target = ROOT / path
    if not target.is_file():
        errors.append(f"Missing required file: {path}")
        return ""
    return target.read_text(encoding="utf-8")


# Parse the Android resource XML and verify every code reference resolves in both locales.
resource_files = {
    "en": ROOT / "app/src/main/res/values/strings.xml",
    "bn": ROOT / "app/src/main/res/values-bn/strings.xml",
}
resources: dict[str, dict[str, str]] = {}
for locale, path in resource_files.items():
    try:
        tree = ET.parse(path)
        resources[locale] = {
            element.attrib["name"]: (element.text or "")
            for element in tree.getroot().findall("string")
            if "name" in element.attrib
        }
    except (ET.ParseError, OSError) as exc:
        errors.append(f"Could not parse {path.relative_to(ROOT)}: {exc}")
        resources[locale] = {}

source_root = ROOT / "app/src/main/java"
string_refs: set[str] = set()
if source_root.is_dir():
    for source in source_root.rglob("*.kt"):
        string_refs.update(re.findall(r"R\.string\.([A-Za-z0-9_]+)", source.read_text(encoding="utf-8")))
for locale in ("en", "bn"):
    missing = sorted(string_refs - resources.get(locale, {}).keys())
    require(not missing, f"{locale} strings missing code references: {', '.join(missing)}")

missing_bengali = sorted(resources.get("en", {}).keys() - resources.get("bn", {}).keys())
require(not missing_bengali, f"Bengali resource entries missing: {', '.join(missing_bengali)}")
for key in resources.get("en", {}).keys() & resources.get("bn", {}).keys():
    en_args = re.findall(r"%\d+\$[sdf]", resources["en"][key])
    bn_args = re.findall(r"%\d+\$[sdf]", resources["bn"][key])
    require(en_args == bn_args, f"String format placeholders differ for {key}")

# Parse all XML resources used by the app so malformed XML fails before Android build.
for xml_path in (ROOT / "app/src/main/res").rglob("*.xml"):
    try:
        ET.parse(xml_path)
    except ET.ParseError as exc:
        errors.append(f"Malformed XML {xml_path.relative_to(ROOT)}: {exc}")

# Guardrails that are cheap to check without a live PostgreSQL/Supabase project.
sql = read("supabase/migrations/0001_phase1_foundation.sql").lower()
for fragment in (
    "alter table public.profiles enable row level security",
    "alter table public.messes enable row level security",
    "alter table public.main_admin_assignments enable row level security",
    "alter table public.audit_logs enable row level security",
    "constraint profiles_role_not_main_admin check (role <> 'main_admin')",
    "grant update (full_name, phone_number) on public.profiles to authenticated",
    "create or replace function public.provision_member_profile(",
    "create or replace function public.set_profile_status(",
    "create or replace function public.transfer_main_admin(",
    "create or replace function public.recover_main_admin_owner_only(",
    "revoke all on function public.recover_main_admin_owner_only(uuid, uuid, text, text) from public, anon, authenticated, service_role",
    "create or replace function public.set_profile_role(",
    "create policy audit_logs_select_main_admin_only",
    "revoke all on function public.transfer_main_admin(uuid, text) from public, anon",
    "grant execute on function public.transfer_main_admin(uuid, text) to authenticated",
    "main_admin_assignment_no_delete",
    "require_active_main_admin_assignment",
):
    require(fragment in sql, f"SQL security contract missing: {fragment}")

update_policy = sql.split("create policy profiles_update_permitted_fields", maxsplit=1)[-1].split(";", maxsplit=1)[0]
require("user_id = auth.uid()" in update_policy, "Profile update policy must be restricted to the authenticated user's own row")
require("is_admin_for_mess" not in update_policy, "Direct profile updates must not let Admin change other users without an audited RPC")

phase2_sql = read("supabase/migrations/0002_phase2_billing_payments.sql").lower()
for fragment in (
    "create table public.billing_periods",
    "create table public.charges",
    "create table public.billing_adjustments",
    "create table public.payments",
    "create table public.receipts",
    "create table public.payment_reversals",
    "alter table public.payments enable row level security",
    "alter table public.receipts enable row level security",
    "alter table public.payment_reversals enable row level security",
    "create view public.my_financial_summary",
    "create view public.my_payment_receipts",
    "security_invoker = true",
    "create or replace function public.record_payment(",
    "create or replace function public.reverse_payment(",
    "create or replace function public.carry_forward_balance(",
    "unique (mess_id, client_request_id)",
    "one_carry_forward_per_member_period",
):
    require(fragment in phase2_sql, f"Phase 2 SQL contract missing: {fragment}")
require("disable row level security" not in phase2_sql, "Phase 2 must never disable RLS")
require(phase2_sql.count("amount_minor bigint") >= 4, "Ledger amounts must use integer minor units")
require("double precision" not in phase2_sql and "real," not in phase2_sql, "Ledger must not use floating-point money")

view_grants_sql = read("supabase/migrations/0003_phase2_view_grants.sql").lower()
for view_name in ("my_financial_summary", "my_payment_receipts"):
    require(
        f"revoke all on public.{view_name} from public, anon, authenticated" in view_grants_sql,
        f"View default privileges must be revoked for {view_name}",
    )
    require(
        f"grant select on public.{view_name} to authenticated" in view_grants_sql,
        f"View must be SELECT-only for {view_name}",
    )

smoke_sql = read("supabase/tests/phase2_smoke_transaction.sql").lower()
require("begin;" in smoke_sql and "rollback;" in smoke_sql, "Phase 2 smoke test must be rollback-only")
require("duplicate payment request id was accepted" in smoke_sql, "Smoke test must cover duplicate payments")
require("advance-credit carry-forward failed" in smoke_sql, "Smoke test must cover advance carry-forward")

profile_dto = read("app/src/main/java/com/smarttransparentmess/erp/data/profile/ProfileDto.kt")
update_dto = profile_dto.split("data class ProfileUpdateDto(", maxsplit=1)[-1]
require("role" not in update_dto.lower(), "Client profile-update DTO must not contain a role field")

app_gradle = read("app/build.gradle.kts")
require("containsServiceRoleCredential" in app_gradle, "Build-time privileged-key rejection is missing")
require("sb_secret_" in app_gradle and "service_role" in app_gradle, "Build does not reject privileged Supabase key formats")
require("config/supabase.public.properties" in app_gradle, "Bundled public configuration fallback is missing")

public_config_text = read("config/supabase.public.properties")
public_config = dict(
    line.split("=", maxsplit=1)
    for line in public_config_text.splitlines()
    if line.strip() and not line.lstrip().startswith("#") and "=" in line
)
require(set(public_config) == {"SUPABASE_URL", "SUPABASE_PUBLISHABLE_KEY"}, "Public config must contain exactly the two Android client values")
require(
    public_config.get("SUPABASE_URL") == "https://iwoiwgkaevobxqojyphb.supabase.co",
    "Public config must target the reviewed mymess-x1-phase1 project",
)
require(
    re.fullmatch(r"sb_publishable_[A-Za-z0-9_-]{20,}", public_config.get("SUPABASE_PUBLISHABLE_KEY", "")) is not None,
    "Public config must contain only a publishable Android key",
)

client_provider = read("app/src/main/java/com/smarttransparentmess/erp/data/security/SupabaseClientProvider.kt")
manifest = read("app/src/main/AndroidManifest.xml")
require("LogLevel.NONE" in client_provider, "Supabase SDK logging should remain disabled pending token-log verification")
require("AndroidEncryptedAuthStorage" in client_provider, "Supabase Auth must use encrypted session storage")
require('android:allowBackup="false"' in manifest, "Android backup must remain disabled for encrypted session data")

workflow = read(".github/workflows/android-debug.yml")
require("assembleDebug" in workflow and "testDebugUnitTest" in workflow, "Workflow must test and build a debug APK")
require("scripts/verify_built_config.py" in workflow, "Workflow must verify generated public Supabase configuration")
require((ROOT / "scripts/verify_built_config.py").is_file(), "Built-config verification script is missing")
require("actions/upload-artifact@v4" in workflow, "Workflow must upload an APK artifact")
require("workflow_dispatch:" in workflow, "Workflow must be manually runnable")
require("create-release" not in workflow.lower() and "gh release" not in workflow.lower(), "Workflow must not auto-publish releases")

require((ROOT / "gradle/wrapper/gradle-wrapper.properties").is_file(), "Gradle wrapper properties are missing")
require((ROOT / "gradlew").is_file(), "Gradle wrapper script is missing")
require((ROOT / ".github/workflows/android-debug.yml").is_file(), "GitHub Actions workflow is missing")

if errors:
    print("Static project checks failed:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    sys.exit(1)

print(
    "Static project checks passed: XML/resources, bilingual strings, "
    "role-update boundary, Supabase RLS guardrails, wrapper, and debug workflow."
)
