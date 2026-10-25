-- SMART TRANSPARENT MESS ERP / Phase 2: billing, payments, receipts, reversals.
-- Additive only. Amounts are integer minor units (BDT poisha), never floating point.
-- Authenticated clients receive SELECT access only; every mutation is an audited RPC.

create table public.billing_periods (
    id uuid primary key default gen_random_uuid(),
    mess_id uuid not null references public.messes(id) on delete restrict,
    label text not null check (char_length(trim(label)) between 2 and 80),
    starts_on date not null,
    ends_on date not null,
    status text not null default 'OPEN' check (status in ('OPEN', 'CLOSED')),
    created_by uuid not null references public.profiles(user_id) on delete restrict,
    created_at timestamptz not null default now(),
    closed_at timestamptz,
    closed_by uuid references public.profiles(user_id) on delete restrict,
    unique (mess_id, starts_on),
    check (ends_on >= starts_on),
    check ((status = 'OPEN' and closed_at is null and closed_by is null)
        or (status = 'CLOSED' and closed_at is not null and closed_by is not null))
);

create table public.charges (
    id uuid primary key default gen_random_uuid(),
    mess_id uuid not null references public.messes(id) on delete restrict,
    billing_period_id uuid not null references public.billing_periods(id) on delete restrict,
    member_id uuid not null references public.profiles(user_id) on delete restrict,
    category text not null check (category in ('SEAT_RENT', 'MEAL_CHARGE', 'UTILITY', 'SERVICE', 'OTHER')),
    amount_minor bigint not null check (amount_minor > 0 and amount_minor <= 1000000000000),
    description text not null check (char_length(trim(description)) between 2 and 240),
    due_date date,
    client_request_id uuid not null,
    created_by uuid not null references public.profiles(user_id) on delete restrict,
    created_at timestamptz not null default now(),
    unique (mess_id, client_request_id)
);

create table public.billing_adjustments (
    id uuid primary key default gen_random_uuid(),
    mess_id uuid not null references public.messes(id) on delete restrict,
    billing_period_id uuid not null references public.billing_periods(id) on delete restrict,
    member_id uuid not null references public.profiles(user_id) on delete restrict,
    kind text not null check (kind in ('OPENING_DUE', 'CREDIT', 'DEBIT_ADJUSTMENT')),
    amount_minor bigint not null check (amount_minor > 0 and amount_minor <= 1000000000000),
    reason text not null check (char_length(trim(reason)) between 8 and 500),
    source_period_id uuid references public.billing_periods(id) on delete restrict,
    client_request_id uuid,
    created_by uuid not null references public.profiles(user_id) on delete restrict,
    created_at timestamptz not null default now(),
    unique (mess_id, client_request_id),
    check (source_period_id is null or source_period_id <> billing_period_id)
);

create unique index one_carry_forward_per_member_period
    on public.billing_adjustments (member_id, billing_period_id, source_period_id)
    where source_period_id is not null;

create table public.payments (
    id uuid primary key default gen_random_uuid(),
    mess_id uuid not null references public.messes(id) on delete restrict,
    billing_period_id uuid not null references public.billing_periods(id) on delete restrict,
    member_id uuid not null references public.profiles(user_id) on delete restrict,
    amount_minor bigint not null check (amount_minor > 0 and amount_minor <= 1000000000000),
    payment_method text not null check (payment_method in ('CASH', 'BANK', 'MOBILE_BANKING', 'OTHER')),
    paid_at timestamptz not null,
    note text check (note is null or char_length(note) <= 500),
    transaction_reference text check (transaction_reference is null or char_length(trim(transaction_reference)) between 2 and 120),
    client_request_id uuid not null,
    recorded_by uuid not null references public.profiles(user_id) on delete restrict,
    created_at timestamptz not null default now(),
    unique (mess_id, client_request_id)
);

create table public.receipts (
    id uuid primary key default gen_random_uuid(),
    mess_id uuid not null references public.messes(id) on delete restrict,
    member_id uuid not null references public.profiles(user_id) on delete restrict,
    payment_id uuid not null unique references public.payments(id) on delete restrict,
    receipt_number text not null unique check (char_length(receipt_number) between 12 and 40),
    issued_at timestamptz not null default now(),
    voided_at timestamptz,
    voided_by uuid references public.profiles(user_id) on delete restrict,
    void_reason text check (void_reason is null or char_length(trim(void_reason)) between 8 and 500),
    check ((voided_at is null and voided_by is null and void_reason is null)
        or (voided_at is not null and voided_by is not null and void_reason is not null))
);

create table public.payment_reversals (
    id uuid primary key default gen_random_uuid(),
    mess_id uuid not null references public.messes(id) on delete restrict,
    member_id uuid not null references public.profiles(user_id) on delete restrict,
    payment_id uuid not null unique references public.payments(id) on delete restrict,
    reason text not null check (char_length(trim(reason)) between 8 and 500),
    reversed_by uuid not null references public.profiles(user_id) on delete restrict,
    created_at timestamptz not null default now()
);

create index charges_member_period_idx on public.charges (member_id, billing_period_id);
create index adjustments_member_period_idx on public.billing_adjustments (member_id, billing_period_id);
create index payments_member_period_idx on public.payments (member_id, billing_period_id, paid_at desc);
create index billing_periods_mess_dates_idx on public.billing_periods (mess_id, starts_on desc, ends_on desc);

alter table public.billing_periods enable row level security;
alter table public.charges enable row level security;
alter table public.billing_adjustments enable row level security;
alter table public.payments enable row level security;
alter table public.receipts enable row level security;
alter table public.payment_reversals enable row level security;

-- Read-only member/admin visibility. No INSERT, UPDATE, or DELETE policies exist.
create policy billing_periods_select on public.billing_periods for select to authenticated
    using (mess_id = public.current_mess_id() and public.current_app_role() is not null);

create policy charges_select on public.charges for select to authenticated
    using (
        (member_id = auth.uid() and mess_id = public.current_mess_id() and public.current_app_role() is not null)
        or public.is_admin_for_mess(mess_id)
    );

create policy adjustments_select on public.billing_adjustments for select to authenticated
    using (
        (member_id = auth.uid() and mess_id = public.current_mess_id() and public.current_app_role() is not null)
        or public.is_admin_for_mess(mess_id)
    );

create policy payments_select on public.payments for select to authenticated
    using (
        (member_id = auth.uid() and mess_id = public.current_mess_id() and public.current_app_role() is not null)
        or public.is_admin_for_mess(mess_id)
    );

create policy receipts_select on public.receipts for select to authenticated
    using (
        (member_id = auth.uid() and mess_id = public.current_mess_id() and public.current_app_role() is not null)
        or public.is_admin_for_mess(mess_id)
    );

create policy reversals_select on public.payment_reversals for select to authenticated
    using (
        (member_id = auth.uid() and mess_id = public.current_mess_id() and public.current_app_role() is not null)
        or public.is_admin_for_mess(mess_id)
    );

revoke all on public.billing_periods from public, anon, authenticated;
revoke all on public.charges from public, anon, authenticated;
revoke all on public.billing_adjustments from public, anon, authenticated;
revoke all on public.payments from public, anon, authenticated;
revoke all on public.receipts from public, anon, authenticated;
revoke all on public.payment_reversals from public, anon, authenticated;

grant select on public.billing_periods to authenticated;
grant select on public.charges to authenticated;
grant select on public.billing_adjustments to authenticated;
grant select on public.payments to authenticated;
grant select on public.receipts to authenticated;
grant select on public.payment_reversals to authenticated;

-- Pre-aggregated CTEs avoid multiplying charges by payments/adjustments.
create view public.my_financial_summary
with (security_invoker = true) as
with charge_totals as (
    select billing_period_id, member_id, sum(amount_minor)::bigint as charges_minor
    from public.charges
    group by billing_period_id, member_id
),
payment_totals as (
    select p.billing_period_id, p.member_id, sum(p.amount_minor)::bigint as valid_payments_minor
    from public.payments p
    where not exists (
        select 1 from public.payment_reversals pr where pr.payment_id = p.id
    )
    group by p.billing_period_id, p.member_id
),
adjustment_totals as (
    select
        billing_period_id,
        member_id,
        coalesce(sum(amount_minor) filter (where kind = 'OPENING_DUE'), 0)::bigint as opening_due_minor,
        coalesce(sum(amount_minor) filter (where kind = 'DEBIT_ADJUSTMENT'), 0)::bigint as debit_adjustments_minor,
        coalesce(sum(amount_minor) filter (where kind = 'CREDIT'), 0)::bigint as credit_adjustments_minor
    from public.billing_adjustments
    group by billing_period_id, member_id
)
select
    bp.id as billing_period_id,
    bp.label as period_label,
    bp.starts_on as period_start,
    bp.ends_on as period_end,
    bp.status as period_status,
    p.user_id as member_id,
    coalesce(a.opening_due_minor, 0::bigint) as opening_due_minor,
    coalesce(c.charges_minor, 0::bigint) as charges_minor,
    coalesce(a.debit_adjustments_minor, 0::bigint) as debit_adjustments_minor,
    coalesce(a.credit_adjustments_minor, 0::bigint) as credit_adjustments_minor,
    coalesce(pay.valid_payments_minor, 0::bigint) as valid_payments_minor,
    greatest(
        coalesce(a.opening_due_minor, 0::bigint)
        + coalesce(c.charges_minor, 0::bigint)
        + coalesce(a.debit_adjustments_minor, 0::bigint)
        - coalesce(pay.valid_payments_minor, 0::bigint)
        - coalesce(a.credit_adjustments_minor, 0::bigint),
        0::bigint
    ) as outstanding_due_minor,
    greatest(
        -(
            coalesce(a.opening_due_minor, 0::bigint)
            + coalesce(c.charges_minor, 0::bigint)
            + coalesce(a.debit_adjustments_minor, 0::bigint)
            - coalesce(pay.valid_payments_minor, 0::bigint)
            - coalesce(a.credit_adjustments_minor, 0::bigint)
        ),
        0::bigint
    ) as advance_credit_minor
from public.billing_periods bp
join public.profiles p
    on p.mess_id = bp.mess_id
   and p.user_id = auth.uid()
   and p.account_status = 'ACTIVE'
left join charge_totals c
    on c.billing_period_id = bp.id and c.member_id = p.user_id
left join payment_totals pay
    on pay.billing_period_id = bp.id and pay.member_id = p.user_id
left join adjustment_totals a
    on a.billing_period_id = bp.id and a.member_id = p.user_id;

create view public.my_payment_receipts
with (security_invoker = true) as
select
    p.id as payment_id,
    p.billing_period_id,
    p.member_id,
    p.amount_minor,
    p.payment_method,
    p.paid_at,
    p.note,
    p.transaction_reference,
    r.id as receipt_id,
    r.receipt_number,
    r.issued_at as receipt_issued_at,
    pr.created_at as reversed_at,
    pr.reason as reversal_reason
from public.payments p
join public.receipts r on r.payment_id = p.id
left join public.payment_reversals pr on pr.payment_id = p.id
where p.member_id = auth.uid();

revoke all on public.my_financial_summary from public, anon;
revoke all on public.my_payment_receipts from public, anon;
grant select on public.my_financial_summary to authenticated;
grant select on public.my_payment_receipts to authenticated;

create or replace function public.create_billing_period(
    target_label text,
    target_start date,
    target_end date
) returns uuid
language plpgsql
security definer
set search_path = public, auth
as $$
declare
    actor_mess_id uuid := public.current_mess_id();
    new_period_id uuid;
begin
    if auth.uid() is null or actor_mess_id is null or not public.is_admin_for_mess(actor_mess_id) then
        raise exception 'Active Admin or Main Admin access required' using errcode = '42501';
    end if;
    if target_label is null or char_length(trim(target_label)) not between 2 and 80 then
        raise exception 'A billing-period label between 2 and 80 characters is required';
    end if;
    if target_start is null or target_end is null or target_end < target_start then
        raise exception 'A valid billing-period date range is required';
    end if;

    perform pg_advisory_xact_lock(hashtext(actor_mess_id::text));
    if exists (
        select 1 from public.billing_periods
        where mess_id = actor_mess_id
          and starts_on <= target_end
          and ends_on >= target_start
    ) then
        raise exception 'Billing periods cannot overlap';
    end if;

    insert into public.billing_periods (mess_id, label, starts_on, ends_on, created_by)
    values (actor_mess_id, trim(target_label), target_start, target_end, auth.uid())
    returning id into new_period_id;

    insert into public.audit_logs (mess_id, actor_user_id, action, target_user_id, details)
    values (
        actor_mess_id, auth.uid(), 'BILLING_PERIOD_CREATED', auth.uid(),
        jsonb_build_object('billing_period_id', new_period_id, 'starts_on', target_start, 'ends_on', target_end)
    );
    return new_period_id;
end;
$$;

create or replace function public.close_billing_period(target_period_id uuid)
returns void
language plpgsql
security definer
set search_path = public, auth
as $$
declare
    actor_mess_id uuid := public.current_mess_id();
begin
    if auth.uid() is null or actor_mess_id is null or not public.is_admin_for_mess(actor_mess_id) then
        raise exception 'Active Admin or Main Admin access required' using errcode = '42501';
    end if;

    update public.billing_periods
    set status = 'CLOSED', closed_at = now(), closed_by = auth.uid()
    where id = target_period_id and mess_id = actor_mess_id and status = 'OPEN';
    if not found then
        raise exception 'Open billing period not found in your mess';
    end if;

    insert into public.audit_logs (mess_id, actor_user_id, action, target_user_id, details)
    values (
        actor_mess_id, auth.uid(), 'BILLING_PERIOD_CLOSED', auth.uid(),
        jsonb_build_object('billing_period_id', target_period_id)
    );
end;
$$;

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
    if target_category not in ('SEAT_RENT', 'MEAL_CHARGE', 'UTILITY', 'SERVICE', 'OTHER') then
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

create or replace function public.add_billing_adjustment(
    target_user_id uuid,
    target_period_id uuid,
    target_kind text,
    target_amount_minor bigint,
    target_reason text,
    client_request_id uuid
) returns uuid
language plpgsql
security definer
set search_path = public, auth
as $$
declare
    actor_mess_id uuid := public.current_mess_id();
    open_period_id uuid;
    new_adjustment_id uuid;
begin
    if auth.uid() is null or actor_mess_id is null or not public.is_admin_for_mess(actor_mess_id) then
        raise exception 'Active Admin or Main Admin access required' using errcode = '42501';
    end if;
    if client_request_id is null then
        raise exception 'A unique client request ID is required';
    end if;
    if target_kind not in ('OPENING_DUE', 'CREDIT', 'DEBIT_ADJUSTMENT') then
        raise exception 'Unsupported adjustment kind';
    end if;
    if target_amount_minor is null or target_amount_minor <= 0 or target_amount_minor > 1000000000000 then
        raise exception 'A positive, bounded adjustment amount is required';
    end if;
    if target_reason is null or char_length(trim(target_reason)) not between 8 and 500 then
        raise exception 'An adjustment reason between 8 and 500 characters is required';
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

    insert into public.billing_adjustments (
        mess_id, billing_period_id, member_id, kind, amount_minor,
        reason, client_request_id, created_by
    ) values (
        actor_mess_id, target_period_id, target_user_id, target_kind, target_amount_minor,
        trim(target_reason), client_request_id, auth.uid()
    ) returning id into new_adjustment_id;

    insert into public.audit_logs (mess_id, actor_user_id, action, target_user_id, details)
    values (
        actor_mess_id, auth.uid(), 'BILLING_ADJUSTMENT_CREATED', target_user_id,
        jsonb_build_object(
            'adjustment_id', new_adjustment_id, 'billing_period_id', target_period_id,
            'kind', target_kind, 'amount_minor', target_amount_minor, 'reason', trim(target_reason)
        )
    );
    return new_adjustment_id;
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
    if target_method not in ('CASH', 'BANK', 'MOBILE_BANKING', 'OTHER') then
        raise exception 'Unsupported payment method';
    end if;
    if target_paid_at is null then
        raise exception 'Payment time is required';
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

create or replace function public.reverse_payment(
    target_payment_id uuid,
    target_reason text
) returns void
language plpgsql
security definer
set search_path = public, auth
as $$
declare
    actor_mess_id uuid := public.current_mess_id();
    payment_row public.payments%rowtype;
    receipt_number_snapshot text;
    new_reversal_id uuid;
begin
    if auth.uid() is null or actor_mess_id is null or not public.is_admin_for_mess(actor_mess_id) then
        raise exception 'Active Admin or Main Admin access required' using errcode = '42501';
    end if;
    if target_reason is null or char_length(trim(target_reason)) not between 8 and 500 then
        raise exception 'A reversal reason between 8 and 500 characters is required';
    end if;

    select p.* into payment_row
    from public.payments p
    join public.billing_periods bp on bp.id = p.billing_period_id
    where p.id = target_payment_id
      and p.mess_id = actor_mess_id
      and bp.status = 'OPEN'
    for update of p;
    if not found then
        raise exception 'Reversible payment not found in an open period in your mess';
    end if;
    if exists (select 1 from public.payment_reversals where payment_id = target_payment_id) then
        raise exception 'Payment has already been reversed';
    end if;

    select receipt_number into receipt_number_snapshot
    from public.receipts where payment_id = target_payment_id for update;

    insert into public.payment_reversals (mess_id, member_id, payment_id, reason, reversed_by)
    values (actor_mess_id, payment_row.member_id, target_payment_id, trim(target_reason), auth.uid())
    returning id into new_reversal_id;

    update public.receipts
    set voided_at = now(), voided_by = auth.uid(), void_reason = trim(target_reason)
    where payment_id = target_payment_id;

    insert into public.audit_logs (mess_id, actor_user_id, action, target_user_id, details)
    values (
        actor_mess_id, auth.uid(), 'PAYMENT_REVERSED', payment_row.member_id,
        jsonb_build_object(
            'payment_id', target_payment_id, 'reversal_id', new_reversal_id,
            'receipt_number', receipt_number_snapshot, 'billing_period_id', payment_row.billing_period_id,
            'previous_amount_minor', payment_row.amount_minor, 'reason', trim(target_reason)
        )
    );
end;
$$;

create or replace function public.carry_forward_balance(
    target_user_id uuid,
    from_period_id uuid,
    to_period_id uuid
) returns uuid
language plpgsql
security definer
set search_path = public, auth
as $$
declare
    actor_mess_id uuid := public.current_mess_id();
    source_label text;
    target_open_id uuid;
    net_minor numeric;
    new_adjustment_id uuid;
    adjustment_kind text;
begin
    if auth.uid() is null or actor_mess_id is null or not public.is_admin_for_mess(actor_mess_id) then
        raise exception 'Active Admin or Main Admin access required' using errcode = '42501';
    end if;
    if from_period_id is null or to_period_id is null or from_period_id = to_period_id then
        raise exception 'Different source and target periods are required';
    end if;
    if not exists (
        select 1 from public.profiles where user_id = target_user_id and mess_id = actor_mess_id
    ) then
        raise exception 'Target member is not in your mess';
    end if;

    perform pg_advisory_xact_lock(hashtext(actor_mess_id::text));

    select label into source_label from public.billing_periods
    where id = from_period_id and mess_id = actor_mess_id and status = 'CLOSED'
    for update;
    if not found then
        raise exception 'Closed source period not found in your mess';
    end if;

    select id into target_open_id from public.billing_periods
    where id = to_period_id and mess_id = actor_mess_id and status = 'OPEN'
    for update;
    if not found then
        raise exception 'Open target period not found in your mess';
    end if;

    select
        coalesce((select sum(amount_minor) from public.billing_adjustments
            where member_id = target_user_id and billing_period_id = from_period_id
              and kind in ('OPENING_DUE', 'DEBIT_ADJUSTMENT')), 0)
        + coalesce((select sum(amount_minor) from public.charges
            where member_id = target_user_id and billing_period_id = from_period_id), 0)
        - coalesce((select sum(p.amount_minor) from public.payments p
            where p.member_id = target_user_id and p.billing_period_id = from_period_id
              and not exists (select 1 from public.payment_reversals pr where pr.payment_id = p.id)), 0)
        - coalesce((select sum(amount_minor) from public.billing_adjustments
            where member_id = target_user_id and billing_period_id = from_period_id
              and kind = 'CREDIT'), 0)
    into net_minor;

    if net_minor = 0 then
        raise exception 'There is no due or advance balance to carry forward';
    end if;
    adjustment_kind := case when net_minor > 0 then 'OPENING_DUE' else 'CREDIT' end;

    insert into public.billing_adjustments (
        mess_id, billing_period_id, member_id, kind, amount_minor,
        reason, source_period_id, created_by
    ) values (
        actor_mess_id, to_period_id, target_user_id, adjustment_kind, abs(net_minor)::bigint,
        'Carried forward from ' || source_label, from_period_id, auth.uid()
    ) returning id into new_adjustment_id;

    insert into public.audit_logs (mess_id, actor_user_id, action, target_user_id, details)
    values (
        actor_mess_id, auth.uid(), 'BALANCE_CARRIED_FORWARD', target_user_id,
        jsonb_build_object(
            'adjustment_id', new_adjustment_id, 'source_period_id', from_period_id,
            'target_period_id', to_period_id, 'kind', adjustment_kind, 'amount_minor', abs(net_minor)::bigint
        )
    );
    return new_adjustment_id;
end;
$$;

-- RPC execution is available to authenticated clients, but each function performs its
-- own active same-mess Admin/Main Admin check. Members receive permission errors.
revoke all on function public.create_billing_period(text, date, date) from public, anon;
revoke all on function public.close_billing_period(uuid) from public, anon;
revoke all on function public.create_charge(uuid, uuid, text, bigint, text, date, uuid) from public, anon;
revoke all on function public.add_billing_adjustment(uuid, uuid, text, bigint, text, uuid) from public, anon;
revoke all on function public.record_payment(uuid, uuid, bigint, text, timestamptz, text, text, uuid) from public, anon;
revoke all on function public.reverse_payment(uuid, text) from public, anon;
revoke all on function public.carry_forward_balance(uuid, uuid, uuid) from public, anon;

grant execute on function public.create_billing_period(text, date, date) to authenticated;
grant execute on function public.close_billing_period(uuid) to authenticated;
grant execute on function public.create_charge(uuid, uuid, text, bigint, text, date, uuid) to authenticated;
grant execute on function public.add_billing_adjustment(uuid, uuid, text, bigint, text, uuid) to authenticated;
grant execute on function public.record_payment(uuid, uuid, bigint, text, timestamptz, text, text, uuid) to authenticated;
grant execute on function public.reverse_payment(uuid, text) to authenticated;
grant execute on function public.carry_forward_balance(uuid, uuid, uuid) to authenticated;

comment on table public.payments is 'Audited payment ledger; amounts are BDT minor units. Direct client writes are not granted.';
comment on table public.receipts is 'One immutable receipt per payment; reversals void receipts without deleting history.';
comment on function public.reverse_payment(uuid, text) is 'Compensating reversal in an open period; original payment remains immutable.';
comment on view public.my_financial_summary is 'Per-period balance for auth.uid(); positive net is due, negative net is advance credit.';
