-- =====================================================================
-- Kvit Web, part 3 (run AFTER schema_web_2.sql, re-runnable):
-- currencies, receipt photos for payments, Telegram link, AI usage log.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Currency of a debt. Totals on the dashboard are shown per currency;
-- Kvit never converts between currencies.
-- ---------------------------------------------------------------------
alter table public.debts add column if not exists currency text not null default 'KZT'
    check (currency in ('KZT', 'USD', 'EUR', 'RUB'));

-- Friend loans and split bills copy the currency of the source row (always KZT today).

-- ---------------------------------------------------------------------
-- Receipt photo for a payment. Private bucket: only the owner of the debt
-- can read or write files under <user id>/...
-- ---------------------------------------------------------------------
alter table public.payments add column if not exists receipt_path text;

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('receipts', 'receipts', false, 5242880, array['image/png', 'image/jpeg', 'image/webp'])
on conflict (id) do update set public = false, file_size_limit = 5242880,
    allowed_mime_types = array['image/png', 'image/jpeg', 'image/webp'];

drop policy if exists "Users read own receipts" on storage.objects;
create policy "Users read own receipts" on storage.objects
    for select to authenticated
    using (bucket_id = 'receipts' and (storage.foldername(name))[1] = (select auth.uid())::text);

drop policy if exists "Users upload own receipts" on storage.objects;
create policy "Users upload own receipts" on storage.objects
    for insert to authenticated
    with check (bucket_id = 'receipts' and (storage.foldername(name))[1] = (select auth.uid())::text);

drop policy if exists "Users delete own receipts" on storage.objects;
create policy "Users delete own receipts" on storage.objects
    for delete to authenticated
    using (bucket_id = 'receipts' and (storage.foldername(name))[1] = (select auth.uid())::text);

-- ---------------------------------------------------------------------
-- Telegram: the site asks the "telegram" Edge Function for a one-time code,
-- the user sends /start <code> to the bot, the bot stores the chat id.
-- Kept out of profiles because profiles are readable by every signed-in user.
-- Only the Edge Function (service role) writes here; users may read and delete their own row.
-- ---------------------------------------------------------------------
create table if not exists public.telegram_links (
    user_id    uuid primary key references auth.users (id) on delete cascade,
    chat_id    bigint unique,
    link_code  text unique,
    code_until timestamptz,
    linked_at  timestamptz
);
alter table public.telegram_links enable row level security;
revoke all on public.telegram_links from anon, public;
grant select, delete on public.telegram_links to authenticated;

drop policy if exists "Own telegram link" on public.telegram_links;
create policy "Own telegram link" on public.telegram_links
    for select to authenticated using (user_id = (select auth.uid()));
drop policy if exists "Unlink own telegram" on public.telegram_links;
create policy "Unlink own telegram" on public.telegram_links
    for delete to authenticated using (user_id = (select auth.uid()));

-- ---------------------------------------------------------------------
-- AI assistant usage: one row per request, used for a simple daily limit
-- per user so one account cannot burn the whole Gemini quota.
-- ---------------------------------------------------------------------
create table if not exists public.ai_usage (
    id         bigint generated always as identity primary key,
    user_id    uuid not null references auth.users (id) on delete cascade,
    created_at timestamptz not null default now()
);
create index if not exists ai_usage_user_idx on public.ai_usage (user_id, created_at desc);
alter table public.ai_usage enable row level security;
revoke all on public.ai_usage from anon, public, authenticated;

-- Remembers when the admin was last told that the Gemini quota ran out (one row).
create table if not exists public.ai_alerts (
    id         int primary key default 1 check (id = 1),
    last_sent  timestamptz
);
insert into public.ai_alerts (id) values (1) on conflict do nothing;
alter table public.ai_alerts enable row level security;
revoke all on public.ai_alerts from anon, public, authenticated;
