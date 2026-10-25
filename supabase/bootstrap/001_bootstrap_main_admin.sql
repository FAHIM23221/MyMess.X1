-- ONE-TIME bootstrap for a new Phase 1 Supabase project.
-- Replace all three REPLACE_* values with the exact values you chose in Supabase Auth.
-- Run after 0001_phase1_foundation.sql. Never put a service key in this file.
-- The transaction fails closed if the auth account is missing, unconfirmed, or already profiled.

do $bootstrap$
declare
    admin_email text := 'REPLACE_WITH_CONFIRMED_ADMIN_EMAIL';
    admin_full_name text := 'REPLACE_WITH_MAIN_ADMIN_FULL_NAME';
    mess_name text := 'REPLACE_WITH_MESS_NAME';
    admin_user_id uuid;
    new_mess_id uuid;
begin
    if admin_email like 'REPLACE_%'
       or admin_full_name like 'REPLACE_%'
       or mess_name like 'REPLACE_%' then
        raise exception 'Edit all REPLACE_* values before running this bootstrap';
    end if;

    if exists (select 1 from public.main_admin_assignments) then
        raise exception 'A Main Admin assignment already exists; do not rerun bootstrap';
    end if;

    select u.id
      into admin_user_id
      from auth.users u
     where lower(u.email) = lower(btrim(admin_email))
       and u.email_confirmed_at is not null;

    if admin_user_id is null then
        raise exception 'Create and confirm the designated Auth user before bootstrap';
    end if;
    if exists (select 1 from public.profiles p where p.user_id = admin_user_id) then
        raise exception 'A profile already exists for this Auth user; review before proceeding';
    end if;

    insert into public.messes(name, time_zone)
    values (btrim(mess_name), 'Asia/Dhaka')
    returning id into new_mess_id;

    insert into public.profiles(
        user_id,
        mess_id,
        member_id,
        full_name,
        role,
        account_status,
        is_current_manager
    ) values (
        admin_user_id,
        new_mess_id,
        null,
        btrim(admin_full_name),
        'MEMBER'::public.app_role,
        'ACTIVE'::public.account_status,
        false
    );

    insert into public.main_admin_assignments(
        singleton_key,
        mess_id,
        user_id,
        assigned_by
    ) values (
        true,
        new_mess_id,
        admin_user_id,
        admin_user_id
    );

    insert into public.audit_logs(
        mess_id,
        actor_user_id,
        action,
        target_user_id,
        reason,
        details
    ) values (
        new_mess_id,
        admin_user_id,
        'MAIN_ADMIN_BOOTSTRAPPED',
        admin_user_id,
        'Initial owner bootstrap',
        jsonb_build_object('bootstrap', 'operator-run-once')
    );
end;
$bootstrap$;
