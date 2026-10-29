# Kvit Web

Веб-версия Kvit: React + Vite + TypeScript + Tailwind + Framer Motion, данные в Supabase
(те же таблицы `debts` и `payments`, что у настольного приложения, плюс профили, друзья, чат и расписки).

## Запуск локально

```powershell
cd web
npm install
copy .env.example .env     # вписать URL проекта и publishable-ключ Supabase
npm run dev                # http://localhost:5173
```

Проверка типов и сборка: `npx tsc -p tsconfig.app.json --noEmit`, `npm run build` (результат в `dist/`).

## Настройка Supabase (один раз)

1. SQL Editor → выполнить по порядку `supabase/schema.sql` (если ещё не выполнялся), `supabase/schema_web.sql`,
   `supabase/schema_web_2.sql` (напоминания, график погашения, складчины, групповые чаты; для напоминаний
   нужно включить расширение pg_cron: Database → Extensions).
   Второй скрипт можно запускать повторно: он добавляет таблицы `profiles`, `friendships`, `messages`,
   `loans`, `notifications`, колонку `direction` в `debts`, bucket `avatars` и включает Realtime.
2. Authentication → URL Configuration:
   - Site URL — адрес сайта (например `https://kvit.vercel.app`);
   - Redirect URLs — добавить `https://<сайт>/reset-password` и `http://localhost:5173/reset-password`.
3. Ключ: только **publishable** (`sb_publishable_…`). Клиент откажется запускаться с секретным ключом.

## Хостинг (бесплатно)

Любой статический хостинг подходит; настройки SPA уже лежат в репозитории (`vercel.json`, `public/_redirects`).

**Vercel:** Import Git Repository → Root Directory `web` → Framework Vite → Environment Variables
`VITE_SUPABASE_URL`, `VITE_SUPABASE_PUBLISHABLE_KEY` → Deploy.

**Netlify / Cloudflare Pages:** то же самое — корень `web`, команда `npm run build`, папка `dist`,
те же две переменные окружения.

После деплоя указать адрес сайта в Supabase (пункт 2 выше), иначе письма подтверждения и сброса пароля
будут вести на localhost.

## Docker

```powershell
cd web
docker compose build --build-arg VITE_SUPABASE_URL=... --build-arg VITE_SUPABASE_PUBLISHABLE_KEY=...
docker compose up -d      # http://localhost:8085
```

Или задать обе переменные в `web/.env` — `docker compose` подставит их сам. Образ двухэтапный: сборка на Node 22,
раздача через nginx с fallback на `index.html` (`nginx.conf`). Ключи вшиваются при сборке, поэтому образ с
publishable-ключом публиковать можно, а с секретным — нет (клиент такой ключ и не примет).

## Realtime

Чат, уведомления и статус расписок идут через WebSocket Supabase Realtime: изменения строк приходят мгновенно,
присутствие («в сети») и индикатор набора текста — через broadcast-канал без записи в базу.

## Что где

```
src/
├── lib/        supabase.ts, i18n.tsx (ru/kk/en), theme.tsx, toast.tsx, auth.tsx, data.tsx,
│               calc.ts (бизнес-правила, как в DebtCalculator), money.ts, dates.ts, csv.ts, receipt.ts (PDF)
├── api/        debts.ts, social.ts — все запросы к Supabase
├── components/ Shell (меню, тема, язык, уведомления, Ctrl+K), ui.tsx, forms.tsx, LoanForm.tsx
└── pages/      Login, ResetPassword, Dashboard, Debts, Payments, Reports, Calendar,
                Friends (+чат), Loans, Loan (расписка), Profile, Settings
```

## Напоминания

- При входе: toast о просроченных и близких сроках, окно в днях задаётся в настройках (`profiles.reminder_days`).
- Каждое утро: pg_cron вызывает `create_due_reminders()` и кладёт уведомление в колокольчик.
- Письма: необязательно, через Edge Function `supabase/functions/due-reminders` и Resend (инструкция рядом).

## Безопасность расписок

- Долг между друзьями появляется только после подтверждения второй стороной (`accept_loan()` в базе).
- Заёмщик указывает только 16 цифр номера карты для перевода. Срок действия и CVV не запрашиваются
  и нигде не хранятся. Номер виден только двум участникам расписки (RLS), на экране показан замаскированным.
- Платёж по расписке зеркалируется второй стороне триггером, поэтому история у обоих одинаковая.
- Это учебный проект: расписка в PDF — документ для двоих, а не нотариальный акт.
