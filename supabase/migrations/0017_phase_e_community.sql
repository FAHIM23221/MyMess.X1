-- Phase E: group/private messages, anonymous suggestions, complaints, and moderation.
create table public.messages (
    id uuid primary key default gen_random_uuid(),
    mess_id uuid not null references public.messes(id) on delete restrict,
    sender_id uuid not null references public.profiles(user_id) on delete restrict,
    recipient_id uuid references public.profiles(user_id) on delete restrict,
    kind text not null check (kind in ('GROUP', 'PRIVATE')),
    body text not null check (char_length(trim(body)) between 1 and 2000),
    moderation_status text not null default 'VISIBLE' check (moderation_status in ('VISIBLE', 'HIDDEN')),
    moderation_reason text check (moderation_reason is null or char_length(trim(moderation_reason)) between 8 and 500),
    moderated_by uuid references public.profiles(user_id) on delete restrict,
    moderated_at timestamptz,
    client_request_id uuid not null,
    created_at timestamptz not null default now(),
    unique (mess_id, client_request_id),
    check ((kind = 'GROUP' and recipient_id is null) or (kind = 'PRIVATE' and recipient_id is not null)),
    check ((moderation_status = 'VISIBLE' and moderated_by is null and moderated_at is null)
        or (moderation_status = 'HIDDEN' and moderated_by is not null and moderated_at is not null and moderation_reason is not null))
);
create table public.message_reads (
    message_id uuid not null references public.messages(id) on delete restrict,
    user_id uuid not null references public.profiles(user_id) on delete restrict,
    read_at timestamptz not null default now(),
    primary key (message_id, user_id)
);
create table public.message_reports (
    id uuid primary key default gen_random_uuid(),
    mess_id uuid not null references public.messes(id) on delete restrict,
    message_id uuid not null references public.messages(id) on delete restrict,
    reporter_id uuid not null references public.profiles(user_id) on delete restrict,
    reason text not null check (char_length(trim(reason)) between 8 and 500),
    status text not null default 'OPEN' check (status in ('OPEN', 'REVIEWED', 'DISMISSED')),
    reviewed_by uuid references public.profiles(user_id) on delete restrict,
    reviewed_at timestamptz,
    created_at timestamptz not null default now(),
    unique (message_id, reporter_id)
);
create table public.suggestions (
    id uuid primary key default gen_random_uuid(),
    mess_id uuid not null references public.messes(id) on delete restrict,
    body text not null check (char_length(trim(body)) between 8 and 2000),
    status text not null default 'PENDING' check (status in ('PENDING', 'PUBLISHED', 'REJECTED')),
    moderation_reason text check (moderation_reason is null or char_length(trim(moderation_reason)) between 8 and 500),
    moderated_by uuid references public.profiles(user_id) on delete restrict,
    moderated_at timestamptz,
    client_request_id uuid not null,
    created_at timestamptz not null default now(),
    unique (mess_id, client_request_id),
    check ((status = 'PENDING' and moderated_by is null and moderated_at is null)
        or (status <> 'PENDING' and moderated_by is not null and moderated_at is not null))
);
-- Identity is physically separated from the public suggestion row.
create table public.suggestion_identities (
    suggestion_id uuid primary key references public.suggestions(id) on delete restrict,
    mess_id uuid not null references public.messes(id) on delete restrict,
    author_id uuid not null references public.profiles(user_id) on delete restrict
);
create table public.complaints (
    id uuid primary key default gen_random_uuid(),
    mess_id uuid not null references public.messes(id) on delete restrict,
    author_id uuid not null references public.profiles(user_id) on delete restrict,
    subject text not null check (char_length(trim(subject)) between 2 and 160),
    body text not null check (char_length(trim(body)) between 8 and 3000),
    status text not null default 'OPEN' check (status in ('OPEN', 'IN_PROGRESS', 'RESOLVED', 'REJECTED')),
    response text check (response is null or char_length(trim(response)) between 2 and 2000),
    response_by uuid references public.profiles(user_id) on delete restrict,
    responded_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
create index messages_mess_created_idx on public.messages(mess_id, created_at desc);
create index complaints_mess_status_idx on public.complaints(mess_id, status, created_at desc);

alter table public.messages enable row level security;
alter table public.message_reads enable row level security;
alter table public.message_reports enable row level security;
alter table public.suggestions enable row level security;
alter table public.suggestion_identities enable row level security;
alter table public.complaints enable row level security;
create policy messages_select on public.messages for select to authenticated using (
    public.is_active_user() and mess_id = public.current_mess_id() and (
        sender_id = auth.uid() or recipient_id = auth.uid()
        or (kind = 'GROUP' and (moderation_status = 'VISIBLE' or public.is_admin_for_mess(mess_id)))
        or public.current_app_role() = 'MAIN_ADMIN'
    )
);
create policy message_reads_select on public.message_reads for select to authenticated using (
    public.is_active_user() and user_id = auth.uid()
    and exists (select 1 from public.messages m where m.id = message_reads.message_id and m.mess_id = public.current_mess_id())
);
create policy message_reports_select on public.message_reports for select to authenticated using (
    public.is_active_user() and mess_id = public.current_mess_id()
    and (reporter_id = auth.uid() or public.is_admin_for_mess(mess_id))
);
create policy suggestions_select on public.suggestions for select to authenticated using (
    public.is_active_user() and mess_id = public.current_mess_id()
    and (status = 'PUBLISHED' or public.is_admin_for_mess(mess_id))
);
create policy suggestion_identities_main_admin_only on public.suggestion_identities for select to authenticated using (
    public.is_active_user() and mess_id = public.current_mess_id() and public.current_app_role() = 'MAIN_ADMIN'
);
create policy complaints_select on public.complaints for select to authenticated using (
    public.is_active_user() and mess_id = public.current_mess_id()
    and (author_id = auth.uid() or public.is_admin_for_mess(mess_id))
);
revoke all on public.messages from public, anon, authenticated;
revoke all on public.message_reads from public, anon, authenticated;
revoke all on public.message_reports from public, anon, authenticated;
revoke all on public.suggestions from public, anon, authenticated;
revoke all on public.suggestion_identities from public, anon, authenticated;
revoke all on public.complaints from public, anon, authenticated;
grant select on public.messages to authenticated;
grant select on public.message_reads to authenticated;
grant select on public.message_reports to authenticated;
grant select on public.suggestions to authenticated;
grant select on public.suggestion_identities to authenticated;
grant select on public.complaints to authenticated;

create or replace function public.send_message(
    target_kind text, target_recipient_id uuid, target_body text, client_request_id uuid
) returns uuid language plpgsql security definer set search_path = public, auth as $$
declare
    actor_mess_id uuid := public.current_mess_id();
    actor_is_admin boolean;
    existing_id uuid;
    new_id uuid;
    recipient record;
begin
    if auth.uid() is null or actor_mess_id is null or not public.is_active_user() then raise exception 'An active member session is required' using errcode = '42501'; end if;
    if client_request_id is null then raise exception 'A unique client request ID is required'; end if;
    actor_is_admin := public.is_admin_for_mess(actor_mess_id);
    select m.id into existing_id from public.messages m where m.mess_id = actor_mess_id and m.client_request_id = send_message.client_request_id;
    if found then return existing_id; end if;
    if target_kind not in ('GROUP', 'PRIVATE') then raise exception 'Unsupported message kind'; end if;
    if target_body is null or char_length(trim(target_body)) not between 1 and 2000 then raise exception 'Message body must be between 1 and 2000 characters'; end if;
    if target_kind = 'GROUP' and target_recipient_id is not null then raise exception 'Group messages cannot have a recipient'; end if;
    if target_kind = 'PRIVATE' then
        if target_recipient_id is null or target_recipient_id = auth.uid() then raise exception 'A different private recipient is required'; end if;
        if not exists (select 1 from public.profiles p where p.user_id = target_recipient_id and p.mess_id = actor_mess_id and p.account_status = 'ACTIVE') then raise exception 'Active recipient not found in your mess'; end if;
        if not actor_is_admin and not exists (
            select 1 from public.profiles p where p.user_id = target_recipient_id and p.mess_id = actor_mess_id
              and (p.role = 'ADMIN' or exists(select 1 from public.main_admin_assignments ma where ma.user_id = p.user_id))
        ) then raise exception 'Members can send private messages only to Admins'; end if;
    end if;
    insert into public.messages (mess_id, sender_id, recipient_id, kind, body, client_request_id)
    values (actor_mess_id, auth.uid(), target_recipient_id, target_kind, trim(target_body), client_request_id) returning id into new_id;
    if target_kind = 'PRIVATE' then
        insert into public.notifications (mess_id, recipient_id, kind, title, body)
        values (actor_mess_id, target_recipient_id, 'SYSTEM', 'New private message', trim(target_body));
    else
        for recipient in select p.user_id from public.profiles p where p.mess_id = actor_mess_id and p.account_status = 'ACTIVE' and p.user_id <> auth.uid() loop
            insert into public.notifications (mess_id, recipient_id, kind, title, body)
            values (actor_mess_id, recipient.user_id, 'SYSTEM', 'New group message', trim(target_body));
        end loop;
    end if;
    insert into public.audit_logs (mess_id, actor_user_id, action, target_user_id, details)
    values (actor_mess_id, auth.uid(), 'MESSAGE_SENT', coalesce(target_recipient_id, auth.uid()), jsonb_build_object('message_id', new_id, 'kind', target_kind));
    return new_id;
end;
$$;

create or replace function public.mark_message_read(target_message_id uuid)
returns void language plpgsql security definer set search_path = public, auth as $$
declare actor_mess_id uuid := public.current_mess_id();
begin
    if auth.uid() is null or actor_mess_id is null or not public.is_active_user() then raise exception 'An active member session is required' using errcode = '42501'; end if;
    if not exists (select 1 from public.messages m where m.id = target_message_id and m.mess_id = actor_mess_id and (m.sender_id = auth.uid() or m.recipient_id = auth.uid() or (m.kind = 'GROUP' and m.moderation_status = 'VISIBLE') or public.current_app_role() = 'MAIN_ADMIN')) then raise exception 'Message is not visible to this member'; end if;
    insert into public.message_reads (message_id, user_id) values (target_message_id, auth.uid()) on conflict do nothing;
end;
$$;

create or replace function public.report_message(target_message_id uuid, target_reason text)
returns uuid language plpgsql security definer set search_path = public, auth as $$
declare actor_mess_id uuid := public.current_mess_id(); new_id uuid;
begin
    if auth.uid() is null or actor_mess_id is null or not public.is_active_user() then raise exception 'An active member session is required' using errcode = '42501'; end if;
    if target_reason is null or char_length(trim(target_reason)) not between 8 and 500 then raise exception 'A message report reason is required'; end if;
    if not exists (select 1 from public.messages m where m.id = target_message_id and m.mess_id = actor_mess_id and (m.sender_id = auth.uid() or m.recipient_id = auth.uid() or (m.kind = 'GROUP' and m.moderation_status = 'VISIBLE') or public.current_app_role() = 'MAIN_ADMIN')) then raise exception 'Message is not visible to this member'; end if;
    insert into public.message_reports (mess_id, message_id, reporter_id, reason)
    values (actor_mess_id, target_message_id, auth.uid(), trim(target_reason))
    on conflict (message_id, reporter_id) do update set reason = excluded.reason, status = 'OPEN', reviewed_by = null, reviewed_at = null
    returning id into new_id;
    insert into public.audit_logs (mess_id, actor_user_id, action, target_user_id, details)
    values (actor_mess_id, auth.uid(), 'MESSAGE_REPORTED', auth.uid(), jsonb_build_object('report_id', new_id, 'message_id', target_message_id));
    return new_id;
end;
$$;

create or replace function public.moderate_message(target_message_id uuid, target_status text, target_reason text)
returns void language plpgsql security definer set search_path = public, auth as $$
declare actor_mess_id uuid := public.current_mess_id();
begin
    if auth.uid() is null or actor_mess_id is null or public.current_app_role() <> 'MAIN_ADMIN' then raise exception 'Main Admin moderation access required' using errcode = '42501'; end if;
    if target_status not in ('VISIBLE', 'HIDDEN') then raise exception 'Unsupported message moderation status'; end if;
    if target_status = 'HIDDEN' and (target_reason is null or char_length(trim(target_reason)) not between 8 and 500) then raise exception 'A message moderation reason is required'; end if;
    if not exists (select 1 from public.messages m where m.id = target_message_id and m.mess_id = actor_mess_id) then raise exception 'Message not found in your mess'; end if;
    if target_status = 'VISIBLE' then
        update public.messages set moderation_status = 'VISIBLE', moderation_reason = null, moderated_by = null, moderated_at = null where id = target_message_id;
    else
        update public.messages set moderation_status = 'HIDDEN', moderation_reason = trim(target_reason), moderated_by = auth.uid(), moderated_at = now() where id = target_message_id;
    end if;
    insert into public.audit_logs (mess_id, actor_user_id, action, target_user_id, details)
    values (actor_mess_id, auth.uid(), 'MESSAGE_MODERATED', auth.uid(), jsonb_build_object('message_id', target_message_id, 'status', target_status));
end;
$$;

create or replace function public.create_suggestion(target_body text, client_request_id uuid)
returns uuid language plpgsql security definer set search_path = public, auth as $$
declare actor_mess_id uuid := public.current_mess_id(); existing_id uuid; new_id uuid; main_admin_id uuid;
begin
    if auth.uid() is null or actor_mess_id is null or not public.is_active_user() then raise exception 'An active member session is required' using errcode = '42501'; end if;
    if client_request_id is null then raise exception 'A unique client request ID is required'; end if;
    if target_body is null or char_length(trim(target_body)) not between 8 and 2000 then raise exception 'Suggestion body must be between 8 and 2000 characters'; end if;
    select s.id into existing_id from public.suggestions s where s.mess_id = actor_mess_id and s.client_request_id = create_suggestion.client_request_id;
    if found then return existing_id; end if;
    insert into public.suggestions (mess_id, body, client_request_id) values (actor_mess_id, trim(target_body), client_request_id) returning id into new_id;
    insert into public.suggestion_identities (suggestion_id, mess_id, author_id) values (new_id, actor_mess_id, auth.uid());
    select ma.user_id into main_admin_id from public.main_admin_assignments ma where ma.mess_id = actor_mess_id limit 1;
    if main_admin_id is not null and main_admin_id <> auth.uid() then
        insert into public.notifications (mess_id, recipient_id, kind, title, body)
        values (actor_mess_id, main_admin_id, 'SYSTEM', 'Suggestion awaiting approval', 'A new anonymous suggestion is awaiting Main Admin review.');
    end if;
    insert into public.audit_logs (mess_id, actor_user_id, action, target_user_id, details)
    values (actor_mess_id, auth.uid(), 'SUGGESTION_CREATED', auth.uid(), jsonb_build_object('suggestion_id', new_id, 'status', 'PENDING'));
    return new_id;
end;
$$;

create or replace function public.review_suggestion(target_suggestion_id uuid, target_decision text, target_reason text)
returns void language plpgsql security definer set search_path = public, auth as $$
declare actor_mess_id uuid := public.current_mess_id(); author_id uuid;
begin
    if auth.uid() is null or actor_mess_id is null or public.current_app_role() <> 'MAIN_ADMIN' then raise exception 'Main Admin publication approval required' using errcode = '42501'; end if;
    if target_decision not in ('PUBLISHED', 'REJECTED') then raise exception 'Unsupported suggestion decision'; end if;
    if target_reason is not null and char_length(trim(target_reason)) not between 8 and 500 then raise exception 'Invalid suggestion moderation reason'; end if;
    if not exists (select 1 from public.suggestions s where s.id = target_suggestion_id and s.mess_id = actor_mess_id and s.status = 'PENDING') then raise exception 'Pending suggestion not found in your mess'; end if;
    update public.suggestions set status = target_decision, moderation_reason = nullif(trim(target_reason), ''), moderated_by = auth.uid(), moderated_at = now() where id = target_suggestion_id;
    select si.author_id into author_id from public.suggestion_identities si where si.suggestion_id = target_suggestion_id;
    if author_id is not null then
        insert into public.notifications (mess_id, recipient_id, kind, title, body)
        values (actor_mess_id, author_id, 'SYSTEM', 'Suggestion ' || target_decision, coalesce(nullif(trim(target_reason), ''), 'Your suggestion was reviewed.'));
    end if;
    insert into public.audit_logs (mess_id, actor_user_id, action, target_user_id, details)
    values (actor_mess_id, auth.uid(), 'SUGGESTION_REVIEWED', auth.uid(), jsonb_build_object('suggestion_id', target_suggestion_id, 'decision', target_decision));
end;
$$;

create or replace function public.create_complaint(target_subject text, target_body text)
returns uuid language plpgsql security definer set search_path = public, auth as $$
declare actor_mess_id uuid := public.current_mess_id(); new_id uuid; admin_row record;
begin
    if auth.uid() is null or actor_mess_id is null or not public.is_active_user() then raise exception 'An active member session is required' using errcode = '42501'; end if;
    if target_subject is null or char_length(trim(target_subject)) not between 2 and 160 then raise exception 'Invalid complaint subject'; end if;
    if target_body is null or char_length(trim(target_body)) not between 8 and 3000 then raise exception 'Invalid complaint body'; end if;
    insert into public.complaints (mess_id, author_id, subject, body) values (actor_mess_id, auth.uid(), trim(target_subject), trim(target_body)) returning id into new_id;
    for admin_row in select p.user_id from public.profiles p where p.mess_id = actor_mess_id and p.account_status = 'ACTIVE' and p.role = 'ADMIN' and p.user_id <> auth.uid() loop
        insert into public.notifications (mess_id, recipient_id, kind, title, body)
        values (actor_mess_id, admin_row.user_id, 'SYSTEM', 'New complaint: ' || trim(target_subject), trim(target_body));
    end loop;
    insert into public.audit_logs (mess_id, actor_user_id, action, target_user_id, details)
    values (actor_mess_id, auth.uid(), 'COMPLAINT_CREATED', auth.uid(), jsonb_build_object('complaint_id', new_id));
    return new_id;
end;
$$;

create or replace function public.respond_complaint(target_complaint_id uuid, target_status text, target_response text)
returns void language plpgsql security definer set search_path = public, auth as $$
declare actor_mess_id uuid := public.current_mess_id(); complaint public.complaints%rowtype;
begin
    if auth.uid() is null or actor_mess_id is null or not public.is_admin_for_mess(actor_mess_id) then raise exception 'Active Admin or Main Admin access required' using errcode = '42501'; end if;
    if target_status not in ('IN_PROGRESS', 'RESOLVED', 'REJECTED') then raise exception 'Unsupported complaint status'; end if;
    if target_response is null or char_length(trim(target_response)) not between 2 and 2000 then raise exception 'A complaint response is required'; end if;
    select * into complaint from public.complaints c where c.id = target_complaint_id and c.mess_id = actor_mess_id for update;
    if not found then raise exception 'Complaint not found in your mess'; end if;
    update public.complaints set status = target_status, response = trim(target_response), response_by = auth.uid(), responded_at = now(), updated_at = now() where id = target_complaint_id;
    insert into public.notifications (mess_id, recipient_id, kind, title, body)
    values (actor_mess_id, complaint.author_id, 'SYSTEM', 'Complaint ' || target_status, trim(target_response));
    insert into public.audit_logs (mess_id, actor_user_id, action, target_user_id, details)
    values (actor_mess_id, auth.uid(), 'COMPLAINT_RESPONDED', complaint.author_id, jsonb_build_object('complaint_id', target_complaint_id, 'status', target_status));
end;
$$;

revoke all on function public.send_message(text, uuid, text, uuid) from public, anon;
revoke all on function public.mark_message_read(uuid) from public, anon;
revoke all on function public.report_message(uuid, text) from public, anon;
revoke all on function public.moderate_message(uuid, text, text) from public, anon;
revoke all on function public.create_suggestion(text, uuid) from public, anon;
revoke all on function public.review_suggestion(uuid, text, text) from public, anon;
revoke all on function public.create_complaint(text, text) from public, anon;
revoke all on function public.respond_complaint(uuid, text, text) from public, anon;
grant execute on function public.send_message(text, uuid, text, uuid) to authenticated;
grant execute on function public.mark_message_read(uuid) to authenticated;
grant execute on function public.report_message(uuid, text) to authenticated;
grant execute on function public.moderate_message(uuid, text, text) to authenticated;
grant execute on function public.create_suggestion(text, uuid) to authenticated;
grant execute on function public.review_suggestion(uuid, text, text) to authenticated;
grant execute on function public.create_complaint(text, text) to authenticated;
grant execute on function public.respond_complaint(uuid, text, text) to authenticated;
