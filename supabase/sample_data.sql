-- =====================================================================
-- Sample data for development and demos.
-- 1. First register in the app (or Supabase Dashboard -> Authentication -> Add user).
-- 2. Replace the email below with that user's email.
-- 3. Run in Supabase Dashboard -> SQL Editor.
-- =====================================================================

do $$
declare
    owner_email text := 'student@example.com';  -- <-- change this
    owner_id    uuid;
    debt_id     bigint;
begin
    select id into owner_id from auth.users where email = owner_email;
    if owner_id is null then
        raise exception 'User % not found. Register first.', owner_email;
    end if;

    insert into public.debts (user_id, title, creditor_name, original_amount, interest_rate, due_date, notes)
    values (owner_id, 'Кредит на ноутбук', 'Kaspi Bank', 350000.00, 0, current_date + 120, 'Рассрочка на 12 месяцев')
    returning id into debt_id;
    insert into public.payments (debt_id, amount, payment_date, notes) values
        (debt_id, 29200.00, current_date - 60, 'Платёж 1'),
        (debt_id, 29200.00, current_date - 30, 'Платёж 2');

    insert into public.debts (user_id, title, creditor_name, original_amount, due_date, notes)
    values (owner_id, 'Долг другу', 'Айдос', 50000.00, current_date - 10, 'Обещал вернуть до конца месяца')
    returning id into debt_id;
    insert into public.payments (debt_id, amount, payment_date) values
        (debt_id, 20000.00, current_date - 20);

    insert into public.debts (user_id, title, creditor_name, original_amount, due_date, status)
    values (owner_id, 'Оплата общежития', 'Университет', 120000.00, current_date - 40, 'PAID')
    returning id into debt_id;
    insert into public.payments (debt_id, amount, payment_date) values
        (debt_id, 120000.00, current_date - 45);

    insert into public.debts (user_id, title, creditor_name, original_amount, due_date)
    values (owner_id, 'Курсы английского', 'Language School', 90000.00, current_date + 30);
end;
$$;
