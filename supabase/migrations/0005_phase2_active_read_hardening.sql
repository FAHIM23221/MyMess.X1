-- Phase 2 read hardening: suspended profiles must not read ledger data.
-- SELECT policies are tightened in place; no data or grant relaxation is introduced.

drop policy billing_periods_select on public.billing_periods;
create policy billing_periods_select on public.billing_periods for select to authenticated
    using (
        public.is_active_user()
        and mess_id = public.current_mess_id()
        and public.current_app_role() is not null
    );

drop policy charges_select on public.charges;
create policy charges_select on public.charges for select to authenticated
    using (
        public.is_active_user()
        and mess_id = public.current_mess_id()
        and (
            member_id = auth.uid()
            or public.is_admin_for_mess(mess_id)
        )
    );

drop policy adjustments_select on public.billing_adjustments;
create policy adjustments_select on public.billing_adjustments for select to authenticated
    using (
        public.is_active_user()
        and mess_id = public.current_mess_id()
        and (
            member_id = auth.uid()
            or public.is_admin_for_mess(mess_id)
        )
    );

drop policy payments_select on public.payments;
create policy payments_select on public.payments for select to authenticated
    using (
        public.is_active_user()
        and mess_id = public.current_mess_id()
        and (
            member_id = auth.uid()
            or public.is_admin_for_mess(mess_id)
        )
    );

drop policy receipts_select on public.receipts;
create policy receipts_select on public.receipts for select to authenticated
    using (
        public.is_active_user()
        and mess_id = public.current_mess_id()
        and (
            member_id = auth.uid()
            or public.is_admin_for_mess(mess_id)
        )
    );

drop policy reversals_select on public.payment_reversals;
create policy reversals_select on public.payment_reversals for select to authenticated
    using (
        public.is_active_user()
        and mess_id = public.current_mess_id()
        and (
            member_id = auth.uid()
            or public.is_admin_for_mess(mess_id)
        )
    );

