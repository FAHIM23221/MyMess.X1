-- Rollback-only Phase A member-management smoke test.
-- It does not create Auth users or send email. Run only in a disposable/test project.
begin;

do $smoke$
declare
    admin_id uuid;
    smoke_mess_id uuid;
    run_id text := gen_random_uuid()::text;
    request_id uuid := gen_random_uuid();
    invitation_id uuid;
    repeated_invitation_id uuid;
    result text;
    current_total integer;
    admin_total integer;
    fill_count integer;
    audit_count integer;
    rejected boolean;
    i integer;
begin
    select a.user_id, a.mess_id into admin_id, smoke_mess_id
    from public.main_admin_assignments a limit 1;
    if admin_id is null then
        raise exception 'Smoke test requires one Main Admin assignment';
    end if;
    perform set_config(
        'request.jwt.claims',
        jsonb_build_object('sub', admin_id, 'role', 'authenticated')::text,
        true
    );

    select count(*) into current_total
    from public.list_member_management_profiles()
    where user_id = admin_id;
    if current_total <> 1 then
        raise exception 'Admin member-management list did not return the Main Admin';
    end if;

    select r.invitation_id, r.result_status into invitation_id, result
    from public.request_member_onboarding(
        'Phase A Smoke', 'phase-a-' || run_id || '-one@example.invalid',
        '+8801000000000', 'SA-' || run_id, current_date, 'MEMBER', request_id
    ) r;
    if result <> 'PENDING' or invitation_id is null then
        raise exception 'Expected a pending invitation without an Auth user';
    end if;

    select r.invitation_id, r.result_status into repeated_invitation_id, result
    from public.request_member_onboarding(
        'Phase A Smoke', 'phase-a-' || run_id || '-one@example.invalid',
        '+8801000000000', 'SA-' || run_id, current_date, 'MEMBER', request_id
    ) r;
    if repeated_invitation_id <> invitation_id or result <> 'PENDING' then
        raise exception 'Client request ID was not idempotent';
    end if;

    rejected := false;
    begin
        perform public.request_member_onboarding(
            'Duplicate Email', 'phase-a-' || run_id || '-one@example.invalid',
            '+8801000000000', 'SA-DUP-' || run_id, current_date, 'MEMBER', gen_random_uuid()
        );
    exception when unique_violation then rejected := true;
    end;
    if not rejected then raise exception 'Duplicate pending email was accepted'; end if;

    rejected := false;
    begin
        perform public.request_member_onboarding(
            'Duplicate Member ID', 'phase-a-' || run_id || '-two@example.invalid',
            '+8801000000000', 'SA-' || run_id, current_date, 'MEMBER', gen_random_uuid()
        );
    exception when unique_violation then rejected := true;
    end;
    if not rejected then raise exception 'Duplicate pending member ID was accepted'; end if;

    select
      (select count(*) from public.profiles p
       left join public.main_admin_assignments m
         on m.mess_id = p.mess_id and m.user_id = p.user_id
       where p.mess_id = smoke_mess_id and p.departed_at is null
         and p.role = 'ADMIN' and m.user_id is null)
      + (select count(*) from public.member_invitations i
         where i.mess_id = smoke_mess_id and i.status = 'PENDING' and i.intended_role = 'ADMIN')
    into admin_total;
    if admin_total > 5 then raise exception 'Baseline Admin capacity is invalid'; end if;
    fill_count := 5 - admin_total;
    for i in 1..fill_count loop
        insert into public.member_invitations (
            mess_id, email, full_name, phone_number, member_id, join_date,
            intended_role, invited_by, client_request_id
        ) values (
            smoke_mess_id, 'phase-a-' || run_id || '-admin-' || i || '@example.invalid',
            'Smoke Admin ' || i, '+8801000000000', 'SAA-' || run_id || '-' || i,
            current_date, 'ADMIN', admin_id, gen_random_uuid()
        );
    end loop;

    rejected := false;
    begin
        perform public.request_member_onboarding(
            'Over Admin Capacity', 'phase-a-' || run_id || '-admin-over@example.invalid',
            '+8801000000000', 'SAO-' || run_id, current_date, 'ADMIN', gen_random_uuid()
        );
    exception when others then
        if position('Admin capacity reached' in sqlerrm) > 0 then rejected := true;
        else raise;
        end if;
    end;
    if not rejected then raise exception 'Sixth effective Admin was accepted'; end if;

    update public.member_invitations
    set status = 'CANCELLED'
    where mess_id = smoke_mess_id and invited_by = admin_id
      and email like 'phase-a-' || run_id || '-admin-%';

    select
      (select count(*) from public.profiles p
       where p.mess_id = smoke_mess_id and p.departed_at is null)
      + (select count(*) from public.member_invitations i
         where i.mess_id = smoke_mess_id and i.status = 'PENDING')
    into current_total;
    if current_total >= 20 then
        raise exception 'Smoke test requires at least one free member slot; no rows were retained';
    end if;
    fill_count := 20 - current_total;
    for i in 1..fill_count loop
        insert into public.member_invitations (
            mess_id, email, full_name, phone_number, member_id, join_date,
            intended_role, invited_by, client_request_id
        ) values (
            smoke_mess_id, 'phase-a-' || run_id || '-member-' || i || '@example.invalid',
            'Smoke Member ' || i, '+8801000000000', 'SAM-' || run_id || '-' || i,
            current_date, 'MEMBER', admin_id, gen_random_uuid()
        );
    end loop;

    rejected := false;
    begin
        perform public.request_member_onboarding(
            'Over Member Capacity', 'phase-a-' || run_id || '-member-over@example.invalid',
            '+8801000000000', 'SAMO-' || run_id, current_date, 'MEMBER', gen_random_uuid()
        );
    exception when others then
        if position('Mess capacity reached' in sqlerrm) > 0 then rejected := true;
        else raise;
        end if;
    end;
    if not rejected then raise exception 'Twenty-first effective member was accepted'; end if;

    update public.member_invitations
    set status = 'CANCELLED'
    where mess_id = smoke_mess_id and invited_by = admin_id
      and email = 'phase-a-' || run_id || '-member-1@example.invalid';
    select r.result_status into result
    from public.request_member_onboarding(
        'Capacity Released', 'phase-a-' || run_id || '-released@example.invalid',
        '+8801000000000', 'SAR-' || run_id, current_date, 'MEMBER', gen_random_uuid()
    ) r;
    if result <> 'PENDING' then raise exception 'Cancellation did not release member capacity'; end if;

    select count(*) into audit_count from public.audit_logs
    where mess_id = smoke_mess_id and action = 'MEMBER_INVITATION_CREATED'
      and details->>'invitation_id' = invitation_id::text;
    if audit_count <> 1 then raise exception 'Invitation audit row was not written'; end if;

    set local role authenticated;
    rejected := false;
    begin
        insert into public.member_invitations (
            mess_id, email, full_name, phone_number, member_id, join_date,
            intended_role, invited_by, client_request_id
        ) values (
            smoke_mess_id, 'phase-a-' || run_id || '-direct@example.invalid',
            'Direct Write', '+8801000000000', 'SAD-' || run_id,
            current_date, 'MEMBER', admin_id, gen_random_uuid()
        );
    exception when insufficient_privilege then rejected := true;
    end;
    if not rejected then raise exception 'Direct authenticated invitation insert was accepted'; end if;

    rejected := false;
    begin
        perform public.assert_member_management_capacity(smoke_mess_id, 'MEMBER');
    exception when insufficient_privilege then rejected := true;
    end;
    if not rejected then raise exception 'Internal capacity helper was callable by authenticated role'; end if;
    reset role;

    perform set_config(
        'request.jwt.claims',
        jsonb_build_object('sub', gen_random_uuid(), 'role', 'authenticated')::text,
        true
    );
    rejected := false;
    begin
        perform public.request_member_onboarding(
            'Unauthorized', 'phase-a-' || run_id || '-unauthorized@example.invalid',
            '+8801000000000', 'SAU-' || run_id, current_date, 'MEMBER', gen_random_uuid()
        );
    exception when others then
        if position('Active Admin or Main Admin access required' in sqlerrm) > 0 then rejected := true;
        else raise;
        end if;
    end;
    if not rejected then raise exception 'Missing-profile identity was not rejected'; end if;
end;
$smoke$;

rollback;
