-- =====================================================================
-- Kvit Web, part 4 (run AFTER schema_web_3.sql, re-runnable):
-- when Telegram/e-mail reminders arrive.
-- =====================================================================

-- reminder_time  local time of day, counted in reminder_tz (the user's browser time zone)
-- reminder_mode  'daily'    every day
--                'weekdays' only on reminder_weekdays (ISO numbers, 1 = Monday ... 7 = Sunday)
--                'dates'    only on the calendar dates in reminder_dates
-- last_reminded_on is written by the due-reminders function so a user gets at most one message a day.
alter table public.profiles add column if not exists reminder_time time not null default '08:00';
alter table public.profiles add column if not exists reminder_tz text not null default 'Asia/Almaty';
alter table public.profiles add column if not exists reminder_mode text not null default 'daily';
alter table public.profiles add column if not exists reminder_weekdays smallint[] not null default '{}';
alter table public.profiles add column if not exists reminder_dates date[] not null default '{}';
alter table public.profiles add column if not exists last_reminded_on date;

alter table public.profiles drop constraint if exists profiles_reminder_mode_check;
alter table public.profiles add constraint profiles_reminder_mode_check
    check (reminder_mode in ('daily', 'weekdays', 'dates'));

alter table public.profiles drop constraint if exists profiles_reminder_weekdays_check;
alter table public.profiles add constraint profiles_reminder_weekdays_check
    check (reminder_weekdays <@ array[1, 2, 3, 4, 5, 6, 7]::smallint[]);

alter table public.profiles drop constraint if exists profiles_reminder_dates_check;
alter table public.profiles add constraint profiles_reminder_dates_check
    check (cardinality(reminder_dates) <= 60);

alter table public.profiles drop constraint if exists profiles_reminder_tz_check;
alter table public.profiles add constraint profiles_reminder_tz_check
    check (char_length(reminder_tz) between 1 and 64);

-- The function is now called every 15 minutes and decides itself whose time has come.
-- Change the schedule of the existing job (created by cron_reminders.local.sql) if it exists.
do $$
begin
    perform cron.alter_job(jobid, schedule := '*/15 * * * *')
    from cron.job where jobname = 'kvit-due-messages';
exception when others then
    raise notice 'kvit-due-messages job not found; run cron_reminders.local.sql first';
end $$;
