-- Rollback-only Phase D smoke with injected claims; no push provider is contacted.
begin;
do $smoke$
declare
    admin_id uuid;
    smoke_duty_id uuid;
    smoke_duty_again_id uuid;
    smoke_complete_duty_id uuid;
    smoke_request_id uuid;
    smoke_rotation_id uuid;
    smoke_notice_id uuid;
    smoke_scheduled_notice_id uuid;
    notice_count integer;
    published_count integer;
    rejected boolean;
begin
    select user_id into admin_id from public.main_admin_assignments limit 1;
    if admin_id is null then raise exception 'One Main Admin is required'; end if;
    perform set_config('request.jwt.claims', jsonb_build_object('sub', admin_id, 'role', 'authenticated')::text, true);

    smoke_duty_id := public.create_duty(admin_id, 'Rollback duty', 'Rollback-only duty description', date '2004-01-15', gen_random_uuid());
    smoke_duty_again_id := public.create_duty(admin_id, 'Rollback duty', 'Rollback-only duty description', date '2004-01-15', (select d.client_request_id from public.duties d where d.id = smoke_duty_id));
    if smoke_duty_id <> smoke_duty_again_id then raise exception 'Duty creation was not idempotent'; end if;
    smoke_request_id := public.request_duty_change(smoke_duty_id, 'CANNOT_DO', null, 'Rollback-only cannot-do reason');
    perform public.review_duty_request(smoke_request_id, 'APPROVED', 'Rollback-only decision');
    if not exists (select 1 from public.duties d where d.id = smoke_duty_id and d.status = 'MISSED') then raise exception 'Cannot-do approval did not update duty'; end if;
    smoke_complete_duty_id := public.create_duty(admin_id, 'Rollback completion', 'Rollback-only completion description', date '2004-01-16', gen_random_uuid());
    perform public.update_duty_status(smoke_complete_duty_id, 'COMPLETED', 'Completed in rollback smoke');

    smoke_rotation_id := public.schedule_manager_rotation(admin_id, date '2004-01-01', null, 'Rollback-only handover');
    perform public.activate_manager_rotation(smoke_rotation_id);
    if not exists (select 1 from public.manager_rotations r where r.id = smoke_rotation_id and r.status = 'ACTIVE') then raise exception 'Rotation did not activate'; end if;

    smoke_notice_id := public.save_notice('Rollback notice', 'Rollback-only notice body', 'ALL', 'PUBLISHED', timestamptz '2004-01-01 00:00:00+00', null, gen_random_uuid());
    perform public.mark_notice_read(smoke_notice_id);
    perform public.set_notice_status(smoke_notice_id, 'PUBLISHED');
    select count(*) into notice_count from public.notifications n where n.notice_id = smoke_notice_id;
    if notice_count <> 1 then raise exception 'Notice dispatch was not idempotent: %', notice_count; end if;
    smoke_scheduled_notice_id := public.save_notice('Rollback scheduled', 'Rollback-only scheduled body', 'ALL', 'SCHEDULED', now() - interval '1 hour', null, gen_random_uuid());
    published_count := public.publish_due_notices();
    if published_count <> 1 then raise exception 'Unexpected due-notice count: %', published_count; end if;
    perform public.mark_notice_read(smoke_scheduled_notice_id);

    set local role authenticated;
    rejected := false;
    begin
        insert into public.duties (mess_id, title, description, assigned_to, due_on, client_request_id, created_by)
        values (public.current_mess_id(), 'Direct write must fail', 'Direct write must fail', admin_id, date '2004-01-01', gen_random_uuid(), admin_id);
    exception when insufficient_privilege then rejected := true; end;
    if not rejected then raise exception 'Direct duty insert was accepted'; end if;
    reset role;

    perform set_config('request.jwt.claims', jsonb_build_object('sub', gen_random_uuid(), 'role', 'authenticated')::text, true);
    rejected := false;
    begin perform public.create_duty(admin_id, 'Unauthorized', 'Unauthorized duty', date '2004-01-01', gen_random_uuid());
    exception when insufficient_privilege then rejected := true; end;
    if not rejected then raise exception 'Missing-profile duty call was accepted'; end if;
end;
$smoke$;
rollback;
