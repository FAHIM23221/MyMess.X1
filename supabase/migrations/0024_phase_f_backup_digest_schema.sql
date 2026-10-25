-- pgcrypto is installed in the extensions schema on Supabase.
-- Keep the already-created export function body unchanged and resolve digest safely.
alter function public.export_erp_backup()
    set search_path = public, auth, pg_temp, extensions;
