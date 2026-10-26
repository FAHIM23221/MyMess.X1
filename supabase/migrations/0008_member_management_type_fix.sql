begin;

-- auth.users.email is varchar in Supabase; the management API exposes text.
create or replace function public.list_member_management_profiles()
returns table (
  user_id uuid,
  full_name text,
  email text,
  phone_number text,
  member_id text,
  role public.app_role,
  effective_role public.app_role,
  account_status public.account_status,
  is_current_manager boolean,
  join_date date,
  departed_at timestamptz,
  departure_reason text,
  created_at timestamptz,
  updated_at timestamptz
)
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $$
declare
  actor_mess_id uuid;
  actor_role public.app_role;
begin
  select p.mess_id,
    case when m.user_id is not null then 'MAIN_ADMIN'::public.app_role else p.role end
  into actor_mess_id, actor_role
  from public.profiles p
  left join public.main_admin_assignments m
    on m.mess_id = p.mess_id and m.user_id = p.user_id
  where p.user_id = auth.uid()
    and p.account_status = 'ACTIVE'
    and p.departed_at is null;

  if actor_mess_id is null or actor_role not in ('MAIN_ADMIN', 'ADMIN') then
    raise exception 'Active Admin or Main Admin access required';
  end if;

  return query
  select p.user_id, p.full_name, coalesce(u.email::text, ''), coalesce(p.phone_number::text, ''),
    p.member_id, p.role,
    case when m.user_id is not null then 'MAIN_ADMIN'::public.app_role else p.role end,
    p.account_status, p.is_current_manager, p.join_date,
    p.departed_at, p.departure_reason, p.created_at, p.updated_at
  from public.profiles p
  join auth.users u on u.id = p.user_id
  left join public.main_admin_assignments m
    on m.mess_id = p.mess_id and m.user_id = p.user_id
  where p.mess_id = actor_mess_id
  order by p.full_name, p.member_id;
end;
$$;

commit;
