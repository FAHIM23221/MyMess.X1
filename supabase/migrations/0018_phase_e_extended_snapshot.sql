-- Phase E projections. Suggestion identity is emitted only for the active Main Admin.
alter function public.get_extended_erp_snapshot() rename to get_extended_erp_snapshot_phase_d;
revoke all on function public.get_extended_erp_snapshot_phase_d() from public, anon, authenticated;

create function public.get_extended_erp_snapshot()
returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare
    actor_mess_id uuid := public.current_mess_id();
    actor_is_admin boolean;
    base jsonb;
begin
    base := public.get_extended_erp_snapshot_phase_d();
    actor_is_admin := public.is_admin_for_mess(actor_mess_id);
    return base || jsonb_build_object(
        'messages', coalesce((select jsonb_agg(jsonb_build_object(
            'id', m.id, 'sender_id', m.sender_id, 'sender_name', sp.full_name,
            'recipient_id', m.recipient_id, 'recipient_name', rp.full_name,
            'kind', m.kind, 'body', m.body, 'status', m.moderation_status,
            'moderation_reason', m.moderation_reason, 'created_at', m.created_at,
            'current_user_read', (m.sender_id = auth.uid() or exists(
                select 1 from public.message_reads mr where mr.message_id = m.id and mr.user_id = auth.uid()
            ))
        ) order by m.created_at desc) from public.messages m
        join public.profiles sp on sp.user_id = m.sender_id
        left join public.profiles rp on rp.user_id = m.recipient_id
        where m.mess_id = actor_mess_id and (
            m.sender_id = auth.uid() or m.recipient_id = auth.uid()
            or (m.kind = 'GROUP' and (m.moderation_status = 'VISIBLE' or actor_is_admin))
            or public.current_app_role() = 'MAIN_ADMIN'
        )), '[]'::jsonb),
        'suggestions', coalesce((select jsonb_agg(jsonb_build_object(
            'id', s.id, 'body', s.body, 'status', s.status,
            'moderation_reason', s.moderation_reason, 'created_at', s.created_at,
            'author_id', case when public.current_app_role() = 'MAIN_ADMIN' then si.author_id else null end,
            'author_name', case when public.current_app_role() = 'MAIN_ADMIN' then ap.full_name else null end
        ) order by s.created_at desc) from public.suggestions s
        left join public.suggestion_identities si on si.suggestion_id = s.id
        left join public.profiles ap on ap.user_id = si.author_id
        where s.mess_id = actor_mess_id and (
            s.status = 'PUBLISHED' or actor_is_admin or si.author_id = auth.uid()
        )), '[]'::jsonb),
        'complaints', coalesce((select jsonb_agg(jsonb_build_object(
            'id', c.id, 'author_id', c.author_id, 'author_name', p.full_name,
            'subject', c.subject, 'body', c.body, 'status', c.status,
            'response', c.response, 'responded_at', c.responded_at, 'created_at', c.created_at
        ) order by c.created_at desc) from public.complaints c
        join public.profiles p on p.user_id = c.author_id
        where c.mess_id = actor_mess_id and (actor_is_admin or c.author_id = auth.uid())), '[]'::jsonb),
        'message_reports', coalesce((select jsonb_agg(jsonb_build_object(
            'id', r.id, 'message_id', r.message_id, 'reporter_id', r.reporter_id,
            'reporter_name', p.full_name, 'reason', r.reason, 'status', r.status,
            'created_at', r.created_at
        ) order by r.created_at desc) from public.message_reports r
        join public.profiles p on p.user_id = r.reporter_id
        where r.mess_id = actor_mess_id and (actor_is_admin or r.reporter_id = auth.uid())), '[]'::jsonb)
    );
end;
$$;
revoke all on function public.get_extended_erp_snapshot() from public, anon;
grant execute on function public.get_extended_erp_snapshot() to authenticated;
