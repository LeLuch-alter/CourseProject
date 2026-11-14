-- =====================================================================
-- Kvit Web, part 5 (run AFTER schema_web_4.sql, re-runnable):
-- security and integrity fixes for objects created by the earlier scripts.
--
-- This file is the final word on grants, policies and helper functions.
-- If you ever re-run schema.sql or any schema_web*.sql file, run this
-- file again afterwards: the older scripts would restore the weaker grants.
-- No table is dropped and no user data is deleted, except that
-- debts.loan_id values pointing to a loan that no longer exists are cleared
-- before the new foreign key is added.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Profile creation for new users
-- Fixes: upper-case letters were removed from the username instead of being
-- lowered; a user without e-mail (phone/anonymous/OAuth) or with a long
-- display name in the metadata could not sign up (NOT NULL / CHECK failure).
-- ---------------------------------------------------------------------
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    local_part text;
    base       text;
    candidate  text;
    shown_name text;
begin
    local_part := split_part(coalesce(new.email, ''), '@', 1);
    base := regexp_replace(lower(local_part), '[^a-z0-9_]', '', 'g');
    if char_length(base) < 3 then base := 'user'; end if;
    base := left(base, 18);
    candidate := base;
    while exists (select 1 from public.profiles where username = candidate) loop
        candidate := base || '_' || substr(md5(random()::text), 1, 4);
    end loop;

    shown_name := left(btrim(coalesce(new.raw_user_meta_data ->> 'display_name', '')), 60);
    if shown_name = '' then shown_name := left(nullif(btrim(local_part), ''), 60); end if;
    if shown_name is null then shown_name := candidate; end if;

    insert into public.profiles (id, username, display_name)
    values (new.id, candidate, shown_name)
    on conflict (id) do nothing;
    return new;
end;
$$;

-- ---------------------------------------------------------------------
-- 2. Helper functions used inside RLS policies.
-- They are security definer (to read tables the caller cannot see), so they
-- only answer questions about the caller; otherwise any user could probe the
-- friend list or room membership of other people through /rpc.
-- ---------------------------------------------------------------------
create or replace function public.are_friends(a uuid, b uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select coalesce((select auth.uid()) in (a, b), false)
       and exists (
           select 1 from public.friendships f
           where f.status = 'ACCEPTED'
             and ((f.requester_id = a and f.addressee_id = b) or (f.requester_id = b and f.addressee_id = a))
       );
$$;

create or replace function public.is_room_member(r uuid, u uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select coalesce(u = (select auth.uid()), false)
       and exists (select 1 from public.room_members where room_id = r and user_id = u);
$$;

-- The old split policies queried each other (splits -> split_members -> splits),
-- which Postgres rejects with "infinite recursion detected in policy".
create or replace function public.is_split_participant(s uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (select 1 from public.splits where id = s and payer_id = (select auth.uid()))
        or exists (select 1 from public.split_members where split_id = s and user_id = (select auth.uid()));
$$;

drop policy if exists "Split visible to payer and members" on public.splits;
create policy "Split visible to payer and members" on public.splits
    for select to authenticated
    using (public.is_split_participant(id));

drop policy if exists "Split members visible to participants" on public.split_members;
create policy "Split members visible to participants" on public.split_members
    for select to authenticated
    using (public.is_split_participant(split_id));

-- ---------------------------------------------------------------------
-- 3. Column-level grants: clients may change only the columns the site uses.
-- (Revoking a table privilege also removes old column privileges, so this
-- block can be re-run.)
-- ---------------------------------------------------------------------

-- Chat: the receiver may only set read_at (before, the receiver could rewrite
-- the body or even the sender of a message). The sender cannot backdate or
-- pre-mark a message as read.
revoke insert, update on public.messages from authenticated;
grant insert (sender_id, receiver_id, body) on public.messages to authenticated;
grant update (read_at) on public.messages to authenticated;

-- Notifications: only the read flag.
revoke update on public.notifications from authenticated;
grant update (read) on public.notifications to authenticated;

-- Friendships: the addressee could change requester_id while accepting and
-- so become "friends" with any user, then message them, send loan requests and
-- put debts into their account through split bills. Only status may change now.
revoke insert, update on public.friendships from authenticated;
grant insert (requester_id, addressee_id, status) on public.friendships to authenticated;
grant update (status) on public.friendships to authenticated;

-- Loans: receipt number, acceptance time etc. are set only by accept_loan().
-- Declining/cancelling may change the status and nothing else.
revoke insert, update on public.loans from authenticated;
grant insert (lender_id, borrower_id, initiator_id, amount, purpose, due_date, borrower_card, interest_rate, status)
    on public.loans to authenticated;
grant update (status) on public.loans to authenticated;

-- Group chat messages: no backdating.
revoke insert on public.room_messages from authenticated;
grant insert (room_id, sender_id, body) on public.room_messages to authenticated;

-- ---------------------------------------------------------------------
-- 4. Loan policies
-- ---------------------------------------------------------------------
-- Only the borrower can put a card number into a request.
drop policy if exists "Friends create loan requests" on public.loans;
create policy "Friends create loan requests" on public.loans
    for insert to authenticated
    with check (initiator_id = (select auth.uid())
                and status = 'PENDING'
                and (borrower_card is null or initiator_id = borrower_id)
                and public.are_friends(lender_id, borrower_id));

-- The other party declines, the initiator withdraws (cancels).
drop policy if exists "Parties decline or cancel" on public.loans;
create policy "Parties decline or cancel" on public.loans
    for update to authenticated
    using ((select auth.uid()) in (lender_id, borrower_id) and status = 'PENDING')
    with check ((status = 'DECLINED' and initiator_id <> (select auth.uid()))
             or (status = 'CANCELLED' and initiator_id = (select auth.uid())));

-- ---------------------------------------------------------------------
-- 5. debts.loan_id gets a real foreign key.
-- ON DELETE SET NULL: a loan disappears only when one of its users deletes the
-- account; the other person keeps their debt as an ordinary record.
-- ---------------------------------------------------------------------
update public.debts d
   set loan_id = null
 where d.loan_id is not null
   and not exists (select 1 from public.loans l where l.id = d.loan_id);

do $$
begin
    if not exists (select 1 from pg_constraint
                   where conname = 'debts_loan_id_fkey' and conrelid = 'public.debts'::regclass) then
        alter table public.debts
            add constraint debts_loan_id_fkey foreign key (loan_id)
            references public.loans (id) on delete set null not valid;
    end if;
end $$;
alter table public.debts validate constraint debts_loan_id_fkey;

-- ---------------------------------------------------------------------
-- 6. Guards for direct changes of debts and payments.
-- Kvit's own security-definer functions (accept_loan, create_split, the
-- payment mirrors) run as the table owner, so they are not affected; only
-- requests made with a user's token (role "authenticated") are checked.
-- ---------------------------------------------------------------------

-- Without this, a borrower could attach loan_id/split_id of a real loan to a
-- small fake debt, pay it off and have the loan marked REPAID on the lender's
-- side, or lower the amount of the loan debt to what was already paid.
create or replace function public.guard_debt_change()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
    if current_user <> 'authenticated' then
        return new;
    end if;

    if tg_op = 'INSERT' then
        if new.loan_id is not null or new.split_id is not null or new.counterparty_id is not null then
            raise exception 'Linked debts are created only by confirming a loan or a split bill';
        end if;
        return new;
    end if;

    if new.user_id is distinct from old.user_id
       or new.loan_id is distinct from old.loan_id
       or new.split_id is distinct from old.split_id
       or new.counterparty_id is distinct from old.counterparty_id then
        raise exception 'The owner and the links of a debt cannot be changed';
    end if;

    if old.loan_id is not null
       and (new.original_amount <> old.original_amount
            or new.currency is distinct from old.currency
            or new.direction is distinct from old.direction) then
        raise exception 'Amount, currency and direction of a friend loan are fixed by the receipt';
    end if;

    return new;
end;
$$;

drop trigger if exists debts_guard_change on public.debts;
create trigger debts_guard_change
before insert or update on public.debts
for each row execute function public.guard_debt_change();

-- mirror_of and created_by were writable by clients. With mirror_of pointing to
-- someone else's payment, deleting your own payment made delete_payment_mirror()
-- (security definer) delete that other user's payment.
create or replace function public.guard_payment_change()
returns trigger
language plpgsql
set search_path = ''
as $$
declare
    is_linked boolean;
begin
    if current_user <> 'authenticated' then
        return new;
    end if;

    if tg_op = 'INSERT' then
        if new.mirror_of is not null then
            raise exception 'mirror_of is set by Kvit only';
        end if;
        new.created_by := (select auth.uid());
        return new;
    end if;

    if new.debt_id <> old.debt_id
       or new.mirror_of is distinct from old.mirror_of
       or new.created_by is distinct from old.created_by then
        raise exception 'A payment cannot be moved to another debt or relinked';
    end if;

    -- Payments of friend loans and split bills exist on both sides; editing one
    -- copy would make the two histories disagree.
    if new.amount <> old.amount or new.payment_date <> old.payment_date then
        select (d.loan_id is not null or d.split_id is not null) into is_linked
        from public.debts d where d.id = old.debt_id;
        if coalesce(is_linked, false) then
            raise exception 'A payment shared with a friend cannot be edited; delete it and add a new one';
        end if;
    end if;

    return new;
end;
$$;

drop trigger if exists payments_guard_change on public.payments;
create trigger payments_guard_change
before insert or update on public.payments
for each row execute function public.guard_payment_change();

-- The balance check only matters when the amount or the debt changes. This also
-- keeps the mirror-detach updates below from being re-checked.
drop trigger if exists payments_within_balance on public.payments;
create trigger payments_within_balance
before insert or update of debt_id, amount on public.payments
for each row execute function public.check_payment_within_balance();

-- ---------------------------------------------------------------------
-- 7. Payment mirrors (friend loans and split bills)
-- ---------------------------------------------------------------------

-- Fixes: the loan was never marked REPAID when the other side's debt was gone,
-- and PAID overwrote a CANCELLED status.
create or replace function public.mirror_loan_payment()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    src_loan   uuid;
    other_debt bigint;
begin
    if new.mirror_of is not null then return new; end if;
    select loan_id into src_loan from public.debts where id = new.debt_id;
    if src_loan is null then return new; end if;

    select id into other_debt from public.debts
    where loan_id = src_loan and id <> new.debt_id
    order by id
    limit 1;
    if other_debt is not null then
        insert into public.payments (debt_id, amount, payment_date, notes, mirror_of, created_by)
        values (other_debt, new.amount, new.payment_date, new.notes, new.id, new.created_by);
    end if;

    -- when the loan is fully repaid, close it
    if (select coalesce(sum(amount), 0) from public.payments where debt_id = new.debt_id)
       >= (select original_amount from public.debts where id = new.debt_id) then
        update public.loans set status = 'REPAID' where id = src_loan and status = 'ACTIVE';
        update public.debts set status = 'PAID' where loan_id = src_loan and status = 'ACTIVE';
    end if;
    return new;
end;
$$;

-- Deleting one copy of a shared payment deletes the other copy (as before).
-- New: if that makes a repaid loan unpaid again, the loan and both debts are
-- reopened.
create or replace function public.delete_payment_mirror()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    src_loan    uuid;
    debt_amount numeric(14, 2);
begin
    if old.mirror_of is not null then
        delete from public.payments where id = old.mirror_of;
    end if;

    -- The debt is missing when the payment goes away together with its debt.
    select loan_id, original_amount into src_loan, debt_amount
    from public.debts where id = old.debt_id;
    if src_loan is not null
       and (select coalesce(sum(amount), 0) from public.payments where debt_id = old.debt_id) < debt_amount then
        update public.loans set status = 'ACTIVE' where id = src_loan and status = 'REPAID';
        update public.debts set status = 'ACTIVE' where loan_id = src_loan and status = 'PAID';
    end if;
    return old;
end;
$$;

-- Deleting your copy of a loan or split debt used to erase the other person's
-- payment history too (FK cascade on mirror_of plus delete_payment_mirror).
-- The mirror links are cut first, so the other side keeps its payments.
create or replace function public.detach_payment_mirrors()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    update public.payments
       set mirror_of = null
     where mirror_of in (select id from public.payments where debt_id = old.id);
    update public.payments
       set mirror_of = null
     where debt_id = old.id and mirror_of is not null;
    return old;
end;
$$;

drop trigger if exists debts_detach_payment_mirrors on public.debts;
create trigger debts_detach_payment_mirrors
before delete on public.debts
for each row execute function public.detach_payment_mirrors();

-- ---------------------------------------------------------------------
-- 8. Split bills and group chats: input validation
-- ---------------------------------------------------------------------
create or replace function public.create_split(p_title text, p_total numeric, p_members uuid[], p_due date default null)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
    me      uuid := auth.uid();
    sid     uuid;
    n       int := coalesce(array_length(p_members, 1), 0);
    v_total numeric(14, 2) := round(p_total, 2);
    base    numeric(14, 2);
    rest    numeric(14, 2);
    uid     uuid;
    i       int := 0;
    myname  text;
    mname   text;
    share   numeric(14, 2);
begin
    if me is null then raise exception 'Not signed in'; end if;
    if n = 0 then raise exception 'Pick at least one friend'; end if;
    if v_total is null or v_total <= 0 then raise exception 'Total must be greater than zero'; end if;
    -- count(distinct) skips nulls, so a null member is caught here as well
    if (select count(distinct t.m) from unnest(p_members) as t(m)) <> n then
        raise exception 'Each friend can be picked only once';
    end if;
    foreach uid in array p_members loop
        if uid = me then raise exception 'You cannot owe yourself'; end if;
        if not public.are_friends(me, uid) then raise exception 'All members must be your friends'; end if;
    end loop;

    -- Shares are equal (payer included), rounded down to tiyn; the remainder goes to the first member.
    base := trunc(v_total / (n + 1), 2);
    if base <= 0 then raise exception 'Total is too small to split'; end if;
    rest := v_total - base * (n + 1);
    select display_name into myname from public.profiles where id = me;

    insert into public.splits (payer_id, title, total, due_date) values (me, p_title, v_total, p_due) returning id into sid;

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
    insert into public.rooms (name, created_by) values (btrim(p_name), me) returning id into rid;
    insert into public.room_members (room_id, user_id) values (rid, me);
    foreach uid in array coalesce(p_members, '{}'::uuid[]) loop
        if uid is not null and uid <> me and public.are_friends(me, uid) then
            insert into public.room_members (room_id, user_id) values (rid, uid) on conflict do nothing;
            perform public.notify(uid, 'room', p_name, null, '/friends/room/' || rid);
        end if;
    end loop;
    return rid;
end;
$$;

-- An installment may only point to a payment of the same debt.
drop policy if exists "Installments follow the debt owner" on public.installments;
create policy "Installments follow the debt owner" on public.installments
    for all to authenticated
    using (exists (select 1 from public.debts d where d.id = installments.debt_id and d.user_id = (select auth.uid())))
    with check (exists (select 1 from public.debts d where d.id = installments.debt_id and d.user_id = (select auth.uid()))
                and (payment_id is null
                     or exists (select 1 from public.payments p
                                where p.id = installments.payment_id and p.debt_id = installments.debt_id)));

-- ---------------------------------------------------------------------
-- 9. EXECUTE privileges. Postgres gives EXECUTE to PUBLIC and Supabase also
-- grants it to anon/authenticated by default, so every function was callable
-- through /rest/v1/rpc. notify() in particular let anyone (even without login)
-- put a notification with any text and link into any user's account.
-- Trigger functions never need EXECUTE for the caller.
-- ---------------------------------------------------------------------
revoke execute on function
    public.check_payment_within_balance(),
    public.check_debt_amount_covers_payments(),
    public.handle_new_user(),
    public.are_friends(uuid, uuid),
    public.accept_loan(uuid, text),
    public.mirror_loan_payment(),
    public.delete_payment_mirror(),
    public.detach_payment_mirrors(),
    public.notify(uuid, text, text, text, text),
    public.on_friendship_change(),
    public.on_loan_change(),
    public.on_message_insert(),
    public.create_due_reminders(),
    public.create_split(text, numeric, uuid[], date),
    public.mirror_split_payment(),
    public.is_room_member(uuid, uuid),
    public.is_split_participant(uuid),
    public.create_room(text, uuid[]),
    public.guard_debt_change(),
    public.guard_payment_change()
from public, anon, authenticated;

-- Called by the site.
grant execute on function public.accept_loan(uuid, text) to authenticated;
grant execute on function public.create_split(text, numeric, uuid[], date) to authenticated;
grant execute on function public.create_room(text, uuid[]) to authenticated;
-- Used inside RLS policies, which run with the caller's rights.
grant execute on function public.are_friends(uuid, uuid) to authenticated;
grant execute on function public.is_room_member(uuid, uuid) to authenticated;
grant execute on function public.is_split_participant(uuid) to authenticated;

-- ---------------------------------------------------------------------
-- 10. Indexes for foreign keys used in filters, RLS and ON DELETE actions
-- ---------------------------------------------------------------------
create index if not exists debts_counterparty_id_idx on public.debts (counterparty_id);
create index if not exists debts_split_id_idx on public.debts (split_id);
create index if not exists payments_mirror_of_idx on public.payments (mirror_of);
create index if not exists installments_payment_id_idx on public.installments (payment_id);
create index if not exists friendships_requester_idx on public.friendships (requester_id);
create index if not exists friendships_addressee_idx on public.friendships (addressee_id);
create index if not exists messages_sender_idx on public.messages (sender_id);
create index if not exists messages_receiver_idx on public.messages (receiver_id);
create index if not exists loans_lender_idx on public.loans (lender_id);
create index if not exists loans_borrower_idx on public.loans (borrower_id);
create index if not exists loans_initiator_idx on public.loans (initiator_id);
create index if not exists splits_payer_idx on public.splits (payer_id);
create index if not exists split_members_user_idx on public.split_members (user_id);
create index if not exists rooms_created_by_idx on public.rooms (created_by);
create index if not exists room_members_user_idx on public.room_members (user_id);
create index if not exists room_messages_sender_idx on public.room_messages (sender_id);

-- ---------------------------------------------------------------------
-- 11. Realtime: the Friends page listens to friendships, but the table was
-- never added to the publication.
-- ---------------------------------------------------------------------
do $$
begin
    alter publication supabase_realtime add table public.friendships;
exception when duplicate_object then null; end $$;
