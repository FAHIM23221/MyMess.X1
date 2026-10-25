-- Phase C: additive meal schedules, purchase metadata, corrections, alerts, and cash controls.
-- Existing source ledgers remain append-only. New guards also cover older RPC entry points.

create table public.meal_schedules (
    id uuid primary key default gen_random_uuid(),
    mess_id uuid not null references public.messes(id) on delete restrict,
    member_id uuid not null references public.profiles(user_id) on delete restrict,
    starts_on date not null,
    ends_on date,
    status text not null check (status in ('ON', 'OFF')),
    reason text check (reason is null or char_length(trim(reason)) between 2 and 500),
    created_by uuid not null references public.profiles(user_id) on delete restrict,
    created_at timestamptz not null default now(),
    check (ends_on is null or ends_on >= starts_on)
);
create index meal_schedules_member_dates_idx on public.meal_schedules(member_id, starts_on, ends_on);

create table public.rice_purchases (
    id uuid primary key default gen_random_uuid(),
    mess_id uuid not null references public.messes(id) on delete restrict,
    movement_id uuid not null unique references public.rice_movements(id) on delete restrict,
    expense_id uuid not null unique references public.expenses(id) on delete restrict,
    purchase_unit text not null check (purchase_unit in ('GRAM', 'KG')),
    unit_price_minor bigint not null check (unit_price_minor > 0 and unit_price_minor <= 100000000),
    total_amount_minor bigint not null check (total_amount_minor > 0 and total_amount_minor <= 1000000000000),
    vendor text not null check (char_length(trim(vendor)) between 2 and 160),
    receipt_reference text check (receipt_reference is null or char_length(trim(receipt_reference)) between 2 and 120),
    purchased_on date not null,
    client_request_id uuid not null,
    created_by uuid not null references public.profiles(user_id) on delete restrict,
    created_at timestamptz not null default now(),
    unique (mess_id, client_request_id)
);

create table public.rice_movement_reversals (
    id uuid primary key default gen_random_uuid(),
    mess_id uuid not null references public.messes(id) on delete restrict,
    movement_id uuid not null unique references public.rice_movements(id) on delete restrict,
    reversal_movement_id uuid not null unique references public.rice_movements(id) on delete restrict,
    reason text not null check (char_length(trim(reason)) between 8 and 500),
    reversed_by uuid not null references public.profiles(user_id) on delete restrict,
    created_at timestamptz not null default now()
);

create table public.expense_details (
    expense_id uuid primary key references public.expenses(id) on delete restrict,
    mess_id uuid not null references public.messes(id) on delete restrict,
    vendor text check (vendor is null or char_length(trim(vendor)) between 2 and 160),
    receipt_reference text check (receipt_reference is null or char_length(trim(receipt_reference)) between 2 and 120),
    approval_status text not null default 'PENDING' check (approval_status in ('PENDING', 'APPROVED', 'REJECTED')),
    approval_reason text check (approval_reason is null or char_length(trim(approval_reason)) between 8 and 500),
    approved_by uuid references public.profiles(user_id) on delete restrict,
    approved_at timestamptz,
    check ((approval_status = 'PENDING' and approved_by is null and approved_at is null)
        or (approval_status <> 'PENDING' and approved_by is not null and approved_at is not null))
);

create table public.mess_settings (
    mess_id uuid primary key references public.messes(id) on delete restrict,
    locale text not null default 'bn' check (locale in ('bn', 'en')),
    timezone text not null default 'Asia/Dhaka' check (timezone = 'Asia/Dhaka'),
    low_rice_threshold_grams integer not null default 0 check (low_rice_threshold_grams between 0 and 1000000000),
    meal_reminders_enabled boolean not null default true,
    updated_by uuid not null references public.profiles(user_id) on delete restrict,
    updated_at timestamptz not null default now()
);

create table public.notifications (
    id uuid primary key default gen_random_uuid(),
    mess_id uuid not null references public.messes(id) on delete restrict,
    recipient_id uuid not null references public.profiles(user_id) on delete restrict,
    kind text not null check (kind in ('MEAL', 'RICE', 'DUTY', 'NOTICE', 'FINANCE', 'SYSTEM')),
    title text not null check (char_length(trim(title)) between 2 and 160),
    body text not null check (char_length(trim(body)) between 2 and 1000),
    read_at timestamptz,
    created_at timestamptz not null default now()
);
create index notifications_recipient_idx on public.notifications(recipient_id, created_at desc);

create table public.rice_stock_alerts (
    id uuid primary key default gen_random_uuid(),
    mess_id uuid not null references public.messes(id) on delete restrict,
    stock_grams bigint not null,
    threshold_grams integer not null,
    status text not null default 'OPEN' check (status in ('OPEN', 'RESOLVED')),
    created_at timestamptz not null default now(),
    resolved_at timestamptz,
    check ((status = 'OPEN' and resolved_at is null) or (status = 'RESOLVED' and resolved_at is not null))
);
create unique index one_open_rice_alert_per_mess on public.rice_stock_alerts(mess_id) where status = 'OPEN';

create table public.cash_accounts (
    id uuid primary key default gen_random_uuid(),
    mess_id uuid not null references public.messes(id) on delete restrict,
    name text not null check (char_length(trim(name)) between 2 and 80),
    kind text not null check (kind in ('CASH', 'BANK', 'MOBILE_WALLET')),
    opening_balance_minor bigint not null default 0,
    active boolean not null default true,
    created_by uuid not null references public.profiles(user_id) on delete restrict,
    created_at timestamptz not null default now(),
    unique (mess_id, name)
);
create table public.cash_entry_accounts (
    cash_book_entry_id uuid primary key references public.cash_book_entries(id) on delete restrict,
    account_id uuid not null references public.cash_accounts(id) on delete restrict,
    mess_id uuid not null references public.messes(id) on delete restrict,
    assigned_by uuid not null references public.profiles(user_id) on delete restrict,
    assigned_at timestamptz not null default now()
);
create table public.cash_period_locks (
    id uuid primary key default gen_random_uuid(),
    mess_id uuid not null references public.messes(id) on delete restrict,
    starts_on date not null,
    ends_on date not null,
    opening_balance_minor bigint not null,
    closing_balance_minor bigint not null,
    note text check (note is null or char_length(trim(note)) between 8 and 500),
    locked_by uuid not null references public.profiles(user_id) on delete restrict,
    locked_at timestamptz not null default now(),
    check (ends_on >= starts_on),
    unique (mess_id, starts_on, ends_on)
);

alter table public.meal_schedules enable row level security;
alter table public.rice_purchases enable row level security;
alter table public.rice_movement_reversals enable row level security;
alter table public.expense_details enable row level security;
alter table public.mess_settings enable row level security;
alter table public.notifications enable row level security;
alter table public.rice_stock_alerts enable row level security;
alter table public.cash_accounts enable row level security;
alter table public.cash_entry_accounts enable row level security;
alter table public.cash_period_locks enable row level security;

create policy meal_schedules_select on public.meal_schedules for select to authenticated using (
    public.is_active_user() and mess_id = public.current_mess_id()
    and (member_id = auth.uid() or public.is_admin_for_mess(mess_id))
);
create policy rice_purchases_select on public.rice_purchases for select to authenticated using (
    public.is_active_user() and mess_id = public.current_mess_id()
);
create policy rice_reversals_select on public.rice_movement_reversals for select to authenticated using (
    public.is_active_user() and mess_id = public.current_mess_id()
);
create policy expense_details_select on public.expense_details for select to authenticated using (
    public.is_active_user() and mess_id = public.current_mess_id()
);
create policy mess_settings_select on public.mess_settings for select to authenticated using (
    public.is_active_user() and mess_id = public.current_mess_id()
);
create policy notifications_select on public.notifications for select to authenticated using (
    public.is_active_user() and mess_id = public.current_mess_id() and recipient_id = auth.uid()
);
create policy rice_alerts_select on public.rice_stock_alerts for select to authenticated using (
    public.is_active_user() and mess_id = public.current_mess_id()
);
create policy cash_accounts_select on public.cash_accounts for select to authenticated using (
    public.is_active_user() and mess_id = public.current_mess_id()
);
create policy cash_entry_accounts_select on public.cash_entry_accounts for select to authenticated using (
    public.is_active_user() and mess_id = public.current_mess_id()
);
create policy cash_locks_select on public.cash_period_locks for select to authenticated using (
    public.is_active_user() and mess_id = public.current_mess_id()
);

revoke all on public.meal_schedules from public, anon, authenticated;
revoke all on public.rice_purchases from public, anon, authenticated;
revoke all on public.rice_movement_reversals from public, anon, authenticated;
revoke all on public.expense_details from public, anon, authenticated;
revoke all on public.mess_settings from public, anon, authenticated;
revoke all on public.notifications from public, anon, authenticated;
revoke all on public.rice_stock_alerts from public, anon, authenticated;
revoke all on public.cash_accounts from public, anon, authenticated;
revoke all on public.cash_entry_accounts from public, anon, authenticated;
revoke all on public.cash_period_locks from public, anon, authenticated;
grant select on public.meal_schedules to authenticated;
grant select on public.rice_purchases to authenticated;
grant select on public.rice_movement_reversals to authenticated;
grant select on public.expense_details to authenticated;
grant select on public.mess_settings to authenticated;
grant select on public.notifications to authenticated;
grant select on public.rice_stock_alerts to authenticated;
grant select on public.cash_accounts to authenticated;
grant select on public.cash_entry_accounts to authenticated;
grant select on public.cash_period_locks to authenticated;

create or replace function public.meal_schedule_guard()
returns trigger language plpgsql security definer set search_path = public, pg_temp as $$
begin
    if exists (
        select 1 from public.meal_schedules s
        where s.mess_id = new.mess_id and s.member_id = new.member_id
          and s.status = 'OFF' and new.meal_date >= s.starts_on
          and (s.ends_on is null or new.meal_date <= s.ends_on)
    ) then
        raise exception 'Meal entry is disabled by an active OFF schedule';
    end if;
    return new;
end;
$$;
create trigger meal_entries_schedule_guard before insert on public.meal_entries
for each row execute function public.meal_schedule_guard();

create or replace function public.cash_period_lock_guard()
returns trigger language plpgsql security definer set search_path = public, pg_temp as $$
begin
    if exists (
        select 1 from public.cash_period_locks l
        where l.mess_id = new.mess_id
          and (new.occurred_at at time zone 'Asia/Dhaka')::date between l.starts_on and l.ends_on
    ) then
        raise exception 'Cash period is locked for this date';
    end if;
    return new;
end;
$$;
create trigger cash_book_period_lock_guard before insert on public.cash_book_entries
for each row execute function public.cash_period_lock_guard();

create or replace function public.rice_stock_alert_after_movement()
returns trigger language plpgsql security definer set search_path = public, pg_temp as $$
declare
    threshold integer;
    stock bigint;
    admin_row record;
begin
    select low_rice_threshold_grams into threshold from public.mess_settings where mess_id = new.mess_id;
    if threshold is null or threshold <= 0 then return new; end if;
    perform pg_advisory_xact_lock(hashtext(new.mess_id::text));
    select coalesce(sum(case when kind = 'STOCK_IN' then quantity_grams else -quantity_grams end), 0)
    into stock from public.rice_movements where mess_id = new.mess_id;
    if stock <= threshold then
        if not exists (select 1 from public.rice_stock_alerts where mess_id = new.mess_id and status = 'OPEN') then
            insert into public.rice_stock_alerts (mess_id, stock_grams, threshold_grams)
            values (new.mess_id, stock, threshold);
            for admin_row in
                select user_id from public.profiles
                where mess_id = new.mess_id and account_status = 'ACTIVE' and role = 'ADMIN'
            loop
                insert into public.notifications (mess_id, recipient_id, kind, title, body)
                values (new.mess_id, admin_row.user_id, 'RICE', 'Low rice stock',
                    'Rice stock is ' || stock || ' grams; threshold is ' || threshold || ' grams.');
            end loop;
        end if;
    else
        update public.rice_stock_alerts set status = 'RESOLVED', resolved_at = now()
        where mess_id = new.mess_id and status = 'OPEN';
    end if;
    return new;
end;
$$;
create trigger rice_movements_stock_alert after insert on public.rice_movements
for each row execute function public.rice_stock_alert_after_movement();

create or replace function public.set_meal_schedule(
    target_user_id uuid,
    target_start date,
    target_end date,
    target_status text,
    target_reason text
) returns uuid language plpgsql security definer set search_path = public, auth as $$
declare
    actor_mess_id uuid := public.current_mess_id();
    new_id uuid;
begin
    if auth.uid() is null or actor_mess_id is null or not public.is_active_user() then
        raise exception 'An active member session is required' using errcode = '42501';
    end if;
    if auth.uid() <> target_user_id and not public.is_admin_for_mess(actor_mess_id) then
        raise exception 'You can only change your own meal schedule' using errcode = '42501';
    end if;
    if not exists (select 1 from public.profiles where user_id = target_user_id and mess_id = actor_mess_id and account_status = 'ACTIVE') then
        raise exception 'Active target member not found in your mess';
    end if;
    if target_start is null or (target_end is not null and target_end < target_start) then raise exception 'Invalid meal schedule dates'; end if;
    if target_status not in ('ON', 'OFF') then raise exception 'Unsupported meal schedule status'; end if;
    if target_reason is not null and char_length(trim(target_reason)) not between 2 and 500 then raise exception 'Invalid schedule reason'; end if;
    perform pg_advisory_xact_lock(hashtext(target_user_id::text));
    if exists (
        select 1 from public.meal_schedules s
        where s.mess_id = actor_mess_id and s.member_id = target_user_id
          and target_start <= coalesce(s.ends_on, date '9999-12-31')
          and coalesce(target_end, date '9999-12-31') >= s.starts_on
    ) then
        raise exception 'Meal schedule overlaps an existing schedule';
    end if;
    insert into public.meal_schedules (mess_id, member_id, starts_on, ends_on, status, reason, created_by)
    values (actor_mess_id, target_user_id, target_start, target_end, target_status, nullif(trim(target_reason), ''), auth.uid())
    returning id into new_id;
    insert into public.notifications (mess_id, recipient_id, kind, title, body)
    values (actor_mess_id, target_user_id, 'MEAL', 'Meal schedule updated',
        'Meal schedule is ' || target_status || ' from ' || target_start || coalesce(' to ' || target_end, ''));
    insert into public.audit_logs (mess_id, actor_user_id, action, target_user_id, details)
    values (actor_mess_id, auth.uid(), 'MEAL_SCHEDULE_CREATED', target_user_id, jsonb_build_object(
        'schedule_id', new_id, 'starts_on', target_start, 'ends_on', target_end, 'status', target_status
    ));
    return new_id;
end;
$$;

create or replace function public.record_rice_purchase(
    target_quantity_grams integer,
    target_unit text,
    target_unit_price_minor bigint,
    target_vendor text,
    target_receipt_reference text,
    target_purchased_on date,
    client_request_id uuid
) returns uuid language plpgsql security definer set search_path = public, auth as $$
declare
    actor_mess_id uuid := public.current_mess_id();
    existing_id uuid;
    movement_id uuid;
    expense_id uuid;
    purchase_id uuid;
    total_amount bigint;
begin
    if auth.uid() is null or actor_mess_id is null or not public.is_admin_for_mess(actor_mess_id) then
        raise exception 'Active Admin or Main Admin access required' using errcode = '42501';
    end if;
    if client_request_id is null then raise exception 'A unique client request ID is required'; end if;
    select id into existing_id from public.rice_purchases rp
    where rp.mess_id = actor_mess_id and rp.client_request_id = record_rice_purchase.client_request_id;
    if found then return existing_id; end if;
    if target_quantity_grams is null or target_quantity_grams not between 1 and 1000000000 then raise exception 'Invalid rice quantity'; end if;
    if target_unit not in ('GRAM', 'KG') then raise exception 'Unsupported rice purchase unit'; end if;
    if target_unit_price_minor is null or target_unit_price_minor <= 0 or target_unit_price_minor > 100000000 then raise exception 'Invalid rice unit price'; end if;
    if target_vendor is null or char_length(trim(target_vendor)) not between 2 and 160 then raise exception 'Invalid rice vendor'; end if;
    if target_receipt_reference is not null and char_length(trim(target_receipt_reference)) not between 2 and 120 then raise exception 'Invalid receipt reference'; end if;
    if target_purchased_on is null or target_purchased_on > (now() at time zone 'Asia/Dhaka')::date then raise exception 'Invalid rice purchase date'; end if;
    if target_unit = 'KG' then
        total_amount := ceil(target_quantity_grams::numeric * target_unit_price_minor / 1000)::bigint;
    else
        total_amount := target_quantity_grams::bigint * target_unit_price_minor;
    end if;
    if total_amount <= 0 or total_amount > 1000000000000 then raise exception 'Rice purchase total exceeds the supported boundary'; end if;
    movement_id := public.record_rice_movement('STOCK_IN', target_quantity_grams,
        'Rice purchase: ' || trim(target_vendor), client_request_id);
    expense_id := public.record_expense('RICE', total_amount, target_purchased_on,
        'Rice purchase: ' || trim(target_vendor), target_receipt_reference, client_request_id);
    insert into public.rice_purchases (
        mess_id, movement_id, expense_id, purchase_unit, unit_price_minor, total_amount_minor,
        vendor, receipt_reference, purchased_on, client_request_id, created_by
    ) values (
        actor_mess_id, movement_id, expense_id, target_unit, target_unit_price_minor, total_amount,
        trim(target_vendor), nullif(trim(target_receipt_reference), ''), target_purchased_on, client_request_id, auth.uid()
    ) returning id into purchase_id;
    insert into public.expense_details (expense_id, mess_id, vendor, receipt_reference, approval_status)
    values (expense_id, actor_mess_id, trim(target_vendor), nullif(trim(target_receipt_reference), ''), 'PENDING');
    insert into public.audit_logs (mess_id, actor_user_id, action, target_user_id, details)
    values (actor_mess_id, auth.uid(), 'RICE_PURCHASE_RECORDED', auth.uid(), jsonb_build_object(
        'purchase_id', purchase_id, 'movement_id', movement_id, 'expense_id', expense_id,
        'quantity_grams', target_quantity_grams, 'total_amount_minor', total_amount, 'vendor', trim(target_vendor)
    ));
    return purchase_id;
end;
$$;

create or replace function public.reverse_rice_movement(
    target_movement_id uuid,
    target_reason text
) returns uuid language plpgsql security definer set search_path = public, auth as $$
declare
    actor_mess_id uuid := public.current_mess_id();
    source public.rice_movements%rowtype;
    reversal_id uuid;
    link_id uuid;
begin
    if auth.uid() is null or actor_mess_id is null or not public.is_admin_for_mess(actor_mess_id) then
        raise exception 'Active Admin or Main Admin access required' using errcode = '42501';
    end if;
    if target_reason is null or char_length(trim(target_reason)) not between 8 and 500 then raise exception 'A reversal reason is required'; end if;
    select * into source from public.rice_movements where id = target_movement_id and mess_id = actor_mess_id for update;
    if not found then raise exception 'Rice movement not found in your mess'; end if;
    if exists (select 1 from public.rice_movement_reversals where movement_id = target_movement_id) then raise exception 'Rice movement has already been reversed'; end if;
    reversal_id := public.record_rice_movement(
        case when source.kind = 'STOCK_IN' then 'STOCK_OUT' else 'STOCK_IN' end,
        source.quantity_grams, 'Rice reversal: ' || trim(target_reason), gen_random_uuid()
    );
    insert into public.rice_movement_reversals (mess_id, movement_id, reversal_movement_id, reason, reversed_by)
    values (actor_mess_id, target_movement_id, reversal_id, trim(target_reason), auth.uid()) returning id into link_id;
    insert into public.audit_logs (mess_id, actor_user_id, action, target_user_id, details)
    values (actor_mess_id, auth.uid(), 'RICE_MOVEMENT_REVERSED', auth.uid(), jsonb_build_object(
        'movement_id', target_movement_id, 'reversal_movement_id', reversal_id, 'reason', trim(target_reason)
    ));
    return link_id;
end;
$$;

create or replace function public.update_expense_details(
    target_expense_id uuid,
    target_vendor text,
    target_receipt_reference text
) returns void language plpgsql security definer set search_path = public, auth as $$
declare actor_mess_id uuid := public.current_mess_id();
begin
    if auth.uid() is null or actor_mess_id is null or not public.is_admin_for_mess(actor_mess_id) then raise exception 'Active Admin or Main Admin access required' using errcode = '42501'; end if;
    if not exists (select 1 from public.expenses where id = target_expense_id and mess_id = actor_mess_id) then raise exception 'Expense not found in your mess'; end if;
    if target_vendor is not null and char_length(trim(target_vendor)) not between 2 and 160 then raise exception 'Invalid expense vendor'; end if;
    if target_receipt_reference is not null and char_length(trim(target_receipt_reference)) not between 2 and 120 then raise exception 'Invalid expense receipt reference'; end if;
    insert into public.expense_details (expense_id, mess_id, vendor, receipt_reference)
    values (target_expense_id, actor_mess_id, nullif(trim(target_vendor), ''), nullif(trim(target_receipt_reference), ''))
    on conflict (expense_id) do update set vendor = excluded.vendor, receipt_reference = excluded.receipt_reference;
    insert into public.audit_logs (mess_id, actor_user_id, action, target_user_id, details)
    values (actor_mess_id, auth.uid(), 'EXPENSE_DETAILS_UPDATED', auth.uid(), jsonb_build_object('expense_id', target_expense_id));
end;
$$;

create or replace function public.review_expense(
    target_expense_id uuid,
    target_status text,
    target_reason text
) returns void language plpgsql security definer set search_path = public, auth as $$
declare actor_mess_id uuid := public.current_mess_id();
begin
    if auth.uid() is null or actor_mess_id is null or not public.is_admin_for_mess(actor_mess_id) then raise exception 'Active Admin or Main Admin access required' using errcode = '42501'; end if;
    if target_status not in ('APPROVED', 'REJECTED') then raise exception 'Unsupported expense approval status'; end if;
    if target_status = 'REJECTED' and (target_reason is null or char_length(trim(target_reason)) not between 8 and 500) then raise exception 'An expense rejection reason is required'; end if;
    if not exists (select 1 from public.expenses where id = target_expense_id and mess_id = actor_mess_id) then raise exception 'Expense not found in your mess'; end if;
    insert into public.expense_details (expense_id, mess_id, approval_status, approval_reason, approved_by, approved_at)
    values (target_expense_id, actor_mess_id, target_status, nullif(trim(target_reason), ''), auth.uid(), now())
    on conflict (expense_id) do update set approval_status = excluded.approval_status,
        approval_reason = excluded.approval_reason, approved_by = excluded.approved_by, approved_at = excluded.approved_at;
    insert into public.audit_logs (mess_id, actor_user_id, action, target_user_id, details)
    values (actor_mess_id, auth.uid(), 'EXPENSE_REVIEWED', auth.uid(), jsonb_build_object('expense_id', target_expense_id, 'status', target_status));
end;
$$;

create or replace function public.upsert_cash_account(
    target_name text,
    target_kind text,
    target_opening_balance_minor bigint
) returns uuid language plpgsql security definer set search_path = public, auth as $$
declare actor_mess_id uuid := public.current_mess_id(); new_id uuid;
begin
    if auth.uid() is null or actor_mess_id is null or not public.is_admin_for_mess(actor_mess_id) then raise exception 'Active Admin or Main Admin access required' using errcode = '42501'; end if;
    if target_name is null or char_length(trim(target_name)) not between 2 and 80 then raise exception 'Invalid cash account name'; end if;
    if target_kind not in ('CASH', 'BANK', 'MOBILE_WALLET') then raise exception 'Unsupported cash account kind'; end if;
    if target_opening_balance_minor is null or abs(target_opening_balance_minor) > 1000000000000 then raise exception 'Invalid opening balance'; end if;
    insert into public.cash_accounts (mess_id, name, kind, opening_balance_minor, created_by)
    values (actor_mess_id, trim(target_name), target_kind, target_opening_balance_minor, auth.uid())
    on conflict (mess_id, name) do update set kind = excluded.kind, opening_balance_minor = excluded.opening_balance_minor
    returning id into new_id;
    insert into public.audit_logs (mess_id, actor_user_id, action, target_user_id, details)
    values (actor_mess_id, auth.uid(), 'CASH_ACCOUNT_SAVED', auth.uid(), jsonb_build_object('account_id', new_id, 'kind', target_kind));
    return new_id;
end;
$$;

create or replace function public.assign_cash_entry_account(
    target_cash_book_entry_id uuid,
    target_account_id uuid
) returns void language plpgsql security definer set search_path = public, auth as $$
declare actor_mess_id uuid := public.current_mess_id();
begin
    if auth.uid() is null or actor_mess_id is null or not public.is_admin_for_mess(actor_mess_id) then raise exception 'Active Admin or Main Admin access required' using errcode = '42501'; end if;
    if not exists (select 1 from public.cash_book_entries where id = target_cash_book_entry_id and mess_id = actor_mess_id) then raise exception 'Cash entry not found in your mess'; end if;
    if not exists (select 1 from public.cash_accounts where id = target_account_id and mess_id = actor_mess_id and active) then raise exception 'Active cash account not found in your mess'; end if;
    insert into public.cash_entry_accounts (cash_book_entry_id, account_id, mess_id, assigned_by)
    values (target_cash_book_entry_id, target_account_id, actor_mess_id, auth.uid())
    on conflict (cash_book_entry_id) do update set account_id = excluded.account_id, assigned_by = excluded.assigned_by, assigned_at = now();
    insert into public.audit_logs (mess_id, actor_user_id, action, target_user_id, details)
    values (actor_mess_id, auth.uid(), 'CASH_ENTRY_ACCOUNT_ASSIGNED', auth.uid(), jsonb_build_object('cash_book_entry_id', target_cash_book_entry_id, 'account_id', target_account_id));
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
    into closing_balance from public.cash_book_entries
    where mess_id = actor_mess_id and (occurred_at at time zone 'Asia/Dhaka')::date between target_start and target_end;
    insert into public.cash_period_locks (mess_id, starts_on, ends_on, opening_balance_minor, closing_balance_minor, note, locked_by)
    values (actor_mess_id, target_start, target_end, target_opening_balance_minor, closing_balance, nullif(trim(target_note), ''), auth.uid())
    returning id into lock_id;
    insert into public.audit_logs (mess_id, actor_user_id, action, target_user_id, details)
    values (actor_mess_id, auth.uid(), 'CASH_PERIOD_LOCKED', auth.uid(), jsonb_build_object('lock_id', lock_id, 'starts_on', target_start, 'ends_on', target_end, 'closing_balance_minor', closing_balance));
    return lock_id;
end;
$$;

revoke all on function public.set_meal_schedule(uuid, date, date, text, text) from public, anon;
revoke all on function public.record_rice_purchase(integer, text, bigint, text, text, date, uuid) from public, anon;
revoke all on function public.reverse_rice_movement(uuid, text) from public, anon;
revoke all on function public.update_expense_details(uuid, text, text) from public, anon;
revoke all on function public.review_expense(uuid, text, text) from public, anon;
revoke all on function public.upsert_cash_account(text, text, bigint) from public, anon;
revoke all on function public.assign_cash_entry_account(uuid, uuid) from public, anon;
revoke all on function public.close_cash_period(date, date, bigint, text) from public, anon;
grant execute on function public.set_meal_schedule(uuid, date, date, text, text) to authenticated;
grant execute on function public.record_rice_purchase(integer, text, bigint, text, text, date, uuid) to authenticated;
grant execute on function public.reverse_rice_movement(uuid, text) to authenticated;
grant execute on function public.update_expense_details(uuid, text, text) to authenticated;
grant execute on function public.review_expense(uuid, text, text) to authenticated;
grant execute on function public.upsert_cash_account(text, text, bigint) to authenticated;
grant execute on function public.assign_cash_entry_account(uuid, uuid) to authenticated;
grant execute on function public.close_cash_period(date, date, bigint, text) to authenticated;

revoke all on function public.meal_schedule_guard() from public, anon, authenticated;
revoke all on function public.cash_period_lock_guard() from public, anon, authenticated;
revoke all on function public.rice_stock_alert_after_movement() from public, anon, authenticated;
