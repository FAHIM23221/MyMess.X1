-- Phase 1 foundation for one shared mess.
-- Run only in a new Supabase project after reviewing the migration.
-- No service-role key or other secret belongs in this file or in the Android app.

begin;

create extension if not exists pgcrypto;

do $$
begin
    create type public.app_role as enum ('MEMBER', 'ADMIN', 'MAIN_ADMIN');
exception when duplicate_object then null;
end $$;

do $$
begin
    create type public.account_status as enum ('ACTIVE', 'PENDING', 'SUSPENDED');
exception when duplicate_object then null;
end $$;

create table if not exists public.messes (
    id uuid primary key default gen_random_uuid(),
    name text not null check (length(btrim(name)) between 2 and 120),
    time_zone text not null default 'Asia/Dhaka',
    created_at timestamptz not null default now()
);

create table if not exists public.profiles (
    user_id uuid primary key references auth.users(id) on delete restrict,
    mess_id uuid not null references public.messes(id) on delete restrict,
    member_id text null check (member_id is null or length(btrim(member_id)) between 1 and 50),
    full_name text not null check (length(btrim(full_name)) between 2 and 80),
    phone_number text null check (
        phone_number is null or (
            length(phone_number) between 7 and 24
            and phone_number ~ '^[0-9০-৯+() -]+$'
            and length(regexp_replace(phone_number, '[^0-9০-৯]', '', 'g')) >= 7
        )
    ),
    room_number text null check (room_number is null or length(btrim(room_number)) between 1 and 20),
    seat_number text null check (seat_number is null or length(btrim(seat_number)) between 1 and 20),
    join_date date null,
    -- MAIN_ADMIN is derived from main_admin_assignments and cannot be set here.
    role public.app_role not null default 'MEMBER',
    account_status public.account_status not null default 'PENDING',
    is_current_manager boolean not null default false,
    avatar_path text null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint profiles_role_not_main_admin check (role <> 'MAIN_ADMIN'),
    constraint profiles_user_mess_unique unique (user_id, mess_id),
    constraint profiles_member_id_unique_per_mess unique (mess_id, member_id)
);

-- A single assignment row designates the only Main Admin for this one-mess app.
-- singleton_key + mess_id uniqueness allow at most one assignment; a deferred
-- constraint trigger below requires exactly one assignment for every mess at commit.
create table if not exists public.main_admin_assignments (
    singleton_key boolean primary key default true check (singleton_key is true),
    mess_id uuid not null unique references public.messes(id) on delete restrict,
    user_id uuid not null unique,
    assigned_at timestamptz not null default now(),
    assigned_by uuid null references auth.users(id) on delete set null,
    constraint main_admin_profile_same_mess_fk
        foreign key (user_id, mess_id)
        references public.profiles(user_id, mess_id)
        on delete restrict
);

create table if not exists public.audit_logs (
    id uuid primary key default gen_random_uuid(),
    mess_id uuid not null references public.messes(id) on delete restrict,
    actor_user_id uuid null,
    actor_type text not null default 'USER' check (actor_type in ('USER', 'PROJECT_OWNER_RECOVERY')),
    action text not null check (length(btrim(action)) between 2 and 100),
    target_user_id uuid null,
    reason text null,
    details jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    constraint audit_actor_consistency check (
        (actor_type = 'USER' and actor_user_id is not null)
        or (actor_type = 'PROJECT_OWNER_RECOVERY' and actor_user_id is null)
    )
);

create index if not exists profiles_mess_status_idx
    on public.profiles(mess_id, account_status);
create index if not exists audit_logs_mess_created_idx
    on public.audit_logs(mess_id, created_at desc);

create or replace function public.set_updated_at()
returns trigger
language plpgsql
set search_path = public, pg_temp
as $$
begin
    new.updated_at := now();
    return new;
end;
$$;

drop trigger if exists profiles_set_updated_at on public.profiles;
create trigger profiles_set_updated_at
before update on public.profiles
for each row execute function public.set_updated_at();

create or replace function public.current_mess_id()
returns uuid
language sql
stable
security definer
set search_path = public, pg_temp
as $$
    select p.mess_id
    from public.profiles p
    where p.user_id = auth.uid()
    limit 1
$$;

create or replace function public.is_active_user()
returns boolean
language sql
stable
security definer
set search_path = public, pg_temp
as $$
    select exists (
        select 1
        from public.profiles p
        where p.user_id = auth.uid()
          and p.account_status = 'ACTIVE'
    )
$$;

create or replace function public.current_app_role()
returns public.app_role
language sql
stable
security definer
set search_path = public, pg_temp
as $$
    select case
        when exists (
            select 1
            from public.main_admin_assignments a
            where a.user_id = p.user_id
              and a.mess_id = p.mess_id
        ) then 'MAIN_ADMIN'::public.app_role
        else p.role
    end
    from public.profiles p
    where p.user_id = auth.uid()
    limit 1
$$;

create or replace function public.is_admin_for_mess(target_mess_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public, pg_temp
as $$
    select public.is_active_user()
       and public.current_mess_id() = target_mess_id
       and public.current_app_role() in ('ADMIN'::public.app_role, 'MAIN_ADMIN'::public.app_role)
$$;

-- Fail closed: a mess cannot be committed without an active Main Admin assignment.
create or replace function public.require_main_admin_assignment()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
    if not exists (
        select 1
        from public.main_admin_assignments a
        join public.profiles p
          on p.user_id = a.user_id and p.mess_id = a.mess_id
        where a.mess_id = new.id
          and p.account_status = 'ACTIVE'
    ) then
        raise exception 'Every mess must have exactly one active Main Admin assignment';
    end if;
    return null;
end;
$$;

drop trigger if exists mess_requires_main_admin_at_commit on public.messes;
create constraint trigger mess_requires_main_admin_at_commit
after insert on public.messes
deferrable initially deferred
for each row execute function public.require_main_admin_assignment();

create or replace function public.require_active_main_admin_assignment()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    target_status public.account_status;
begin
    select p.account_status
      into target_status
      from public.profiles p
     where p.user_id = new.user_id
       and p.mess_id = new.mess_id;

    if target_status is distinct from 'ACTIVE'::public.account_status then
        raise exception 'The designated Main Admin must have an active profile';
    end if;
    return new;
end;
$$;

drop trigger if exists main_admin_assignment_requires_active_profile on public.main_admin_assignments;
create trigger main_admin_assignment_requires_active_profile
before insert or update of user_id, mess_id on public.main_admin_assignments
for each row execute function public.require_active_main_admin_assignment();

create or replace function public.prevent_main_admin_assignment_delete()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
    raise exception 'Main Admin assignment cannot be deleted; use a controlled transfer';
end;
$$;

drop trigger if exists main_admin_assignment_no_delete on public.main_admin_assignments;
create trigger main_admin_assignment_no_delete
before delete on public.main_admin_assignments
for each row execute function public.prevent_main_admin_assignment_delete();

create or replace function public.prevent_main_admin_suspension()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
    if new.account_status <> 'ACTIVE'
       and exists (
           select 1 from public.main_admin_assignments a
           where a.user_id = old.user_id and a.mess_id = old.mess_id
       ) then
        raise exception 'Transfer the Main Admin assignment before suspending that account';
    end if;
    return new;
end;
$$;

drop trigger if exists profiles_protect_active_main_admin on public.profiles;
create trigger profiles_protect_active_main_admin
before update of account_status on public.profiles
for each row execute function public.prevent_main_admin_suspension();

-- The Main Admin may link an already-created, confirmed Auth user to a profile.
-- Auth user creation remains an operator/Auth Dashboard task; no client service key is used.
create or replace function public.provision_member_profile(
    target_user_id uuid,
    new_member_id text,
    new_full_name text,
    provision_reason text,
    new_phone_number text default null,
    new_room_number text default null,
    new_seat_number text default null,
    new_join_date date default null
)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    actor_mess_id uuid;
begin
    if auth.uid() is null or not public.is_active_user()
       or public.current_app_role() <> 'MAIN_ADMIN'::public.app_role then
        raise exception 'Only the active Main Admin may provision a profile';
    end if;
    if length(btrim(coalesce(provision_reason, ''))) < 8 then
        raise exception 'A reason of at least 8 characters is required';
    end if;
    if length(btrim(coalesce(new_member_id, ''))) not between 1 and 50 then
        raise exception 'Member ID must contain 1 to 50 characters';
    end if;
    if length(btrim(coalesce(new_full_name, ''))) not between 2 and 80 then
        raise exception 'Full name must contain 2 to 80 characters';
    end if;

    actor_mess_id := public.current_mess_id();
    if not exists (
        select 1 from auth.users u
        where u.id = target_user_id
          and u.email_confirmed_at is not null
    ) then
        raise exception 'Create and confirm the Auth user before provisioning a profile';
    end if;
    if exists (select 1 from public.profiles p where p.user_id = target_user_id) then
        raise exception 'A profile already exists for this Auth user';
    end if;

    insert into public.profiles(
        user_id, mess_id, member_id, full_name, phone_number,
        room_number, seat_number, join_date, role, account_status
    ) values (
        target_user_id, actor_mess_id, btrim(new_member_id), btrim(new_full_name),
        nullif(btrim(coalesce(new_phone_number, '')), ''),
        nullif(btrim(coalesce(new_room_number, '')), ''),
        nullif(btrim(coalesce(new_seat_number, '')), ''),
        new_join_date, 'MEMBER'::public.app_role, 'ACTIVE'::public.account_status
    );

    insert into public.audit_logs(mess_id, actor_user_id, action, target_user_id, reason, details)
    values (
        actor_mess_id, auth.uid(), 'MEMBER_PROFILE_PROVISIONED', target_user_id,
        btrim(provision_reason), jsonb_build_object('role', 'MEMBER')
    );
end;
$$;

-- Account status changes are server-side only and audited. The Main Admin cannot
-- suspend their own account; the active assignment is independently protected by a trigger.
create or replace function public.set_profile_status(
    target_user_id uuid,
    new_status public.account_status,
    change_reason text
)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    actor_mess_id uuid;
begin
    if auth.uid() is null or not public.is_active_user()
       or public.current_app_role() <> 'MAIN_ADMIN'::public.app_role then
        raise exception 'Only the active Main Admin may change account status';
    end if;
    if length(btrim(coalesce(change_reason, ''))) < 8 then
        raise exception 'A reason of at least 8 characters is required';
    end if;
    if target_user_id = auth.uid() then
        raise exception 'You cannot change your own account status';
    end if;
    if new_status is null then
        raise exception 'Account status is required';
    end if;

    actor_mess_id := public.current_mess_id();
    if not exists (
        select 1 from public.profiles p
        where p.user_id = target_user_id and p.mess_id = actor_mess_id
    ) then
        raise exception 'Target profile does not belong to this mess';
    end if;
    if exists (
        select 1 from public.main_admin_assignments a
        where a.user_id = target_user_id
    ) then
        raise exception 'Transfer the Main Admin assignment before changing this status';
    end if;

    update public.profiles
       set account_status = new_status
     where user_id = target_user_id and mess_id = actor_mess_id;

    insert into public.audit_logs(mess_id, actor_user_id, action, target_user_id, reason, details)
    values (
        actor_mess_id, auth.uid(), 'PROFILE_STATUS_CHANGED', target_user_id,
        btrim(change_reason), jsonb_build_object('new_status', new_status::text)
    );
end;
$$;

-- Only the current Main Admin can assign MEMBER/ADMIN roles. MAIN_ADMIN is
-- transferred separately so there is never a client path to promote itself.
create or replace function public.set_profile_role(
    target_user_id uuid,
    new_role public.app_role,
    change_reason text
)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    actor_mess_id uuid;
begin
    if auth.uid() is null then
        raise exception 'Authentication required';
    end if;
    if length(btrim(coalesce(change_reason, ''))) < 8 then
        raise exception 'A reason of at least 8 characters is required';
    end if;
    if new_role is null
       or new_role not in ('MEMBER'::public.app_role, 'ADMIN'::public.app_role) then
        raise exception 'Use the dedicated transfer operation for Main Admin';
    end if;
    if not public.is_active_user()
       or public.current_app_role() <> 'MAIN_ADMIN'::public.app_role then
        raise exception 'Only the active Main Admin may change roles';
    end if;
    if target_user_id = auth.uid() then
        raise exception 'You cannot change your own role';
    end if;

    actor_mess_id := public.current_mess_id();
    if not exists (
        select 1 from public.profiles p
        where p.user_id = target_user_id
          and p.mess_id = actor_mess_id
          and p.account_status = 'ACTIVE'
    ) then
        raise exception 'Target must be an active profile in this mess';
    end if;
    if exists (
        select 1 from public.main_admin_assignments a
        where a.user_id = target_user_id
    ) then
        raise exception 'Use the dedicated transfer operation for the Main Admin';
    end if;

    update public.profiles
       set role = new_role
     where user_id = target_user_id
       and mess_id = actor_mess_id;

    insert into public.audit_logs(mess_id, actor_user_id, action, target_user_id, reason, details)
    values (
        actor_mess_id,
        auth.uid(),
        'PROFILE_ROLE_CHANGED',
        target_user_id,
        btrim(change_reason),
        jsonb_build_object('new_role', new_role::text)
    );
end;
$$;

-- Transfers the unique assignment row atomically. The previous Main Admin is
-- retained as an ADMIN, the target must already be an active profile, and the
-- assignment row is updated rather than deleted/recreated.
create or replace function public.transfer_main_admin(
    new_main_admin_user_id uuid,
    transfer_reason text
)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    actor_mess_id uuid;
    existing_main_admin_id uuid;
begin
    if auth.uid() is null then
        raise exception 'Authentication required';
    end if;
    if length(btrim(coalesce(transfer_reason, ''))) < 8 then
        raise exception 'A reason of at least 8 characters is required';
    end if;

    actor_mess_id := public.current_mess_id();
    if actor_mess_id is null or not public.is_active_user()
       or public.current_app_role() <> 'MAIN_ADMIN'::public.app_role then
        raise exception 'Only the active Main Admin may transfer the assignment';
    end if;

    select a.user_id
      into existing_main_admin_id
      from public.main_admin_assignments a
     where a.singleton_key is true
       and a.mess_id = actor_mess_id
     for update;

    if existing_main_admin_id is distinct from auth.uid() then
        raise exception 'Current user is not the designated Main Admin';
    end if;
    if new_main_admin_user_id = auth.uid() then
        raise exception 'Choose a different active profile for transfer';
    end if;
    if not exists (
        select 1 from public.profiles p
        where p.user_id = new_main_admin_user_id
          and p.mess_id = actor_mess_id
          and p.account_status = 'ACTIVE'
    ) then
        raise exception 'The new Main Admin must be an active profile in this mess';
    end if;

    update public.profiles
       set role = 'ADMIN'::public.app_role
     where user_id = auth.uid()
       and mess_id = actor_mess_id;

    update public.main_admin_assignments
       set user_id = new_main_admin_user_id,
           assigned_by = auth.uid(),
           assigned_at = now()
     where singleton_key is true
       and mess_id = actor_mess_id;

    insert into public.audit_logs(mess_id, actor_user_id, action, target_user_id, reason, details)
    values (
        actor_mess_id,
        auth.uid(),
        'MAIN_ADMIN_TRANSFERRED',
        new_main_admin_user_id,
        btrim(transfer_reason),
        jsonb_build_object('previous_main_admin_user_id', auth.uid())
    );
end;
$$;

-- Owner-only recovery for a lost/compromised Main Admin. No PostgREST role,
-- including service_role, receives EXECUTE. Run only in an authenticated, reviewed
-- project-owner SQL session with an external approval/ticket reference.
create or replace function public.recover_main_admin_owner_only(
    expected_old_main_admin_user_id uuid,
    new_main_admin_user_id uuid,
    recovery_reason text,
    approval_reference text
)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    current_main_admin_id uuid;
    target_mess_id uuid;
begin
    if length(btrim(coalesce(recovery_reason, ''))) < 8 then
        raise exception 'A recovery reason of at least 8 characters is required';
    end if;
    if length(btrim(coalesce(approval_reference, ''))) < 8 then
        raise exception 'An external approval/ticket reference is required';
    end if;

    select a.user_id, a.mess_id
      into current_main_admin_id, target_mess_id
      from public.main_admin_assignments a
     where a.singleton_key is true
     for update;

    if current_main_admin_id is null or target_mess_id is null then
        raise exception 'No Main Admin assignment exists; stop and use the reviewed recovery procedure';
    end if;
    if current_main_admin_id <> expected_old_main_admin_user_id then
        raise exception 'Expected current Main Admin does not match; no change was made';
    end if;
    if new_main_admin_user_id = current_main_admin_id then
        raise exception 'Recovery target must differ from the current Main Admin';
    end if;
    if not exists (
        select 1
        from public.profiles p
        join auth.users u on u.id = p.user_id
        where p.user_id = new_main_admin_user_id
          and p.mess_id = target_mess_id
          and p.account_status = 'ACTIVE'
          and u.email_confirmed_at is not null
    ) then
        raise exception 'Recovery target must be a confirmed Auth user with an active same-mess profile';
    end if;

    update public.main_admin_assignments
       set user_id = new_main_admin_user_id,
           assigned_by = null,
           assigned_at = now()
     where singleton_key is true
       and mess_id = target_mess_id;

    -- Suspend the old account after transferring the assignment so the normal
    -- guard does not leave a compromised/locked-out account active as Admin.
    update public.profiles
       set role = 'ADMIN'::public.app_role,
           account_status = 'SUSPENDED'
     where user_id = current_main_admin_id
       and mess_id = target_mess_id;

    insert into public.audit_logs(
        mess_id, actor_user_id, actor_type, action, target_user_id, reason, details
    ) values (
        target_mess_id,
        null,
        'PROJECT_OWNER_RECOVERY',
        'MAIN_ADMIN_RECOVERED',
        new_main_admin_user_id,
        btrim(recovery_reason),
        jsonb_build_object(
            'previous_main_admin_user_id', current_main_admin_id,
            'approval_reference', btrim(approval_reference)
        )
    );
end;
$$;

-- This read-only view exposes only the caller's own profile and derives
-- MAIN_ADMIN from the protected assignment table.
create or replace view public.my_profile
with (security_invoker = true)
as
select
    p.user_id,
    p.mess_id,
    p.member_id,
    p.full_name,
    p.phone_number,
    p.room_number,
    p.seat_number,
    p.join_date,
    public.current_app_role() as role,
    p.account_status,
    p.is_current_manager,
    p.avatar_path,
    p.created_at,
    p.updated_at
from public.profiles p
where p.user_id = auth.uid();

alter table public.messes enable row level security;
alter table public.profiles enable row level security;
alter table public.main_admin_assignments enable row level security;
alter table public.audit_logs enable row level security;

-- No anonymous access. Keep authenticated grants deliberately narrow.
revoke all on public.messes from anon, authenticated;
revoke all on public.profiles from anon, authenticated;
revoke all on public.main_admin_assignments from anon, authenticated;
revoke all on public.audit_logs from anon, authenticated;
revoke all on public.my_profile from anon, authenticated;

revoke create on schema public from public, anon, authenticated;
grant usage on schema public to authenticated;
grant select on public.messes to authenticated;
grant select on public.profiles to authenticated;
grant update (full_name, phone_number) on public.profiles to authenticated;
grant select on public.my_profile to authenticated;
grant select on public.audit_logs to authenticated;

create policy messes_select_own_mess
on public.messes for select to authenticated
using (id = public.current_mess_id() and public.is_active_user());

create policy profiles_select_own_or_admin
on public.profiles for select to authenticated
using (
    user_id = auth.uid()
    or public.is_admin_for_mess(mess_id)
);

create policy profiles_update_permitted_fields
on public.profiles for update to authenticated
using (user_id = auth.uid() and public.is_active_user())
with check (user_id = auth.uid() and public.is_active_user());

create policy audit_logs_select_main_admin_only
on public.audit_logs for select to authenticated
using (
    mess_id = public.current_mess_id()
    and public.is_active_user()
    and public.current_app_role() = 'MAIN_ADMIN'::public.app_role
);

grant usage on type public.app_role, public.account_status to authenticated;

-- SECURITY DEFINER functions are not callable by PUBLIC/anon. Authenticated
-- users can call reviewed RPCs/read helpers; trigger-only routines below are
-- granted for trigger execution and cannot be invoked as ordinary SQL functions.
revoke all on function public.set_updated_at() from public, anon;
revoke all on function public.require_main_admin_assignment() from public, anon;
revoke all on function public.require_active_main_admin_assignment() from public, anon;
revoke all on function public.prevent_main_admin_assignment_delete() from public, anon;
revoke all on function public.prevent_main_admin_suspension() from public, anon;
-- Trigger execution permissions vary by context across tooling; authenticated
-- may execute these trigger-only routines, but cannot call them as ordinary SQL.
grant execute on function public.set_updated_at() to authenticated;
grant execute on function public.require_main_admin_assignment() to authenticated;
grant execute on function public.require_active_main_admin_assignment() to authenticated;
grant execute on function public.prevent_main_admin_assignment_delete() to authenticated;
grant execute on function public.prevent_main_admin_suspension() to authenticated;
revoke all on function public.current_mess_id() from public, anon;
revoke all on function public.is_active_user() from public, anon;
revoke all on function public.current_app_role() from public, anon;
revoke all on function public.is_admin_for_mess(uuid) from public, anon;
revoke all on function public.provision_member_profile(uuid, text, text, text, text, text, text, date) from public, anon;
revoke all on function public.set_profile_status(uuid, public.account_status, text) from public, anon;
revoke all on function public.set_profile_role(uuid, public.app_role, text) from public, anon;
revoke all on function public.transfer_main_admin(uuid, text) from public, anon;
revoke all on function public.recover_main_admin_owner_only(uuid, uuid, text, text) from public, anon, authenticated, service_role;
grant execute on function public.current_mess_id() to authenticated;
grant execute on function public.is_active_user() to authenticated;
grant execute on function public.current_app_role() to authenticated;
grant execute on function public.is_admin_for_mess(uuid) to authenticated;
grant execute on function public.provision_member_profile(uuid, text, text, text, text, text, text, date) to authenticated;
grant execute on function public.set_profile_status(uuid, public.account_status, text) to authenticated;
grant execute on function public.set_profile_role(uuid, public.app_role, text) to authenticated;
grant execute on function public.transfer_main_admin(uuid, text) to authenticated;

commit;
