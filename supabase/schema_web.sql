-- =====================================================================
-- Kvit Web — additions to schema.sql (run AFTER schema.sql, re-runnable).
-- Adds: profiles with avatars, friends, chat, loans between friends with
-- a signed receipt, notifications, and the "direction" of a debt
-- (what I owe vs what I lent). Existing debts/payments data is preserved.
-- =====================================================================

create extension if not exists pgcrypto;

-- ---------------------------------------------------------------------
-- debts: direction + link to a friend loan
-- ---------------------------------------------------------------------
alter table public.debts
    add column if not exists direction text not null default 'OWED'
        check (direction in ('OWED', 'LENT')),
    add column if not exists loan_id uuid,
    add column if not exists counterparty_id uuid references auth.users (id) on delete set null;

create index if not exists debts_loan_id_idx on public.debts (loan_id);

-- ---------------------------------------------------------------------
-- profiles
-- ---------------------------------------------------------------------
create table if not exists public.profiles (
    id           uuid primary key references auth.users (id) on delete cascade,
    username     text not null unique
                 check (username ~ '^[a-z0-9_]{3,24}$'),
    display_name text not null check (char_length(btrim(display_name)) between 1 and 60),
    avatar_url   text,
    bio          text check (char_length(bio) <= 200),
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now()
);

-- A profile row is created automatically for every new auth user.
-- The username is derived from the email and made unique with a random suffix.
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    base text;
    candidate text;
begin
    base := lower(regexp_replace(split_part(new.email, '@', 1), '[^a-z0-9_]', '', 'g'));
    if char_length(base) < 3 then base := 'user'; end if;
    base := left(base, 18);
    candidate := base;
    while exists (select 1 from public.profiles where username = candidate) loop
        candidate := base || '_' || substr(md5(random()::text), 1, 4);
    end loop;
    insert into public.profiles (id, username, display_name)
    values (new.id, candidate, coalesce(new.raw_user_meta_data ->> 'display_name', split_part(new.email, '@', 1)))
    on conflict (id) do nothing;
    return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
after insert on auth.users
for each row execute function public.handle_new_user();

-- Backfill profiles for users that existed before this script.
insert into public.profiles (id, username, display_name)
select u.id,
       left(lower(regexp_replace(split_part(u.email, '@', 1), '[^a-z0-9_]', '', 'g')), 18)
           || '_' || substr(md5(u.id::text), 1, 4),
       split_part(u.email, '@', 1)
from auth.users u
where not exists (select 1 from public.profiles p where p.id = u.id);

alter table public.profiles enable row level security;
revoke all on public.profiles from anon, public;
grant select, update on public.profiles to authenticated;

drop policy if exists "Profiles are visible to logged-in users" on public.profiles;
create policy "Profiles are visible to logged-in users" on public.profiles
    for select to authenticated using (true);

drop policy if exists "Users update own profile" on public.profiles;
create policy "Users update own profile" on public.profiles
    for update to authenticated
    using (id = (select auth.uid())) with check (id = (select auth.uid()));

-- Avatars: public bucket, each user writes only inside the folder named after their id.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('avatars', 'avatars', true, 2097152, array['image/png', 'image/jpeg', 'image/webp'])
on conflict (id) do update set public = true, file_size_limit = 2097152,
    allowed_mime_types = array['image/png', 'image/jpeg', 'image/webp'];

drop policy if exists "Avatar images are public" on storage.objects;
create policy "Avatar images are public" on storage.objects
    for select using (bucket_id = 'avatars');

drop policy if exists "Users upload own avatar" on storage.objects;
create policy "Users upload own avatar" on storage.objects
    for insert to authenticated
    with check (bucket_id = 'avatars' and (storage.foldername(name))[1] = (select auth.uid())::text);

drop policy if exists "Users update own avatar" on storage.objects;
create policy "Users update own avatar" on storage.objects
    for update to authenticated
    using (bucket_id = 'avatars' and (storage.foldername(name))[1] = (select auth.uid())::text);

drop policy if exists "Users delete own avatar" on storage.objects;
create policy "Users delete own avatar" on storage.objects
    for delete to authenticated
    using (bucket_id = 'avatars' and (storage.foldername(name))[1] = (select auth.uid())::text);

-- ---------------------------------------------------------------------
-- friendships
-- ---------------------------------------------------------------------
create table if not exists public.friendships (
    id           bigint generated always as identity primary key,
    requester_id uuid not null references auth.users (id) on delete cascade,
    addressee_id uuid not null references auth.users (id) on delete cascade,
    status       text not null default 'PENDING' check (status in ('PENDING', 'ACCEPTED')),
    created_at   timestamptz not null default now(),
    check (requester_id <> addressee_id)
);
-- one relation per pair, regardless of who asked first
create unique index if not exists friendships_pair_unique
    on public.friendships (least(requester_id, addressee_id), greatest(requester_id, addressee_id));

alter table public.friendships enable row level security;
revoke all on public.friendships from anon, public;
grant select, insert, update, delete on public.friendships to authenticated;

drop policy if exists "Friendship visible to both sides" on public.friendships;
create policy "Friendship visible to both sides" on public.friendships
    for select to authenticated
    using ((select auth.uid()) in (requester_id, addressee_id));

drop policy if exists "Users send friend requests" on public.friendships;
create policy "Users send friend requests" on public.friendships
    for insert to authenticated
    with check (requester_id = (select auth.uid()) and status = 'PENDING');

-- Only the addressee can accept.
drop policy if exists "Addressee accepts" on public.friendships;
create policy "Addressee accepts" on public.friendships
    for update to authenticated
    using (addressee_id = (select auth.uid()))
    with check (addressee_id = (select auth.uid()) and status = 'ACCEPTED');

drop policy if exists "Either side removes friendship" on public.friendships;
create policy "Either side removes friendship" on public.friendships
    for delete to authenticated
    using ((select auth.uid()) in (requester_id, addressee_id));

create or replace function public.are_friends(a uuid, b uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1 from public.friendships f
        where f.status = 'ACCEPTED'
          and ((f.requester_id = a and f.addressee_id = b) or (f.requester_id = b and f.addressee_id = a))
    );
$$;

-- ---------------------------------------------------------------------
-- messages (direct chat between friends)
-- ---------------------------------------------------------------------
create table if not exists public.messages (
    id          bigint generated always as identity primary key,
    sender_id   uuid not null references auth.users (id) on delete cascade,
    receiver_id uuid not null references auth.users (id) on delete cascade,
    body        text not null check (char_length(btrim(body)) between 1 and 2000),
    created_at  timestamptz not null default now(),
    read_at     timestamptz,
    check (sender_id <> receiver_id)
);

create index if not exists messages_pair_idx on public.messages (least(sender_id, receiver_id), greatest(sender_id, receiver_id), created_at);

alter table public.messages enable row level security;
revoke all on public.messages from anon, public;
grant select, insert, update on public.messages to authenticated;

drop policy if exists "Chat visible to participants" on public.messages;
create policy "Chat visible to participants" on public.messages
    for select to authenticated
    using ((select auth.uid()) in (sender_id, receiver_id));

drop policy if exists "Friends send messages" on public.messages;
create policy "Friends send messages" on public.messages
    for insert to authenticated
    with check (sender_id = (select auth.uid()) and public.are_friends(sender_id, receiver_id));

-- The receiver may only mark a message as read.
drop policy if exists "Receiver marks read" on public.messages;
create policy "Receiver marks read" on public.messages
    for update to authenticated
    using (receiver_id = (select auth.uid()))
    with check (receiver_id = (select auth.uid()));

-- ---------------------------------------------------------------------
-- loans between friends
-- ---------------------------------------------------------------------
create table if not exists public.loans (
    id             uuid primary key default gen_random_uuid(),
    lender_id      uuid not null references auth.users (id) on delete cascade,
    borrower_id    uuid not null references auth.users (id) on delete cascade,
    -- who created the request: the lender offers, or the borrower asks
    initiator_id   uuid not null references auth.users (id) on delete cascade,
    amount         numeric(14, 2) not null check (amount > 0),
    purpose        text not null check (char_length(btrim(purpose)) between 1 and 200),
    due_date       date not null,
    -- Card number used for the transfer. Digits only, no expiry date and no CVV:
    -- a card number alone is what people share for transfers, nothing more is stored.
    borrower_card  text check (borrower_card ~ '^[0-9]{16}$'),
    status         text not null default 'PENDING'
                   check (status in ('PENDING', 'ACTIVE', 'DECLINED', 'CANCELLED', 'REPAID')),
    receipt_no     text unique,
    accepted_at    timestamptz,
    created_at     timestamptz not null default now(),
    check (lender_id <> borrower_id),
    check (initiator_id in (lender_id, borrower_id))
);

alter table public.loans enable row level security;
revoke all on public.loans from anon, public;
grant select, insert, update on public.loans to authenticated;

drop policy if exists "Loan visible to both parties" on public.loans;
create policy "Loan visible to both parties" on public.loans
    for select to authenticated
    using ((select auth.uid()) in (lender_id, borrower_id));

drop policy if exists "Friends create loan requests" on public.loans;
create policy "Friends create loan requests" on public.loans
    for insert to authenticated
    with check (initiator_id = (select auth.uid())
                and status = 'PENDING'
                and public.are_friends(lender_id, borrower_id));

-- Direct updates are limited to declining/cancelling. Acceptance goes through accept_loan().
drop policy if exists "Parties decline or cancel" on public.loans;
create policy "Parties decline or cancel" on public.loans
    for update to authenticated
    using ((select auth.uid()) in (lender_id, borrower_id) and status = 'PENDING')
    with check (status in ('DECLINED', 'CANCELLED'));

-- Accepting a loan: the party that did not create the request confirms it.
-- The borrower must have given a card number. Two linked debt records are created:
-- one "OWED" for the borrower and one "LENT" for the lender, so every existing
-- screen (dashboard, reports, calendar) works for both sides without changes.
create or replace function public.accept_loan(p_loan_id uuid, p_card text default null)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
    l         public.loans%rowtype;
    me        uuid := auth.uid();
    lender    text;
    borrower  text;
    receipt   text;
begin
    select * into l from public.loans where id = p_loan_id for update;
    if not found then raise exception 'Loan not found'; end if;
    if l.status <> 'PENDING' then raise exception 'Loan is not pending'; end if;
    if me is null or me not in (l.lender_id, l.borrower_id) then raise exception 'Not a party of this loan'; end if;
    if me = l.initiator_id then raise exception 'The other party must confirm'; end if;

    if me = l.borrower_id and p_card is not null then
        l.borrower_card := p_card;
    end if;
    if l.borrower_card is null or l.borrower_card !~ '^[0-9]{16}$' then
        raise exception 'Borrower card number is required';
    end if;

    select display_name into lender from public.profiles where id = l.lender_id;
    select display_name into borrower from public.profiles where id = l.borrower_id;
    receipt := 'KV-' || to_char(now(), 'YYYYMMDD') || '-' || upper(substr(l.id::text, 1, 6));

    update public.loans
       set status = 'ACTIVE', accepted_at = now(), receipt_no = receipt, borrower_card = l.borrower_card
     where id = l.id;

    insert into public.debts (user_id, title, creditor_name, original_amount, due_date, direction, loan_id, counterparty_id, notes)
    values (l.borrower_id, l.purpose, lender, l.amount, l.due_date, 'OWED', l.id, l.lender_id, 'Расписка ' || receipt);
    insert into public.debts (user_id, title, creditor_name, original_amount, due_date, direction, loan_id, counterparty_id, notes)
    values (l.lender_id, l.purpose, borrower, l.amount, l.due_date, 'LENT', l.id, l.borrower_id, 'Расписка ' || receipt);

    return l.id;
end;
$$;

grant execute on function public.accept_loan(uuid, text) to authenticated;

-- Payments on a loan are mirrored to the other party's linked debt so both sides
-- always see the same history. Mirrored rows carry mirror_of to avoid loops.
alter table public.payments add column if not exists mirror_of bigint references public.payments (id) on delete cascade;
alter table public.payments add column if not exists created_by uuid default auth.uid();

create or replace function public.mirror_loan_payment()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    src_loan uuid;
    other_debt bigint;
begin
    if new.mirror_of is not null then return new; end if;
    select loan_id into src_loan from public.debts where id = new.debt_id;
    if src_loan is null then return new; end if;
    select id into other_debt from public.debts where loan_id = src_loan and id <> new.debt_id;
    if other_debt is null then return new; end if;
    insert into public.payments (debt_id, amount, payment_date, notes, mirror_of, created_by)
    values (other_debt, new.amount, new.payment_date, new.notes, new.id, new.created_by);
    -- when the loan is fully repaid, close it
    if (select coalesce(sum(amount), 0) from public.payments where debt_id = new.debt_id)
       >= (select original_amount from public.debts where id = new.debt_id) then
        update public.loans set status = 'REPAID' where id = src_loan;
        update public.debts set status = 'PAID' where loan_id = src_loan;
    end if;
    return new;
end;
$$;

drop trigger if exists payments_mirror_loan on public.payments;
create trigger payments_mirror_loan
after insert on public.payments
for each row execute function public.mirror_loan_payment();

-- The mirrored copy must be visible to the owner of the mirrored debt: the existing
-- payments policies already allow that (payment belongs to the owner of its debt).
-- Deleting a payment on a loan debt deletes its mirror through the FK cascade; the
-- reverse direction is handled here.
create or replace function public.delete_payment_mirror()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    if old.mirror_of is not null then
        delete from public.payments where id = old.mirror_of;
    end if;
    return old;
end;
$$;

drop trigger if exists payments_delete_mirror on public.payments;
create trigger payments_delete_mirror
after delete on public.payments
for each row execute function public.delete_payment_mirror();

-- ---------------------------------------------------------------------
-- notifications (filled by triggers, shown as toasts on the site)
-- ---------------------------------------------------------------------
create table if not exists public.notifications (
    id         bigint generated always as identity primary key,
    user_id    uuid not null references auth.users (id) on delete cascade,
    kind       text not null,
    title      text not null,
    body       text,
    link       text,
    read       boolean not null default false,
    created_at timestamptz not null default now()
);
create index if not exists notifications_user_idx on public.notifications (user_id, read, created_at desc);

alter table public.notifications enable row level security;
revoke all on public.notifications from anon, public;
grant select, update, delete on public.notifications to authenticated;

drop policy if exists "Own notifications" on public.notifications;
create policy "Own notifications" on public.notifications
    for select to authenticated using (user_id = (select auth.uid()));
drop policy if exists "Mark own notifications" on public.notifications;
create policy "Mark own notifications" on public.notifications
    for update to authenticated using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));
drop policy if exists "Delete own notifications" on public.notifications;
create policy "Delete own notifications" on public.notifications
    for delete to authenticated using (user_id = (select auth.uid()));

create or replace function public.notify(p_user uuid, p_kind text, p_title text, p_body text, p_link text)
returns void
language sql
security definer
set search_path = ''
as $$
    insert into public.notifications (user_id, kind, title, body, link)
    values (p_user, p_kind, p_title, p_body, p_link);
$$;

create or replace function public.on_friendship_change()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    who text;
begin
    if tg_op = 'INSERT' then
        select display_name into who from public.profiles where id = new.requester_id;
        perform public.notify(new.addressee_id, 'friend_request', who, 'friend_request', '/friends');
    elsif tg_op = 'UPDATE' and new.status = 'ACCEPTED' and old.status = 'PENDING' then
        select display_name into who from public.profiles where id = new.addressee_id;
        perform public.notify(new.requester_id, 'friend_accepted', who, 'friend_accepted', '/friends');
    end if;
    return new;
end;
$$;

drop trigger if exists friendships_notify on public.friendships;
create trigger friendships_notify
after insert or update on public.friendships
for each row execute function public.on_friendship_change();

create or replace function public.on_loan_change()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    who text;
    target uuid;
begin
    if tg_op = 'INSERT' then
        target := case when new.initiator_id = new.lender_id then new.borrower_id else new.lender_id end;
        select display_name into who from public.profiles where id = new.initiator_id;
        perform public.notify(target, 'loan_request', who, new.amount::text, '/loans/' || new.id);
    elsif tg_op = 'UPDATE' and new.status <> old.status then
        target := case when auth.uid() = new.lender_id then new.borrower_id else new.lender_id end;
        select display_name into who from public.profiles where id = coalesce(auth.uid(), new.initiator_id);
        perform public.notify(target, 'loan_' || lower(new.status), who, new.amount::text, '/loans/' || new.id);
    end if;
    return new;
end;
$$;

drop trigger if exists loans_notify on public.loans;
create trigger loans_notify
after insert or update on public.loans
for each row execute function public.on_loan_change();

create or replace function public.on_message_insert()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare who text;
begin
    select display_name into who from public.profiles where id = new.sender_id;
    perform public.notify(new.receiver_id, 'message', who, left(new.body, 80), '/friends/' || new.sender_id);
    return new;
end;
$$;

drop trigger if exists messages_notify on public.messages;
create trigger messages_notify
after insert on public.messages
for each row execute function public.on_message_insert();

-- Realtime: the site subscribes to these tables for live toasts and chat.
do $$
begin
    alter publication supabase_realtime add table public.notifications;
exception when duplicate_object then null; end $$;
do $$
begin
    alter publication supabase_realtime add table public.messages;
exception when duplicate_object then null; end $$;
do $$
begin
    alter publication supabase_realtime add table public.loans;
exception when duplicate_object then null; end $$;

-- ---------------------------------------------------------------------
-- Interest rate on friend loans (stored for the receipt; Kvit does not accrue interest)
-- ---------------------------------------------------------------------
alter table public.loans add column if not exists interest_rate numeric(5, 2) check (interest_rate between 0 and 100);

create or replace function public.accept_loan(p_loan_id uuid, p_card text default null)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
    l         public.loans%rowtype;
    me        uuid := auth.uid();
    lender    text;
    borrower  text;
    receipt   text;
begin
    select * into l from public.loans where id = p_loan_id for update;
    if not found then raise exception 'Loan not found'; end if;
    if l.status <> 'PENDING' then raise exception 'Loan is not pending'; end if;
    if me is null or me not in (l.lender_id, l.borrower_id) then raise exception 'Not a party of this loan'; end if;
    if me = l.initiator_id then raise exception 'The other party must confirm'; end if;

    if me = l.borrower_id and p_card is not null then
        l.borrower_card := p_card;
    end if;
    if l.borrower_card is null or l.borrower_card !~ '^[0-9]{16}$' then
        raise exception 'Borrower card number is required';
    end if;

    select display_name into lender from public.profiles where id = l.lender_id;
    select display_name into borrower from public.profiles where id = l.borrower_id;
    receipt := 'KV-' || to_char(now(), 'YYYYMMDD') || '-' || upper(substr(l.id::text, 1, 6));

    update public.loans
       set status = 'ACTIVE', accepted_at = now(), receipt_no = receipt, borrower_card = l.borrower_card
     where id = l.id;

    insert into public.debts (user_id, title, creditor_name, original_amount, interest_rate, due_date, direction, loan_id, counterparty_id, notes)
    values (l.borrower_id, l.purpose, lender, l.amount, l.interest_rate, l.due_date, 'OWED', l.id, l.lender_id, 'Расписка ' || receipt);
    insert into public.debts (user_id, title, creditor_name, original_amount, interest_rate, due_date, direction, loan_id, counterparty_id, notes)
    values (l.lender_id, l.purpose, borrower, l.amount, l.interest_rate, l.due_date, 'LENT', l.id, l.borrower_id, 'Расписка ' || receipt);

    return l.id;
end;
$$;

-- Presence / typing indicators use Realtime broadcast channels only; nothing is stored.
