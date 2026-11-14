// Reminders about debts that are due soon: Telegram (if linked) and e-mail (if enabled).
// pg_cron calls it every 15 minutes with the CRON_SECRET header, so outsiders cannot trigger it.
// Each user chooses the time and the days in Settings; a user gets at most one message per day.
// Deploy:  supabase functions deploy due-reminders --no-verify-jwt
import { admin, escapeHtml, json, sendTelegram, siteUrl } from '../_shared/common.ts'

interface DebtRow {
  id: number
  title: string
  due_date: string
  original_amount: number
  currency: string
  payments: Array<{ amount: number }>
}

interface ProfileRow {
  id: string
  display_name: string
  reminder_days: number
  email_reminders: boolean
  reminder_time: string // 'HH:MM:SS'
  reminder_tz: string
  reminder_mode: 'daily' | 'weekdays' | 'dates'
  reminder_weekdays: number[]
  reminder_dates: string[]
  last_reminded_on: string | null
}

const SYMBOL: Record<string, string> = { KZT: '₸', USD: '$', EUR: '€', RUB: '₽' }
const WEEKDAY: Record<string, number> = { Mon: 1, Tue: 2, Wed: 3, Thu: 4, Fri: 5, Sat: 6, Sun: 7 }

// Current date, minute of the day and ISO weekday in the user's time zone.
function localNow(timeZone: string): { date: string; minutes: number; weekday: number } {
  let parts: Intl.DateTimeFormatPart[]
  try {
    parts = new Intl.DateTimeFormat('en-US', { timeZone, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23', weekday: 'short' }).formatToParts(new Date())
  } catch {
    return localNow('Asia/Almaty')
  }
  const get = (type: string) => parts.find((p) => p.type === type)?.value ?? ''
  return {
    date: `${get('year')}-${get('month')}-${get('day')}`,
    minutes: Number(get('hour')) * 60 + Number(get('minute')),
    weekday: WEEKDAY[get('weekday')] ?? 1,
  }
}

function addDays(isoDate: string, days: number): string {
  const d = new Date(isoDate + 'T00:00:00Z')
  d.setUTCDate(d.getUTCDate() + days)
  return d.toISOString().slice(0, 10)
}

// The cron job runs every 15 minutes, so 23:45 is the last run of a day. A later chosen
// time (for example 23:50) would never be reached and is treated as 23:45.
const LAST_RUN_MINUTES = 23 * 60 + 45

// Money is added in tiyn (integers) so 0.1 + 0.2 style float errors do not show up as a debt.
const toTiyn = (v: number | string) => Math.round(Number(v) * 100)

// The reminder is due once the chosen time has passed today, on a day the user selected,
// and nothing was sent yet today. ">=" instead of "==" means a missed cron run is caught up later.
function isDue(p: ProfileRow, now: { date: string; minutes: number; weekday: number }): boolean {
  if (p.last_reminded_on === now.date) return false
  const [h, m] = (p.reminder_time ?? '08:00').split(':').map(Number)
  const chosen = Number.isFinite(h) && Number.isFinite(m) ? Math.min(h * 60 + m, LAST_RUN_MINUTES) : 8 * 60
  if (now.minutes < chosen) return false
  if (p.reminder_mode === 'weekdays') return p.reminder_weekdays.includes(now.weekday)
  if (p.reminder_mode === 'dates') return p.reminder_dates.includes(now.date)
  return true
}

Deno.serve(async (req) => {
  // An unset secret must not turn into "any request without the header is accepted".
  const cronSecret = Deno.env.get('CRON_SECRET')
  if (!cronSecret || req.headers.get('x-cron-secret') !== cronSecret) return new Response('forbidden', { status: 403 })
  const db = admin()
  const resendKey = Deno.env.get('RESEND_API_KEY')
  const site = siteUrl()

  const { data: profiles, error } = await db
    .from('profiles')
    .select('id, display_name, reminder_days, email_reminders, reminder_time, reminder_tz, reminder_mode, reminder_weekdays, reminder_dates, last_reminded_on')
  if (error) return json({ error: error.message }, 500)
  const { data: links, error: linksError } = await db.from('telegram_links').select('user_id, chat_id').not('chat_id', 'is', null)
  if (linksError) return json({ error: linksError.message }, 500)
  const chatOf = new Map((links ?? []).map((l) => [l.user_id as string, l.chat_id as number]))

  let telegram = 0, email = 0
  for (const p of (profiles ?? []) as ProfileRow[]) {
    const chatId = chatOf.get(p.id)
    if (!chatId && !(p.email_reminders && resendKey)) continue
    const now = localNow(p.reminder_tz)
    if (!isDue(p, now)) continue

    // Mark the day first: if sending fails, the user is not spammed every 15 minutes.
    // The update only matches while the day is not marked yet, so when two runs overlap
    // only one of them gets the row back and sends the message.
    const { data: claimed, error: markError } = await db
      .from('profiles')
      .update({ last_reminded_on: now.date })
      .eq('id', p.id)
      .or(`last_reminded_on.is.null,last_reminded_on.neq.${now.date}`)
      .select('id')
    if (markError) {
      console.error('Could not mark reminder as sent', p.id, markError.message)
      continue
    }
    if (!claimed || claimed.length === 0) continue

    const { data, error: debtsError } = await db
      .from('debts')
      .select('id, title, due_date, original_amount, currency, payments(amount)')
      .eq('user_id', p.id)
      .eq('status', 'ACTIVE')
      .eq('direction', 'OWED')
      .lte('due_date', addDays(now.date, p.reminder_days))
      .order('due_date')
    if (debtsError) {
      console.error('Could not load debts', p.id, debtsError.message)
      continue
    }
    const open = ((data ?? []) as DebtRow[])
      .map((d) => ({ ...d, left: (toTiyn(d.original_amount) - (d.payments ?? []).reduce((a, x) => a + toTiyn(x.amount), 0)) / 100 }))
      .filter((d) => d.left > 0)
    if (open.length === 0) continue

    const lines = open.map((d) => `${d.due_date < now.date ? '🔴' : '🟡'} ${escapeHtml(d.title)} — ${d.left.toLocaleString('ru-RU')} ${SYMBOL[d.currency] ?? d.currency}, срок ${d.due_date}`)

    if (chatId) {
      const ok = await sendTelegram(chatId, `<b>Kvit: сроки в ближайшие ${p.reminder_days} дн.</b>\n\n${lines.join('\n')}`, { text: 'Открыть Kvit', url: `${site}/debts` })
      if (ok) telegram++
    }
    if (p.email_reminders && resendKey) {
      const { data: u, error: userError } = await db.auth.admin.getUserById(p.id)
      if (userError) console.error('Could not load user e-mail', p.id, userError.message)
      const to = u?.user?.email
      if (!to) continue
      try {
        const res = await fetch('https://api.resend.com/emails', {
          method: 'POST',
          headers: { Authorization: `Bearer ${resendKey}`, 'Content-Type': 'application/json' },
          body: JSON.stringify({
            from: Deno.env.get('MAIL_FROM') ?? 'Kvit <onboarding@resend.dev>',
            to,
            subject: `Kvit: ${open.length} срок(а) в ближайшие ${p.reminder_days} дн.`,
            html: `<p>Здравствуйте, ${escapeHtml(p.display_name)}!</p><p>${lines.join('<br>')}</p><p><a href="${site}/debts">Открыть Kvit</a></p>`,
          }),
        })
        if (res.ok) email++
        else console.error('Resend failed', res.status, await res.text())
      } catch (e) {
        // keep going: the other users still get their reminders
        console.error('Resend failed', p.id, e instanceof Error ? e.message : e)
      }
    }
  }
  return json({ telegram, email })
})
