-- Phase F: authorized monthly aggregates and a read-only, integrity-stamped export.
-- This is not a destructive restore or a replacement for Supabase physical backups.
create or replace function public.get_monthly_report(target_start date, target_end date)
returns jsonb language plpgsql security definer set search_path = public, auth, pg_temp as $$
declare
    actor_mess_id uuid := public.current_mess_id();
    actor_is_admin boolean;
    summary jsonb;
    member_rows jsonb;
    category_rows jsonb;
begin
    if auth.uid() is null or actor_mess_id is null or not public.is_active_user() then
        raise exception 'An active member session is required' using errcode = '42501';
    end if;
    if target_start is null or target_end is null or target_end < target_start or target_end > target_start + 366 then
        raise exception 'A report range of at most 366 days is required';
    end if;
    actor_is_admin := public.is_admin_for_mess(actor_mess_id);
    select jsonb_build_object(
        'charges_minor', coalesce((select sum(c.amount_minor) from public.charges c
            join public.billing_periods bp on bp.id = c.billing_period_id
            where c.mess_id = actor_mess_id and bp.starts_on <= target_end and bp.ends_on >= target_start
              and (actor_is_admin or c.member_id = auth.uid())), 0),
        'payments_minor', coalesce((select sum(p.amount_minor) from public.payments p
            where p.mess_id = actor_mess_id and (p.paid_at at time zone 'Asia/Dhaka')::date between target_start and target_end
              and not exists(select 1 from public.payment_reversals pr where pr.payment_id = p.id)
              and (actor_is_admin or p.member_id = auth.uid())), 0),
        'meal_units', coalesce((select sum(me.quantity_units) from public.meal_entries me
            where me.mess_id = actor_mess_id and me.meal_date between target_start and target_end
              and not exists(select 1 from public.meal_reversals mr where mr.meal_entry_id = me.id)
              and (actor_is_admin or me.member_id = auth.uid())), 0),
        'rice_in_grams', coalesce((select sum(rm.quantity_grams) from public.rice_movements rm
            where rm.mess_id = actor_mess_id and rm.kind = 'STOCK_IN'
              and (rm.created_at at time zone 'Asia/Dhaka')::date between target_start and target_end), 0),
        'rice_out_grams', coalesce((select sum(rm.quantity_grams) from public.rice_movements rm
            where rm.mess_id = actor_mess_id and rm.kind = 'STOCK_OUT'
              and (rm.created_at at time zone 'Asia/Dhaka')::date between target_start and target_end), 0),
        'expenses_minor', coalesce((select sum(e.amount_minor) from public.expenses e
            where e.mess_id = actor_mess_id and e.spent_on between target_start and target_end
              and not exists(select 1 from public.expense_reversals er where er.expense_id = e.id)), 0),
        'cash_in_minor', coalesce((select sum(cb.amount_minor) from public.cash_book_entries cb
            where cb.mess_id = actor_mess_id and cb.direction = 'INFLOW'
              and public.cash_entry_business_date(cb.source_type, cb.source_id, cb.occurred_at) between target_start and target_end), 0),
        'cash_out_minor', coalesce((select sum(cb.amount_minor) from public.cash_book_entries cb
            where cb.mess_id = actor_mess_id and cb.direction = 'OUTFLOW'
              and public.cash_entry_business_date(cb.source_type, cb.source_id, cb.occurred_at) between target_start and target_end), 0)
    ) into summary;

    select coalesce(jsonb_agg(jsonb_build_object(
        'id', p.user_id, 'member_id', p.member_id, 'member_name', p.full_name,
        'charges_minor', coalesce((select sum(c.amount_minor) from public.charges c
            join public.billing_periods bp on bp.id = c.billing_period_id
            where c.mess_id = actor_mess_id and c.member_id = p.user_id
              and bp.starts_on <= target_end and bp.ends_on >= target_start), 0),
        'payments_minor', coalesce((select sum(py.amount_minor) from public.payments py
            where py.mess_id = actor_mess_id and py.member_id = p.user_id
              and (py.paid_at at time zone 'Asia/Dhaka')::date between target_start and target_end
              and not exists(select 1 from public.payment_reversals pr where pr.payment_id = py.id)), 0),
        'meal_units', coalesce((select sum(me.quantity_units) from public.meal_entries me
            where me.mess_id = actor_mess_id and me.member_id = p.user_id
              and me.meal_date between target_start and target_end
              and not exists(select 1 from public.meal_reversals mr where mr.meal_entry_id = me.id)), 0)
    ) order by lower(p.full_name)), '[]'::jsonb) into member_rows
    from public.profiles p
    where p.mess_id = actor_mess_id and (actor_is_admin or p.user_id = auth.uid());

    with rows as (
        select 'CHARGE' as kind, c.category as category, sum(c.amount_minor) as amount_minor
        from public.charges c join public.billing_periods bp on bp.id = c.billing_period_id
        where c.mess_id = actor_mess_id and bp.starts_on <= target_end and bp.ends_on >= target_start
          and (actor_is_admin or c.member_id = auth.uid()) group by c.category
        union all
        select 'EXPENSE', e.category, sum(e.amount_minor)
        from public.expenses e
        where e.mess_id = actor_mess_id and e.spent_on between target_start and target_end
          and not exists(select 1 from public.expense_reversals er where er.expense_id = e.id)
        group by e.category
    )
    select coalesce(jsonb_agg(jsonb_build_object(
        'id', kind || ':' || category, 'kind', kind, 'category', category, 'amount_minor', amount_minor
    ) order by kind, category), '[]'::jsonb) into category_rows from rows;

    return jsonb_build_object(
        'start_date', target_start, 'end_date', target_end, 'timezone', 'Asia/Dhaka',
        'scope', case when actor_is_admin then 'MESS' else 'MEMBER' end,
        'generated_at', now(), 'summary', summary, 'members', member_rows, 'categories', category_rows
    );
end;
$$;

create or replace function public.export_erp_backup()
returns jsonb language plpgsql security definer set search_path = public, auth, pg_temp, extensions as $$
declare
    actor_mess_id uuid := public.current_mess_id();
    payload jsonb;
    payload_text text;
begin
    if auth.uid() is null or actor_mess_id is null or not public.is_active_user() then
        raise exception 'An active member session is required' using errcode = '42501';
    end if;
    payload := jsonb_build_object(
        'snapshot', public.get_extended_erp_snapshot(),
        'export_scope', case when public.is_admin_for_mess(actor_mess_id) then 'AUTHORIZED_MESS_DATA' else 'AUTHORIZED_MEMBER_DATA' end,
        'physical_backup_included', false,
        'restore_supported', false
    );
    if public.current_app_role() = 'MAIN_ADMIN' then
        payload := payload || jsonb_build_object('audit_logs', coalesce((select jsonb_agg(jsonb_build_object(
            'id', a.id, 'actor_user_id', a.actor_user_id, 'actor_type', a.actor_type,
            'action', a.action, 'target_user_id', a.target_user_id, 'reason', a.reason,
            'details', a.details, 'created_at', a.created_at
        ) order by a.created_at desc) from public.audit_logs a where a.mess_id = actor_mess_id), '[]'::jsonb));
    end if;
    payload_text := payload::text;
    return jsonb_build_object(
        'format_version', 1, 'generated_at', now(), 'mess_id', actor_mess_id,
        'role', public.current_app_role(), 'payload', payload_text,
        'integrity_algorithm', 'SHA-256',
        'sha256', encode(digest(payload_text, 'sha256'), 'hex'),
        'restore_supported', false
    );
end;
$$;

revoke all on function public.get_monthly_report(date, date) from public, anon;
revoke all on function public.export_erp_backup() from public, anon;
grant execute on function public.get_monthly_report(date, date) to authenticated;
grant execute on function public.export_erp_backup() to authenticated;
