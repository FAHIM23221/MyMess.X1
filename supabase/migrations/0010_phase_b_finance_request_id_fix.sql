-- Correct PL/pgSQL parameter/column ambiguity in the Phase B fine retry lookup.
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

revoke all on function public.create_fine(uuid, uuid, bigint, text, date, uuid) from public, anon;
grant execute on function public.create_fine(uuid, uuid, bigint, text, date, uuid) to authenticated;
