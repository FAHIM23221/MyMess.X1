-- Rollback-only Phase E smoke; verifies privacy-oriented RPC behavior with injected claims.
begin;
do $smoke$
declare
    admin_id uuid;
    smoke_message_id uuid;
    smoke_message_again_id uuid;
    smoke_suggestion_id uuid;
    smoke_complaint_id uuid;
    smoke_notice_id uuid;
    report_id uuid;
    rejected boolean;
begin
    select user_id into admin_id from public.main_admin_assignments limit 1;
    if admin_id is null then raise exception 'One Main Admin is required'; end if;
    perform set_config('request.jwt.claims', jsonb_build_object('sub', admin_id, 'role', 'authenticated')::text, true);

    smoke_message_id := public.send_message('GROUP', null, repeat('m', 1500), gen_random_uuid());
    smoke_message_again_id := public.send_message('GROUP', null, 'Rollback-only group message', (select m.client_request_id from public.messages m where m.id = smoke_message_id));
    if smoke_message_id <> smoke_message_again_id then raise exception 'Message retry was not idempotent'; end if;
    perform public.mark_message_read(smoke_message_id);
    report_id := public.report_message(smoke_message_id, 'Rollback-only message report');
    if report_id is null then raise exception 'Message report was not created'; end if;
    perform public.review_message_report(report_id, 'REVIEWED');
    if not exists (select 1 from public.message_reports r where r.id = report_id and r.status = 'REVIEWED') then raise exception 'Message report review failed'; end if;
    perform public.moderate_message(smoke_message_id, 'HIDDEN', 'Rollback-only moderation reason');
    if not exists (select 1 from public.messages m where m.id = smoke_message_id and m.moderation_status = 'HIDDEN') then raise exception 'Message moderation failed'; end if;
    rejected := false;
    begin perform public.send_message('PRIVATE', admin_id, 'Self-private message must fail', gen_random_uuid());
    exception when others then if position('different private recipient' in sqlerrm) > 0 then rejected := true; else raise; end if; end;
    if not rejected then raise exception 'Self-private message was accepted'; end if;

    smoke_suggestion_id := public.create_suggestion('Rollback-only anonymous suggestion', gen_random_uuid());
    if not exists (select 1 from public.suggestion_identities si where si.suggestion_id = smoke_suggestion_id and si.author_id = admin_id) then raise exception 'Suggestion identity was not stored separately'; end if;
    perform public.review_suggestion(smoke_suggestion_id, 'PUBLISHED', null);
    if not exists (select 1 from public.suggestions s where s.id = smoke_suggestion_id and s.status = 'PUBLISHED') then raise exception 'Suggestion publication failed'; end if;

    smoke_complaint_id := public.create_complaint(repeat('s', 160), repeat('c', 1500));
    perform public.respond_complaint(smoke_complaint_id, 'RESOLVED', repeat('r', 1500));
    if not exists (select 1 from public.complaints c where c.id = smoke_complaint_id and c.status = 'RESOLVED' and c.response is not null) then raise exception 'Complaint response failed'; end if;
    smoke_notice_id := public.save_notice(repeat('n', 160), repeat('b', 4000), 'ALL', 'PUBLISHED', now(), null, gen_random_uuid());
    if not exists (select 1 from public.notifications n where n.notice_id = smoke_notice_id and char_length(n.body) = 4000) then raise exception 'Long notice notification was not retained'; end if;

    set local role authenticated;
    rejected := false;
    begin
        insert into public.suggestions (mess_id, body, client_request_id)
        values (public.current_mess_id(), 'Direct write must fail', gen_random_uuid());
    exception when insufficient_privilege then rejected := true; end;
    if not rejected then raise exception 'Direct suggestion insert was accepted'; end if;
    reset role;

    perform set_config('request.jwt.claims', jsonb_build_object('sub', gen_random_uuid(), 'role', 'authenticated')::text, true);
    rejected := false;
    begin perform public.create_suggestion('Unauthorized suggestion', gen_random_uuid());
    exception when insufficient_privilege then rejected := true; end;
    if not rejected then raise exception 'Missing-profile suggestion call was accepted'; end if;
end;
$smoke$;
rollback;
