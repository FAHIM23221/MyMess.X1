-- Complete the report lifecycle without granting table writes to Android.
create or replace function public.review_message_report(target_report_id uuid, target_decision text)
returns void language plpgsql security definer set search_path = public, auth as $$
declare actor_mess_id uuid := public.current_mess_id(); report_record public.message_reports%rowtype;
begin
    if auth.uid() is null or actor_mess_id is null or not public.is_admin_for_mess(actor_mess_id) then
        raise exception 'Active Admin or Main Admin access required' using errcode = '42501';
    end if;
    if target_decision not in ('REVIEWED', 'DISMISSED') then raise exception 'Unsupported report decision'; end if;
    select * into report_record from public.message_reports r
    where r.id = target_report_id and r.mess_id = actor_mess_id and r.status = 'OPEN' for update;
    if not found then raise exception 'Open message report not found in your mess'; end if;
    update public.message_reports set status = target_decision, reviewed_by = auth.uid(), reviewed_at = now() where id = target_report_id;
    insert into public.audit_logs (mess_id, actor_user_id, action, target_user_id, details)
    values (actor_mess_id, auth.uid(), 'MESSAGE_REPORT_REVIEWED', report_record.reporter_id,
        jsonb_build_object('report_id', target_report_id, 'message_id', report_record.message_id, 'decision', target_decision));
end;
$$;
revoke all on function public.review_message_report(uuid, text) from public, anon;
grant execute on function public.review_message_report(uuid, text) to authenticated;
