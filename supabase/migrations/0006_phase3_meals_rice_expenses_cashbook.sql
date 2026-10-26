-- Phase 3: meals, rice stock, expenses, and an append-only cash book.
-- Authenticated clients receive SELECT only. Every mutation is an audited RPC.
-- Quantities use integer half-meal units and grams; money uses BDT minor units.

create table public.meal_entries (
    id uuid primary key default gen_random_uuid(),
    mess_id uuid not null references public.messes(id) on delete restrict,
    member_id uuid not null references public.profiles(user_id) on delete restrict,
    meal_date date not null,
    meal_slot text not null check (meal_slot in ('BREAKFAST', 'LUNCH', 'DINNER')),
    quantity_units integer not null check (quantity_units between 1 and 20),
    note text check (note is null or char_length(note) <= 240),
    client_request_id uuid not null,
    created_by uuid not null references public.profiles(user_id) on delete restrict,
    created_at timestamptz not null default now(),
    unique (mess_id, client_request_id)
);

create table public.meal_reversals (
    id uuid primary key default gen_random_uuid(),
    mess_id uuid not null references public.messes(id) on delete restrict,
    meal_entry_id uuid not null unique references public.meal_entries(id) on delete restrict,
    reason text not null check (char_length(trim(reason)) between 8 and 500),
    reversed_by uuid not null references public.profiles(user_id) on delete restrict,
    created_at timestamptz not null default now()
);

create table public.rice_movements (
    id uuid primary key default gen_random_uuid(),
    mess_id uuid not null references public.messes(id) on delete restrict,
    kind text not null check (kind in ('STOCK_IN', 'STOCK_OUT')),
    quantity_grams integer not null check (quantity_grams between 1 and 1000000000),
    note text not null check (char_length(trim(note)) between 2 and 240),
    client_request_id uuid not null,
    created_by uuid not null references public.profiles(user_id) on delete restrict,
    created_at timestamptz not null default now(),
    unique (mess_id, client_request_id)
);

create table public.expenses (
    id uuid primary key default gen_random_uuid(),
    mess_id uuid not null references public.messes(id) on delete restrict,
    category text not null check (category in ('RICE', 'GROCERY', 'UTILITY', 'MAINTENANCE', 'OTHER')),
    amount_minor bigint not null check (amount_minor > 0 and amount_minor <= 1000000000000),
    spent_on date not null,
    description text not null check (char_length(trim(description)) between 2 and 240),
    transaction_reference text check (
        transaction_reference is null or char_length(trim(transaction_reference)) between 2 and 120
    ),
    client_request_id uuid not null,
    created_by uuid not null references public.profiles(user_id) on delete restrict,
    created_at timestamptz not null default now(),
    unique (mess_id, client_request_id)
);

create table public.expense_reversals (
    id uuid primary key default gen_random_uuid(),
    mess_id uuid not null references public.messes(id) on delete restrict,
    expense_id uuid not null unique references public.expenses(id) on delete restrict,
    reason text not null check (char_length(trim(reason)) between 8 and 500),
    reversed_by uuid not null references public.profiles(user_id) on delete restrict,
    created_at timestamptz not null default now()
);

create table public.cash_book_entries (
    id uuid primary key default gen_random_uuid(),
    mess_id uuid not null references public.messes(id) on delete restrict,
    source_type text not null check (
        source_type in ('PAYMENT', 'PAYMENT_REVERSAL', 'EXPENSE', 'EXPENSE_REVERSAL')
    ),
    source_id uuid not null,
    occurred_at timestamptz not null,
    direction text not null check (direction in ('INFLOW', 'OUTFLOW')),
    amount_minor bigint not null check (amount_minor > 0 and amount_minor <= 1000000000000),
    description text not null check (char_length(trim(description)) between 2 and 500),
    member_id uuid references public.profiles(user_id) on delete restrict,
    created_at timestamptz not null default now(),
    unique (source_type, source_id)
);

create index meal_entries_member_date_idx on public.meal_entries(member_id, meal_date desc);
create index meal_entries_mess_date_idx on public.meal_entries(mess_id, meal_date desc);
create index rice_movements_mess_created_idx on public.rice_movements(mess_id, created_at desc);
create index expenses_mess_date_idx on public.expenses(mess_id, spent_on desc);
create index cash_book_mess_time_idx on public.cash_book_entries(mess_id, occurred_at desc);

alter table public.meal_entries enable row level security;
alter table public.meal_reversals enable row level security;
alter table public.rice_movements enable row level security;
alter table public.expenses enable row level security;
alter table public.expense_reversals enable row level security;
alter table public.cash_book_entries enable row level security;

create policy meal_entries_select on public.meal_entries for select to authenticated
    using (
        public.is_active_user()
        and mess_id = public.current_mess_id()
        and (member_id = auth.uid() or public.is_admin_for_mess(mess_id))
    );

create policy meal_reversals_select on public.meal_reversals for select to authenticated
    using (
        public.is_active_user()
        and mess_id = public.current_mess_id()
        and exists (
            select 1 from public.meal_entries m
            where m.id = meal_entry_id
              and m.mess_id = meal_reversals.mess_id
              and (m.member_id = auth.uid() or public.is_admin_for_mess(m.mess_id))
        )
    );

create policy rice_movements_select on public.rice_movements for select to authenticated
    using (public.is_active_user() and mess_id = public.current_mess_id());

create policy expenses_select on public.expenses for select to authenticated
    using (public.is_active_user() and mess_id = public.current_mess_id());

create policy expense_reversals_select on public.expense_reversals for select to authenticated
    using (public.is_active_user() and mess_id = public.current_mess_id());

create policy cash_book_entries_select on public.cash_book_entries for select to authenticated
    using (public.is_active_user() and mess_id = public.current_mess_id());

revoke all on public.meal_entries from public, anon, authenticated;
revoke all on public.meal_reversals from public, anon, authenticated;
revoke all on public.rice_movements from public, anon, authenticated;
revoke all on public.expenses from public, anon, authenticated;
revoke all on public.expense_reversals from public, anon, authenticated;
revoke all on public.cash_book_entries from public, anon, authenticated;

grant select on public.meal_entries to authenticated;
grant select on public.meal_reversals to authenticated;
grant select on public.rice_movements to authenticated;
grant select on public.expenses to authenticated;
grant select on public.expense_reversals to authenticated;
grant select on public.cash_book_entries to authenticated;

-- Cash-book rows are system-generated from immutable source events. Source tables
-- remain client SELECT-only, so these triggers run only inside reviewed RPCs.
create or replace function public.payment_cash_book_after_insert()
returns trigger language plpgsql security definer set search_path = public, pg_temp as $$
begin
    insert into public.cash_book_entries (
        mess_id, source_type, source_id, occurred_at, direction,
        amount_minor, description, member_id
    ) values (
        new.mess_id, 'PAYMENT', new.id, new.paid_at, 'INFLOW',
        new.amount_minor, 'Member payment received', new.member_id
    );
    return new;
end;
$$;

create or replace function public.payment_reversal_cash_book_after_insert()
returns trigger language plpgsql security definer set search_path = public, pg_temp as $$
declare
    source_payment public.payments%rowtype;
begin
    select * into source_payment from public.payments where id = new.payment_id;
    insert into public.cash_book_entries (
        mess_id, source_type, source_id, occurred_at, direction,
        amount_minor, description, member_id
    ) values (
        new.mess_id, 'PAYMENT_REVERSAL', new.id, new.created_at, 'OUTFLOW',
        source_payment.amount_minor, 'Payment reversal: ' || trim(new.reason), new.member_id
    );
    return new;
end;
$$;

create or replace function public.expense_cash_book_after_insert()
returns trigger language plpgsql security definer set search_path = public, pg_temp as $$
begin
    insert into public.cash_book_entries (
        mess_id, source_type, source_id, occurred_at, direction,
        amount_minor, description, member_id
    ) values (
        new.mess_id, 'EXPENSE', new.id, new.created_at, 'OUTFLOW',
        new.amount_minor, 'Expense: ' || trim(new.description), null
    );
    return new;
end;
$$;

create or replace function public.expense_reversal_cash_book_after_insert()
returns trigger language plpgsql security definer set search_path = public, pg_temp as $$
declare
    source_expense public.expenses%rowtype;
begin
    select * into source_expense from public.expenses where id = new.expense_id;
    insert into public.cash_book_entries (
        mess_id, source_type, source_id, occurred_at, direction,
        amount_minor, description, member_id
    ) values (
        new.mess_id, 'EXPENSE_REVERSAL', new.id, new.created_at, 'INFLOW',
        source_expense.amount_minor, 'Expense reversal: ' || trim(new.reason), null
    );
    return new;
end;
$$;

create trigger payments_cash_book
    after insert on public.payments
    for each row execute function public.payment_cash_book_after_insert();
create trigger payment_reversals_cash_book
    after insert on public.payment_reversals
    for each row execute function public.payment_reversal_cash_book_after_insert();
create trigger expenses_cash_book
    after insert on public.expenses
    for each row execute function public.expense_cash_book_after_insert();
create trigger expense_reversals_cash_book
    after insert on public.expense_reversals
    for each row execute function public.expense_reversal_cash_book_after_insert();

-- Backfill existing events once. Conflict handling makes the backfill non-duplicating.
insert into public.cash_book_entries (
    mess_id, source_type, source_id, occurred_at, direction,
    amount_minor, description, member_id
)
select p.mess_id, 'PAYMENT', p.id, p.paid_at, 'INFLOW',
       p.amount_minor, 'Member payment received', p.member_id
from public.payments p
on conflict (source_type, source_id) do nothing;

insert into public.cash_book_entries (
    mess_id, source_type, source_id, occurred_at, direction,
    amount_minor, description, member_id
)
select pr.mess_id, 'PAYMENT_REVERSAL', pr.id, pr.created_at, 'OUTFLOW',
       p.amount_minor, 'Payment reversal: ' || trim(pr.reason), pr.member_id
from public.payment_reversals pr
join public.payments p on p.id = pr.payment_id
on conflict (source_type, source_id) do nothing;

revoke all on function public.payment_cash_book_after_insert() from public, anon, authenticated;
revoke all on function public.payment_reversal_cash_book_after_insert() from public, anon, authenticated;
revoke all on function public.expense_cash_book_after_insert() from public, anon, authenticated;
revoke all on function public.expense_reversal_cash_book_after_insert() from public, anon, authenticated;

create or replace function public.record_meal_entry(
    target_user_id uuid,
    target_date date,
    target_slot text,
    target_quantity_units integer,
    target_note text,
    client_request_id uuid
) returns uuid language plpgsql security definer set search_path = public, auth as $$
declare
    actor_mess_id uuid := public.current_mess_id();
    new_entry_id uuid;
begin
    if auth.uid() is null or actor_mess_id is null or not public.is_admin_for_mess(actor_mess_id) then
        raise exception 'Active Admin or Main Admin access required' using errcode = '42501';
    end if;
    if client_request_id is null then
        raise exception 'A unique client request ID is required';
    end if;
    if target_date is null or target_date > (now() at time zone 'Asia/Dhaka')::date then
        raise exception 'A meal date that is not in the future is required';
    end if;
    if target_slot not in ('BREAKFAST', 'LUNCH', 'DINNER') then
        raise exception 'Unsupported meal slot';
    end if;
    if target_quantity_units is null or target_quantity_units not between 1 and 20 then
        raise exception 'Meal quantity must be between 0.5 and 10 meals';
    end if;
    if target_note is not null and char_length(target_note) > 240 then
        raise exception 'Meal note is too long';
    end if;
    if not exists (
        select 1 from public.profiles
        where user_id = target_user_id and mess_id = actor_mess_id and account_status = 'ACTIVE'
    ) then
        raise exception 'Active target member is not in your mess';
    end if;

    insert into public.meal_entries (
        mess_id, member_id, meal_date, meal_slot, quantity_units,
        note, client_request_id, created_by
    ) values (
        actor_mess_id, target_user_id, target_date, target_slot, target_quantity_units,
        nullif(trim(target_note), ''), client_request_id, auth.uid()
    ) returning id into new_entry_id;

    insert into public.audit_logs (mess_id, actor_user_id, action, target_user_id, details)
    values (
        actor_mess_id, auth.uid(), 'MEAL_ENTRY_RECORDED', target_user_id,
        jsonb_build_object(
            'meal_entry_id', new_entry_id, 'meal_date', target_date,
            'meal_slot', target_slot, 'quantity_units', target_quantity_units
        )
    );
    return new_entry_id;
end;
$$;

create or replace function public.reverse_meal_entry(target_entry_id uuid, target_reason text)
returns void language plpgsql security definer set search_path = public, auth as $$
declare
    actor_mess_id uuid := public.current_mess_id();
    source_entry public.meal_entries%rowtype;
begin
    if auth.uid() is null or actor_mess_id is null or not public.is_admin_for_mess(actor_mess_id) then
        raise exception 'Active Admin or Main Admin access required' using errcode = '42501';
    end if;
    if target_reason is null or char_length(trim(target_reason)) not between 8 and 500 then
        raise exception 'A reversal reason between 8 and 500 characters is required';
    end if;
    select * into source_entry from public.meal_entries
    where id = target_entry_id and mess_id = actor_mess_id for update;
    if not found then
        raise exception 'Meal entry not found in your mess';
    end if;
    if exists (select 1 from public.meal_reversals where meal_entry_id = target_entry_id) then
        raise exception 'Meal entry has already been reversed';
    end if;

    insert into public.meal_reversals (mess_id, meal_entry_id, reason, reversed_by)
    values (actor_mess_id, target_entry_id, trim(target_reason), auth.uid());
    insert into public.audit_logs (mess_id, actor_user_id, action, target_user_id, details)
    values (
        actor_mess_id, auth.uid(), 'MEAL_ENTRY_REVERSED', source_entry.member_id,
        jsonb_build_object('meal_entry_id', target_entry_id, 'reason', trim(target_reason))
    );
end;
$$;

create or replace function public.record_rice_movement(
    target_kind text,
    target_quantity_grams integer,
    target_note text,
    client_request_id uuid
) returns uuid language plpgsql security definer set search_path = public, auth as $$
declare
    actor_mess_id uuid := public.current_mess_id();
    current_stock bigint;
    new_movement_id uuid;
begin
    if auth.uid() is null or actor_mess_id is null or not public.is_admin_for_mess(actor_mess_id) then
        raise exception 'Active Admin or Main Admin access required' using errcode = '42501';
    end if;
    if client_request_id is null then
        raise exception 'A unique client request ID is required';
    end if;
    if target_kind not in ('STOCK_IN', 'STOCK_OUT') then
        raise exception 'Unsupported rice movement kind';
    end if;
    if target_quantity_grams is null or target_quantity_grams not between 1 and 1000000000 then
        raise exception 'A positive, bounded rice quantity is required';
    end if;
    if target_note is null or char_length(trim(target_note)) not between 2 and 240 then
        raise exception 'A rice movement note between 2 and 240 characters is required';
    end if;

    perform pg_advisory_xact_lock(hashtext(actor_mess_id::text));
    select coalesce(sum(case when kind = 'STOCK_IN' then quantity_grams else -quantity_grams end), 0)
    into current_stock from public.rice_movements where mess_id = actor_mess_id;
    if target_kind = 'STOCK_OUT' and target_quantity_grams > current_stock then
        raise exception 'Insufficient rice stock';
    end if;

    insert into public.rice_movements (
        mess_id, kind, quantity_grams, note, client_request_id, created_by
    ) values (
        actor_mess_id, target_kind, target_quantity_grams, trim(target_note), client_request_id, auth.uid()
    ) returning id into new_movement_id;

    insert into public.audit_logs (mess_id, actor_user_id, action, target_user_id, details)
    values (
        actor_mess_id, auth.uid(), 'RICE_MOVEMENT_RECORDED', auth.uid(),
        jsonb_build_object(
            'rice_movement_id', new_movement_id, 'kind', target_kind,
            'quantity_grams', target_quantity_grams, 'stock_after_grams',
            current_stock + case when target_kind = 'STOCK_IN' then target_quantity_grams else -target_quantity_grams end
        )
    );
    return new_movement_id;
end;
$$;

create or replace function public.record_expense(
    target_category text,
    target_amount_minor bigint,
    target_spent_on date,
    target_description text,
    target_reference text,
    client_request_id uuid
) returns uuid language plpgsql security definer set search_path = public, auth as $$
declare
    actor_mess_id uuid := public.current_mess_id();
    new_expense_id uuid;
begin
    if auth.uid() is null or actor_mess_id is null or not public.is_admin_for_mess(actor_mess_id) then
        raise exception 'Active Admin or Main Admin access required' using errcode = '42501';
    end if;
    if client_request_id is null then
        raise exception 'A unique client request ID is required';
    end if;
    if target_category not in ('RICE', 'GROCERY', 'UTILITY', 'MAINTENANCE', 'OTHER') then
        raise exception 'Unsupported expense category';
    end if;
    if target_amount_minor is null or target_amount_minor <= 0 or target_amount_minor > 1000000000000 then
        raise exception 'A positive, bounded expense amount is required';
    end if;
    if target_spent_on is null or target_spent_on > (now() at time zone 'Asia/Dhaka')::date then
        raise exception 'An expense date that is not in the future is required';
    end if;
    if target_description is null or char_length(trim(target_description)) not between 2 and 240 then
        raise exception 'An expense description between 2 and 240 characters is required';
    end if;
    if target_reference is not null and char_length(trim(target_reference)) not between 2 and 120 then
        raise exception 'Transaction reference must be between 2 and 120 characters';
    end if;

    insert into public.expenses (
        mess_id, category, amount_minor, spent_on, description,
        transaction_reference, client_request_id, created_by
    ) values (
        actor_mess_id, target_category, target_amount_minor, target_spent_on, trim(target_description),
        nullif(trim(target_reference), ''), client_request_id, auth.uid()
    ) returning id into new_expense_id;

    insert into public.audit_logs (mess_id, actor_user_id, action, target_user_id, details)
    values (
        actor_mess_id, auth.uid(), 'EXPENSE_RECORDED', auth.uid(),
        jsonb_build_object(
            'expense_id', new_expense_id, 'category', target_category,
            'amount_minor', target_amount_minor, 'spent_on', target_spent_on
        )
    );
    return new_expense_id;
end;
$$;

create or replace function public.reverse_expense(target_expense_id uuid, target_reason text)
returns void language plpgsql security definer set search_path = public, auth as $$
declare
    actor_mess_id uuid := public.current_mess_id();
    source_expense public.expenses%rowtype;
begin
    if auth.uid() is null or actor_mess_id is null or not public.is_admin_for_mess(actor_mess_id) then
        raise exception 'Active Admin or Main Admin access required' using errcode = '42501';
    end if;
    if target_reason is null or char_length(trim(target_reason)) not between 8 and 500 then
        raise exception 'A reversal reason between 8 and 500 characters is required';
    end if;
    select * into source_expense from public.expenses
    where id = target_expense_id and mess_id = actor_mess_id for update;
    if not found then
        raise exception 'Expense not found in your mess';
    end if;
    if exists (select 1 from public.expense_reversals where expense_id = target_expense_id) then
        raise exception 'Expense has already been reversed';
    end if;

    insert into public.expense_reversals (mess_id, expense_id, reason, reversed_by)
    values (actor_mess_id, target_expense_id, trim(target_reason), auth.uid());
    insert into public.audit_logs (mess_id, actor_user_id, action, target_user_id, details)
    values (
        actor_mess_id, auth.uid(), 'EXPENSE_REVERSED', auth.uid(),
        jsonb_build_object(
            'expense_id', target_expense_id, 'previous_amount_minor', source_expense.amount_minor,
            'reason', trim(target_reason)
        )
    );
end;
$$;

revoke all on function public.record_meal_entry(uuid, date, text, integer, text, uuid) from public, anon;
revoke all on function public.reverse_meal_entry(uuid, text) from public, anon;
revoke all on function public.record_rice_movement(text, integer, text, uuid) from public, anon;
revoke all on function public.record_expense(text, bigint, date, text, text, uuid) from public, anon;
revoke all on function public.reverse_expense(uuid, text) from public, anon;

grant execute on function public.record_meal_entry(uuid, date, text, integer, text, uuid) to authenticated;
grant execute on function public.reverse_meal_entry(uuid, text) to authenticated;
grant execute on function public.record_rice_movement(text, integer, text, uuid) to authenticated;
grant execute on function public.record_expense(text, bigint, date, text, text, uuid) to authenticated;
grant execute on function public.reverse_expense(uuid, text) to authenticated;

comment on table public.meal_entries is 'Integer half-meal units; duplicate client request IDs are rejected per mess.';
comment on table public.rice_movements is 'Append-only rice stock in grams; stock-outs are checked under a per-mess transaction lock.';
comment on table public.expenses is 'Audited expense ledger in BDT minor units; direct client writes are not granted.';
comment on table public.cash_book_entries is 'System-generated append-only cash events from payments, reversals, and expenses.';
