-- Cash locks use the source business date, not the time a historical row was entered.
create or replace function public.cash_entry_business_date(
    source_kind text,
    source_uuid uuid,
    fallback_at timestamptz
) returns date language plpgsql stable security definer set search_path = public, pg_temp as $$
declare business_date date;
begin
    if source_kind = 'PAYMENT' then
        select (p.paid_at at time zone 'Asia/Dhaka')::date into business_date
        from public.payments p where p.id = source_uuid;
    elsif source_kind = 'PAYMENT_REVERSAL' then
        select (p.paid_at at time zone 'Asia/Dhaka')::date into business_date
        from public.payment_reversals pr join public.payments p on p.id = pr.payment_id
        where pr.id = source_uuid;
    elsif source_kind = 'EXPENSE' then
        select e.spent_on into business_date from public.expenses e where e.id = source_uuid;
    elsif source_kind = 'EXPENSE_REVERSAL' then
        select e.spent_on into business_date
        from public.expense_reversals er join public.expenses e on e.id = er.expense_id
        where er.id = source_uuid;
    end if;
    return coalesce(business_date, (fallback_at at time zone 'Asia/Dhaka')::date);
end;
$$;

create or replace function public.cash_period_lock_guard()
returns trigger language plpgsql security definer set search_path = public, pg_temp as $$
begin
    if exists (
        select 1 from public.cash_period_locks l
        where l.mess_id = new.mess_id
          and public.cash_entry_business_date(new.source_type, new.source_id, new.occurred_at)
              between l.starts_on and l.ends_on
    ) then
        raise exception 'Cash period is locked for this date';
    end if;
    return new;
end;
$$;

create or replace function public.close_cash_period(
    target_start date,
    target_end date,
    target_opening_balance_minor bigint,
    target_note text
) returns uuid language plpgsql security definer set search_path = public, auth as $$
declare
    actor_mess_id uuid := public.current_mess_id();
    closing_balance bigint;
    lock_id uuid;
begin
    if auth.uid() is null or actor_mess_id is null or not public.is_admin_for_mess(actor_mess_id) then raise exception 'Active Admin or Main Admin access required' using errcode = '42501'; end if;
    if target_start is null or target_end is null or target_end < target_start then raise exception 'Invalid cash period dates'; end if;
    if target_opening_balance_minor is null or abs(target_opening_balance_minor) > 1000000000000 then raise exception 'Invalid opening balance'; end if;
    if target_note is not null and char_length(trim(target_note)) not between 8 and 500 then raise exception 'Invalid cash close note'; end if;
    perform pg_advisory_xact_lock(hashtext(actor_mess_id::text || ':cash-close'));
    if exists (select 1 from public.cash_period_locks l where l.mess_id = actor_mess_id
        and target_start <= l.ends_on and target_end >= l.starts_on) then raise exception 'Cash period overlaps an existing lock'; end if;
    select target_opening_balance_minor + coalesce(sum(case when direction = 'INFLOW' then amount_minor else -amount_minor end), 0)
    into closing_balance from public.cash_book_entries cb
    where cb.mess_id = actor_mess_id
      and public.cash_entry_business_date(cb.source_type, cb.source_id, cb.occurred_at) between target_start and target_end;
    insert into public.cash_period_locks (mess_id, starts_on, ends_on, opening_balance_minor, closing_balance_minor, note, locked_by)
    values (actor_mess_id, target_start, target_end, target_opening_balance_minor, closing_balance, nullif(trim(target_note), ''), auth.uid())
    returning id into lock_id;
    insert into public.audit_logs (mess_id, actor_user_id, action, target_user_id, details)
    values (actor_mess_id, auth.uid(), 'CASH_PERIOD_LOCKED', auth.uid(), jsonb_build_object('lock_id', lock_id, 'starts_on', target_start, 'ends_on', target_end, 'closing_balance_minor', closing_balance));
    return lock_id;
end;
$$;

revoke all on function public.cash_entry_business_date(text, uuid, timestamptz) from public, anon, authenticated;
revoke all on function public.cash_period_lock_guard() from public, anon, authenticated;
revoke all on function public.close_cash_period(date, date, bigint, text) from public, anon;
grant execute on function public.close_cash_period(date, date, bigint, text) to authenticated;
