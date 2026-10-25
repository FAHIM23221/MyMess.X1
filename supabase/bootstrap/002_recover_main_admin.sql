-- Owner-only, one-time recovery call for a lost/compromised Main Admin.
-- Do not run this as a normal Member/Admin, from the Android app, or through PostgREST.
-- Before use, verify authority and approvals out of band, identify the current
-- and replacement users, and retain the approval/ticket record securely.
-- The replacement Auth user must already be email-confirmed and have an ACTIVE
-- profile in this mess. The old account is transferred to ADMIN then SUSPENDED.
-- Replace every value below in SQL Editor only; do not commit real IDs/reasons.

select public.recover_main_admin_owner_only(
    'REPLACE_WITH_EXPECTED_OLD_MAIN_ADMIN_USER_UUID'::uuid,
    'REPLACE_WITH_NEW_ACTIVE_MAIN_ADMIN_USER_UUID'::uuid,
    'REPLACE_WITH_RECOVERY_REASON_AT_LEAST_8_CHARACTERS',
    'REPLACE_WITH_EXTERNAL_APPROVAL_OR_TICKET_REFERENCE'
);
