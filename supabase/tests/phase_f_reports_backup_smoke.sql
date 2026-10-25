-- Rollback-only Phase F report/export smoke with injected claims.
-- This verifies logical export integrity, not physical backup, restore, or device sharing.
begin;
do $smoke$
declare
    admin_id uuid;
    report jsonb;
    backup jsonb;
    rejected boolean;
begin
    select a.user_id into admin_id from public.main_admin_assignments a limit 1;
    if admin_id is null then
        raise exception 'Smoke test requires one Main Admin assignment';
    end if;
    perform set_config(
        'request.jwt.claims',
        jsonb_build_object('sub', admin_id, 'role', 'authenticated')::text,
        true
    );

    report := public.get_monthly_report(current_date - 30, current_date);
    if report->>'scope' <> 'MESS'
       or report->>'timezone' <> 'Asia/Dhaka'
       or report->'summary' is null or report->'members' is null or report->'categories' is null then
        raise exception 'Admin monthly report projection is incomplete';
    end if;

    rejected := false;
    begin
        perform public.get_monthly_report(current_date, current_date - 1);
    exception when others then
        if position('report range' in sqlerrm) > 0 then rejected := true;
        else raise;
        end if;
    end;
    if not rejected then
        raise exception 'An invalid report range was accepted';
    end if;

    backup := public.export_erp_backup();
    if backup->>'integrity_algorithm' <> 'SHA-256'
       or backup->>'sha256' !~ '^[0-9a-f]{64}$'
       or backup->>'sha256' <> encode(extensions.digest(backup->>'payload', 'sha256'), 'hex') then
        raise exception 'Backup SHA-256 integrity digest does not match the payload';
    end if;
    if (backup->>'restore_supported')::boolean
       or ((backup->>'payload')::jsonb->>'restore_supported')::boolean
       or ((backup->>'payload')::jsonb->>'physical_backup_included')::boolean then
        raise exception 'Logical export must not claim physical backup or restore support';
    end if;
    if not ((backup->>'payload')::jsonb ? 'audit_logs')
       or not (((backup->>'payload')::jsonb->'snapshot') ? 'notification_batches') then
        raise exception 'Main Admin export is missing audit or current snapshot data';
    end if;
    if (backup->>'payload') ilike '%service_role%' or (backup->>'payload') ilike '%sb_secret_%' then
        raise exception 'Logical export contains a privileged credential marker';
    end if;

    perform set_config(
        'request.jwt.claims',
        jsonb_build_object('sub', gen_random_uuid(), 'role', 'authenticated')::text,
        true
    );
    rejected := false;
    begin
        perform public.get_monthly_report(current_date - 1, current_date);
    exception when insufficient_privilege then rejected := true;
    end;
    if not rejected then
        raise exception 'A missing-profile actor generated a monthly report';
    end if;
    rejected := false;
    begin
        perform public.export_erp_backup();
    exception when insufficient_privilege then rejected := true;
    end;
    if not rejected then
        raise exception 'A missing-profile actor generated a backup export';
    end if;
end;
$smoke$;
rollback;
