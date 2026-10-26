-- Defense in depth: role helpers do not themselves imply an active profile.
create or replace function public.moderate_message(target_message_id uuid, target_status text, target_reason text)
returns void language plpgsql security definer set search_path = public, auth as $$
declare actor_mess_id uuid := public.current_mess_id();
begin
    if auth.uid() is null or actor_mess_id is null or not public.is_active_user() or public.current_app_role() <> 'MAIN_ADMIN' then raise exception 'Active Main Admin moderation access required' using errcode = '42501'; end if;
    if target_status not in ('VISIBLE', 'HIDDEN') then raise exception 'Unsupported message moderation status'; end if;
    if target_status = 'HIDDEN' and (target_reason is null or char_length(trim(target_reason)) not between 8 and 500) then raise exception 'A message moderation reason is required'; end if;
    if not exists (select 1 from public.messages m where m.id = target_message_id and m.mess_id = actor_mess_id) then raise exception 'Message not found in your mess'; end if;
    if target_status = 'VISIBLE' then
        update public.messages set moderation_status = 'VISIBLE', moderation_reason = null, moderated_by = null, moderated_at = null where id = target_message_id;
    else
        update public.messages set moderation_status = 'HIDDEN', moderation_reason = trim(target_reason), moderated_by = auth.uid(), moderated_at = now() where id = target_message_id;
    end if;
    insert into public.audit_logs (mess_id, actor_user_id, action, target_user_id, details)
    values (actor_mess_id, auth.uid(), 'MESSAGE_MODERATED', auth.uid(), jsonb_build_object('message_id', target_message_id, 'status', target_status));
end;
$$;

create or replace function public.review_suggestion(target_suggestion_id uuid, target_decision text, target_reason text)
returns void language plpgsql security definer set search_path = public, auth as $$
declare actor_mess_id uuid := public.current_mess_id(); author_id uuid; suggestion_record public.suggestions%rowtype;
begin
    if auth.uid() is null or actor_mess_id is null or not public.is_active_user() or public.current_app_role() <> 'MAIN_ADMIN' then raise exception 'Active Main Admin publication approval required' using errcode = '42501'; end if;
    if target_decision not in ('PUBLISHED', 'REJECTED') then raise exception 'Unsupported suggestion decision'; end if;
    if target_decision = 'REJECTED' and (target_reason is null or char_length(trim(target_reason)) not between 8 and 500) then raise exception 'A rejection reason is required'; end if;
    if target_reason is not null and char_length(trim(target_reason)) not between 8 and 500 then raise exception 'Invalid suggestion moderation reason'; end if;
    select * into suggestion_record from public.suggestions s where s.id = target_suggestion_id and s.mess_id = actor_mess_id and s.status = 'PENDING' for update;
    if not found then raise exception 'Pending suggestion not found in your mess'; end if;
    update public.suggestions set status = target_decision, moderation_reason = nullif(trim(target_reason), ''), moderated_by = auth.uid(), moderated_at = now() where id = target_suggestion_id;
    select si.author_id into author_id from public.suggestion_identities si where si.suggestion_id = target_suggestion_id;
    if author_id is not null then
        insert into public.notifications (mess_id, recipient_id, kind, title, body)
        values (actor_mess_id, author_id, 'SYSTEM', 'Suggestion ' || target_decision, coalesce(nullif(trim(target_reason), ''), 'Your suggestion was reviewed.'));
    end if;
    insert into public.audit_logs (mess_id, actor_user_id, action, target_user_id, details)
    values (actor_mess_id, auth.uid(), 'SUGGESTION_REVIEWED', auth.uid(), jsonb_build_object('suggestion_id', target_suggestion_id, 'decision', target_decision));
end;
$$;
revoke all on function public.moderate_message(uuid, text, text) from public, anon;
revoke all on function public.review_suggestion(uuid, text, text) from public, anon;
grant execute on function public.moderate_message(uuid, text, text) to authenticated;
grant execute on function public.review_suggestion(uuid, text, text) to authenticated;
