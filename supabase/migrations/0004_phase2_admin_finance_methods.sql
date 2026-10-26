-- Phase 2 Admin finance: explicit mobile-wallet methods and detailed charge categories.
-- Additive data model change; existing rows remain valid. No table/RLS relaxation.

alter table public.payments drop constraint payments_payment_method_check;
alter table public.payments add constraint payments_payment_method_check
    check (payment_method in ('CASH', 'BKASH', 'NAGAD', 'BANK', 'MOBILE_BANKING', 'OTHER'));

alter table public.charges drop constraint charges_category_check;
alter table public.charges add constraint charges_category_check
    check (category in (
        'SEAT_RENT', 'MEAL_CHARGE', 'ELECTRICITY', 'WIFI', 'GAS', 'WATER',
        'FINE', 'UTILITY', 'SERVICE', 'OTHER'
    ));

create or replace function public.create_charge(
    target_user_id uuid,
    target_period_id uuid,
    target_category text,
    target_amount_minor bigint,
    target_description text,
    target_due_date date,
    client_request_id uuid
) returns uuid
language plpgsql
security definer
set search_path = public, auth
as $$
declare
    actor_mess_id uuid := public.current_mess_id();
    open_period_id uuid;
    new_charge_id uuid;
begin
    if auth.uid() is null or actor_mess_id is null or not public.is_admin_for_mess(actor_mess_id) then
        raise exception 'Active Admin or Main Admin access required' using errcode = '42501';
    end if;
    if client_request_id is null then
        raise exception 'A unique client request ID is required';
    end if;
    if target_amount_minor is null or target_amount_minor <= 0 or target_amount_minor > 1000000000000 then
        raise exception 'A positive, bounded charge amount is required';
    end if;
    if target_category not in (
        'SEAT_RENT', 'MEAL_CHARGE', 'ELECTRICITY', 'WIFI', 'GAS', 'WATER',
        'FINE', 'UTILITY', 'SERVICE', 'OTHER'
    ) then
        raise exception 'Unsupported charge category';
    end if;
    if target_description is null or char_length(trim(target_description)) not between 2 and 240 then
        raise exception 'A charge description between 2 and 240 characters is required';
    end if;
    if not exists (
        select 1 from public.profiles where user_id = target_user_id and mess_id = actor_mess_id
    ) then
        raise exception 'Target member is not in your mess';
    end if;

    select id into open_period_id from public.billing_periods
    where id = target_period_id and mess_id = actor_mess_id and status = 'OPEN'
    for update;
    if not found then
        raise exception 'Open billing period not found in your mess';
    end if;

    insert into public.charges (
        mess_id, billing_period_id, member_id, category, amount_minor,
        description, due_date, client_request_id, created_by
    ) values (
        actor_mess_id, target_period_id, target_user_id, target_category, target_amount_minor,
        trim(target_description), target_due_date, client_request_id, auth.uid()
    ) returning id into new_charge_id;

    insert into public.audit_logs (mess_id, actor_user_id, action, target_user_id, details)
    values (
        actor_mess_id, auth.uid(), 'CHARGE_CREATED', target_user_id,
        jsonb_build_object(
            'charge_id', new_charge_id, 'billing_period_id', target_period_id,
            'category', target_category, 'amount_minor', target_amount_minor
        )
    );
    return new_charge_id;
end;
$$;

create or replace function public.record_payment(
    target_user_id uuid,
    target_period_id uuid,
    target_amount_minor bigint,
    target_method text,
    target_paid_at timestamptz,
    target_note text,
    target_transaction_reference text,
    client_request_id uuid
) returns uuid
language plpgsql
security definer
set search_path = public, auth
as $$
declare
    actor_mess_id uuid := public.current_mess_id();
    open_period_id uuid;
    new_payment_id uuid;
    new_receipt_id uuid;
    new_receipt_number text;
begin
    if auth.uid() is null or actor_mess_id is null or not public.is_admin_for_mess(actor_mess_id) then
        raise exception 'Active Admin or Main Admin access required' using errcode = '42501';
    end if;
    if client_request_id is null then
        raise exception 'A unique client request ID is required';
    end if;
    if target_amount_minor is null or target_amount_minor <= 0 or target_amount_minor > 1000000000000 then
        raise exception 'A positive, bounded payment amount is required';
    end if;
    if target_method not in ('CASH', 'BKASH', 'NAGAD', 'BANK', 'MOBILE_BANKING', 'OTHER') then
        raise exception 'Unsupported payment method';
    end if;
    if target_paid_at is null then
        raise exception 'Payment time is required';
    end if;
    if target_paid_at > now() + interval '5 minutes' then
        raise exception 'Future payment time is not allowed';
    end if;
    if target_note is not null and char_length(target_note) > 500 then
        raise exception 'Payment note is too long';
    end if;
    if target_transaction_reference is not null
       and char_length(trim(target_transaction_reference)) not between 2 and 120 then
        raise exception 'Transaction reference must be between 2 and 120 characters';
    end if;
    if not exists (
        select 1 from public.profiles where user_id = target_user_id and mess_id = actor_mess_id
    ) then
        raise exception 'Target member is not in your mess';
    end if;

    select id into open_period_id from public.billing_periods
    where id = target_period_id and mess_id = actor_mess_id and status = 'OPEN'
    for update;
    if not found then
        raise exception 'Open billing period not found in your mess';
    end if;

    insert into public.payments (
        mess_id, billing_period_id, member_id, amount_minor, payment_method,
        paid_at, note, transaction_reference, client_request_id, recorded_by
    ) values (
        actor_mess_id, target_period_id, target_user_id, target_amount_minor, target_method,
        target_paid_at, nullif(trim(target_note), ''), nullif(trim(target_transaction_reference), ''),
        client_request_id, auth.uid()
    ) returning id into new_payment_id;

    new_receipt_number := 'SM-' || upper(replace(gen_random_uuid()::text, '-', ''));
    insert into public.receipts (mess_id, member_id, payment_id, receipt_number)
    values (actor_mess_id, target_user_id, new_payment_id, new_receipt_number)
    returning id into new_receipt_id;

    insert into public.audit_logs (mess_id, actor_user_id, action, target_user_id, details)
    values (
        actor_mess_id, auth.uid(), 'PAYMENT_RECORDED', target_user_id,
        jsonb_build_object(
            'payment_id', new_payment_id, 'receipt_id', new_receipt_id,
            'receipt_number', new_receipt_number, 'billing_period_id', target_period_id,
            'amount_minor', target_amount_minor, 'payment_method', target_method
        )
    );
    return new_payment_id;
end;
$$;

revoke all on function public.create_charge(uuid, uuid, text, bigint, text, date, uuid) from public, anon;
revoke all on function public.record_payment(uuid, uuid, bigint, text, timestamptz, text, text, uuid) from public, anon;
grant execute on function public.create_charge(uuid, uuid, text, bigint, text, date, uuid) to authenticated;
grant execute on function public.record_payment(uuid, uuid, bigint, text, timestamptz, text, text, uuid) to authenticated;

comment on function public.record_payment(uuid, uuid, bigint, text, timestamptz, text, text, uuid) is
    'Audited payment entry with unique request ID and receipt; future timestamps are rejected.';
