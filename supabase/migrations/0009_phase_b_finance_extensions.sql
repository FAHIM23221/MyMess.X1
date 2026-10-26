-- Phase B: additive fines, payment verification, and explicit meal billing.
-- Existing ledgers remain intact. All mutations are authenticated, audited RPCs.

create table public.fines (
    id uuid primary key default gen_random_uuid(),
    mess_id uuid not null references public.messes(id) on delete restrict,
    charge_id uuid not null unique references public.charges(id) on delete restrict,
    member_id uuid not null references public.profiles(user_id) on delete restrict,
    billing_period_id uuid not null references public.billing_periods(id) on delete restrict,
    amount_minor bigint not null check (amount_minor > 0 and amount_minor <= 1000000000000),
    reason text not null check (char_length(trim(reason)) between 8 and 500),
    created_by uuid not null references public.profiles(user_id) on delete restrict,
    created_at timestamptz not null default now()
);

create table public.payment_reviews (
    payment_id uuid primary key references public.payments(id) on delete restrict,
    mess_id uuid not null references public.messes(id) on delete restrict,
    status text not null check (status in ('VERIFIED', 'REJECTED')),
    reason text check (reason is null or char_length(trim(reason)) between 8 and 500),
    reviewed_by uuid not null references public.profiles(user_id) on delete restrict,
    reviewed_at timestamptz not null default now(),
    check ((status = 'REJECTED' and reason is not null) or status = 'VERIFIED')
);

create table public.meal_billing_policies (
    mess_id uuid primary key references public.messes(id) on delete restrict,
    price_per_half_meal_minor bigint not null check (
        price_per_half_meal_minor > 0 and price_per_half_meal_minor <= 100000000
    ),
    enabled boolean not null default false,
    updated_by uuid not null references public.profiles(user_id) on delete restrict,
    updated_at timestamptz not null default now()
);

create table public.meal_billing_runs (
    id uuid primary key default gen_random_uuid(),
    mess_id uuid not null references public.messes(id) on delete restrict,
    billing_period_id uuid not null references public.billing_periods(id) on delete restrict,
    total_half_meal_units bigint not null default 0 check (total_half_meal_units >= 0),
    total_amount_minor bigint not null default 0 check (total_amount_minor >= 0),
    created_by uuid not null references public.profiles(user_id) on delete restrict,
    created_at timestamptz not null default now(),
    unique (mess_id, billing_period_id)
);

alter table public.charges
    add column meal_billing_run_id uuid references public.meal_billing_runs(id) on delete restrict;
create unique index one_meal_billing_charge_per_member_run
    on public.charges (meal_billing_run_id, member_id)
    where meal_billing_run_id is not null;

create index fines_member_period_idx on public.fines(member_id, billing_period_id, created_at desc);
create index payment_reviews_mess_idx on public.payment_reviews(mess_id, reviewed_at desc);
create index meal_billing_runs_period_idx on public.meal_billing_runs(mess_id, billing_period_id);

alter table public.fines enable row level security;
alter table public.payment_reviews enable row level security;
alter table public.meal_billing_policies enable row level security;
alter table public.meal_billing_runs enable row level security;

create policy fines_select on public.fines for select to authenticated using (
    public.is_active_user() and mess_id = public.current_mess_id()
    and (member_id = auth.uid() or public.is_admin_for_mess(mess_id))
);
create policy payment_reviews_select on public.payment_reviews for select to authenticated using (
    public.is_active_user() and mess_id = public.current_mess_id()
    and exists (
        select 1 from public.payments p
        where p.id = payment_id and p.mess_id = payment_reviews.mess_id
          and (p.member_id = auth.uid() or public.is_admin_for_mess(p.mess_id))
    )
);
create policy meal_billing_policies_select on public.meal_billing_policies for select to authenticated using (
    public.is_active_user() and mess_id = public.current_mess_id()
);
create policy meal_billing_runs_select on public.meal_billing_runs for select to authenticated using (
    public.is_active_user() and mess_id = public.current_mess_id()
);

revoke all on public.fines from public, anon, authenticated;
revoke all on public.payment_reviews from public, anon, authenticated;
revoke all on public.meal_billing_policies from public, anon, authenticated;
revoke all on public.meal_billing_runs from public, anon, authenticated;
grant select on public.fines to authenticated;
grant select on public.payment_reviews to authenticated;
grant select on public.meal_billing_policies to authenticated;
grant select on public.meal_billing_runs to authenticated;

create or replace function public.create_fine(
    target_user_id uuid,
    target_period_id uuid,
    target_amount_minor bigint,
    target_reason text,
    target_due_date date,
    client_request_id uuid
) returns uuid language plpgsql security definer set search_path = public, auth as $$
declare
    actor_mess_id uuid := public.current_mess_id();
    existing_charge_id uuid;
    existing_fine_id uuid;
    new_charge_id uuid;
    new_fine_id uuid;
begin
    if auth.uid() is null or actor_mess_id is null or not public.is_admin_for_mess(actor_mess_id) then
        raise exception 'Active Admin or Main Admin access required' using errcode = '42501';
    end if;
    if client_request_id is null then raise exception 'A unique client request ID is required'; end if;
    if target_amount_minor is null or target_amount_minor <= 0 or target_amount_minor > 1000000000000 then
        raise exception 'A positive, bounded fine amount is required';
    end if;
    if target_reason is null or char_length(trim(target_reason)) not between 8 and 500 then
        raise exception 'A fine reason between 8 and 500 characters is required';
    end if;
    if not exists (select 1 from public.profiles where user_id = target_user_id and mess_id = actor_mess_id) then
        raise exception 'Target member is not in your mess';
    end if;
    if not exists (select 1 from public.billing_periods where id = target_period_id and mess_id = actor_mess_id and status = 'OPEN') then
        raise exception 'Open billing period not found in your mess';
    end if;

    select c.id into existing_charge_id from public.charges c
    where c.mess_id = actor_mess_id and c.client_request_id = create_fine.client_request_id;
    if found then
        select f.id into existing_fine_id from public.fines f where f.charge_id = existing_charge_id;
        if existing_fine_id is null then raise exception 'Request ID is already used by another charge'; end if;
        return existing_fine_id;
    end if;

    insert into public.charges (
        mess_id, billing_period_id, member_id, category, amount_minor,
        description, due_date, client_request_id, created_by
    ) values (
        actor_mess_id, target_period_id, target_user_id, 'FINE', target_amount_minor,
        'Fine: ' || trim(target_reason), target_due_date, client_request_id, auth.uid()
    ) returning id into new_charge_id;
    insert into public.fines (
        mess_id, charge_id, member_id, billing_period_id, amount_minor, reason, created_by
    ) values (
        actor_mess_id, new_charge_id, target_user_id, target_period_id,
        target_amount_minor, trim(target_reason), auth.uid()
    ) returning id into new_fine_id;
    insert into public.audit_logs (mess_id, actor_user_id, action, target_user_id, details)
    values (actor_mess_id, auth.uid(), 'FINE_CREATED', target_user_id, jsonb_build_object(
        'fine_id', new_fine_id, 'charge_id', new_charge_id, 'billing_period_id', target_period_id,
        'amount_minor', target_amount_minor, 'reason', trim(target_reason), 'client_request_id', client_request_id
    ));
    return new_fine_id;
end;
$$;

create or replace function public.review_payment(
    target_payment_id uuid,
    target_status text,
    target_reason text
) returns uuid language plpgsql security definer set search_path = public, auth as $$
declare
    actor_mess_id uuid := public.current_mess_id();
    target_payment public.payments%rowtype;
begin
    if auth.uid() is null or actor_mess_id is null or not public.is_admin_for_mess(actor_mess_id) then
        raise exception 'Active Admin or Main Admin access required' using errcode = '42501';
    end if;
    if target_status not in ('VERIFIED', 'REJECTED') then raise exception 'Unsupported payment review status'; end if;
    if target_status = 'REJECTED' and (target_reason is null or char_length(trim(target_reason)) not between 8 and 500) then
        raise exception 'A rejection reason between 8 and 500 characters is required';
    end if;
    if target_reason is not null and char_length(trim(target_reason)) > 500 then raise exception 'Review reason is too long'; end if;
    select * into target_payment from public.payments
    where id = target_payment_id and mess_id = actor_mess_id for update;
    if not found then raise exception 'Payment not found in your mess'; end if;
    if exists (select 1 from public.payment_reversals where payment_id = target_payment_id) then
        raise exception 'A reversed payment cannot be reviewed';
    end if;
    insert into public.payment_reviews (payment_id, mess_id, status, reason, reviewed_by)
    values (target_payment_id, actor_mess_id, target_status, nullif(trim(target_reason), ''), auth.uid())
    on conflict (payment_id) do update set status = excluded.status, reason = excluded.reason,
        reviewed_by = excluded.reviewed_by, reviewed_at = now();
    insert into public.audit_logs (mess_id, actor_user_id, action, target_user_id, details)
    values (actor_mess_id, auth.uid(), 'PAYMENT_REVIEWED', target_payment.member_id, jsonb_build_object(
        'payment_id', target_payment_id, 'status', target_status, 'reason', nullif(trim(target_reason), '')
    ));
    return target_payment_id;
end;
$$;

create or replace function public.set_meal_billing_policy(
    target_price_per_half_meal_minor bigint,
    target_enabled boolean
) returns void language plpgsql security definer set search_path = public, auth as $$
declare actor_mess_id uuid := public.current_mess_id();
begin
    if auth.uid() is null or actor_mess_id is null or not public.is_admin_for_mess(actor_mess_id) then
        raise exception 'Active Admin or Main Admin access required' using errcode = '42501';
    end if;
    if target_price_per_half_meal_minor is null or target_price_per_half_meal_minor <= 0
       or target_price_per_half_meal_minor > 100000000 then
        raise exception 'A positive, bounded half-meal price is required';
    end if;
    insert into public.meal_billing_policies (mess_id, price_per_half_meal_minor, enabled, updated_by)
    values (actor_mess_id, target_price_per_half_meal_minor, coalesce(target_enabled, false), auth.uid())
    on conflict (mess_id) do update set price_per_half_meal_minor = excluded.price_per_half_meal_minor,
        enabled = excluded.enabled, updated_by = excluded.updated_by, updated_at = now();
    insert into public.audit_logs (mess_id, actor_user_id, action, target_user_id, details)
    values (actor_mess_id, auth.uid(), 'MEAL_BILLING_POLICY_UPDATED', auth.uid(), jsonb_build_object(
        'price_per_half_meal_minor', target_price_per_half_meal_minor, 'enabled', coalesce(target_enabled, false)
    ));
end;
$$;

create or replace function public.generate_meal_bill(
    target_period_id uuid,
    target_due_date date
) returns uuid language plpgsql security definer set search_path = public, auth as $$
declare
    actor_mess_id uuid := public.current_mess_id();
    target_period public.billing_periods%rowtype;
    policy public.meal_billing_policies%rowtype;
    existing_run_id uuid;
    new_run_id uuid;
    meal_row record;
    line_amount bigint;
    total_units bigint := 0;
    total_amount bigint := 0;
begin
    if auth.uid() is null or actor_mess_id is null or not public.is_admin_for_mess(actor_mess_id) then
        raise exception 'Active Admin or Main Admin access required' using errcode = '42501';
    end if;
    select * into target_period from public.billing_periods
    where id = target_period_id and mess_id = actor_mess_id for update;
    if not found then raise exception 'Billing period not found in your mess'; end if;
    select id into existing_run_id from public.meal_billing_runs
    where mess_id = actor_mess_id and billing_period_id = target_period_id;
    if found then return existing_run_id; end if;
    if target_period.status <> 'OPEN' then raise exception 'A closed period cannot receive a meal bill'; end if;
    select * into policy from public.meal_billing_policies where mess_id = actor_mess_id;
    if not found or not policy.enabled then raise exception 'Enable a meal billing policy before generating a bill'; end if;

    insert into public.meal_billing_runs (mess_id, billing_period_id, created_by)
    values (actor_mess_id, target_period_id, auth.uid()) returning id into new_run_id;
    for meal_row in
        select m.member_id, sum(m.quantity_units)::bigint as units
        from public.meal_entries m
        where m.mess_id = actor_mess_id
          and m.meal_date between target_period.starts_on and target_period.ends_on
          and not exists (select 1 from public.meal_reversals mr where mr.meal_entry_id = m.id)
        group by m.member_id
        having sum(m.quantity_units) > 0
    loop
        if meal_row.units > 1000000000000 / policy.price_per_half_meal_minor then
            raise exception 'Meal bill amount exceeds the supported boundary';
        end if;
        line_amount := meal_row.units * policy.price_per_half_meal_minor;
        insert into public.charges (
            mess_id, billing_period_id, member_id, category, amount_minor,
            description, due_date, client_request_id, created_by, meal_billing_run_id
        ) values (
            actor_mess_id, target_period_id, meal_row.member_id, 'MEAL_CHARGE', line_amount,
            'Meal bill: ' || meal_row.units || ' half-meal units', target_due_date,
            gen_random_uuid(), auth.uid(), new_run_id
        );
        total_units := total_units + meal_row.units;
        total_amount := total_amount + line_amount;
    end loop;
    update public.meal_billing_runs set total_half_meal_units = total_units,
        total_amount_minor = total_amount where id = new_run_id;
    insert into public.audit_logs (mess_id, actor_user_id, action, target_user_id, details)
    values (actor_mess_id, auth.uid(), 'MEAL_BILL_GENERATED', auth.uid(), jsonb_build_object(
        'run_id', new_run_id, 'billing_period_id', target_period_id,
        'total_half_meal_units', total_units, 'total_amount_minor', total_amount
    ));
    return new_run_id;
end;
$$;

revoke all on function public.create_fine(uuid, uuid, bigint, text, date, uuid) from public, anon;
revoke all on function public.review_payment(uuid, text, text) from public, anon;
revoke all on function public.set_meal_billing_policy(bigint, boolean) from public, anon;
revoke all on function public.generate_meal_bill(uuid, date) from public, anon;
grant execute on function public.create_fine(uuid, uuid, bigint, text, date, uuid) to authenticated;
grant execute on function public.review_payment(uuid, text, text) to authenticated;
grant execute on function public.set_meal_billing_policy(bigint, boolean) to authenticated;
grant execute on function public.generate_meal_bill(uuid, date) to authenticated;

comment on table public.fines is 'Reasoned fine metadata linked one-to-one to an immutable FINE charge.';
comment on table public.payment_reviews is 'Separate Admin verification decision; it does not alter payment arithmetic.';
comment on function public.generate_meal_bill(uuid, date) is 'One transactional, idempotent meal-billing run per mess and billing period.';
