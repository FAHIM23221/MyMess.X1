-- The two legacy management RPCs use current_date for their join-date window.
-- Pin their business calendar to the mess's supported Asia/Dhaka timezone.
alter function public.request_member_onboarding(text, text, text, text, date, public.app_role, uuid)
    set timezone = 'Asia/Dhaka';
alter function public.update_managed_member_profile(uuid, text, text, text, date)
    set timezone = 'Asia/Dhaka';
comment on function public.request_member_onboarding(text, text, text, text, date, public.app_role, uuid) is
    'Capacity-safe onboarding; join-date window uses the Asia/Dhaka business calendar.';
comment on function public.update_managed_member_profile(uuid, text, text, text, date) is
    'Audited member edit; join-date window uses the Asia/Dhaka business calendar.';
