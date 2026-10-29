# due-reminders

Письма о приближающемся сроке. Необязательная часть: уведомления внутри сайта работают и без неё
(их пишет `create_due_reminders()` из `schema_web.sql` по расписанию pg_cron).

Что нужно для писем:

1. Аккаунт [Resend](https://resend.com) (бесплатный тариф: 100 писем в день) и его API-ключ.
2. Supabase CLI: `supabase functions deploy due-reminders --no-verify-jwt`.
3. `supabase secrets set RESEND_API_KEY=re_... SITE_URL=https://ваш-сайт`.
4. Расширения pg_cron и pg_net включены (Dashboard → Database → Extensions), затем SQL из шапки `index.ts`.

Пользователь включает письма в настройках сайта («Напоминать на email»). Service-role ключ используется
только внутри функции на сервере Supabase и никогда не попадает в браузер.
