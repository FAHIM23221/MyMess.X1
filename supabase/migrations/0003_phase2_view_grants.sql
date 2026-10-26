-- Tighten Phase 2 view privileges after live grant inspection.
-- Supabase default privileges may grant ALL on new views to authenticated.
revoke all on public.my_financial_summary from public, anon, authenticated;
revoke all on public.my_payment_receipts from public, anon, authenticated;

grant select on public.my_financial_summary to authenticated;
grant select on public.my_payment_receipts to authenticated;
