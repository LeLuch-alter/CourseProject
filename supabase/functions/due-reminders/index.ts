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

// The reminder is due once the chosen time has passed today, on a day the user selected,
// and nothing was sent yet today. ">=" instead of "==" means a missed cron run is caught up later.
function isDue(p: ProfileRow, now: { date: string; minutes: number; weekday: number }): boolean {
  if (p.last_reminded_on === now.date) return false
  const [h, m] = p.reminder_time.split(':').map(Number)
  if (now.minutes < h * 60 + m) return false
  if (p.reminder_mode === 'weekdays') return p.reminder_weekdays.includes(now.weekday)
  if (p.reminder_mode === 'dates') return p.reminder_dates.includes(now.date)
  return true
}

Deno.serve(async (req) => {
  if (req.headers.get('x-cron-secret') !== Deno.env.get('CRON_SECRET')) return new Response('forbidden', { status: 403 })
  const db = admin()
  const resendKey = Deno.env.get('RESEND_API_KEY')
  const site = siteUrl()

  const { data: profiles, error } = await db
    .from('profiles')
    .select('id, display_name, reminder_days, email_reminders, reminder_time, reminder_tz, reminder_mode, reminder_weekdays, reminder_dates, last_reminded_on')
  if (error) return json({ error: error.message }, 500)
  const { data: links } = await db.from('telegram_links').select('user_id, chat_id').not('chat_id', 'is', null)
  const chatOf = new Map((links ?? []).map((l) => [l.user_id as string, l.chat_id as number]))

  let telegram = 0, email = 0
  for (const p of (profiles ?? []) as ProfileRow[]) {
    const chatId = chatOf.get(p.id)
    if (!chatId && !(p.email_reminders && resendKey)) continue
    const now = localNow(p.reminder_tz)
    if (!isDue(p, now)) continue

    // Mark the day first: if sending fails, the user is not spammed every 15 minutes.
    const { error: markError } = await db.from('profiles').update({ last_reminded_on: now.date }).eq('id', p.id)
    if (markError) {
      console.error('Could not mark reminder as sent', p.id, markError.message)
      continue
    }

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
      .map((d) => ({ ...d, left: Number(d.original_amount) - d.payments.reduce((a, x) => a + Number(x.amount), 0) }))
      .filter((d) => d.left > 0)
    if (open.length === 0) continue

    const lines = open.map((d) => `${d.due_date < now.date ? '🔴' : '🟡'} ${escapeHtml(d.title)} — ${d.left.toLocaleString('ru-RU')} ${SYMBOL[d.currency] ?? d.currency}, срок ${d.due_date}`)

    if (chatId) {
      const ok = await sendTelegram(chatId, `<b>Kvit: сроки в ближайшие ${p.reminder_days} дн.</b>\n\n${lines.join('\n')}`, { text: 'Открыть Kvit', url: `${site}/debts` })
      if (ok) telegram++
    }
    if (p.email_reminders && resendKey) {
      const { data: u } = await db.auth.admin.getUserById(p.id)
      const to = u?.user?.email
      if (!to) continue
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
    }
  }
  return json({ telegram, email })
})
