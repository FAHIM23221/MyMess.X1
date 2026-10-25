-- Rollback-only Phase 2 smoke test. Run only in a disposable/test project.
-- It uses the existing Main Admin identity through request.jwt.claims and leaves no rows.
begin;

do $smoke$
declare
    admin_id uuid;
    period_one uuid;
    period_two uuid;
    period_three uuid;
    smoke_payment_id uuid;
    payment_request_id uuid := gen_random_uuid();
    due_minor bigint;
    advance_minor bigint;
    opening_minor bigint;
    credit_minor bigint;
    rejected boolean;
begin
    select user_id into admin_id from public.main_admin_assignments limit 1;
    if admin_id is null then
        raise exception 'Smoke test requires one Main Admin assignment';
    end if;

    perform set_config(
        'request.jwt.claims',
        jsonb_build_object('sub', admin_id, 'role', 'authenticated')::text,
        true
    );

    period_one := public.create_billing_period('SMOKE 2000-01', date '2000-01-01', date '2000-01-31');
    perform public.create_charge(
        admin_id, period_one, 'ELECTRICITY', 1000, 'Rollback-only smoke charge', null, gen_random_uuid()
    );
    smoke_payment_id := public.record_payment(
        admin_id, period_one, 400, 'BKASH', timestamptz '2000-01-15 10:00:00+00',
        'Rollback-only smoke payment', null, payment_request_id
    );

    select outstanding_due_minor, advance_credit_minor into due_minor, advance_minor
    from public.my_financial_summary where billing_period_id = period_one;
    if not found or due_minor <> 600 or advance_minor <> 0 then
        raise exception 'Partial-payment calculation failed: due %, advance %', due_minor, advance_minor;
    end if;

    rejected := false;
    begin
        perform public.record_payment(
            admin_id, period_one, 400, 'BKASH', timestamptz '2000-01-15 10:00:00+00',
            'Duplicate must roll back', null, payment_request_id
        );
    exception when unique_violation then
        rejected := true;
    end;
    if not rejected then
        raise exception 'Duplicate payment request ID was accepted';
    end if;

    perform public.reverse_payment(smoke_payment_id, 'Rollback-only reversal verification');

    rejected := false;
    begin
        perform public.reverse_payment(smoke_payment_id, 'Second reversal must be rejected');
    exception when others then
        if position('already been reversed' in sqlerrm) > 0 then
            rejected := true;
        else
            raise;
        end if;
    end;
    if not rejected then
        raise exception 'Double payment reversal was accepted';
    end if;

    select outstanding_due_minor, advance_credit_minor into due_minor, advance_minor
    from public.my_financial_summary where billing_period_id = period_one;
    if due_minor <> 1000 or advance_minor <> 0 then
        raise exception 'Reversal was not excluded from valid payments';
    end if;
    if not exists (
        select 1 from public.receipts
        where receipts.payment_id = smoke_payment_id and voided_at is not null and void_reason is not null
    ) then
        raise exception 'Reversed payment receipt was not voided';
    end if;

    perform public.close_billing_period(period_one);

    rejected := false;
    begin
        perform public.create_charge(
            admin_id, period_one, 'OTHER', 1, 'Closed period must reject', null, gen_random_uuid()
        );
    exception when others then
        if position('Open billing period not found' in sqlerrm) > 0 then
            rejected := true;
        else
            raise;
        end if;
    end;
    if not rejected then
        raise exception 'Closed-period charge was accepted';
    end if;

    period_two := public.create_billing_period('SMOKE 2000-02', date '2000-02-01', date '2000-02-29');
    perform public.carry_forward_balance(admin_id, period_one, period_two);

    select opening_due_minor, outstanding_due_minor into opening_minor, due_minor
    from public.my_financial_summary where billing_period_id = period_two;
    if opening_minor <> 1000 or due_minor <> 1000 then
        raise exception 'Due carry-forward failed';
    end if;

    rejected := false;
    begin
        perform public.carry_forward_balance(admin_id, period_one, period_two);
    exception when unique_violation then
        rejected := true;
    end;
    if not rejected then
        raise exception 'Duplicate carry-forward was accepted';
    end if;

    perform public.record_payment(
        admin_id, period_two, 1500, 'NAGAD', timestamptz '2000-02-15 10:00:00+00',
        'Rollback-only overpayment', 'SMOKE-REFERENCE', gen_random_uuid()
    );
    select outstanding_due_minor, advance_credit_minor into due_minor, advance_minor
    from public.my_financial_summary where billing_period_id = period_two;
    if due_minor <> 0 or advance_minor <> 500 then
        raise exception 'Overpayment did not become advance credit';
    end if;

    perform public.close_billing_period(period_two);
    period_three := public.create_billing_period('SMOKE 2000-03', date '2000-03-01', date '2000-03-31');
    perform public.carry_forward_balance(admin_id, period_two, period_three);

    select credit_adjustments_minor, advance_credit_minor into credit_minor, advance_minor
    from public.my_financial_summary where billing_period_id = period_three;
    if credit_minor <> 500 or advance_minor <> 500 then
        raise exception 'Advance-credit carry-forward failed';
    end if;

    rejected := false;
    begin
        perform public.record_payment(
            admin_id, period_three, 100, 'CASH', now() + interval '1 day',
            'Future payment must be rejected', null, gen_random_uuid()
        );
    exception when others then
        if position('Future payment time is not allowed' in sqlerrm) > 0 then
            rejected := true;
        else
            raise;
        end if;
    end;
    if not rejected then
        raise exception 'Future payment timestamp was accepted';
    end if;

    perform set_config(
        'request.jwt.claims',
        jsonb_build_object('sub', gen_random_uuid(), 'role', 'authenticated')::text,
        true
    );
    rejected := false;
    begin
        perform public.create_billing_period('UNAUTHORIZED', date '2000-04-01', date '2000-04-30');
    exception when insufficient_privilege then
        rejected := true;
    end;
    if not rejected then
        raise exception 'Missing-profile identity was not rejected';
    end if;
end;
$smoke$;

rollback;
