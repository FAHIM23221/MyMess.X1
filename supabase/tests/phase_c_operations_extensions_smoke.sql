-- Rollback-only Phase C smoke; no real JWT, external files, or retained operational rows.
begin;
do $smoke$
declare
    admin_id uuid;
    schedule_id uuid;
    meal_id uuid;
    purchase_id uuid;
    purchase_again_id uuid;
    smoke_movement_id uuid;
    expense_id uuid;
    cash_entry_id uuid;
    account_id uuid;
    lock_id uuid;
    total_amount bigint;
    rejected boolean;
begin
    select user_id into admin_id from public.main_admin_assignments limit 1;
    if admin_id is null then raise exception 'One Main Admin is required'; end if;
    perform set_config('request.jwt.claims', jsonb_build_object('sub', admin_id, 'role', 'authenticated')::text, true);

    schedule_id := public.set_meal_schedule(admin_id, date '2003-01-10', date '2003-01-20', 'OFF', 'Rollback-only pause');
    rejected := false;
    begin perform public.record_meal_entry(admin_id, date '2003-01-15', 'LUNCH', 2, 'OFF schedule must reject', gen_random_uuid());
    exception when others then if position('OFF schedule' in sqlerrm) > 0 then rejected := true; else raise; end if; end;
    if not rejected then raise exception 'Meal was recorded during an OFF schedule'; end if;
    meal_id := public.record_meal_entry(admin_id, date '2003-01-21', 'LUNCH', 2, 'Post-schedule meal', gen_random_uuid());
    rejected := false;
    begin perform public.set_meal_schedule(admin_id, date '2003-01-15', date '2003-01-25', 'ON', null);
    exception when others then if position('overlaps' in sqlerrm) > 0 then rejected := true; else raise; end if; end;
    if not rejected then raise exception 'Overlapping meal schedule was accepted'; end if;

    insert into public.mess_settings (mess_id, low_rice_threshold_grams, updated_by)
    values (public.current_mess_id(), 10000, admin_id);
    purchase_id := public.record_rice_purchase(5000, 'KG', 1000, 'Rollback Vendor', 'RCPT-C-1', date '2003-02-01', gen_random_uuid());
    select id into purchase_again_id from public.rice_purchases where id = purchase_id;
    if purchase_again_id is null then raise exception 'Rice purchase was not persisted'; end if;
    select rp.total_amount_minor, rp.movement_id, rp.expense_id into total_amount, smoke_movement_id, expense_id
    from public.rice_purchases rp where rp.id = purchase_id;
    if total_amount <> 5000 then raise exception 'Rice purchase total failed: %', total_amount; end if;
    if not exists (select 1 from public.rice_stock_alerts where mess_id = public.current_mess_id() and status = 'OPEN') then
        raise exception 'Low rice alert was not generated';
    end if;
    perform public.review_expense(expense_id, 'APPROVED', null);
    rejected := false;
    begin perform public.review_expense(expense_id, 'REJECTED', null);
    exception when others then if position('rejection reason' in sqlerrm) > 0 then rejected := true; else raise; end if; end;
    if not rejected then raise exception 'Expense rejection without reason was accepted'; end if;

    perform public.record_rice_movement('STOCK_IN', 10000, 'Rollback stock replenishment', gen_random_uuid());
    if not exists (select 1 from public.rice_stock_alerts where mess_id = public.current_mess_id() and status = 'RESOLVED') then
        raise exception 'Low rice alert did not resolve';
    end if;
    perform public.reverse_rice_movement(smoke_movement_id, 'Rollback-only rice correction');
    if not exists (select 1 from public.rice_movement_reversals rr where rr.movement_id = smoke_movement_id) then raise exception 'Linked rice reversal missing'; end if;
    rejected := false;
    begin perform public.reverse_rice_movement(smoke_movement_id, 'Second reversal must fail');
    exception when others then if position('already been reversed' in sqlerrm) > 0 then rejected := true; else raise; end if; end;
    if not rejected then raise exception 'Double rice reversal was accepted'; end if;

    account_id := public.upsert_cash_account('Rollback Cash', 'CASH', 0);
    select id into cash_entry_id from public.cash_book_entries where source_id = expense_id and source_type = 'EXPENSE';
    perform public.assign_cash_entry_account(cash_entry_id, account_id);
    lock_id := public.close_cash_period(date '2003-02-01', date '2003-02-28', 0, 'Rollback-only cash close');
    if lock_id is null then raise exception 'Cash period lock missing'; end if;
    rejected := false;
    begin perform public.record_expense('OTHER', 100, date '2003-02-02', 'Locked cash period must reject', null, gen_random_uuid());
    exception when others then if position('Cash period is locked' in sqlerrm) > 0 then rejected := true; else raise; end if; end;
    if not rejected then raise exception 'Cash entry was accepted in a locked period'; end if;

    set local role authenticated;
    rejected := false;
    begin
        insert into public.notifications (mess_id, recipient_id, kind, title, body)
        values (public.current_mess_id(), admin_id, 'SYSTEM', 'Direct write must fail', 'Direct write must fail');
    exception when insufficient_privilege then rejected := true; end;
    if not rejected then raise exception 'Direct notification insert was accepted'; end if;
    reset role;

    perform set_config('request.jwt.claims', jsonb_build_object('sub', gen_random_uuid(), 'role', 'authenticated')::text, true);
    rejected := false;
    begin perform public.set_meal_schedule(admin_id, date '2003-03-01', null, 'OFF', 'Unauthorized');
    exception when insufficient_privilege then rejected := true; end;
    if not rejected then raise exception 'Missing-profile schedule call was accepted'; end if;
end;
$smoke$;
rollback;
