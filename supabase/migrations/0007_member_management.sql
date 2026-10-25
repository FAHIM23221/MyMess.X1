begin;

alter table public.profiles
  add column departed_at timestamptz,
  add column departure_reason text;

alter table public.profiles
  add constraint profiles_departure_reason_check
  check (departure_reason is null or char_length(btrim(departure_reason)) between 3 and 500),
  add constraint profiles_departure_consistency_check
  check (
    (departed_at is null and departure_reason is null)
    or (departed_at is not null and account_status = 'SUSPENDED')
  );

comment on column public.profiles.departed_at is
  'Archive/departure timestamp. DEPARTED is derived from this value while account_status remains SUSPENDED for authorization.';
comment on column public.profiles.departure_reason is
  'Audited departure reason; retained financial rows are never deleted by departure.';

create table public.member_invitations (
  id uuid primary key default gen_random_uuid(),
  mess_id uuid not null references public.messes(id) on delete restrict,
  email text not null,
  full_name text not null,
  phone_number text not null,
  member_id text not null,
  join_date date not null,
  intended_role public.app_role not null,
  status text not null default 'PENDING',
  delivery_status text not null default 'PENDING',
  auth_user_id uuid references auth.users(id) on delete restrict,
  invited_by uuid not null references public.profiles(user_id) on delete restrict,
  client_request_id uuid not null,
  delivery_attempts integer not null default 0,
  last_sent_at timestamptz,
  last_delivery_attempt_at timestamptz,
  last_delivery_error text,
  accepted_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint member_invitations_email_check check (
    char_length(email) between 3 and 320
    and email = lower(btrim(email))
    and email ~ '^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$'
  ),
  constraint member_invitations_full_name_check check (char_length(btrim(full_name)) between 2 and 80),
  constraint member_invitations_phone_check check (phone_number ~ '^[0-9+() -]{7,20}$'),
  constraint member_invitations_member_id_check check (char_length(btrim(member_id)) between 1 and 50),
  constraint member_invitations_role_check check (intended_role in ('MEMBER', 'ADMIN')),
  constraint member_invitations_status_check check (status in ('PENDING', 'ACCEPTED', 'CANCELLED')),
  constraint member_invitations_delivery_check check (delivery_status in ('PENDING', 'SENT', 'FAILED', 'UNCONFIRMED', 'NOT_REQUIRED')),
  constraint member_invitations_attempts_check check (delivery_attempts >= 0),
  constraint member_invitations_error_check check (
    last_delivery_error is null or last_delivery_error ~ '^[A-Z0-9_]{1,80}$'
  ),
  constraint member_invitations_acceptance_check check (
    status <> 'ACCEPTED' or (accepted_at is not null and auth_user_id is not null)
  ),
  constraint member_invitations_request_unique unique (mess_id, client_request_id)
);

create unique index member_invitations_one_open_email
  on public.member_invitations (mess_id, lower(email))
  where status = 'PENDING';

create unique index member_invitations_one_open_member_id
  on public.member_invitations (mess_id, member_id)
  where status = 'PENDING';

create index member_invitations_mess_status_idx
  on public.member_invitations (mess_id, status, created_at desc);

create trigger member_invitations_set_updated_at
before update on public.member_invitations
for each row execute function public.set_updated_at();

alter table public.member_invitations enable row level security;
revoke all on public.member_invitations from anon, authenticated;
grant select on public.member_invitations to authenticated;

create policy member_invitations_select_admin
on public.member_invitations for select to authenticated
using (
  mess_id = public.current_mess_id()
  and public.current_app_role() in ('MAIN_ADMIN', 'ADMIN')
  and exists (
    select 1 from public.profiles p
    where p.user_id = auth.uid()
      and p.mess_id = member_invitations.mess_id
      and p.account_status = 'ACTIVE'
      and p.departed_at is null
  )
);

comment on table public.member_invitations is
  'Admin-created onboarding requests. Roles are accepted only from this server row after confirmed-email matching; no passwords or privileged credentials are stored.';

create or replace function public.assert_member_management_capacity(
  target_mess_id uuid,
  added_role public.app_role,
  exclude_invitation_id uuid default null
)
returns void
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $$
declare
  current_members integer;
  pending_members integer;
  current_admins integer;
  pending_admins integer;
begin
  if target_mess_id is null or added_role not in ('MEMBER', 'ADMIN') then
    raise exception 'Invalid member capacity request';
  end if;

  perform pg_advisory_xact_lock(hashtextextended(target_mess_id::text, 23221));

  select count(*) into current_members
  from public.profiles p
  where p.mess_id = target_mess_id
    and p.departed_at is null;

  select count(*) into pending_members
  from public.member_invitations i
  where i.mess_id = target_mess_id
    and i.status = 'PENDING'
    and (exclude_invitation_id is null or i.id <> exclude_invitation_id);

  if current_members + pending_members >= 20 then
    raise exception 'Mess capacity reached: maximum 20 current members and pending invitations';
  end if;

  if added_role = 'ADMIN' then
    select count(*) into current_admins
    from public.profiles p
    left join public.main_admin_assignments m
      on m.mess_id = p.mess_id and m.user_id = p.user_id
    where p.mess_id = target_mess_id
      and p.departed_at is null
      and p.role = 'ADMIN'
      and m.user_id is null;

    select count(*) into pending_admins
    from public.member_invitations i
    where i.mess_id = target_mess_id
      and i.status = 'PENDING'
      and i.intended_role = 'ADMIN'
      and (exclude_invitation_id is null or i.id <> exclude_invitation_id);

    if current_admins + pending_admins >= 5 then
      raise exception 'Admin capacity reached: maximum 5 current Admins and pending Admin invitations';
    end if;
  end if;
end;
$$;

comment on function public.assert_member_management_capacity(uuid, public.app_role, uuid) is
  'Internal only. Per-mess transaction advisory lock serializes capacity checks; departed profiles and cancelled/accepted invitations do not consume capacity.';

create or replace function public.list_member_management_profiles()
returns table (
  user_id uuid,
  full_name text,
  email text,
  phone_number text,
  member_id text,
  role public.app_role,
  effective_role public.app_role,
  account_status public.account_status,
  is_current_manager boolean,
  join_date date,
  departed_at timestamptz,
  departure_reason text,
  created_at timestamptz,
  updated_at timestamptz
)
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $$
declare
  actor_mess_id uuid;
  actor_role public.app_role;
begin
  select p.mess_id,
    case when m.user_id is not null then 'MAIN_ADMIN'::public.app_role else p.role end
  into actor_mess_id, actor_role
  from public.profiles p
  left join public.main_admin_assignments m
    on m.mess_id = p.mess_id and m.user_id = p.user_id
  where p.user_id = auth.uid()
    and p.account_status = 'ACTIVE'
    and p.departed_at is null;

  if actor_mess_id is null or actor_role not in ('MAIN_ADMIN', 'ADMIN') then
    raise exception 'Active Admin or Main Admin access required';
  end if;

  return query
  select p.user_id, p.full_name, coalesce(u.email::text, ''), coalesce(p.phone_number::text, ''),
    p.member_id, p.role,
    case when m.user_id is not null then 'MAIN_ADMIN'::public.app_role else p.role end,
    p.account_status, p.is_current_manager, p.join_date,
    p.departed_at, p.departure_reason, p.created_at, p.updated_at
  from public.profiles p
  join auth.users u on u.id = p.user_id
  left join public.main_admin_assignments m
    on m.mess_id = p.mess_id and m.user_id = p.user_id
  where p.mess_id = actor_mess_id
  order by p.full_name, p.member_id;
end;
$$;

create or replace function public.request_member_onboarding(
  full_name text,
  email text,
  phone_number text,
  member_id text,
  join_date date,
  intended_role public.app_role,
  client_request_id uuid
)
returns table (invitation_id uuid, result_status text)
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $$
declare
  actor record;
  target_auth record;
  existing_profile record;
  existing_request public.member_invitations%rowtype;
  new_invitation_id uuid;
  normalized_email text := lower(btrim(email));
  normalized_name text := btrim(full_name);
  normalized_member_id text := btrim(member_id);
begin
  select p.user_id, p.mess_id,
    case when m.user_id is not null then 'MAIN_ADMIN'::public.app_role else p.role end as role
  into actor
  from public.profiles p
  left join public.main_admin_assignments m
    on m.mess_id = p.mess_id and m.user_id = p.user_id
  where p.user_id = auth.uid()
    and p.account_status = 'ACTIVE'
    and p.departed_at is null;

  if actor.mess_id is null or actor.role not in ('MAIN_ADMIN', 'ADMIN') then
    raise exception 'Active Admin or Main Admin access required';
  end if;
  if intended_role is null or intended_role not in ('MEMBER', 'ADMIN') then
    raise exception 'Only MEMBER or ADMIN invitations are supported';
  end if;
  if actor.role = 'ADMIN' and intended_role <> 'MEMBER' then
    raise exception 'Only Main Admin can onboard an Admin';
  end if;
  if normalized_name !~ '^.{2,80}$' then
    raise exception 'Full name must be 2-80 characters';
  end if;
  if normalized_email !~ '^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$'
     or char_length(normalized_email) > 320 then
    raise exception 'A valid email is required';
  end if;
  if phone_number !~ '^[0-9+() -]{7,20}$' then
    raise exception 'A valid phone number is required';
  end if;
  if char_length(normalized_member_id) not between 1 and 50 then
    raise exception 'Member ID must be 1-50 characters';
  end if;
  if join_date is null or join_date < current_date - 3650 or join_date > current_date + 365 then
    raise exception 'Join date is outside the supported range';
  end if;
  if client_request_id is null then
    raise exception 'Client request ID is required';
  end if;

  select * into existing_request
  from public.member_invitations i
  where i.mess_id = actor.mess_id and i.client_request_id = request_member_onboarding.client_request_id;

  if found then
    if existing_request.email <> normalized_email
       or existing_request.full_name <> normalized_name
       or existing_request.phone_number <> phone_number
       or existing_request.member_id <> normalized_member_id
       or existing_request.join_date <> request_member_onboarding.join_date
       or existing_request.intended_role <> intended_role then
      raise exception 'Client request ID was already used for different member data';
    end if;
    return query select existing_request.id,
      case
        when existing_request.status = 'ACCEPTED' then 'ACTIVE'
        when existing_request.status = 'PENDING' and existing_request.delivery_status = 'SENT' then 'SENT'
        else 'PENDING'
      end;
    return;
  end if;

  perform public.assert_member_management_capacity(actor.mess_id, intended_role);

  select p.user_id, p.mess_id into existing_profile
  from public.profiles p
  join auth.users u on u.id = p.user_id
  where lower(u.email) = normalized_email;

  if found then
    if existing_profile.mess_id = actor.mess_id then
      raise exception 'A profile already exists for this email; edit or reactivate that member instead';
    end if;
    raise exception 'This email already belongs to a profile in another mess';
  end if;

  if exists (
    select 1 from public.profiles p
    where p.mess_id = actor.mess_id and p.member_id = normalized_member_id
  ) then
    raise exception 'Member ID already exists in this mess';
  end if;

  select u.id, u.email_confirmed_at into target_auth
  from auth.users u
  where lower(u.email) = normalized_email
  order by u.created_at
  limit 1;

  if target_auth.id is not null and target_auth.email_confirmed_at is not null then
    insert into public.profiles (
      user_id, mess_id, full_name, phone_number, member_id, role, account_status, join_date
    ) values (
      target_auth.id, actor.mess_id, normalized_name, phone_number,
      normalized_member_id, intended_role, 'ACTIVE', join_date
    );

    insert into public.member_invitations (
      mess_id, email, full_name, phone_number, member_id, join_date,
      intended_role, status, delivery_status, auth_user_id, invited_by,
      client_request_id, accepted_at
    ) values (
      actor.mess_id, normalized_email, normalized_name, phone_number,
      normalized_member_id, join_date, intended_role, 'ACCEPTED', 'NOT_REQUIRED',
      target_auth.id, actor.user_id, client_request_id, now()
    ) returning id into new_invitation_id;

    insert into public.audit_logs (mess_id, actor_user_id, target_user_id, action, details)
    values (actor.mess_id, actor.user_id, target_auth.id, 'MEMBER_ONBOARDED_EXISTING_AUTH',
      jsonb_build_object('invitation_id', new_invitation_id, 'role', intended_role, 'email_delivery', 'NOT_REQUIRED'));

    return query select new_invitation_id, 'ACTIVE';
    return;
  end if;

  insert into public.member_invitations (
    mess_id, email, full_name, phone_number, member_id, join_date,
    intended_role, status, delivery_status, auth_user_id, invited_by, client_request_id
  ) values (
    actor.mess_id, normalized_email, normalized_name, phone_number,
    normalized_member_id, join_date, intended_role, 'PENDING', 'PENDING',
    target_auth.id, actor.user_id, client_request_id
  ) returning id into new_invitation_id;

  insert into public.audit_logs (mess_id, actor_user_id, action, details)
  values (actor.mess_id, actor.user_id, 'MEMBER_INVITATION_CREATED',
    jsonb_build_object('invitation_id', new_invitation_id, 'role', intended_role, 'delivery', 'PENDING'));

  return query select new_invitation_id, 'PENDING';
end;
$$;

create or replace function public.prepare_member_invitation_resend(target_invitation_id uuid)
returns table (invitation_id uuid, result_status text)
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $$
declare
  actor record;
  invitation public.member_invitations%rowtype;
  target_auth record;
  target_profile record;
begin
  select p.user_id, p.mess_id,
    case when m.user_id is not null then 'MAIN_ADMIN'::public.app_role else p.role end as role
  into actor
  from public.profiles p
  left join public.main_admin_assignments m
    on m.mess_id = p.mess_id and m.user_id = p.user_id
  where p.user_id = auth.uid()
    and p.account_status = 'ACTIVE'
    and p.departed_at is null;

  if actor.mess_id is null or actor.role not in ('MAIN_ADMIN', 'ADMIN') then
    raise exception 'Active Admin or Main Admin access required';
  end if;

  select * into invitation
  from public.member_invitations i
  where i.id = target_invitation_id and i.mess_id = actor.mess_id
  for update;

  if not found or invitation.status <> 'PENDING' then
    raise exception 'Pending invitation was not found in your mess';
  end if;
  if actor.role = 'ADMIN' and invitation.intended_role <> 'MEMBER' then
    raise exception 'Only Main Admin can resend an Admin invitation';
  end if;

  select u.id, u.email_confirmed_at into target_auth
  from auth.users u
  where lower(u.email) = invitation.email
  order by u.created_at
  limit 1;

  if target_auth.id is null or target_auth.email_confirmed_at is null then
    return query select invitation.id, 'PENDING';
    return;
  end if;

  select p.user_id, p.mess_id, p.account_status into target_profile
  from public.profiles p where p.user_id = target_auth.id;

  if found then
    if target_profile.mess_id <> actor.mess_id then
      raise exception 'Confirmed Auth user already belongs to another mess';
    end if;
    update public.member_invitations i
    set status = 'ACCEPTED', delivery_status = 'NOT_REQUIRED',
      auth_user_id = target_auth.id, accepted_at = coalesce(i.accepted_at, now())
    where i.id = invitation.id;

    insert into public.audit_logs (mess_id, actor_user_id, target_user_id, action, details)
    values (actor.mess_id, actor.user_id, target_auth.id, 'MEMBER_INVITATION_LINKED_EXISTING_PROFILE',
      jsonb_build_object('invitation_id', invitation.id));
    return query select invitation.id, 'ACTIVE';
    return;
  end if;

  perform public.assert_member_management_capacity(actor.mess_id, invitation.intended_role, invitation.id);

  insert into public.profiles (
    user_id, mess_id, full_name, phone_number, member_id, role, account_status, join_date
  ) values (
    target_auth.id, actor.mess_id, invitation.full_name, invitation.phone_number,
    invitation.member_id, invitation.intended_role, 'ACTIVE', invitation.join_date
  );

  update public.member_invitations i
  set status = 'ACCEPTED', delivery_status = 'NOT_REQUIRED',
    auth_user_id = target_auth.id, accepted_at = now()
  where i.id = invitation.id;

  insert into public.audit_logs (mess_id, actor_user_id, target_user_id, action, details)
  values (actor.mess_id, actor.user_id, target_auth.id, 'MEMBER_INVITATION_ACCEPTED_BY_ADMIN',
    jsonb_build_object('invitation_id', invitation.id, 'role', invitation.intended_role));
  return query select invitation.id, 'ACTIVE';
end;
$$;

create or replace function public.accept_pending_member_invitation()
returns void
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $$
declare
  invited_auth record;
  invitation public.member_invitations%rowtype;
  invitation_count integer;
begin
  select u.id, u.email into invited_auth
  from auth.users u
  where u.id = auth.uid() and u.email_confirmed_at is not null;

  if invited_auth.id is null or invited_auth.email is null then
    raise exception 'A confirmed Auth email is required';
  end if;

  if exists (select 1 from public.profiles p where p.user_id = invited_auth.id) then
    raise exception 'A profile already exists for this Auth user';
  end if;

  select count(*) into invitation_count
  from public.member_invitations i
  where lower(i.email) = lower(invited_auth.email) and i.status = 'PENDING';
  if invitation_count <> 1 then
    if invitation_count = 0 then
      raise exception 'No pending invitation matches this confirmed email';
    end if;
    raise exception 'Multiple pending invitations match; an Admin must cancel duplicates before acceptance';
  end if;

  select * into invitation
  from public.member_invitations i
  where lower(i.email) = lower(invited_auth.email) and i.status = 'PENDING'
  for update;
  if not found or invitation.status <> 'PENDING' then
    raise exception 'Invitation is no longer pending';
  end if;

  perform public.assert_member_management_capacity(invitation.mess_id, invitation.intended_role, invitation.id);

  insert into public.profiles (
    user_id, mess_id, full_name, phone_number, member_id, role, account_status, join_date
  ) values (
    invited_auth.id, invitation.mess_id, invitation.full_name, invitation.phone_number,
    invitation.member_id, invitation.intended_role, 'ACTIVE', invitation.join_date
  );

  update public.member_invitations i
  set status = 'ACCEPTED', auth_user_id = invited_auth.id, accepted_at = now()
  where i.id = invitation.id;

  insert into public.audit_logs (mess_id, actor_user_id, target_user_id, action, details)
  values (invitation.mess_id, invited_auth.id, invited_auth.id, 'MEMBER_INVITATION_ACCEPTED',
    jsonb_build_object('invitation_id', invitation.id, 'invited_by', invitation.invited_by, 'role', invitation.intended_role));
end;
$$;

create or replace function public.finalize_member_invitation_delivery(
  target_invitation_id uuid,
  actor_user_id uuid,
  outcome text,
  error_code text default null
)
returns void
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $$
declare
  actor record;
  invitation public.member_invitations%rowtype;
  linked_auth_id uuid;
  audit_action text;
begin
  if outcome is null or outcome not in ('SENT', 'FAILED', 'UNCONFIRMED') then
    raise exception 'Unsupported invitation delivery outcome';
  end if;
  if error_code is not null and error_code !~ '^[A-Z0-9_]{1,80}$' then
    raise exception 'Invalid safe delivery error code';
  end if;

  select p.user_id, p.mess_id,
    case when m.user_id is not null then 'MAIN_ADMIN'::public.app_role else p.role end as role
  into actor
  from public.profiles p
  left join public.main_admin_assignments m
    on m.mess_id = p.mess_id and m.user_id = p.user_id
  where p.user_id = actor_user_id
    and p.account_status = 'ACTIVE'
    and p.departed_at is null;

  if actor.mess_id is null or actor.role not in ('MAIN_ADMIN', 'ADMIN') then
    raise exception 'Active Admin or Main Admin actor required';
  end if;

  select * into invitation
  from public.member_invitations i
  where i.id = target_invitation_id and i.mess_id = actor.mess_id and i.status = 'PENDING'
  for update;
  if not found then
    raise exception 'Pending invitation was not found for this actor';
  end if;
  if actor.role = 'ADMIN' and invitation.intended_role <> 'MEMBER' then
    raise exception 'Only Main Admin can finalize an Admin invitation';
  end if;

  select u.id into linked_auth_id from auth.users u
  where lower(u.email) = invitation.email
  order by u.created_at limit 1;
  if outcome = 'SENT' and linked_auth_id is null then
    raise exception 'Cannot record SENT without an Auth user';
  end if;

  update public.member_invitations i
  set delivery_status = outcome,
    auth_user_id = coalesce(i.auth_user_id, linked_auth_id),
    delivery_attempts = i.delivery_attempts + 1,
    last_delivery_attempt_at = now(),
    last_sent_at = case when outcome = 'SENT' then now() else i.last_sent_at end,
    last_delivery_error = case when outcome = 'SENT' then null else coalesce(error_code, 'AUTH_DELIVERY_UNCONFIRMED') end
  where i.id = invitation.id;

  audit_action := case outcome
    when 'SENT' then 'MEMBER_INVITATION_SENT'
    when 'FAILED' then 'MEMBER_INVITATION_DELIVERY_FAILED'
    else 'MEMBER_INVITATION_DELIVERY_UNCONFIRMED'
  end;
  insert into public.audit_logs (mess_id, actor_user_id, action, details)
  values (actor.mess_id, actor.user_id, audit_action,
    jsonb_build_object('invitation_id', invitation.id, 'delivery_status', outcome, 'safe_error_code', error_code));
end;
$$;

create or replace function public.cancel_member_invitation(target_invitation_id uuid)
returns void
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $$
declare
  actor record;
  invitation public.member_invitations%rowtype;
begin
  select p.user_id, p.mess_id,
    case when m.user_id is not null then 'MAIN_ADMIN'::public.app_role else p.role end as role
  into actor
  from public.profiles p
  left join public.main_admin_assignments m
    on m.mess_id = p.mess_id and m.user_id = p.user_id
  where p.user_id = auth.uid()
    and p.account_status = 'ACTIVE'
    and p.departed_at is null;
  if actor.mess_id is null or actor.role not in ('MAIN_ADMIN', 'ADMIN') then
    raise exception 'Active Admin or Main Admin access required';
  end if;

  select * into invitation from public.member_invitations i
  where i.id = target_invitation_id and i.mess_id = actor.mess_id
  for update;
  if not found or invitation.status <> 'PENDING' then
    raise exception 'Pending invitation was not found in your mess';
  end if;
  if actor.role = 'ADMIN' and invitation.intended_role <> 'MEMBER' then
    raise exception 'Only Main Admin can cancel an Admin invitation';
  end if;

  update public.member_invitations i set status = 'CANCELLED' where i.id = invitation.id;
  insert into public.audit_logs (mess_id, actor_user_id, action, details)
  values (actor.mess_id, actor.user_id, 'MEMBER_INVITATION_CANCELLED',
    jsonb_build_object('invitation_id', invitation.id, 'previous_delivery_status', invitation.delivery_status));
end;
$$;

create or replace function public.update_managed_member_profile(
  target_user_id uuid,
  full_name text,
  phone_number text,
  member_id text,
  join_date date
)
returns void
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $$
declare
  actor record;
  target record;
  normalized_name text := btrim(full_name);
  normalized_member_id text := btrim(member_id);
begin
  select p.user_id, p.mess_id,
    case when m.user_id is not null then 'MAIN_ADMIN'::public.app_role else p.role end as role
  into actor
  from public.profiles p
  left join public.main_admin_assignments m
    on m.mess_id = p.mess_id and m.user_id = p.user_id
  where p.user_id = auth.uid()
    and p.account_status = 'ACTIVE'
    and p.departed_at is null;
  if actor.mess_id is null or actor.role not in ('MAIN_ADMIN', 'ADMIN') then
    raise exception 'Active Admin or Main Admin access required';
  end if;

  select p.*,
    case when m.user_id is not null then 'MAIN_ADMIN'::public.app_role else p.role end as effective_role
  into target
  from public.profiles p
  left join public.main_admin_assignments m
    on m.mess_id = p.mess_id and m.user_id = p.user_id
  where p.user_id = target_user_id and p.mess_id = actor.mess_id
  for update of p;
  if not found then
    raise exception 'Member was not found in your mess';
  end if;
  if target.departed_at is not null then
    raise exception 'Reactivate a departed member before editing the profile';
  end if;
  if target.effective_role = 'MAIN_ADMIN' and actor.user_id <> target.user_id then
    raise exception 'Main Admin profile cannot be edited by another Admin';
  end if;
  if target.effective_role in ('MAIN_ADMIN', 'ADMIN') and actor.role <> 'MAIN_ADMIN' then
    raise exception 'Only Main Admin can edit an Admin profile';
  end if;
  if normalized_name !~ '^.{2,80}$' then
    raise exception 'Full name must be 2-80 characters';
  end if;
  if phone_number !~ '^[0-9+() -]{7,20}$' then
    raise exception 'A valid phone number is required';
  end if;
  if char_length(normalized_member_id) not between 1 and 50 then
    raise exception 'Member ID must be 1-50 characters';
  end if;
  if join_date is null or join_date < current_date - 3650 or join_date > current_date + 365 then
    raise exception 'Join date is outside the supported range';
  end if;

  update public.profiles p
  set full_name = normalized_name, phone_number = update_managed_member_profile.phone_number,
    member_id = normalized_member_id, join_date = update_managed_member_profile.join_date
  where p.user_id = target.user_id;

  insert into public.audit_logs (mess_id, actor_user_id, target_user_id, action, details)
  values (actor.mess_id, actor.user_id, target.user_id, 'MEMBER_PROFILE_UPDATED',
    jsonb_build_object(
      'before', jsonb_build_object('full_name', target.full_name, 'phone_number', target.phone_number, 'member_id', target.member_id, 'join_date', target.join_date),
      'after', jsonb_build_object('full_name', normalized_name, 'phone_number', update_managed_member_profile.phone_number, 'member_id', normalized_member_id, 'join_date', update_managed_member_profile.join_date)
    ));
end;
$$;

create or replace function public.set_managed_member_lifecycle(
  target_user_id uuid,
  new_status text,
  reason text
)
returns void
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $$
declare
  actor record;
  target record;
  previous_status text;
begin
  if new_status is null or new_status not in ('ACTIVE', 'PENDING', 'SUSPENDED', 'DEPARTED') then
    raise exception 'Unsupported member lifecycle status';
  end if;
  if reason is null or char_length(btrim(reason)) not between 3 and 500 then
    raise exception 'A reason of 3-500 characters is required';
  end if;

  select p.user_id, p.mess_id,
    case when m.user_id is not null then 'MAIN_ADMIN'::public.app_role else p.role end as role
  into actor
  from public.profiles p
  left join public.main_admin_assignments m
    on m.mess_id = p.mess_id and m.user_id = p.user_id
  where p.user_id = auth.uid()
    and p.account_status = 'ACTIVE'
    and p.departed_at is null;
  if actor.mess_id is null or actor.role not in ('MAIN_ADMIN', 'ADMIN') then
    raise exception 'Active Admin or Main Admin access required';
  end if;

  select p.*,
    case when m.user_id is not null then 'MAIN_ADMIN'::public.app_role else p.role end as effective_role
  into target
  from public.profiles p
  left join public.main_admin_assignments m
    on m.mess_id = p.mess_id and m.user_id = p.user_id
  where p.user_id = target_user_id and p.mess_id = actor.mess_id
  for update of p;
  if not found then
    raise exception 'Member was not found in your mess';
  end if;
  if target.user_id = actor.user_id or target.effective_role = 'MAIN_ADMIN' then
    raise exception 'Main Admin or self lifecycle changes are not allowed here';
  end if;
  if target.effective_role = 'ADMIN' and actor.role <> 'MAIN_ADMIN' then
    raise exception 'Only Main Admin can change an Admin lifecycle';
  end if;

  previous_status := case when target.departed_at is not null then 'DEPARTED' else target.account_status::text end;
  if previous_status = new_status then
    raise exception 'Member already has this lifecycle status';
  end if;
  if new_status = 'ACTIVE' and target.departed_at is not null then
    perform public.assert_member_management_capacity(actor.mess_id,
      case when target.role = 'ADMIN' then 'ADMIN'::public.app_role else 'MEMBER'::public.app_role end);
  end if;

  if new_status = 'DEPARTED' then
    update public.profiles p
    set account_status = 'SUSPENDED', departed_at = now(), departure_reason = btrim(reason)
    where p.user_id = target.user_id;
  else
    update public.profiles p
    set account_status = new_status::public.account_status, departed_at = null, departure_reason = null
    where p.user_id = target.user_id;
  end if;

  insert into public.audit_logs (mess_id, actor_user_id, target_user_id, action, details)
  values (actor.mess_id, actor.user_id, target.user_id, 'MEMBER_LIFECYCLE_CHANGED',
    jsonb_build_object('from', previous_status, 'to', new_status, 'reason', btrim(reason)));
end;
$$;

-- Harden the original Phase 1 entry points as well; callers cannot bypass capacity by using old RPCs.
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
set search_path = public, auth, pg_temp
as $$
declare
  actor_mess_id uuid;
begin
  select p.mess_id into actor_mess_id from public.profiles p
  where p.user_id = auth.uid() and p.account_status = 'ACTIVE' and p.departed_at is null;
  if actor_mess_id is null or not public.is_main_admin(actor_mess_id) then
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
  if not exists (
    select 1 from auth.users u
    where u.id = target_user_id and u.email_confirmed_at is not null
  ) then
    raise exception 'Create and confirm the Auth user before provisioning a profile';
  end if;
  if exists (select 1 from public.profiles p where p.user_id = target_user_id) then
    raise exception 'A profile already exists for this Auth user';
  end if;

  perform public.assert_member_management_capacity(actor_mess_id, 'MEMBER');
  insert into public.profiles (
    user_id, mess_id, member_id, full_name, phone_number,
    room_number, seat_number, join_date, role, account_status
  ) values (
    target_user_id, actor_mess_id, btrim(new_member_id), btrim(new_full_name),
    nullif(btrim(coalesce(new_phone_number, '')), ''),
    nullif(btrim(coalesce(new_room_number, '')), ''),
    nullif(btrim(coalesce(new_seat_number, '')), ''),
    new_join_date, 'MEMBER', 'ACTIVE'
  );
  insert into public.audit_logs (mess_id, actor_user_id, action, target_user_id, reason, details)
  values (actor_mess_id, auth.uid(), 'MEMBER_PROFILE_PROVISIONED', target_user_id,
    btrim(provision_reason), jsonb_build_object('role', 'MEMBER', 'source', 'LEGACY_CONFIRMED_AUTH'));
end;
$$;

create or replace function public.set_profile_status(
  target_user_id uuid,
  new_status public.account_status,
  change_reason text
)
returns void
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $$
declare
  actor_mess_id uuid;
  target record;
begin
  if length(btrim(coalesce(change_reason, ''))) < 8 then
    raise exception 'A reason of at least 8 characters is required';
  end if;
  if new_status is null or target_user_id = auth.uid() then
    raise exception 'Valid non-self account status change required';
  end if;
  select p.mess_id into actor_mess_id from public.profiles p
  where p.user_id = auth.uid() and p.account_status = 'ACTIVE' and p.departed_at is null;
  if actor_mess_id is null or not public.is_main_admin(actor_mess_id) then
    raise exception 'Only the active Main Admin may change account status';
  end if;
  select p.* into target from public.profiles p
  where p.user_id = target_user_id and p.mess_id = actor_mess_id for update;
  if not found then
    raise exception 'Target profile does not belong to this mess';
  end if;
  if target.departed_at is not null then
    raise exception 'Use member lifecycle controls for departed profiles';
  end if;
  if exists (select 1 from public.main_admin_assignments a where a.user_id = target_user_id) then
    raise exception 'Transfer the Main Admin assignment before changing this status';
  end if;

  update public.profiles p set account_status = new_status where p.user_id = target_user_id;
  insert into public.audit_logs (mess_id, actor_user_id, action, target_user_id, reason, details)
  values (actor_mess_id, auth.uid(), 'PROFILE_STATUS_CHANGED', target_user_id,
    btrim(change_reason), jsonb_build_object('new_status', new_status));
end;
$$;

create or replace function public.set_profile_role(
  target_user_id uuid,
  new_role public.app_role,
  change_reason text
)
returns void
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $$
declare
  actor_mess_id uuid;
  target record;
begin
  if length(btrim(coalesce(change_reason, ''))) < 8 then
    raise exception 'A reason of at least 8 characters is required';
  end if;
  if new_role is null or new_role not in ('MEMBER', 'ADMIN') then
    raise exception 'Use the dedicated transfer operation for Main Admin';
  end if;
  if target_user_id = auth.uid() then
    raise exception 'You cannot change your own role';
  end if;
  select p.mess_id into actor_mess_id from public.profiles p
  where p.user_id = auth.uid() and p.account_status = 'ACTIVE' and p.departed_at is null;
  if actor_mess_id is null or not public.is_main_admin(actor_mess_id) then
    raise exception 'Only the active Main Admin may change roles';
  end if;
  select p.* into target from public.profiles p
  where p.user_id = target_user_id and p.mess_id = actor_mess_id
    and p.account_status = 'ACTIVE' and p.departed_at is null
  for update;
  if not found then
    raise exception 'Target must be an active, non-departed profile in this mess';
  end if;
  if exists (select 1 from public.main_admin_assignments a where a.user_id = target_user_id) then
    raise exception 'Use the dedicated transfer operation for the Main Admin';
  end if;
  if target.role = new_role then
    raise exception 'Member already has this role';
  end if;
  if new_role = 'ADMIN' then
    perform public.assert_member_management_capacity(actor_mess_id, 'ADMIN');
  end if;

  update public.profiles p set role = new_role where p.user_id = target_user_id;
  insert into public.audit_logs (mess_id, actor_user_id, action, target_user_id, reason, details)
  values (actor_mess_id, auth.uid(), 'PROFILE_ROLE_CHANGED', target_user_id,
    btrim(change_reason), jsonb_build_object('from', target.role, 'to', new_role));
end;
$$;

create or replace function public.transfer_main_admin(
  new_main_admin_user_id uuid,
  transfer_reason text
)
returns void
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $$
declare
  actor record;
  target record;
  existing_main_admin_id uuid;
  admin_count integer;
  pending_admin_count integer;
begin
  if length(btrim(coalesce(transfer_reason, ''))) < 8 then
    raise exception 'A reason of at least 8 characters is required';
  end if;
  select p.user_id, p.mess_id into actor from public.profiles p
  where p.user_id = auth.uid() and p.account_status = 'ACTIVE' and p.departed_at is null;
  if actor.mess_id is null or not public.is_main_admin(actor.mess_id) then
    raise exception 'Only the active Main Admin may transfer the assignment';
  end if;

  perform pg_advisory_xact_lock(hashtextextended(actor.mess_id::text, 23221));
  select a.user_id into existing_main_admin_id from public.main_admin_assignments a
  where a.singleton_key is true and a.mess_id = actor.mess_id for update;
  if existing_main_admin_id is distinct from actor.user_id then
    raise exception 'Current user is not the designated Main Admin';
  end if;
  select p.* into target from public.profiles p
  where p.user_id = new_main_admin_user_id and p.mess_id = actor.mess_id
    and p.account_status = 'ACTIVE' and p.departed_at is null
  for update;
  if not found or target.user_id = actor.user_id then
    raise exception 'Target must be another active, non-departed member of the same mess';
  end if;

  -- After transfer the target is excluded as Main Admin and the actor becomes an Admin.
  select count(*) into admin_count from public.profiles p
  where p.mess_id = actor.mess_id and p.departed_at is null and p.role = 'ADMIN'
    and p.user_id not in (actor.user_id, target.user_id);
  select count(*) into pending_admin_count from public.member_invitations i
  where i.mess_id = actor.mess_id and i.status = 'PENDING' and i.intended_role = 'ADMIN';
  if admin_count + pending_admin_count + 1 > 5 then
    raise exception 'Admin capacity reached: transfer would leave more than 5 current and pending Admins';
  end if;

  update public.main_admin_assignments m
  set user_id = target.user_id, assigned_by = actor.user_id, assigned_at = now()
  where m.singleton_key is true and m.mess_id = actor.mess_id and m.user_id = actor.user_id;
  if not found then
    raise exception 'Main Admin assignment changed; retry required';
  end if;
  update public.profiles p set role = 'ADMIN' where p.user_id = actor.user_id;
  insert into public.audit_logs (mess_id, actor_user_id, action, target_user_id, reason, details)
  values (actor.mess_id, actor.user_id, 'MAIN_ADMIN_TRANSFERRED', target.user_id,
    btrim(transfer_reason), jsonb_build_object('previous_main_admin_user_id', actor.user_id));
end;
$$;

create or replace function public.recover_main_admin_owner_only(
  expected_old_main_admin_user_id uuid,
  new_main_admin_user_id uuid,
  recovery_reason text,
  approval_reference text
)
returns void
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $$
declare
  current_main_admin_id uuid;
  target_mess_id uuid;
  admin_count integer;
  pending_admin_count integer;
begin
  if length(btrim(coalesce(recovery_reason, ''))) < 8
     or length(btrim(coalesce(approval_reference, ''))) < 8 then
    raise exception 'Recovery reason and external approval reference are required';
  end if;

  select a.user_id, a.mess_id into current_main_admin_id, target_mess_id
  from public.main_admin_assignments a where a.singleton_key is true;
  if current_main_admin_id is null or target_mess_id is null then
    raise exception 'No Main Admin assignment exists; stop and use the reviewed recovery procedure';
  end if;
  perform pg_advisory_xact_lock(hashtextextended(target_mess_id::text, 23221));
  select a.user_id, a.mess_id into current_main_admin_id, target_mess_id
  from public.main_admin_assignments a where a.singleton_key is true for update;
  if current_main_admin_id <> expected_old_main_admin_user_id then
    raise exception 'Expected current Main Admin does not match; no change was made';
  end if;
  if new_main_admin_user_id = current_main_admin_id then
    raise exception 'Recovery target must differ from the current Main Admin';
  end if;
  if not exists (
    select 1 from public.profiles p join auth.users u on u.id = p.user_id
    where p.user_id = new_main_admin_user_id and p.mess_id = target_mess_id
      and p.account_status = 'ACTIVE' and p.departed_at is null
      and u.email_confirmed_at is not null
  ) then
    raise exception 'Recovery target must be a confirmed, active, non-departed same-mess profile';
  end if;

  select count(*) into admin_count from public.profiles p
  where p.mess_id = target_mess_id and p.departed_at is null and p.role = 'ADMIN'
    and p.user_id not in (current_main_admin_id, new_main_admin_user_id);
  select count(*) into pending_admin_count from public.member_invitations i
  where i.mess_id = target_mess_id and i.status = 'PENDING' and i.intended_role = 'ADMIN';
  if admin_count + pending_admin_count + 1 > 5 then
    raise exception 'Admin capacity reached: recovery would leave more than 5 current and pending Admins';
  end if;

  update public.main_admin_assignments
  set user_id = new_main_admin_user_id, assigned_by = null, assigned_at = now()
  where singleton_key is true and mess_id = target_mess_id;
  update public.profiles
  set role = 'ADMIN', account_status = 'SUSPENDED'
  where user_id = current_main_admin_id and mess_id = target_mess_id;

  insert into public.audit_logs (mess_id, actor_user_id, actor_type, action, target_user_id, reason, details)
  values (target_mess_id, null, 'PROJECT_OWNER_RECOVERY', 'MAIN_ADMIN_RECOVERED',
    new_main_admin_user_id, btrim(recovery_reason),
    jsonb_build_object('previous_main_admin_user_id', current_main_admin_id,
      'approval_reference', btrim(approval_reference)));
end;
$$;

create policy audit_logs_select_member_management_admin
on public.audit_logs for select to authenticated
using (
  mess_id = public.current_mess_id()
  and public.current_app_role() in ('MAIN_ADMIN', 'ADMIN')
  and action in (
    'MEMBER_INVITATION_CREATED', 'MEMBER_INVITATION_SENT',
    'MEMBER_INVITATION_DELIVERY_FAILED', 'MEMBER_INVITATION_DELIVERY_UNCONFIRMED',
    'MEMBER_INVITATION_ACCEPTED', 'MEMBER_INVITATION_ACCEPTED_BY_ADMIN',
    'MEMBER_INVITATION_LINKED_EXISTING_PROFILE', 'MEMBER_INVITATION_CANCELLED',
    'MEMBER_ONBOARDED_EXISTING_AUTH', 'MEMBER_PROFILE_UPDATED', 'MEMBER_LIFECYCLE_CHANGED'
  )
  and exists (
    select 1 from public.profiles p
    where p.user_id = auth.uid() and p.mess_id = audit_logs.mess_id
      and p.account_status = 'ACTIVE' and p.departed_at is null
  )
);

revoke all on function public.assert_member_management_capacity(uuid, public.app_role, uuid)
  from public, anon, authenticated, service_role;
revoke all on function public.finalize_member_invitation_delivery(uuid, uuid, text, text)
  from public, anon, authenticated;
grant execute on function public.finalize_member_invitation_delivery(uuid, uuid, text, text) to service_role;
revoke all on function public.accept_pending_member_invitation() from public, anon;
grant execute on function public.accept_pending_member_invitation() to authenticated;
revoke all on function public.list_member_management_profiles() from public, anon;
grant execute on function public.list_member_management_profiles() to authenticated;
revoke all on function public.request_member_onboarding(text, text, text, text, date, public.app_role, uuid) from public, anon;
grant execute on function public.request_member_onboarding(text, text, text, text, date, public.app_role, uuid) to authenticated;
revoke all on function public.prepare_member_invitation_resend(uuid) from public, anon;
grant execute on function public.prepare_member_invitation_resend(uuid) to authenticated;
revoke all on function public.cancel_member_invitation(uuid) from public, anon;
grant execute on function public.cancel_member_invitation(uuid) to authenticated;
revoke all on function public.update_managed_member_profile(uuid, text, text, text, date) from public, anon;
grant execute on function public.update_managed_member_profile(uuid, text, text, text, date) to authenticated;
revoke all on function public.set_managed_member_lifecycle(uuid, text, text) from public, anon;
grant execute on function public.set_managed_member_lifecycle(uuid, text, text) to authenticated;

commit;
