-- Admin-directed durable in-app notifications with idempotent batches and audit.
-- External FCM/email delivery is deliberately not implied by these rows.
begin;

create table public.member_notification_batches (
    id uuid primary key default gen_random_uuid(),
    mess_id uuid not null references public.messes(id) on delete restrict,
    actor_user_id uuid not null references public.profiles(user_id) on delete restrict,
    client_request_id uuid not null,
    audience text not null check (audience in ('MEMBER', 'ALL', 'ADMINS')),
    target_member_id uuid references public.profiles(user_id) on delete restrict,
    title text not null check (char_length(btrim(title)) between 2 and 200),
    body text not null check (char_length(btrim(body)) between 2 and 4000),
    request_fingerprint text not null check (char_length(request_fingerprint) = 64),
    recipient_count integer not null default 0 check (recipient_count >= 0),
    created_at timestamptz not null default now(),
    unique (mess_id, client_request_id),
    check ((audience = 'MEMBER') = (target_member_id is not null))
);

create index member_notification_batches_mess_created_idx
    on public.member_notification_batches(mess_id, created_at desc);

alter table public.notifications
    add column batch_id uuid references public.member_notification_batches(id) on delete restrict;
create unique index one_batch_notification_per_recipient
    on public.notifications(batch_id, recipient_id)
    where batch_id is not null;

alter table public.member_notification_batches enable row level security;
revoke all on public.member_notification_batches from public, anon, authenticated;

create or replace function public.send_member_notification(
    target_audience text,
    target_member_id uuid,
    target_title text,
    target_body text,
    client_request_id uuid
)
returns table (batch_id uuid, recipient_count integer, result_status text)
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $$
declare
    actor record;
    existing_batch public.member_notification_batches%rowtype;
    clean_title text := btrim(target_title);
    clean_body text := btrim(target_body);
    fingerprint text;
    new_batch_id uuid;
    new_recipient_count integer;
begin
    select p.mess_id,
        case when m.user_id is not null then 'MAIN_ADMIN'::public.app_role else p.role end as role
    into actor
    from public.profiles p
    left join public.main_admin_assignments m
        on m.mess_id = p.mess_id and m.user_id = p.user_id
    where p.user_id = auth.uid()
      and p.account_status = 'ACTIVE'
      and p.departed_at is null;

    if actor.mess_id is null or actor.role not in ('ADMIN', 'MAIN_ADMIN') then
        raise exception 'Active Admin or Main Admin access required' using errcode = '42501';
    end if;
    if target_audience is null or target_audience not in ('MEMBER', 'ALL', 'ADMINS') then
        raise exception 'Unsupported notification audience';
    end if;
    if (target_audience = 'MEMBER') <> (target_member_id is not null) then
        raise exception 'A specific member is required only for the MEMBER audience';
    end if;
    if clean_title is null or clean_body is null
       or char_length(clean_title) not between 2 and 200
       or char_length(clean_body) not between 2 and 4000 then
        raise exception 'Notification title/body length is invalid';
    end if;
    if client_request_id is null then
        raise exception 'Client request ID is required';
    end if;
    if target_audience = 'MEMBER' and not exists (
        select 1 from public.profiles p
        where p.user_id = target_member_id
          and p.mess_id = actor.mess_id
          and p.account_status = 'ACTIVE'
          and p.departed_at is null
    ) then
        raise exception 'An active same-mess recipient is required';
    end if;

    fingerprint := encode(extensions.digest(
        jsonb_build_array(target_audience, target_member_id, clean_title, clean_body)::text,
        'sha256'
    ), 'hex');

    -- Serialize retries for this mess; the unique request key remains the final guard.
    perform pg_advisory_xact_lock(hashtext('member-notification:' || actor.mess_id::text));
    select * into existing_batch
    from public.member_notification_batches b
    where b.mess_id = actor.mess_id and b.client_request_id = send_member_notification.client_request_id;
    if found then
        if existing_batch.request_fingerprint <> fingerprint then
            raise exception 'Client request ID was already used for different notification data';
        end if;
        return query select existing_batch.id, existing_batch.recipient_count, 'IDEMPOTENT';
        return;
    end if;

    insert into public.member_notification_batches (
        mess_id, actor_user_id, client_request_id, audience, target_member_id,
        title, body, request_fingerprint, recipient_count
    ) values (
        actor.mess_id, auth.uid(), client_request_id, target_audience, target_member_id,
        clean_title, clean_body, fingerprint, 0
    ) returning id into new_batch_id;

    insert into public.notifications (mess_id, recipient_id, kind, title, body, batch_id)
    select actor.mess_id, p.user_id, 'SYSTEM', clean_title, clean_body, new_batch_id
    from public.profiles p
    where p.mess_id = actor.mess_id
      and p.account_status = 'ACTIVE'
      and p.departed_at is null
      and (
          (target_audience = 'MEMBER' and p.user_id = target_member_id)
          or target_audience = 'ALL'
          or (target_audience = 'ADMINS' and (
              p.role = 'ADMIN' or exists (
                  select 1 from public.main_admin_assignments m
                  where m.mess_id = p.mess_id and m.user_id = p.user_id
              )
          ))
      );
    get diagnostics new_recipient_count = row_count;

    if new_recipient_count = 0 then
        raise exception 'No active recipients matched the notification audience';
    end if;

    update public.member_notification_batches b
    set recipient_count = new_recipient_count
    where b.id = new_batch_id;

    insert into public.audit_logs (mess_id, actor_user_id, target_user_id, action, details)
    values (
        actor.mess_id,
        auth.uid(),
        case when target_audience = 'MEMBER' then target_member_id else null end,
        'MEMBER_NOTIFICATION_SENT',
        jsonb_build_object(
            'batch_id', new_batch_id,
            'audience', target_audience,
            'recipient_count', new_recipient_count,
            'delivery', 'IN_APP'
        )
    );

    return query select new_batch_id, new_recipient_count, 'CREATED';
end;
$$;

alter function public.get_extended_erp_snapshot()
    rename to get_extended_erp_snapshot_phase_e;
revoke all on function public.get_extended_erp_snapshot_phase_e() from public, anon, authenticated;

create function public.get_extended_erp_snapshot()
returns jsonb
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $$
declare
    actor_mess_id uuid := public.current_mess_id();
    actor_is_admin boolean;
    base jsonb;
begin
    base := public.get_extended_erp_snapshot_phase_e();
    actor_is_admin := public.is_admin_for_mess(actor_mess_id);
    return base || jsonb_build_object(
        'notification_batches',
        case when actor_is_admin then coalesce((
            select jsonb_agg(jsonb_build_object(
                'id', b.id,
                'audience', b.audience,
                'target_member_id', b.target_member_id,
                'target_member_name', tp.full_name,
                'actor_name', ap.full_name,
                'title', b.title,
                'body', b.body,
                'recipient_count', b.recipient_count,
                'created_at', b.created_at
            ) order by b.created_at desc)
            from (
                select * from public.member_notification_batches nb
                where nb.mess_id = actor_mess_id
                order by nb.created_at desc
                limit 50
            ) b
            join public.profiles ap on ap.user_id = b.actor_user_id and ap.mess_id = b.mess_id
            left join public.profiles tp on tp.user_id = b.target_member_id and tp.mess_id = b.mess_id
        ), '[]'::jsonb) else '[]'::jsonb end
    );
end;
$$;

create policy audit_logs_select_member_notification_admin
on public.audit_logs for select to authenticated
using (
    mess_id = public.current_mess_id()
    and action = 'MEMBER_NOTIFICATION_SENT'
    and public.is_admin_for_mess(mess_id)
);

revoke all on function public.send_member_notification(text, uuid, text, text, uuid) from public, anon;
revoke all on function public.get_extended_erp_snapshot() from public, anon;
grant execute on function public.send_member_notification(text, uuid, text, text, uuid) to authenticated;
grant execute on function public.get_extended_erp_snapshot() to authenticated;

comment on table public.member_notification_batches is
    'Authorized Admin notification batches; durable in-app delivery metadata, not external push proof.';
comment on function public.send_member_notification(text, uuid, text, text, uuid) is
    'Active same-mess Admin/Main Admin only; idempotent, audited in-app notification fan-out.';

commit;
