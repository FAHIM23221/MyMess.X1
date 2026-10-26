#!/usr/bin/env python3
"""Verify the generated debug BuildConfig without printing client configuration values."""
from __future__ import annotations

import re
import sys
from pathlib import Path
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parents[1]
candidates = sorted(
    (ROOT / "app/build/generated/source/buildConfig/debug").rglob("BuildConfig.java")
)
if not candidates:
    print("Generated debug BuildConfig.java was not found; run assembleDebug first.", file=sys.stderr)
    sys.exit(1)

text = candidates[0].read_text(encoding="utf-8")
values: dict[str, str] = {}
for name in ("SUPABASE_URL", "SUPABASE_PUBLISHABLE_KEY"):
    match = re.search(
        rf'public\s+static\s+final\s+String\s+{name}\s*=\s*"([^"]*)"\s*;',
        text,
    )
    if not match:
        print(f"Generated BuildConfig is missing {name}.", file=sys.stderr)
        sys.exit(1)
    values[name] = match.group(1).strip()

url = values["SUPABASE_URL"]
key = values["SUPABASE_PUBLISHABLE_KEY"]
parsed = urlparse(url)
errors: list[str] = []
if parsed.scheme != "https" or not parsed.hostname or not parsed.hostname.endswith(".supabase.co"):
    errors.append("Debug BuildConfig does not contain a valid HTTPS Supabase project URL")
if not key.startswith("sb_publishable_") or len(key) <= len("sb_publishable_") + 16:
    errors.append("Debug BuildConfig does not contain a publishable Supabase key")
if "sb_secret_" in key or "service_role" in key.lower():
    errors.append("Debug BuildConfig contains a forbidden privileged key pattern")

if errors:
    for error in errors:
        print(error, file=sys.stderr)
    sys.exit(1)

print(
    "Built debug configuration verified: HTTPS Supabase URL and publishable key "
    "are present; no privileged key pattern was found."
)
