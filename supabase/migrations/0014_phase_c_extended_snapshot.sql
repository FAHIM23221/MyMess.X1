-- Phase C snapshot, settings, and recipient-owned notification reads.
create or replace function public.update_mess_settings(
    target_locale text,
    target_low_rice_threshold_grams integer,
    target_meal_reminders_enabled boolean
) returns void language plpgsql security definer set search_path = public, auth as $$
declare actor_mess_id uuid := public.current_mess_id();
begin
    if auth.uid() is null or actor_mess_id is null or not public.is_admin_for_mess(actor_mess_id) then
        raise exception 'Active Admin or Main Admin access required' using errcode = '42501';
    end if;
    if target_locale not in ('bn', 'en') then raise exception 'Unsupported locale'; end if;
    if target_low_rice_threshold_grams is null or target_low_rice_threshold_grams not between 0 and 1000000000 then
        raise exception 'Invalid low-rice threshold';
    end if;
    insert into public.mess_settings (mess_id, locale, low_rice_threshold_grams, meal_reminders_enabled, updated_by)
    values (actor_mess_id, target_locale, target_low_rice_threshold_grams, coalesce(target_meal_reminders_enabled, true), auth.uid())
    on conflict (mess_id) do update set locale = excluded.locale,
        low_rice_threshold_grams = excluded.low_rice_threshold_grams,
        meal_reminders_enabled = excluded.meal_reminders_enabled,
        updated_by = excluded.updated_by, updated_at = now();
    insert into public.audit_logs (mess_id, actor_user_id, action, target_user_id, details)
    values (actor_mess_id, auth.uid(), 'MESS_SETTINGS_UPDATED', auth.uid(), jsonb_build_object(
        'locale', target_locale, 'low_rice_threshold_grams', target_low_rice_threshold_grams,
        'meal_reminders_enabled', coalesce(target_meal_reminders_enabled, true)
    ));
end;
$$;

create or replace function public.mark_notification_read(target_notification_id uuid)
returns void language plpgsql security definer set search_path = public, auth as $$
declare actor_mess_id uuid := public.current_mess_id();
begin
    if auth.uid() is null or actor_mess_id is null or not public.is_active_user() then
        raise exception 'An active member session is required' using errcode = '42501';
    end if;
    update public.notifications set read_at = coalesce(read_at, now())
    where id = target_notification_id and mess_id = actor_mess_id and recipient_id = auth.uid();
    if not found then raise exception 'Notification not found for this member'; end if;
end;
$$;

create or replace function public.get_extended_erp_snapshot()
returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare
    actor_mess_id uuid := public.current_mess_id();
    actor_is_admin boolean;
begin
    if auth.uid() is null or actor_mess_id is null or not public.is_active_user() then
        raise exception 'An active member session is required' using errcode = '42501';
    end if;
    actor_is_admin := public.is_admin_for_mess(actor_mess_id);
    return jsonb_build_object(
        'members', coalesce((select jsonb_agg(jsonb_build_object(
            'id', p.user_id, 'full_name', p.full_name, 'member_id', p.member_id,
            'role', p.role, 'account_status', p.account_status, 'departed_at', p.departed_at
        ) order by lower(p.full_name)) from public.profiles p where p.mess_id = actor_mess_id), '[]'::jsonb),
        'periods', coalesce((select jsonb_agg(jsonb_build_object(
            'id', bp.id, 'label', bp.label, 'starts_on', bp.starts_on,
            'ends_on', bp.ends_on, 'status', bp.status
        ) order by bp.starts_on desc) from public.billing_periods bp where bp.mess_id = actor_mess_id), '[]'::jsonb),
        'fines', coalesce((select jsonb_agg(jsonb_build_object(
            'id', f.id, 'charge_id', f.charge_id, 'member_id', f.member_id,
            'member_name', p.full_name, 'billing_period_id', f.billing_period_id,
            'period_label', bp.label, 'amount_minor', f.amount_minor,
            'reason', f.reason, 'due_date', c.due_date, 'created_at', f.created_at
        ) order by f.created_at desc)
        from public.fines f join public.profiles p on p.user_id = f.member_id and p.mess_id = f.mess_id
        join public.billing_periods bp on bp.id = f.billing_period_id join public.charges c on c.id = f.charge_id
        where f.mess_id = actor_mess_id and (actor_is_admin or f.member_id = auth.uid())), '[]'::jsonb),
        'payment_reviews', coalesce((select jsonb_agg(jsonb_build_object(
            'id', p.id, 'payment_id', p.id, 'member_id', p.member_id,
            'member_name', prf.full_name, 'billing_period_id', p.billing_period_id,
            'period_label', bp.label, 'amount_minor', p.amount_minor,
            'payment_method', p.payment_method, 'paid_at', p.paid_at,
            'transaction_reference', p.transaction_reference,
            'status', coalesce(pr.status, 'PENDING'), 'reason', pr.reason,
            'reviewed_at', pr.reviewed_at,
            'reversed', exists(select 1 from public.payment_reversals px where px.payment_id = p.id)
        ) order by p.paid_at desc)
        from public.payments p join public.profiles prf on prf.user_id = p.member_id and prf.mess_id = p.mess_id
        join public.billing_periods bp on bp.id = p.billing_period_id
        left join public.payment_reviews pr on pr.payment_id = p.id
        where p.mess_id = actor_mess_id and (actor_is_admin or p.member_id = auth.uid())), '[]'::jsonb),
        'meal_billing_policies', coalesce((select jsonb_agg(jsonb_build_object(
            'id', mp.mess_id, 'price_per_half_meal_minor', mp.price_per_half_meal_minor,
            'enabled', mp.enabled, 'updated_at', mp.updated_at
        )) from public.meal_billing_policies mp where mp.mess_id = actor_mess_id), '[]'::jsonb),
        'meal_billing_runs', coalesce((select jsonb_agg(jsonb_build_object(
            'id', mr.id, 'billing_period_id', mr.billing_period_id, 'period_label', bp.label,
            'total_half_meal_units', mr.total_half_meal_units,
            'total_amount_minor', mr.total_amount_minor, 'created_at', mr.created_at
        ) order by mr.created_at desc) from public.meal_billing_runs mr
        join public.billing_periods bp on bp.id = mr.billing_period_id where mr.mess_id = actor_mess_id), '[]'::jsonb),
        'meal_schedules', coalesce((select jsonb_agg(jsonb_build_object(
            'id', s.id, 'member_id', s.member_id, 'member_name', p.full_name,
            'starts_on', s.starts_on, 'ends_on', s.ends_on, 'status', s.status,
            'reason', s.reason, 'created_at', s.created_at
        ) order by s.starts_on desc) from public.meal_schedules s
        join public.profiles p on p.user_id = s.member_id
        where s.mess_id = actor_mess_id and (actor_is_admin or s.member_id = auth.uid())), '[]'::jsonb),
        'rice_purchases', coalesce((select jsonb_agg(jsonb_build_object(
            'id', rp.id, 'movement_id', rp.movement_id, 'expense_id', rp.expense_id,
            'purchase_unit', rp.purchase_unit, 'unit_price_minor', rp.unit_price_minor,
            'total_amount_minor', rp.total_amount_minor, 'vendor', rp.vendor,
            'receipt_reference', rp.receipt_reference, 'purchased_on', rp.purchased_on,
            'quantity_grams', rm.quantity_grams, 'created_at', rp.created_at
        ) order by rp.created_at desc) from public.rice_purchases rp
        join public.rice_movements rm on rm.id = rp.movement_id where rp.mess_id = actor_mess_id), '[]'::jsonb),
        'rice_movements', coalesce((select jsonb_agg(jsonb_build_object(
            'id', rm.id, 'kind', rm.kind, 'quantity_grams', rm.quantity_grams,
            'note', rm.note, 'created_at', rm.created_at,
            'reversed', exists(select 1 from public.rice_movement_reversals rr where rr.movement_id = rm.id)
        ) order by rm.created_at desc) from public.rice_movements rm where rm.mess_id = actor_mess_id), '[]'::jsonb),
        'rice_movement_reversals', coalesce((select jsonb_agg(jsonb_build_object(
            'id', rr.id, 'movement_id', rr.movement_id, 'reversal_movement_id', rr.reversal_movement_id,
            'reason', rr.reason, 'created_at', rr.created_at
        ) order by rr.created_at desc) from public.rice_movement_reversals rr where rr.mess_id = actor_mess_id), '[]'::jsonb),
        'expenses', coalesce((select jsonb_agg(jsonb_build_object(
            'id', e.id, 'category', e.category, 'amount_minor', e.amount_minor,
            'spent_on', e.spent_on, 'description', e.description,
            'transaction_reference', e.transaction_reference, 'vendor', ed.vendor,
            'receipt_reference', ed.receipt_reference,
            'approval_status', coalesce(ed.approval_status, 'PENDING'),
            'approval_reason', ed.approval_reason, 'created_at', e.created_at
        ) order by e.spent_on desc) from public.expenses e
        left join public.expense_details ed on ed.expense_id = e.id where e.mess_id = actor_mess_id), '[]'::jsonb),
        'mess_settings', coalesce((select jsonb_agg(jsonb_build_object(
            'id', ms.mess_id, 'locale', ms.locale, 'timezone', ms.timezone,
            'low_rice_threshold_grams', ms.low_rice_threshold_grams,
            'meal_reminders_enabled', ms.meal_reminders_enabled, 'updated_at', ms.updated_at
        )) from public.mess_settings ms where ms.mess_id = actor_mess_id), '[]'::jsonb),
        'notifications', coalesce((select jsonb_agg(jsonb_build_object(
            'id', n.id, 'kind', n.kind, 'title', n.title, 'body', n.body,
            'read_at', n.read_at, 'created_at', n.created_at
        ) order by n.created_at desc) from public.notifications n
        where n.mess_id = actor_mess_id and n.recipient_id = auth.uid()), '[]'::jsonb),
        'rice_stock_alerts', coalesce((select jsonb_agg(jsonb_build_object(
            'id', ra.id, 'stock_grams', ra.stock_grams, 'threshold_grams', ra.threshold_grams,
            'status', ra.status, 'created_at', ra.created_at, 'resolved_at', ra.resolved_at
        ) order by ra.created_at desc) from public.rice_stock_alerts ra where ra.mess_id = actor_mess_id), '[]'::jsonb),
        'cash_accounts', coalesce((select jsonb_agg(jsonb_build_object(
            'id', ca.id, 'name', ca.name, 'kind', ca.kind,
            'opening_balance_minor', ca.opening_balance_minor, 'active', ca.active, 'created_at', ca.created_at
        ) order by lower(ca.name)) from public.cash_accounts ca where ca.mess_id = actor_mess_id), '[]'::jsonb),
        'cash_entries', coalesce((select jsonb_agg(jsonb_build_object(
            'id', cb.id, 'source_type', cb.source_type, 'source_id', cb.source_id,
            'direction', cb.direction, 'amount_minor', cb.amount_minor,
            'description', cb.description, 'occurred_at', cb.occurred_at,
            'business_date', public.cash_entry_business_date(cb.source_type, cb.source_id, cb.occurred_at),
            'account_id', cea.account_id, 'account_name', ca.name
        ) order by cb.occurred_at desc) from public.cash_book_entries cb
        left join public.cash_entry_accounts cea on cea.cash_book_entry_id = cb.id
        left join public.cash_accounts ca on ca.id = cea.account_id where cb.mess_id = actor_mess_id), '[]'::jsonb),
        'cash_period_locks', coalesce((select jsonb_agg(jsonb_build_object(
            'id', cl.id, 'starts_on', cl.starts_on, 'ends_on', cl.ends_on,
            'opening_balance_minor', cl.opening_balance_minor, 'closing_balance_minor', cl.closing_balance_minor,
            'note', cl.note, 'locked_at', cl.locked_at
        ) order by cl.starts_on desc) from public.cash_period_locks cl where cl.mess_id = actor_mess_id), '[]'::jsonb)
    );
end;
$$;

revoke all on function public.update_mess_settings(text, integer, boolean) from public, anon;
revoke all on function public.mark_notification_read(uuid) from public, anon;
grant execute on function public.update_mess_settings(text, integer, boolean) to authenticated;
grant execute on function public.mark_notification_read(uuid) to authenticated;
revoke all on function public.get_extended_erp_snapshot() from public, anon;
grant execute on function public.get_extended_erp_snapshot() to authenticated;
