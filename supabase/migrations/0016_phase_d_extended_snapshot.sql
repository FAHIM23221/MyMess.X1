-- Add Phase D projections without replacing the verified Phase C snapshot body.
alter function public.get_extended_erp_snapshot() rename to get_extended_erp_snapshot_phase_c;
revoke all on function public.get_extended_erp_snapshot_phase_c() from public, anon, authenticated;

create function public.get_extended_erp_snapshot()
returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare
    actor_mess_id uuid := public.current_mess_id();
    actor_is_admin boolean;
    base jsonb;
begin
    base := public.get_extended_erp_snapshot_phase_c();
    actor_is_admin := public.is_admin_for_mess(actor_mess_id);
    return base || jsonb_build_object(
        'duties', coalesce((select jsonb_agg(jsonb_build_object(
            'id', d.id, 'title', d.title, 'description', d.description,
            'assigned_to', d.assigned_to, 'assignee_name', p.full_name,
            'due_on', d.due_on, 'status', d.status, 'completion_note', d.completion_note,
            'created_at', d.created_at, 'updated_at', d.updated_at
        ) order by d.due_on, d.created_at desc) from public.duties d
        join public.profiles p on p.user_id = d.assigned_to where d.mess_id = actor_mess_id), '[]'::jsonb),
        'duty_requests', coalesce((select jsonb_agg(jsonb_build_object(
            'id', dr.id, 'duty_id', dr.duty_id, 'duty_title', d.title,
            'requester_id', dr.requester_id, 'requester_name', rp.full_name,
            'kind', dr.kind, 'target_member_id', dr.target_member_id,
            'target_name', tp.full_name, 'reason', dr.reason, 'status', dr.status,
            'decision_reason', dr.decision_reason, 'created_at', dr.created_at
        ) order by dr.created_at desc) from public.duty_requests dr
        join public.duties d on d.id = dr.duty_id
        join public.profiles rp on rp.user_id = dr.requester_id
        left join public.profiles tp on tp.user_id = dr.target_member_id
        where dr.mess_id = actor_mess_id and (actor_is_admin or dr.requester_id = auth.uid())), '[]'::jsonb),
        'manager_rotations', coalesce((select jsonb_agg(jsonb_build_object(
            'id', r.id, 'manager_id', r.manager_id, 'manager_name', p.full_name,
            'starts_on', r.starts_on, 'ends_on', r.ends_on, 'status', r.status,
            'handover_note', r.handover_note, 'activated_at', r.activated_at, 'created_at', r.created_at
        ) order by r.starts_on desc) from public.manager_rotations r
        join public.profiles p on p.user_id = r.manager_id where r.mess_id = actor_mess_id), '[]'::jsonb),
        'notices', coalesce((select jsonb_agg(jsonb_build_object(
            'id', n.id, 'title', n.title, 'body', n.body, 'audience', n.audience,
            'status', n.status, 'publish_at', n.publish_at, 'expires_at', n.expires_at,
            'created_at', n.created_at,
            'read_count', (select count(*) from public.notice_reads nr where nr.notice_id = n.id),
            'current_user_read', exists(select 1 from public.notice_reads nr where nr.notice_id = n.id and nr.user_id = auth.uid())
        ) order by n.created_at desc) from public.notices n
        where n.mess_id = actor_mess_id and (
            actor_is_admin or (n.status = 'PUBLISHED' and n.publish_at <= now()
                and (n.expires_at is null or n.expires_at > now()) and n.audience in ('ALL', 'MEMBERS'))
        )), '[]'::jsonb)
    );
end;
$$;
revoke all on function public.get_extended_erp_snapshot() from public, anon;
grant execute on function public.get_extended_erp_snapshot() to authenticated;
