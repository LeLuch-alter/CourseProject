-- =====================================================================
-- Debt and Payment Accounting System — Supabase schema
-- Run once in Supabase Dashboard -> SQL Editor -> New query -> Run.
-- The script can be re-run: it drops and recreates the two tables
-- (ALL DATA IN THEM WILL BE DELETED).
-- =====================================================================

drop table if exists public.payments;
drop table if exists public.debts;

-- ---------------------------------------------------------------------
-- Tables
-- ---------------------------------------------------------------------

create table public.debts (
    id              bigint generated always as identity primary key,
    -- Owner of the record. Filled automatically from the logged-in user's token,
    -- so the desktop app never sends (and cannot fake) this value.
    user_id         uuid not null default auth.uid()
                    references auth.users (id) on delete cascade,
    title           text not null check (char_length(btrim(title)) between 1 and 200),
    creditor_name   text not null check (char_length(btrim(creditor_name)) between 1 and 200),
    original_amount numeric(14, 2) not null check (original_amount > 0),
    -- Stored for information only; the app does not calculate interest.
    interest_rate   numeric(5, 2) check (interest_rate between 0 and 100),
    created_at      timestamptz not null default now(),
    due_date        date,
    status          text not null default 'ACTIVE'
                    check (status in ('ACTIVE', 'PAID', 'CANCELLED')),
    notes           text check (char_length(notes) <= 1000)
);

create table public.payments (
    id           bigint generated always as identity primary key,
    debt_id      bigint not null references public.debts (id) on delete cascade,
    amount       numeric(14, 2) not null check (amount > 0),
    payment_date date not null default current_date,
    notes        text check (char_length(notes) <= 1000),
    created_at   timestamptz not null default now()
);

create index debts_user_id_idx on public.debts (user_id);
create index payments_debt_id_idx on public.payments (debt_id);

-- ---------------------------------------------------------------------
-- Business-rule triggers (protection even if someone bypasses the app
-- and calls the REST API directly with their own token)
-- ---------------------------------------------------------------------

-- Total of payments for a debt must never exceed the original amount.
create or replace function public.check_payment_within_balance()
returns trigger
language plpgsql
set search_path = ''
as $$
declare
    debt_amount  numeric(14, 2);
    already_paid numeric(14, 2);
begin
    -- Runs with the caller's rights, so RLS applies: another user's debt is "not found".
    -- FOR UPDATE locks the debt row so two parallel payments cannot both pass the check.
    select original_amount into debt_amount
    from public.debts
    where id = new.debt_id
    for update;

    if not found then
        raise exception 'Debt % not found', new.debt_id;
    end if;

    select coalesce(sum(amount), 0) into already_paid
    from public.payments
    where debt_id = new.debt_id
      and id is distinct from new.id;

    if already_paid + new.amount > debt_amount then
        raise exception 'Payment exceeds remaining balance (remaining: %)',
            debt_amount - already_paid;
    end if;

    return new;
end;
$$;

create trigger payments_within_balance
before insert or update on public.payments
for each row execute function public.check_payment_within_balance();

-- The original amount of a debt cannot be reduced below what is already paid.
create or replace function public.check_debt_amount_covers_payments()
returns trigger
language plpgsql
set search_path = ''
as $$
declare
    already_paid numeric(14, 2);
begin
    select coalesce(sum(amount), 0) into already_paid
    from public.payments
    where debt_id = new.id;

    if new.original_amount < already_paid then
        raise exception 'Debt amount cannot be less than already paid (%)', already_paid;
    end if;

    return new;
end;
$$;

create trigger debts_amount_covers_payments
before update of original_amount on public.debts
for each row execute function public.check_debt_amount_covers_payments();

-- ---------------------------------------------------------------------
-- Access control
-- ---------------------------------------------------------------------

-- Nobody without login (the "anon" role) gets any access to the tables.
revoke all on public.debts, public.payments from anon, public;
grant select, insert, update, delete on public.debts, public.payments to authenticated;

-- Row Level Security: every logged-in user sees and changes only their own rows.
alter table public.debts enable row level security;
alter table public.payments enable row level security;

create policy "Users read own debts" on public.debts
    for select to authenticated
    using (user_id = (select auth.uid()));

create policy "Users create own debts" on public.debts
    for insert to authenticated
    with check (user_id = (select auth.uid()));

create policy "Users update own debts" on public.debts
    for update to authenticated
    using (user_id = (select auth.uid()))
    with check (user_id = (select auth.uid()));

create policy "Users delete own debts" on public.debts
    for delete to authenticated
    using (user_id = (select auth.uid()));

-- A payment belongs to the owner of its debt.
create policy "Users read own payments" on public.payments
    for select to authenticated
    using (exists (select 1 from public.debts d
                   where d.id = payments.debt_id and d.user_id = (select auth.uid())));

create policy "Users create payments for own debts" on public.payments
    for insert to authenticated
    with check (exists (select 1 from public.debts d
                        where d.id = payments.debt_id and d.user_id = (select auth.uid())));

create policy "Users update own payments" on public.payments
    for update to authenticated
    using (exists (select 1 from public.debts d
                   where d.id = payments.debt_id and d.user_id = (select auth.uid())))
    with check (exists (select 1 from public.debts d
                        where d.id = payments.debt_id and d.user_id = (select auth.uid())));

create policy "Users delete own payments" on public.payments
    for delete to authenticated
    using (exists (select 1 from public.debts d
                   where d.id = payments.debt_id and d.user_id = (select auth.uid())));
