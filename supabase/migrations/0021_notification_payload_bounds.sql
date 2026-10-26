-- Keep valid source payloads from failing on their durable notification copies.
-- Bounds remain finite; source tables retain their own validation and RLS.
alter table public.notifications drop constraint notifications_title_check;
alter table public.notifications add constraint notifications_title_check
    check (char_length(trim(title)) between 2 and 200);
alter table public.notifications drop constraint notifications_body_check;
alter table public.notifications add constraint notifications_body_check
    check (char_length(trim(body)) between 2 and 4000);
comment on table public.notifications is 'Durable in-app notifications; bounded payloads may mirror source notice/message/complaint text. No external push delivery is implied.';
