-- =====================================================================
-- Kvit Web — two demo users with ready data (run AFTER schema_web_2.sql).
-- Logins:  aisha@kvit.demo / Kvit12345      daniyar@kvit.demo / Kvit12345
-- Re-running deletes and recreates these two users together with their data.
-- Works in the Supabase SQL Editor (runs as postgres, so RLS is bypassed).
-- =====================================================================

create extension if not exists pgcrypto;

-- clean previous demo users (cascades through profiles, debts, loans, messages...)
delete from auth.users where email in ('aisha@kvit.demo', 'daniyar@kvit.demo');

do $$
declare
    a uuid := gen_random_uuid();   -- Aisha
    d uuid := gen_random_uuid();   -- Daniyar
    loan uuid := gen_random_uuid();
    receipt text;
    debt_a1 bigint; debt_a2 bigint; debt_d1 bigint; debt_d2 bigint;
    loan_debt_borrower bigint; loan_debt_lender bigint;
    split uuid;
    room uuid;
begin
    -- ------------------------------------------------------------ auth users
    insert into auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
                            raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
                            confirmation_token, recovery_token, email_change, email_change_token_new, email_change_token_current)
    values
      ('00000000-0000-0000-0000-000000000000', a, 'authenticated', 'authenticated', 'aisha@kvit.demo',
       crypt('Kvit12345', gen_salt('bf')), now(),
       '{"provider":"email","providers":["email"]}', '{"display_name":"Айша Сейтова"}', now() - interval '40 days', now(), '', '', '', '', ''),
      ('00000000-0000-0000-0000-000000000000', d, 'authenticated', 'authenticated', 'daniyar@kvit.demo',
       crypt('Kvit12345', gen_salt('bf')), now(),
       '{"provider":"email","providers":["email"]}', '{"display_name":"Данияр Ахметов"}', now() - interval '35 days', now(), '', '', '', '', '');

    insert into auth.identities (id, provider_id, user_id, identity_data, provider, last_sign_in_at, created_at, updated_at)
    values
      (gen_random_uuid(), a::text, a, jsonb_build_object('sub', a::text, 'email', 'aisha@kvit.demo', 'email_verified', true), 'email', now(), now(), now()),
      (gen_random_uuid(), d::text, d, jsonb_build_object('sub', d::text, 'email', 'daniyar@kvit.demo', 'email_verified', true), 'email', now(), now(), now());

    -- profiles are created by the trigger; give them nice usernames and bios
    update public.profiles set username = 'aisha', display_name = 'Айша Сейтова', bio = 'Студентка, веду бюджет в Kvit', reminder_days = 10 where id = a;
    update public.profiles set username = 'daniyar', display_name = 'Данияр Ахметов', bio = 'Фрилансер. Долги под контролем', reminder_days = 14 where id = d;

    -- ------------------------------------------------------------ friendship
    insert into public.friendships (requester_id, addressee_id, status, created_at)
    values (a, d, 'ACCEPTED', now() - interval '30 days');

    -- ------------------------------------------------------------ Aisha's own debts
    insert into public.debts (user_id, title, creditor_name, original_amount, interest_rate, created_at, due_date, status, notes)
    values (a, 'Кредит на ноутбук', 'Kaspi Bank', 450000, 18.5, now() - interval '38 days', current_date + 120, 'ACTIVE', 'Рассрочка 12 месяцев')
    returning id into debt_a1;
    insert into public.debts (user_id, title, creditor_name, original_amount, created_at, due_date, status, notes)
    values (a, 'Аренда за октябрь', 'Марат Ибраев', 120000, now() - interval '20 days', current_date - 3, 'ACTIVE', 'Остаток обещала до конца недели')
    returning id into debt_a2;
    insert into public.debts (user_id, title, creditor_name, original_amount, created_at, due_date, status)
    values (a, 'Учебники', 'Меломан', 24500, now() - interval '60 days', current_date - 30, 'PAID');
    insert into public.debts (user_id, title, creditor_name, original_amount, created_at, due_date, status, notes)
    values (a, 'Абонемент в зал', 'Fitness First', 60000, now() - interval '50 days', current_date + 5, 'CANCELLED', 'Отказалась от абонемента');

    insert into public.payments (debt_id, amount, payment_date, notes) values
      (debt_a1, 37500, current_date - 30, 'Платёж 1'),
      (debt_a1, 37500, current_date - 1, 'Платёж 2'),
      (debt_a2, 70000, current_date - 10, 'Половина');
    insert into public.payments (debt_id, amount, payment_date)
    select id, 24500, current_date - 35 from public.debts where user_id = a and title = 'Учебники';

    -- ------------------------------------------------------------ Daniyar's own debts
    insert into public.debts (user_id, title, creditor_name, original_amount, interest_rate, created_at, due_date, status, notes)
    values (d, 'Автокредит', 'Halyk Bank', 2400000, 15, now() - interval '35 days', current_date + 330, 'ACTIVE', 'Ежемесячно 200 000')
    returning id into debt_d1;
    insert into public.debts (user_id, title, creditor_name, original_amount, created_at, due_date, status)
    values (d, 'Телефон в рассрочку', 'Technodom', 380000, now() - interval '25 days', current_date + 12, 'ACTIVE')
    returning id into debt_d2;

    insert into public.payments (debt_id, amount, payment_date, notes) values
      (debt_d1, 200000, current_date - 28, 'Сентябрь'),
      (debt_d1, 200000, current_date - 2, 'Октябрь'),
      (debt_d2, 190000, current_date - 15, null);

    -- installment schedule for the phone: two parts left
    insert into public.installments (debt_id, seq, due_date, amount) values
      (debt_d2, 1, current_date + 12, 95000),
      (debt_d2, 2, current_date + 42, 95000);

    -- ------------------------------------------------------------ IOU: Daniyar lent Aisha 80 000
    receipt := 'KV-' || to_char(now() - interval '12 days', 'YYYYMMDD') || '-' || upper(substr(loan::text, 1, 6));
    insert into public.loans (id, lender_id, borrower_id, initiator_id, amount, purpose, due_date, borrower_card, interest_rate, status, receipt_no, accepted_at, created_at)
    values (loan, d, a, a, 80000, 'До стипендии', current_date + 18, '4400430012345678', 0, 'ACTIVE', receipt, now() - interval '12 days', now() - interval '12 days');

    insert into public.debts (user_id, title, creditor_name, original_amount, due_date, direction, loan_id, counterparty_id, notes, created_at)
    values (a, 'До стипендии', 'Данияр Ахметов', 80000, current_date + 18, 'OWED', loan, d, 'Расписка ' || receipt, now() - interval '12 days')
    returning id into loan_debt_borrower;
    insert into public.debts (user_id, title, creditor_name, original_amount, due_date, direction, loan_id, counterparty_id, notes, created_at)
    values (d, 'До стипендии', 'Айша Сейтова', 80000, current_date + 18, 'LENT', loan, a, 'Расписка ' || receipt, now() - interval '12 days')
    returning id into loan_debt_lender;
    -- one payment by the borrower; the trigger mirrors it to the lender's debt
    insert into public.payments (debt_id, amount, payment_date, notes) values (loan_debt_borrower, 30000, current_date - 4, 'Первая часть');

    -- a second IOU waiting for Daniyar's confirmation: Aisha asks for 15 000
    insert into public.loans (lender_id, borrower_id, initiator_id, amount, purpose, due_date, borrower_card, status, created_at)
    values (d, a, a, 15000, 'Такси и обед на конференции', current_date + 7, '4400430012345678', 'PENDING', now() - interval '1 hour');

    -- ------------------------------------------------------------ split bill: Aisha paid 36 000 for a gift
    insert into public.splits (payer_id, title, total, due_date, created_at) values (a, 'Подарок Алие на день рождения', 36000, current_date + 10, now() - interval '3 days')
    returning id into split;
    insert into public.split_members (split_id, user_id, share) values (split, d, 18000);
    insert into public.debts (user_id, title, creditor_name, original_amount, due_date, direction, counterparty_id, split_id, notes, created_at)
    values (d, 'Подарок Алие на день рождения', 'Айша Сейтова', 18000, current_date + 10, 'OWED', a, split, 'Складчина', now() - interval '3 days');
    insert into public.debts (user_id, title, creditor_name, original_amount, due_date, direction, counterparty_id, split_id, notes, created_at)
    values (a, 'Подарок Алие на день рождения', 'Данияр Ахметов', 18000, current_date + 10, 'LENT', d, split, 'Складчина', now() - interval '3 days');

    -- ------------------------------------------------------------ chat
    insert into public.messages (sender_id, receiver_id, body, created_at, read_at) values
      (a, d, 'Привет! Можешь занять 80 тысяч до стипендии?', now() - interval '12 days 2 hours', now() - interval '12 days 1 hour'),
      (d, a, 'Да, без проблем. Оформи расписку в Kvit, я подтвержу', now() - interval '12 days 1 hour', now() - interval '12 days'),
      (a, d, 'Готово, отправила. Спасибо огромное!', now() - interval '12 days', now() - interval '11 days'),
      (d, a, 'Подтвердил. Удачи с сессией', now() - interval '11 days 23 hours', now() - interval '11 days'),
      (a, d, 'Перевела 30 000, остальное после 15-го', now() - interval '4 days', now() - interval '4 days'),
      (d, a, 'Вижу, спасибо. Как там подарок Алие?', now() - interval '3 days', null);

    -- ------------------------------------------------------------ group chat
    insert into public.rooms (name, created_by, created_at) values ('Подарок Алие', a, now() - interval '3 days') returning id into room;
    insert into public.room_members (room_id, user_id) values (room, a), (room, d);
    insert into public.room_messages (room_id, sender_id, body, created_at) values
      (room, a, 'Скинулись по 18 000, записала в складчину', now() - interval '3 days'),
      (room, d, 'Ок, переведу до пятницы', now() - interval '2 days 20 hours');

    -- demo notifications were created by triggers above; mark the old ones read
    update public.notifications set read = true where user_id in (a, d) and created_at < now() - interval '1 day';
end $$;

select u.email, p.username, p.display_name from auth.users u join public.profiles p on p.id = u.id
where u.email in ('aisha@kvit.demo', 'daniyar@kvit.demo');
