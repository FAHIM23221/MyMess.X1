-- Rollback-only Phase 3 smoke test. Run only in a disposable/test project.
-- Uses the existing Main Admin identity through request.jwt.claims and leaves no rows.
begin;

do $smoke$
declare
    admin_id uuid;
    smoke_period_id uuid;
    smoke_payment_id uuid;
    smoke_meal_id uuid;
    smoke_expense_id uuid;
    meal_request_id uuid := gen_random_uuid();
    rice_request_id uuid := gen_random_uuid();
    expense_request_id uuid := gen_random_uuid();
    stock_grams bigint;
    cash_inflow bigint;
    cash_outflow bigint;
    cash_rows integer;
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

    smoke_meal_id := public.record_meal_entry(
        admin_id, date '2000-01-15', 'BREAKFAST', 3, 'Rollback-only meal', meal_request_id
    );
    rejected := false;
    begin
        perform public.record_meal_entry(
            admin_id, date '2000-01-15', 'BREAKFAST', 3, 'Duplicate meal', meal_request_id
        );
    exception when unique_violation then rejected := true;
    end;
    if not rejected then raise exception 'Duplicate meal request ID was accepted'; end if;

    perform public.reverse_meal_entry(smoke_meal_id, 'Rollback-only meal correction');
    rejected := false;
    begin
        perform public.reverse_meal_entry(smoke_meal_id, 'Second meal reversal must fail');
    exception when others then
        if position('already been reversed' in sqlerrm) > 0 then rejected := true;
        else raise;
        end if;
    end;
    if not rejected then raise exception 'Double meal reversal was accepted'; end if;

    perform public.record_rice_movement('STOCK_IN', 5000, 'Rollback-only rice purchase', rice_request_id);
    rejected := false;
    begin
        perform public.record_rice_movement('STOCK_IN', 5000, 'Duplicate rice', rice_request_id);
    exception when unique_violation then rejected := true;
    end;
    if not rejected then raise exception 'Duplicate rice request ID was accepted'; end if;
    perform public.record_rice_movement('STOCK_OUT', 2000, 'Rollback-only rice issue', gen_random_uuid());

    select coalesce(sum(case when kind = 'STOCK_IN' then quantity_grams else -quantity_grams end), 0)
    into stock_grams from public.rice_movements where mess_id = public.current_mess_id();
    if stock_grams <> 3000 then raise exception 'Rice stock calculation failed: %', stock_grams; end if;

    rejected := false;
    begin
        perform public.record_rice_movement('STOCK_OUT', 4000, 'Insufficient stock must fail', gen_random_uuid());
    exception when others then
        if position('Insufficient rice stock' in sqlerrm) > 0 then rejected := true;
        else raise;
        end if;
    end;
    if not rejected then raise exception 'Rice stock-out beyond balance was accepted'; end if;

    smoke_period_id := public.create_billing_period('SMOKE 2001-01', date '2001-01-01', date '2001-01-31');
    smoke_payment_id := public.record_payment(
        admin_id, smoke_period_id, 1000, 'CASH', timestamptz '2001-01-15 10:00:00+00',
        'Rollback-only cash book payment', null, gen_random_uuid()
    );
    perform public.reverse_payment(smoke_payment_id, 'Rollback-only cash book reversal');

    smoke_expense_id := public.record_expense(
        'GROCERY', 500, date '2001-01-16', 'Rollback-only expense', 'SMOKE-EXPENSE', expense_request_id
    );
    rejected := false;
    begin
        perform public.record_expense(
            'GROCERY', 500, date '2001-01-16', 'Duplicate expense', null, expense_request_id
        );
    exception when unique_violation then rejected := true;
    end;
    if not rejected then raise exception 'Duplicate expense request ID was accepted'; end if;

    rejected := false;
    begin
        perform public.record_expense(
            'OTHER', 100, (now() at time zone 'Asia/Dhaka')::date + 1,
            'Future expense must fail', null, gen_random_uuid()
        );
    exception when others then
        if position('not in the future' in sqlerrm) > 0 then rejected := true;
        else raise;
        end if;
    end;
    if not rejected then raise exception 'Future expense was accepted'; end if;

    perform public.reverse_expense(smoke_expense_id, 'Rollback-only expense correction');
    rejected := false;
    begin
        perform public.reverse_expense(smoke_expense_id, 'Second expense reversal must fail');
    exception when others then
        if position('already been reversed' in sqlerrm) > 0 then rejected := true;
        else raise;
        end if;
    end;
    if not rejected then raise exception 'Double expense reversal was accepted'; end if;

    select
        coalesce(sum(amount_minor) filter (where direction = 'INFLOW'), 0),
        coalesce(sum(amount_minor) filter (where direction = 'OUTFLOW'), 0),
        count(*)
    into cash_inflow, cash_outflow, cash_rows
    from public.cash_book_entries where mess_id = public.current_mess_id();
    if cash_rows <> 4 or cash_inflow <> 1500 or cash_outflow <> 1500 then
        raise exception 'Cash book trigger totals failed: rows %, inflow %, outflow %',
            cash_rows, cash_inflow, cash_outflow;
    end if;

    set local role authenticated;
    rejected := false;
    begin
        insert into public.cash_book_entries (
            mess_id, source_type, source_id, occurred_at, direction, amount_minor, description
        ) values (
            public.current_mess_id(), 'EXPENSE', gen_random_uuid(), now(), 'OUTFLOW', 1, 'Direct write must fail'
        );
    exception when insufficient_privilege then rejected := true;
    end;
    if not rejected then raise exception 'Direct authenticated cash-book insert was accepted'; end if;
    reset role;

    perform set_config(
        'request.jwt.claims',
        jsonb_build_object('sub', gen_random_uuid(), 'role', 'authenticated')::text,
        true
    );
    rejected := false;
    begin
        perform public.record_expense('OTHER', 100, date '2000-01-01', 'Unauthorized', null, gen_random_uuid());
    exception when insufficient_privilege then rejected := true;
    end;
    if not rejected then raise exception 'Missing-profile identity was not rejected'; end if;
end;
$smoke$;

rollback;
