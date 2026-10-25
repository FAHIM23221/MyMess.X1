-- Phase D: duties, rotation, notices, and durable in-app notifications.
create table public.duties (
    id uuid primary key default gen_random_uuid(),
    mess_id uuid not null references public.messes(id) on delete restrict,
    title text not null check (char_length(trim(title)) between 2 and 160),
    description text not null check (char_length(trim(description)) between 2 and 1000),
    assigned_to uuid not null references public.profiles(user_id) on delete restrict,
    due_on date not null,
    status text not null default 'ASSIGNED' check (status in ('ASSIGNED', 'COMPLETED', 'MISSED', 'CANCELLED')),
    completion_note text check (completion_note is null or char_length(trim(completion_note)) between 2 and 500),
    client_request_id uuid not null,
    created_by uuid not null references public.profiles(user_id) on delete restrict,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (mess_id, client_request_id)
);
create table public.duty_requests (
    id uuid primary key default gen_random_uuid(),
    mess_id uuid not null references public.messes(id) on delete restrict,
    duty_id uuid not null references public.duties(id) on delete restrict,
    requester_id uuid not null references public.profiles(user_id) on delete restrict,
    kind text not null check (kind in ('CANNOT_DO', 'SWAP')),
    target_member_id uuid references public.profiles(user_id) on delete restrict,
    reason text not null check (char_length(trim(reason)) between 8 and 500),
    status text not null default 'PENDING' check (status in ('PENDING', 'APPROVED', 'REJECTED')),
    decision_reason text check (decision_reason is null or char_length(trim(decision_reason)) between 2 and 500),
    decided_by uuid references public.profiles(user_id) on delete restrict,
    decided_at timestamptz,
    created_at timestamptz not null default now(),
    check ((kind = 'SWAP' and target_member_id is not null) or (kind = 'CANNOT_DO' and target_member_id is null)),
    check ((status = 'PENDING' and decided_by is null and decided_at is null) or (status <> 'PENDING' and decided_by is not null and decided_at is not null))
);
create table public.manager_rotations (
    id uuid primary key default gen_random_uuid(),
    mess_id uuid not null references public.messes(id) on delete restrict,
    manager_id uuid not null references public.profiles(user_id) on delete restrict,
    starts_on date not null,
    ends_on date,
    status text not null default 'SCHEDULED' check (status in ('SCHEDULED', 'ACTIVE', 'COMPLETED', 'CANCELLED')),
    handover_note text check (handover_note is null or char_length(trim(handover_note)) between 2 and 1000),
    created_by uuid not null references public.profiles(user_id) on delete restrict,
    created_at timestamptz not null default now(),
    activated_at timestamptz,
    check (ends_on is null or ends_on >= starts_on)
);
create table public.notices (
    id uuid primary key default gen_random_uuid(),
    mess_id uuid not null references public.messes(id) on delete restrict,
    title text not null check (char_length(trim(title)) between 2 and 160),
    body text not null check (char_length(trim(body)) between 2 and 4000),
    audience text not null default 'ALL' check (audience in ('ALL', 'MEMBERS', 'ADMINS')),
    status text not null default 'DRAFT' check (status in ('DRAFT', 'SCHEDULED', 'PUBLISHED', 'CANCELLED', 'ARCHIVED')),
    publish_at timestamptz,
    expires_at timestamptz,
    client_request_id uuid not null,
    created_by uuid not null references public.profiles(user_id) on delete restrict,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (mess_id, client_request_id),
    check (expires_at is null or publish_at is null or expires_at > publish_at),
    check ((status in ('DRAFT', 'CANCELLED', 'ARCHIVED')) or publish_at is not null)
);
create table public.notice_reads (
    notice_id uuid not null references public.notices(id) on delete restrict,
    user_id uuid not null references public.profiles(user_id) on delete restrict,
    read_at timestamptz not null default now(),
    primary key (notice_id, user_id)
);
alter table public.notifications add column notice_id uuid references public.notices(id) on delete restrict;
create unique index one_notice_notification_per_recipient on public.notifications(notice_id, recipient_id) where notice_id is not null;
create index duties_mess_due_idx on public.duties(mess_id, due_on, status);
create index duty_requests_mess_status_idx on public.duty_requests(mess_id, status, created_at desc);
create index notices_mess_publish_idx on public.notices(mess_id, status, publish_at);

alter table public.duties enable row level security;
alter table public.duty_requests enable row level security;
alter table public.manager_rotations enable row level security;
alter table public.notices enable row level security;
alter table public.notice_reads enable row level security;
create policy duties_select on public.duties for select to authenticated using (
    public.is_active_user() and mess_id = public.current_mess_id()
);
create policy duty_requests_select on public.duty_requests for select to authenticated using (
    public.is_active_user() and mess_id = public.current_mess_id()
    and (requester_id = auth.uid() or public.is_admin_for_mess(mess_id))
);
create policy rotations_select on public.manager_rotations for select to authenticated using (
    public.is_active_user() and mess_id = public.current_mess_id()
);
create policy notices_select on public.notices for select to authenticated using (
    public.is_active_user() and mess_id = public.current_mess_id()
    and (
        public.is_admin_for_mess(mess_id)
        or (status = 'PUBLISHED' and publish_at <= now() and (expires_at is null or expires_at > now())
            and (audience = 'ALL' or audience = 'MEMBERS'))
    )
);
create policy notice_reads_select on public.notice_reads for select to authenticated using (
    public.is_active_user() and user_id = auth.uid()
    and exists (select 1 from public.notices n where n.id = notice_id and n.mess_id = public.current_mess_id())
);
revoke all on public.duties from public, anon, authenticated;
revoke all on public.duty_requests from public, anon, authenticated;
revoke all on public.manager_rotations from public, anon, authenticated;
revoke all on public.notices from public, anon, authenticated;
revoke all on public.notice_reads from public, anon, authenticated;
grant select on public.duties to authenticated;
grant select on public.duty_requests to authenticated;
grant select on public.manager_rotations to authenticated;
grant select on public.notices to authenticated;
grant select on public.notice_reads to authenticated;

create or replace function public.dispatch_notice(target_notice_id uuid)
returns void language plpgsql security definer set search_path = public, pg_temp as $$
declare n public.notices%rowtype; recipient record;
begin
    select * into n from public.notices where id = target_notice_id;
    if not found or n.status <> 'PUBLISHED' or n.publish_at > now() or (n.expires_at is not null and n.expires_at <= now()) then return; end if;
    for recipient in
        select p.user_id from public.profiles p
        where p.mess_id = n.mess_id and p.account_status = 'ACTIVE'
          and (n.audience = 'ALL' or (n.audience = 'MEMBERS' and p.role = 'MEMBER')
            or (n.audience = 'ADMINS' and (p.role = 'ADMIN' or exists(select 1 from public.main_admin_assignments ma where ma.user_id = p.user_id))))
    loop
        insert into public.notifications (mess_id, recipient_id, kind, title, body, notice_id)
        values (n.mess_id, recipient.user_id, 'NOTICE', n.title, n.body, n.id)
        on conflict do nothing;
    end loop;
end;
$$;

create or replace function public.create_duty(
    target_user_id uuid, target_title text, target_description text, target_due_on date, client_request_id uuid
) returns uuid language plpgsql security definer set search_path = public, auth as $$
declare actor_mess_id uuid := public.current_mess_id(); existing_id uuid; new_id uuid;
begin
    if auth.uid() is null or actor_mess_id is null or not public.is_admin_for_mess(actor_mess_id) then raise exception 'Active Admin or Main Admin access required' using errcode = '42501'; end if;
    if client_request_id is null then raise exception 'A unique client request ID is required'; end if;
    select d.id into existing_id from public.duties d where d.mess_id = actor_mess_id and d.client_request_id = create_duty.client_request_id;
    if found then return existing_id; end if;
    if target_title is null or char_length(trim(target_title)) not between 2 and 160 then raise exception 'Invalid duty title'; end if;
    if target_description is null or char_length(trim(target_description)) not between 2 and 1000 then raise exception 'Invalid duty description'; end if;
    if target_due_on is null then raise exception 'Duty due date is required'; end if;
    if not exists (select 1 from public.profiles p where p.user_id = target_user_id and p.mess_id = actor_mess_id and p.account_status = 'ACTIVE') then raise exception 'Active assignee not found in your mess'; end if;
    insert into public.duties (mess_id, title, description, assigned_to, due_on, client_request_id, created_by)
    values (actor_mess_id, trim(target_title), trim(target_description), target_user_id, target_due_on, client_request_id, auth.uid()) returning id into new_id;
    insert into public.notifications (mess_id, recipient_id, kind, title, body)
    values (actor_mess_id, target_user_id, 'DUTY', 'New duty: ' || trim(target_title), trim(target_description));
    insert into public.audit_logs (mess_id, actor_user_id, action, target_user_id, details)
    values (actor_mess_id, auth.uid(), 'DUTY_CREATED', target_user_id, jsonb_build_object('duty_id', new_id, 'due_on', target_due_on));
    return new_id;
end;
$$;

create or replace function public.update_duty_status(
    target_duty_id uuid, target_status text, target_note text
) returns void language plpgsql security definer set search_path = public, auth as $$
declare actor_mess_id uuid := public.current_mess_id(); duty public.duties%rowtype;
begin
    if auth.uid() is null or actor_mess_id is null or not public.is_active_user() then raise exception 'An active member session is required' using errcode = '42501'; end if;
    select * into duty from public.duties d where d.id = target_duty_id and d.mess_id = actor_mess_id for update;
    if not found then raise exception 'Duty not found in your mess'; end if;
    if not public.is_admin_for_mess(actor_mess_id) and (duty.assigned_to <> auth.uid() or target_status <> 'COMPLETED') then raise exception 'Only the assignee can complete a duty' using errcode = '42501'; end if;
    if target_status not in ('COMPLETED', 'MISSED', 'CANCELLED') then raise exception 'Unsupported duty status'; end if;
    if target_note is not null and char_length(trim(target_note)) not between 2 and 500 then raise exception 'Invalid duty note'; end if;
    update public.duties set status = target_status, completion_note = nullif(trim(target_note), ''), updated_at = now() where id = target_duty_id;
    insert into public.audit_logs (mess_id, actor_user_id, action, target_user_id, details)
    values (actor_mess_id, auth.uid(), 'DUTY_STATUS_UPDATED', duty.assigned_to, jsonb_build_object('duty_id', target_duty_id, 'status', target_status));
end;
$$;

create or replace function public.request_duty_change(
    target_duty_id uuid, target_kind text, target_member_id uuid, target_reason text
) returns uuid language plpgsql security definer set search_path = public, auth as $$
declare actor_mess_id uuid := public.current_mess_id(); duty public.duties%rowtype; new_id uuid;
begin
    if auth.uid() is null or actor_mess_id is null or not public.is_active_user() then raise exception 'An active member session is required' using errcode = '42501'; end if;
    select * into duty from public.duties d where d.id = target_duty_id and d.mess_id = actor_mess_id and d.assigned_to = auth.uid() and d.status = 'ASSIGNED' for update;
    if not found then raise exception 'Only the assignee of an open duty can request a change'; end if;
    if target_kind not in ('CANNOT_DO', 'SWAP') then raise exception 'Unsupported duty request kind'; end if;
    if target_reason is null or char_length(trim(target_reason)) not between 8 and 500 then raise exception 'A duty request reason is required'; end if;
    if target_kind = 'CANNOT_DO' and target_member_id is not null then raise exception 'Cannot-do requests cannot have a swap target'; end if;
    if target_kind = 'SWAP' and (target_member_id is null or target_member_id = auth.uid() or not exists(select 1 from public.profiles p where p.user_id = target_member_id and p.mess_id = actor_mess_id and p.account_status = 'ACTIVE')) then raise exception 'A different active swap target is required'; end if;
    if exists (select 1 from public.duty_requests dr where dr.duty_id = target_duty_id and dr.status = 'PENDING') then raise exception 'A duty request is already pending'; end if;
    insert into public.duty_requests (mess_id, duty_id, requester_id, kind, target_member_id, reason)
    values (actor_mess_id, target_duty_id, auth.uid(), target_kind, target_member_id, trim(target_reason)) returning id into new_id;
    insert into public.audit_logs (mess_id, actor_user_id, action, target_user_id, details)
    values (actor_mess_id, auth.uid(), 'DUTY_CHANGE_REQUESTED', auth.uid(), jsonb_build_object('request_id', new_id, 'duty_id', target_duty_id, 'kind', target_kind));
    return new_id;
end;
$$;

create or replace function public.review_duty_request(
    target_request_id uuid, target_decision text, target_reason text
) returns void language plpgsql security definer set search_path = public, auth as $$
declare actor_mess_id uuid := public.current_mess_id(); request_row public.duty_requests%rowtype;
begin
    if auth.uid() is null or actor_mess_id is null or not public.is_admin_for_mess(actor_mess_id) then raise exception 'Active Admin or Main Admin access required' using errcode = '42501'; end if;
    if target_decision not in ('APPROVED', 'REJECTED') then raise exception 'Unsupported duty request decision'; end if;
    if target_reason is not null and char_length(trim(target_reason)) not between 2 and 500 then raise exception 'Invalid duty decision reason'; end if;
    select * into request_row from public.duty_requests dr where dr.id = target_request_id and dr.mess_id = actor_mess_id and dr.status = 'PENDING' for update;
    if not found then raise exception 'Pending duty request not found in your mess'; end if;
    update public.duty_requests set status = target_decision, decision_reason = nullif(trim(target_reason), ''), decided_by = auth.uid(), decided_at = now() where id = target_request_id;
    if target_decision = 'APPROVED' then
        if request_row.kind = 'SWAP' then
            update public.duties set assigned_to = request_row.target_member_id, updated_at = now() where id = request_row.duty_id;
            insert into public.notifications (mess_id, recipient_id, kind, title, body)
            values (actor_mess_id, request_row.target_member_id, 'DUTY', 'Duty swap approved', 'A duty has been assigned to you after an approved swap.');
        else
            update public.duties set status = 'MISSED', updated_at = now() where id = request_row.duty_id;
        end if;
    end if;
    insert into public.notifications (mess_id, recipient_id, kind, title, body)
    values (actor_mess_id, request_row.requester_id, 'DUTY', 'Duty request ' || target_decision, coalesce(nullif(trim(target_reason), ''), 'Your duty request was reviewed.'));
    insert into public.audit_logs (mess_id, actor_user_id, action, target_user_id, details)
    values (actor_mess_id, auth.uid(), 'DUTY_REQUEST_REVIEWED', request_row.requester_id, jsonb_build_object('request_id', target_request_id, 'decision', target_decision));
end;
$$;

create or replace function public.schedule_manager_rotation(
    target_manager_id uuid, target_start date, target_end date, target_handover_note text
) returns uuid language plpgsql security definer set search_path = public, auth as $$
declare actor_mess_id uuid := public.current_mess_id(); new_id uuid;
begin
    if auth.uid() is null or actor_mess_id is null or not public.is_admin_for_mess(actor_mess_id) then raise exception 'Active Admin or Main Admin access required' using errcode = '42501'; end if;
    if target_start is null or (target_end is not null and target_end < target_start) then raise exception 'Invalid rotation dates'; end if;
    if target_handover_note is not null and char_length(trim(target_handover_note)) not between 2 and 1000 then raise exception 'Invalid handover note'; end if;
    if not exists (select 1 from public.profiles p where p.user_id = target_manager_id and p.mess_id = actor_mess_id and p.account_status = 'ACTIVE' and (p.role = 'ADMIN' or exists(select 1 from public.main_admin_assignments ma where ma.user_id = p.user_id))) then raise exception 'Active Admin manager not found in your mess'; end if;
    perform pg_advisory_xact_lock(hashtext(actor_mess_id::text || ':rotation'));
    if exists (select 1 from public.manager_rotations r where r.mess_id = actor_mess_id and r.status in ('SCHEDULED', 'ACTIVE') and target_start <= coalesce(r.ends_on, date '9999-12-31') and coalesce(target_end, date '9999-12-31') >= r.starts_on) then raise exception 'Manager rotation overlaps an existing rotation'; end if;
    insert into public.manager_rotations (mess_id, manager_id, starts_on, ends_on, handover_note, created_by)
    values (actor_mess_id, target_manager_id, target_start, target_end, nullif(trim(target_handover_note), ''), auth.uid()) returning id into new_id;
    insert into public.audit_logs (mess_id, actor_user_id, action, target_user_id, details)
    values (actor_mess_id, auth.uid(), 'MANAGER_ROTATION_SCHEDULED', target_manager_id, jsonb_build_object('rotation_id', new_id, 'starts_on', target_start, 'ends_on', target_end));
    return new_id;
end;
$$;

create or replace function public.activate_manager_rotation(target_rotation_id uuid)
returns void language plpgsql security definer set search_path = public, auth as $$
declare actor_mess_id uuid := public.current_mess_id(); rotation public.manager_rotations%rowtype;
begin
    if auth.uid() is null or actor_mess_id is null or not public.is_admin_for_mess(actor_mess_id) then raise exception 'Active Admin or Main Admin access required' using errcode = '42501'; end if;
    perform pg_advisory_xact_lock(hashtext(actor_mess_id::text || ':rotation'));
    select * into rotation from public.manager_rotations r where r.id = target_rotation_id and r.mess_id = actor_mess_id and r.status = 'SCHEDULED' for update;
    if not found then raise exception 'Scheduled rotation not found in your mess'; end if;
    if rotation.starts_on > (now() at time zone 'Asia/Dhaka')::date or (rotation.ends_on is not null and rotation.ends_on < (now() at time zone 'Asia/Dhaka')::date) then raise exception 'Rotation is not due for activation'; end if;
    update public.manager_rotations set status = 'COMPLETED' where mess_id = actor_mess_id and status = 'ACTIVE';
    update public.manager_rotations set status = 'ACTIVE', activated_at = now() where id = target_rotation_id;
    update public.profiles set is_current_manager = false where mess_id = actor_mess_id;
    update public.profiles set is_current_manager = true where user_id = rotation.manager_id and mess_id = actor_mess_id;
    insert into public.notifications (mess_id, recipient_id, kind, title, body)
    values (actor_mess_id, rotation.manager_id, 'DUTY', 'Manager handover activated', coalesce(rotation.handover_note, 'Your manager rotation is now active.'));
    insert into public.audit_logs (mess_id, actor_user_id, action, target_user_id, details)
    values (actor_mess_id, auth.uid(), 'MANAGER_ROTATION_ACTIVATED', rotation.manager_id, jsonb_build_object('rotation_id', target_rotation_id));
end;
$$;

create or replace function public.save_notice(
    target_title text, target_body text, target_audience text, target_status text,
    target_publish_at timestamptz, target_expires_at timestamptz, client_request_id uuid
) returns uuid language plpgsql security definer set search_path = public, auth as $$
declare actor_mess_id uuid := public.current_mess_id(); existing_id uuid; new_id uuid; effective_publish timestamptz;
begin
    if auth.uid() is null or actor_mess_id is null or not public.is_admin_for_mess(actor_mess_id) then raise exception 'Active Admin or Main Admin access required' using errcode = '42501'; end if;
    if client_request_id is null then raise exception 'A unique client request ID is required'; end if;
    select n.id into existing_id from public.notices n where n.mess_id = actor_mess_id and n.client_request_id = save_notice.client_request_id;
    if found then return existing_id; end if;
    if target_title is null or char_length(trim(target_title)) not between 2 and 160 then raise exception 'Invalid notice title'; end if;
    if target_body is null or char_length(trim(target_body)) not between 2 and 4000 then raise exception 'Invalid notice body'; end if;
    if target_audience not in ('ALL', 'MEMBERS', 'ADMINS') then raise exception 'Unsupported notice audience'; end if;
    if target_status not in ('DRAFT', 'SCHEDULED', 'PUBLISHED') then raise exception 'Unsupported initial notice status'; end if;
    if target_expires_at is not null and target_publish_at is not null and target_expires_at <= target_publish_at then raise exception 'Notice expiration must be after publication'; end if;
    if target_status in ('SCHEDULED', 'PUBLISHED') and target_publish_at is null and target_status = 'SCHEDULED' then raise exception 'A scheduled notice needs a publication time'; end if;
    effective_publish := case when target_status = 'PUBLISHED' then coalesce(target_publish_at, now()) else target_publish_at end;
    insert into public.notices (mess_id, title, body, audience, status, publish_at, expires_at, client_request_id, created_by)
    values (actor_mess_id, trim(target_title), trim(target_body), target_audience, target_status, effective_publish, target_expires_at, client_request_id, auth.uid()) returning id into new_id;
    if target_status = 'PUBLISHED' then perform public.dispatch_notice(new_id); end if;
    insert into public.audit_logs (mess_id, actor_user_id, action, target_user_id, details)
    values (actor_mess_id, auth.uid(), 'NOTICE_CREATED', auth.uid(), jsonb_build_object('notice_id', new_id, 'status', target_status, 'audience', target_audience));
    return new_id;
end;
$$;

create or replace function public.set_notice_status(target_notice_id uuid, target_status text)
returns void language plpgsql security definer set search_path = public, auth as $$
declare actor_mess_id uuid := public.current_mess_id(); n public.notices%rowtype;
begin
    if auth.uid() is null or actor_mess_id is null or not public.is_admin_for_mess(actor_mess_id) then raise exception 'Active Admin or Main Admin access required' using errcode = '42501'; end if;
    if target_status not in ('PUBLISHED', 'CANCELLED', 'ARCHIVED') then raise exception 'Unsupported notice status'; end if;
    select * into n from public.notices where id = target_notice_id and mess_id = actor_mess_id for update;
    if not found then raise exception 'Notice not found in your mess'; end if;
    update public.notices set status = target_status, publish_at = case when target_status = 'PUBLISHED' then coalesce(publish_at, now()) else publish_at end, updated_at = now() where id = target_notice_id;
    if target_status = 'PUBLISHED' then perform public.dispatch_notice(target_notice_id); end if;
    insert into public.audit_logs (mess_id, actor_user_id, action, target_user_id, details)
    values (actor_mess_id, auth.uid(), 'NOTICE_STATUS_UPDATED', auth.uid(), jsonb_build_object('notice_id', target_notice_id, 'status', target_status));
end;
$$;

create or replace function public.publish_due_notices()
returns integer language plpgsql security definer set search_path = public, auth as $$
declare actor_mess_id uuid := public.current_mess_id(); n record; published_count integer := 0;
begin
    if auth.uid() is null or actor_mess_id is null or not public.is_admin_for_mess(actor_mess_id) then raise exception 'Active Admin or Main Admin access required' using errcode = '42501'; end if;
    for n in select * from public.notices where mess_id = actor_mess_id and status = 'SCHEDULED' and publish_at <= now() for update loop
        update public.notices set status = 'PUBLISHED', updated_at = now() where id = n.id;
        perform public.dispatch_notice(n.id);
        published_count := published_count + 1;
    end loop;
    if published_count > 0 then
        insert into public.audit_logs (mess_id, actor_user_id, action, target_user_id, details)
        values (actor_mess_id, auth.uid(), 'DUE_NOTICES_PUBLISHED', auth.uid(), jsonb_build_object('count', published_count));
    end if;
    return published_count;
end;
$$;

create or replace function public.mark_notice_read(target_notice_id uuid)
returns void language plpgsql security definer set search_path = public, auth as $$
declare actor_mess_id uuid := public.current_mess_id();
begin
    if auth.uid() is null or actor_mess_id is null or not public.is_active_user() then raise exception 'An active member session is required' using errcode = '42501'; end if;
    if not exists (select 1 from public.notices n where n.id = target_notice_id and n.mess_id = actor_mess_id and n.status = 'PUBLISHED' and n.publish_at <= now() and (n.expires_at is null or n.expires_at > now()) and (n.audience in ('ALL', 'MEMBERS') or public.is_admin_for_mess(actor_mess_id))) then raise exception 'Notice is not visible to this member'; end if;
    insert into public.notice_reads (notice_id, user_id) values (target_notice_id, auth.uid()) on conflict do nothing;
end;
$$;

revoke all on function public.dispatch_notice(uuid) from public, anon, authenticated;
revoke all on function public.create_duty(uuid, text, text, date, uuid) from public, anon;
revoke all on function public.update_duty_status(uuid, text, text) from public, anon;
revoke all on function public.request_duty_change(uuid, text, uuid, text) from public, anon;
revoke all on function public.review_duty_request(uuid, text, text) from public, anon;
revoke all on function public.schedule_manager_rotation(uuid, date, date, text) from public, anon;
revoke all on function public.activate_manager_rotation(uuid) from public, anon;
revoke all on function public.save_notice(text, text, text, text, timestamptz, timestamptz, uuid) from public, anon;
revoke all on function public.set_notice_status(uuid, text) from public, anon;
revoke all on function public.publish_due_notices() from public, anon;
revoke all on function public.mark_notice_read(uuid) from public, anon;
grant execute on function public.create_duty(uuid, text, text, date, uuid) to authenticated;
grant execute on function public.update_duty_status(uuid, text, text) to authenticated;
grant execute on function public.request_duty_change(uuid, text, uuid, text) to authenticated;
grant execute on function public.review_duty_request(uuid, text, text) to authenticated;
grant execute on function public.schedule_manager_rotation(uuid, date, date, text) to authenticated;
grant execute on function public.activate_manager_rotation(uuid) to authenticated;
grant execute on function public.save_notice(text, text, text, text, timestamptz, timestamptz, uuid) to authenticated;
grant execute on function public.set_notice_status(uuid, text) to authenticated;
grant execute on function public.publish_due_notices() to authenticated;
grant execute on function public.mark_notice_read(uuid) to authenticated;
