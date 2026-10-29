-- =====================================================================
-- Kvit Web, part 2 (run AFTER schema_web.sql, re-runnable):
-- reminders, installment schedules, split bills, group chats.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Reminders: each user picks how many days ahead to be warned (default 14).
-- A daily pg_cron job writes an in-app notification; e-mail is optional
-- (see supabase/functions/due-reminders/README.md).
-- ---------------------------------------------------------------------
alter table public.profiles add column if not exists reminder_days int not null default 14 check (reminder_days between 0 and 90);
alter table public.profiles add column if not exists email_reminders boolean not null default false;

create or replace function public.create_due_reminders()
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    insert into public.notifications (user_id, kind, title, body, link)
    select d.user_id, 'due_soon', d.title, d.due_date::text, '/debts?id=' || d.id
    from public.debts d
    join public.profiles p on p.id = d.user_id
    where d.status = 'ACTIVE'
      and d.direction = 'OWED'
      and d.due_date between current_date and current_date + p.reminder_days
      and (select coalesce(sum(amount), 0) from public.payments where debt_id = d.id) < d.original_amount
      and not exists (
          select 1 from public.notifications n
          where n.user_id = d.user_id and n.kind = 'due_soon'
            and n.link = '/debts?id=' || d.id and n.created_at > now() - interval '1 day');
end;
$$;

-- Runs every morning at 06:00 UTC. pg_cron must be enabled: Dashboard -> Database -> Extensions.
create extension if not exists pg_cron;
do $$
begin
    perform cron.unschedule('kvit-due-reminders');
exception when others then null; end $$;
select cron.schedule('kvit-due-reminders', '0 6 * * *', 'select public.create_due_reminders()');

-- ---------------------------------------------------------------------
-- Installment schedule for a debt
-- ---------------------------------------------------------------------
create table if not exists public.installments (
    id         bigint generated always as identity primary key,
    debt_id    bigint not null references public.debts (id) on delete cascade,
    seq        int not null,
    due_date   date not null,
    amount     numeric(14, 2) not null check (amount > 0),
    payment_id bigint references public.payments (id) on delete set null,
    unique (debt_id, seq)
);
create index if not exists installments_debt_idx on public.installments (debt_id);
alter table public.installments enable row level security;
revoke all on public.installments from anon, public;
grant select, insert, update, delete on public.installments to authenticated;

drop policy if exists "Installments follow the debt owner" on public.installments;
create policy "Installments follow the debt owner" on public.installments
    for all to authenticated
    using (exists (select 1 from public.debts d where d.id = installments.debt_id and d.user_id = (select auth.uid())))
    with check (exists (select 1 from public.debts d where d.id = installments.debt_id and d.user_id = (select auth.uid())));

-- ---------------------------------------------------------------------
-- Split bills ("chipped in for a gift"): the payer records the total, every
-- friend owes an equal share. Linked debts are created so the existing screens
-- show them; the payer gets one LENT debt per member.
-- ---------------------------------------------------------------------
create table if not exists public.splits (
    id         uuid primary key default gen_random_uuid(),
    payer_id   uuid not null references auth.users (id) on delete cascade,
    title      text not null check (char_length(btrim(title)) between 1 and 200),
    total      numeric(14, 2) not null check (total > 0),
    due_date   date,
    created_at timestamptz not null default now()
);
create table if not exists public.split_members (
    split_id uuid not null references public.splits (id) on delete cascade,
    user_id  uuid not null references auth.users (id) on delete cascade,
    share    numeric(14, 2) not null check (share > 0),
    primary key (split_id, user_id)
);
alter table public.splits enable row level security;
alter table public.split_members enable row level security;
revoke all on public.splits, public.split_members from anon, public;
grant select on public.splits, public.split_members to authenticated;

drop policy if exists "Split visible to payer and members" on public.splits;
create policy "Split visible to payer and members" on public.splits
    for select to authenticated
    using (payer_id = (select auth.uid()) or exists (select 1 from public.split_members m where m.split_id = splits.id and m.user_id = (select auth.uid())));
drop policy if exists "Split members visible to participants" on public.split_members;
create policy "Split members visible to participants" on public.split_members
    for select to authenticated
    using (user_id = (select auth.uid()) or exists (select 1 from public.splits s where s.id = split_members.split_id and s.payer_id = (select auth.uid())));

alter table public.debts add column if not exists split_id uuid references public.splits (id) on delete cascade;

-- Shares are equal (payer included), rounded to tiyn; the remainder goes to the first member.
create or replace function public.create_split(p_title text, p_total numeric, p_members uuid[], p_due date default null)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
    me      uuid := auth.uid();
    sid     uuid;
    n       int := array_length(p_members, 1);
    base    numeric(14, 2);
    rest    numeric(14, 2);
    uid     uuid;
    i       int := 0;
    myname  text;
    mname   text;
    share   numeric(14, 2);
begin
    if me is null then raise exception 'Not signed in'; end if;
    if n is null or n = 0 then raise exception 'Pick at least one friend'; end if;
    foreach uid in array p_members loop
        if uid = me then raise exception 'You cannot owe yourself'; end if;
        if not public.are_friends(me, uid) then raise exception 'All members must be your friends'; end if;
    end loop;

    base := trunc(p_total / (n + 1), 2);
    rest := p_total - base * (n + 1);
    select display_name into myname from public.profiles where id = me;

    insert into public.splits (payer_id, title, total, due_date) values (me, p_title, p_total, p_due) returning id into sid;

    foreach uid in array p_members loop
        i := i + 1;
        share := base + case when i = 1 then rest else 0 end;
        select display_name into mname from public.profiles where id = uid;
        insert into public.split_members (split_id, user_id, share) values (sid, uid, share);
        insert into public.debts (user_id, title, creditor_name, original_amount, due_date, direction, counterparty_id, split_id, notes)
        values (uid, p_title, myname, share, p_due, 'OWED', me, sid, 'Складчина');
        insert into public.debts (user_id, title, creditor_name, original_amount, due_date, direction, counterparty_id, split_id, notes)
        values (me, p_title, mname, share, p_due, 'LENT', uid, sid, 'Складчина');
        perform public.notify(uid, 'split', myname, share::text, '/debts');
    end loop;
    return sid;
end;
$$;
grant execute on function public.create_split(text, numeric, uuid[], date) to authenticated;

-- Payments on a split debt mirror to the matching debt of the other side.
create or replace function public.mirror_split_payment()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    src public.debts%rowtype;
    other_debt bigint;
begin
    if new.mirror_of is not null then return new; end if;
    select * into src from public.debts where id = new.debt_id;
    if src.split_id is null then return new; end if;
    select id into other_debt from public.debts
    where split_id = src.split_id and id <> src.id and user_id = src.counterparty_id and counterparty_id = src.user_id;
    if other_debt is null then return new; end if;
    insert into public.payments (debt_id, amount, payment_date, notes, mirror_of, created_by)
    values (other_debt, new.amount, new.payment_date, new.notes, new.id, new.created_by);
    return new;
end;
$$;
drop trigger if exists payments_mirror_split on public.payments;
create trigger payments_mirror_split
after insert on public.payments
for each row execute function public.mirror_split_payment();

-- ---------------------------------------------------------------------
-- Group chats
-- ---------------------------------------------------------------------
create table if not exists public.rooms (
    id         uuid primary key default gen_random_uuid(),
    name       text not null check (char_length(btrim(name)) between 1 and 60),
    created_by uuid not null references auth.users (id) on delete cascade,
    created_at timestamptz not null default now()
);
create table if not exists public.room_members (
    room_id   uuid not null references public.rooms (id) on delete cascade,
    user_id   uuid not null references auth.users (id) on delete cascade,
    joined_at timestamptz not null default now(),
    primary key (room_id, user_id)
);
create table if not exists public.room_messages (
    id         bigint generated always as identity primary key,
    room_id    uuid not null references public.rooms (id) on delete cascade,
    sender_id  uuid not null references auth.users (id) on delete cascade,
    body       text not null check (char_length(btrim(body)) between 1 and 2000),
    created_at timestamptz not null default now()
);
create index if not exists room_messages_room_idx on public.room_messages (room_id, created_at);

alter table public.rooms enable row level security;
alter table public.room_members enable row level security;
alter table public.room_messages enable row level security;
revoke all on public.rooms, public.room_members, public.room_messages from anon, public;
grant select, insert, update, delete on public.rooms to authenticated;
grant select, insert, delete on public.room_members to authenticated;
grant select, insert on public.room_messages to authenticated;

create or replace function public.is_room_member(r uuid, u uuid)
returns boolean language sql stable security definer set search_path = '' as $$
    select exists (select 1 from public.room_members where room_id = r and user_id = u);
$$;

-- Creating a room and adding its members in one call keeps RLS simple.
create or replace function public.create_room(p_name text, p_members uuid[])
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
    me  uuid := auth.uid();
    rid uuid;
    uid uuid;
begin
    if me is null then raise exception 'Not signed in'; end if;
    insert into public.rooms (name, created_by) values (p_name, me) returning id into rid;
    insert into public.room_members (room_id, user_id) values (rid, me);
    foreach uid in array p_members loop
        if uid <> me and public.are_friends(me, uid) then
            insert into public.room_members (room_id, user_id) values (rid, uid) on conflict do nothing;
            perform public.notify(uid, 'room', p_name, null, '/friends/room/' || rid);
        end if;
    end loop;
    return rid;
end;
$$;
grant execute on function public.create_room(text, uuid[]) to authenticated;

drop policy if exists "Rooms visible to members" on public.rooms;
create policy "Rooms visible to members" on public.rooms for select to authenticated
    using (public.is_room_member(id, (select auth.uid())));
drop policy if exists "Creator deletes room" on public.rooms;
create policy "Creator deletes room" on public.rooms for delete to authenticated
    using (created_by = (select auth.uid()));

drop policy if exists "Members see membership" on public.room_members;
create policy "Members see membership" on public.room_members for select to authenticated
    using (public.is_room_member(room_id, (select auth.uid())));
drop policy if exists "Creator adds friends" on public.room_members;
create policy "Creator adds friends" on public.room_members for insert to authenticated
    with check (exists (select 1 from public.rooms r where r.id = room_id and r.created_by = (select auth.uid()))
                and public.are_friends((select auth.uid()), user_id));
drop policy if exists "Leave room or creator removes" on public.room_members;
create policy "Leave room or creator removes" on public.room_members for delete to authenticated
    using (user_id = (select auth.uid()) or exists (select 1 from public.rooms r where r.id = room_id and r.created_by = (select auth.uid())));

drop policy if exists "Room messages for members" on public.room_messages;
create policy "Room messages for members" on public.room_messages for select to authenticated
    using (public.is_room_member(room_id, (select auth.uid())));
drop policy if exists "Members post" on public.room_messages;
create policy "Members post" on public.room_messages for insert to authenticated
    with check (sender_id = (select auth.uid()) and public.is_room_member(room_id, (select auth.uid())));

do $$ begin alter publication supabase_realtime add table public.room_messages; exception when duplicate_object then null; end $$;
do $$ begin alter publication supabase_realtime add table public.installments; exception when duplicate_object then null; end $$;
