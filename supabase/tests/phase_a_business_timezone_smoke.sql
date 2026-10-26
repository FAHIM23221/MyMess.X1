-- Rollback-only regression for the Asia/Dhaka join-date window.
begin;
set local timezone = 'UTC';
do $smoke$
declare
    admin_id uuid;
    run_id text := gen_random_uuid()::text;
    result text;
    invitation_id uuid;
    rejected boolean;
begin
    select user_id into admin_id from public.main_admin_assignments limit 1;
    perform set_config('request.jwt.claims', jsonb_build_object('sub', admin_id, 'role', 'authenticated')::text, true);
    select r.invitation_id, r.result_status into invitation_id, result
    from public.request_member_onboarding(
        'Timezone Smoke', 'timezone-' || run_id || '@example.invalid', '+8801000000000',
        'TZ-' || run_id, ((now() at time zone 'Asia/Dhaka')::date + 365), 'MEMBER', gen_random_uuid()
    ) r;
    if result <> 'PENDING' or invitation_id is null then
        raise exception 'Asia/Dhaka upper join-date boundary was rejected';
    end if;
    rejected := false;
    begin
        perform public.request_member_onboarding(
            'Timezone Smoke', 'timezone-over-' || run_id || '@example.invalid', '+8801000000000',
            'TZO-' || run_id, ((now() at time zone 'Asia/Dhaka')::date + 366), 'MEMBER', gen_random_uuid()
        );
    exception when others then
        if position('outside the supported range' in sqlerrm) > 0 then rejected := true; else raise; end if;
    end;
    if not rejected then raise exception 'Out-of-range join date was accepted'; end if;
end;
$smoke$;
rollback;
