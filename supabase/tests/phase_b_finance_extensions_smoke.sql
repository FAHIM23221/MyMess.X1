-- Rollback-only Phase B smoke. Uses injected claims, never a real JWT or production data.
begin;
do $smoke$
declare
    admin_id uuid;
    period_id uuid;
    closed_period_id uuid;
    fine_id uuid;
    fine_again_id uuid;
    payment_id uuid;
    run_id uuid;
    run_again_id uuid;
    meal_id uuid;
    request_id uuid := gen_random_uuid();
    charge_count integer;
    total_amount bigint;
    rejected boolean;
begin
    select user_id into admin_id from public.main_admin_assignments limit 1;
    if admin_id is null then raise exception 'One Main Admin is required'; end if;
    perform set_config('request.jwt.claims', jsonb_build_object('sub', admin_id, 'role', 'authenticated')::text, true);

    period_id := public.create_billing_period('SMOKE B 2002-01', date '2002-01-01', date '2002-01-31');
    fine_id := public.create_fine(admin_id, period_id, 1250, 'Rollback-only fine reason', date '2002-01-31', request_id);
    fine_again_id := public.create_fine(admin_id, period_id, 1250, 'Rollback-only fine reason', date '2002-01-31', request_id);
    if fine_id <> fine_again_id then raise exception 'Fine request was not idempotent'; end if;

    payment_id := public.record_payment(admin_id, period_id, 500, 'CASH', timestamptz '2002-01-15 10:00:00+00', 'Rollback-only review', 'SMOKE-B-PAY', gen_random_uuid());
    perform public.review_payment(payment_id, 'VERIFIED', null);
    rejected := false;
    begin perform public.review_payment(payment_id, 'REJECTED', null);
    exception when others then
        if position('rejection reason' in sqlerrm) > 0 then rejected := true; else raise; end if;
    end;
    if not rejected then raise exception 'Rejected payment without a reason was accepted'; end if;

    perform public.set_meal_billing_policy(250, true);
    meal_id := public.record_meal_entry(admin_id, date '2002-01-15', 'LUNCH', 3, 'Rollback-only billed meal', gen_random_uuid());
    run_id := public.generate_meal_bill(period_id, date '2002-01-31');
    run_again_id := public.generate_meal_bill(period_id, date '2002-01-31');
    if run_id <> run_again_id then raise exception 'Meal billing run was not idempotent'; end if;
    select total_amount_minor into total_amount from public.meal_billing_runs where id = run_id;
    if total_amount <> 750 then raise exception 'Unexpected meal bill amount: %', total_amount; end if;
    select count(*) into charge_count from public.charges where meal_billing_run_id = run_id;
    if charge_count <> 1 then raise exception 'Unexpected meal charge count: %', charge_count; end if;

    perform public.reverse_meal_entry(meal_id, 'Rollback-only billed meal correction');
    perform public.reverse_payment(payment_id, 'Rollback-only reviewed payment reversal');
    rejected := false;
    begin perform public.review_payment(payment_id, 'VERIFIED', null);
    exception when others then
        if position('reversed payment' in sqlerrm) > 0 then rejected := true; else raise; end if;
    end;
    if not rejected then raise exception 'Reversed payment was reviewed'; end if;

    closed_period_id := public.create_billing_period('SMOKE B 2002-02', date '2002-02-01', date '2002-02-28');
    perform public.close_billing_period(closed_period_id);
    rejected := false;
    begin perform public.generate_meal_bill(closed_period_id, null);
    exception when others then
        if position('closed period' in sqlerrm) > 0 then rejected := true; else raise; end if;
    end;
    if not rejected then raise exception 'Closed period meal bill was accepted'; end if;

    set local role authenticated;
    rejected := false;
    begin
        insert into public.fines (mess_id, charge_id, member_id, billing_period_id, amount_minor, reason, created_by)
        values (public.current_mess_id(), (select id from public.charges limit 1), admin_id, period_id, 1, 'Direct write must fail', admin_id);
    exception when insufficient_privilege then rejected := true;
    end;
    if not rejected then raise exception 'Direct fine insert was accepted'; end if;
    reset role;

    perform set_config('request.jwt.claims', jsonb_build_object('sub', gen_random_uuid(), 'role', 'authenticated')::text, true);
    rejected := false;
    begin perform public.create_fine(admin_id, period_id, 100, 'Unauthorized fine attempt', null, gen_random_uuid());
    exception when insufficient_privilege then rejected := true;
    end;
    if not rejected then raise exception 'Missing-profile fine call was accepted'; end if;
end;
$smoke$;
rollback;
