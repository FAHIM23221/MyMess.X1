-- Rollback-only Admin notification smoke with injected claims.
-- No external push/email provider is contacted and no smoke rows are retained.
begin;
do $smoke$
declare
    admin_id uuid;
    smoke_mess_id uuid;
    request_id uuid := gen_random_uuid();
    smoke_batch_id uuid;
    repeated_batch_id uuid;
    all_batch_id uuid;
    admin_batch_id uuid;
    smoke_recipient_count integer;
    expected_count integer;
    notification_count integer;
    audit_count integer;
    snapshot_count integer;
    result text;
    rejected boolean;
begin
    select a.user_id, a.mess_id into admin_id, smoke_mess_id
    from public.main_admin_assignments a
    limit 1;
    if admin_id is null then
        raise exception 'Smoke test requires one Main Admin assignment';
    end if;
    perform set_config(
        'request.jwt.claims',
        jsonb_build_object('sub', admin_id, 'role', 'authenticated')::text,
        true
    );

    select r.batch_id, r.recipient_count, r.result_status
    into smoke_batch_id, smoke_recipient_count, result
    from public.send_member_notification(
        'MEMBER', admin_id, 'Rollback notification', 'Rollback-only direct notification', request_id
    ) r;
    if result <> 'CREATED' or smoke_batch_id is null or smoke_recipient_count <> 1 then
        raise exception 'Direct notification batch was not created correctly';
    end if;

    select r.batch_id, r.result_status into repeated_batch_id, result
    from public.send_member_notification(
        'MEMBER', admin_id, 'Rollback notification', 'Rollback-only direct notification', request_id
    ) r;
    if result <> 'IDEMPOTENT' or repeated_batch_id <> smoke_batch_id then
        raise exception 'Notification retry was not idempotent';
    end if;
    select count(*) into notification_count
    from public.notifications n where n.batch_id = smoke_batch_id;
    if notification_count <> 1 then
        raise exception 'Notification retry duplicated a recipient row';
    end if;

    rejected := false;
    begin
        perform public.send_member_notification(
            'MEMBER', admin_id, 'Changed notification', 'Different body for the same request', request_id
        );
    exception when others then
        if position('different notification data' in sqlerrm) > 0 then rejected := true;
        else raise;
        end if;
    end;
    if not rejected then
        raise exception 'A request ID was reused for different notification data';
    end if;

    select count(*) into expected_count
    from public.profiles p
    where p.mess_id = smoke_mess_id and p.account_status = 'ACTIVE' and p.departed_at is null;
    select r.batch_id, r.recipient_count into all_batch_id, smoke_recipient_count
    from public.send_member_notification(
        'ALL', null, 'Rollback all-member notice', 'Rollback-only all-member notification', gen_random_uuid()
    ) r;
    if all_batch_id is null or smoke_recipient_count <> expected_count then
        raise exception 'All-member fan-out count is incorrect';
    end if;
    select count(*) into notification_count
    from public.notifications n where n.batch_id = all_batch_id;
    if notification_count <> expected_count then
        raise exception 'All-member fan-out rows are incomplete';
    end if;

    select r.batch_id into admin_batch_id
    from public.send_member_notification(
        'ADMINS', null, 'Rollback Admin notice', 'Rollback-only Admin-audience notification', gen_random_uuid()
    ) r;
    if not exists (
        select 1 from public.notifications n
        join public.member_notification_batches b on b.id = n.batch_id
        where b.mess_id = smoke_mess_id and b.audience = 'ADMINS' and n.recipient_id = admin_id
    ) then
        raise exception 'Effective Main Admin was omitted from the Admin audience';
    end if;

    select count(*) into audit_count
    from public.audit_logs a
    where a.mess_id = smoke_mess_id and a.action = 'MEMBER_NOTIFICATION_SENT'
      and a.actor_user_id = admin_id
      and a.details->>'batch_id' in (smoke_batch_id::text, all_batch_id::text, admin_batch_id::text);
    if audit_count <> 3 then
        raise exception 'Expected one audit event per distinct batch, found %', audit_count;
    end if;

    select count(*) into snapshot_count
    from jsonb_array_elements(public.get_extended_erp_snapshot() -> 'notification_batches') b
    where b->>'id' in (smoke_batch_id::text, all_batch_id::text, admin_batch_id::text);
    if snapshot_count <> 3 then
        raise exception 'Admin snapshot did not expose the three batch-history rows';
    end if;

    rejected := false;
    begin
        perform public.send_member_notification(
            'MEMBER', gen_random_uuid(), 'Cross-mess target', 'A random non-member recipient must fail', gen_random_uuid()
        );
    exception when others then
        if position('active same-mess recipient' in sqlerrm) > 0 then rejected := true;
        else raise;
        end if;
    end;
    if not rejected then
        raise exception 'A non-member notification recipient was accepted';
    end if;

    set local role authenticated;
    select count(*) into audit_count
    from public.audit_logs a
    where a.mess_id = smoke_mess_id and a.action = 'MEMBER_NOTIFICATION_SENT'
      and a.details->>'batch_id' in (smoke_batch_id::text, all_batch_id::text, admin_batch_id::text);
    if audit_count <> 3 then
        raise exception 'Active Admin could not read the notification audit events';
    end if;
    rejected := false;
    begin
        insert into public.member_notification_batches (
            mess_id, actor_user_id, client_request_id, audience, target_member_id,
            title, body, request_fingerprint, recipient_count
        ) values (
            smoke_mess_id, admin_id, gen_random_uuid(), 'MEMBER', admin_id,
            'Direct write', 'Direct batch writes must fail', repeat('a', 64), 0
        );
    exception when insufficient_privilege then rejected := true;
    end;
    reset role;
    if not rejected then
        raise exception 'Direct notification-batch insert was accepted';
    end if;

    perform set_config(
        'request.jwt.claims',
        jsonb_build_object('sub', gen_random_uuid(), 'role', 'authenticated')::text,
        true
    );
    rejected := false;
    begin
        perform public.send_member_notification(
            'ALL', null, 'Unauthorized notice', 'A missing-profile actor must fail', gen_random_uuid()
        );
    exception when insufficient_privilege then rejected := true;
    end;
    if not rejected then
        raise exception 'A missing-profile notification actor was accepted';
    end if;
end;
$smoke$;
rollback;

do $verify_rollback$
begin
    if exists (
        select 1 from public.member_notification_batches b
        where b.title like 'Rollback%' or b.title in ('Direct write', 'Changed notification')
    ) then
        raise exception 'Notification smoke rows were retained';
    end if;
end;
$verify_rollback$;
