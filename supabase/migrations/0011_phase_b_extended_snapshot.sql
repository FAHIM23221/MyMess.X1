-- Authorized, read-only snapshot for the extended Android ERP workspace.
-- Explicit predicates are required because this function is SECURITY DEFINER.
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
        'members', coalesce((
            select jsonb_agg(jsonb_build_object(
                'id', p.user_id, 'full_name', p.full_name, 'member_id', p.member_id,
                'role', p.role, 'account_status', p.account_status,
                'departed_at', p.departed_at
            ) order by lower(p.full_name))
            from public.profiles p where p.mess_id = actor_mess_id
        ), '[]'::jsonb),
        'periods', coalesce((
            select jsonb_agg(jsonb_build_object(
                'id', bp.id, 'label', bp.label, 'starts_on', bp.starts_on,
                'ends_on', bp.ends_on, 'status', bp.status
            ) order by bp.starts_on desc)
            from public.billing_periods bp where bp.mess_id = actor_mess_id
        ), '[]'::jsonb),
        'fines', coalesce((
            select jsonb_agg(jsonb_build_object(
                'id', f.id, 'charge_id', f.charge_id, 'member_id', f.member_id,
                'member_name', p.full_name, 'billing_period_id', f.billing_period_id,
                'period_label', bp.label, 'amount_minor', f.amount_minor,
                'reason', f.reason, 'due_date', c.due_date, 'created_at', f.created_at
            ) order by f.created_at desc)
            from public.fines f
            join public.profiles p on p.user_id = f.member_id and p.mess_id = f.mess_id
            join public.billing_periods bp on bp.id = f.billing_period_id
            join public.charges c on c.id = f.charge_id
            where f.mess_id = actor_mess_id and (actor_is_admin or f.member_id = auth.uid())
        ), '[]'::jsonb),
        'payment_reviews', coalesce((
            select jsonb_agg(jsonb_build_object(
                'id', p.id, 'payment_id', p.id, 'member_id', p.member_id,
                'member_name', prf.full_name, 'billing_period_id', p.billing_period_id,
                'period_label', bp.label, 'amount_minor', p.amount_minor,
                'payment_method', p.payment_method, 'paid_at', p.paid_at,
                'transaction_reference', p.transaction_reference,
                'status', coalesce(pr.status, 'PENDING'), 'reason', pr.reason,
                'reviewed_at', pr.reviewed_at,
                'reversed', exists(select 1 from public.payment_reversals px where px.payment_id = p.id)
            ) order by p.paid_at desc)
            from public.payments p
            join public.profiles prf on prf.user_id = p.member_id and prf.mess_id = p.mess_id
            join public.billing_periods bp on bp.id = p.billing_period_id
            left join public.payment_reviews pr on pr.payment_id = p.id
            where p.mess_id = actor_mess_id and (actor_is_admin or p.member_id = auth.uid())
        ), '[]'::jsonb),
        'meal_billing_policies', coalesce((
            select jsonb_agg(jsonb_build_object(
                'id', mp.mess_id, 'price_per_half_meal_minor', mp.price_per_half_meal_minor,
                'enabled', mp.enabled, 'updated_at', mp.updated_at
            ))
            from public.meal_billing_policies mp where mp.mess_id = actor_mess_id
        ), '[]'::jsonb),
        'meal_billing_runs', coalesce((
            select jsonb_agg(jsonb_build_object(
                'id', mr.id, 'billing_period_id', mr.billing_period_id,
                'period_label', bp.label, 'total_half_meal_units', mr.total_half_meal_units,
                'total_amount_minor', mr.total_amount_minor, 'created_at', mr.created_at
            ) order by mr.created_at desc)
            from public.meal_billing_runs mr
            join public.billing_periods bp on bp.id = mr.billing_period_id
            where mr.mess_id = actor_mess_id
        ), '[]'::jsonb)
    );
end;
$$;

revoke all on function public.get_extended_erp_snapshot() from public, anon;
grant execute on function public.get_extended_erp_snapshot() to authenticated;
comment on function public.get_extended_erp_snapshot() is
    'Active same-mess snapshot with member-owned finance filtering and Admin-only full finance rows.';
