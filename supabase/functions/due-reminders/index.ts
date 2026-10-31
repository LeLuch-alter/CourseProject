// Daily reminders about debts that are due soon: Telegram (if linked) and e-mail (if enabled).
// Called once a day by pg_cron with the CRON_SECRET header, so outsiders cannot trigger it.
// Deploy:  supabase functions deploy due-reminders --no-verify-jwt
import { admin, escapeHtml, json, sendTelegram } from '../_shared/common.ts'

interface DebtRow {
  id: number
  title: string
  due_date: string
  original_amount: number
  currency: string
  payments: Array<{ amount: number }>
}

const SYMBOL: Record<string, string> = { KZT: '₸', USD: '$', EUR: '€', RUB: '₽' }

Deno.serve(async (req) => {
  if (req.headers.get('x-cron-secret') !== Deno.env.get('CRON_SECRET')) return new Response('forbidden', { status: 403 })
  const db = admin()
  const resendKey = Deno.env.get('RESEND_API_KEY')
  const site = Deno.env.get('SITE_URL') ?? ''

  const { data: profiles, error } = await db.from('profiles').select('id, display_name, reminder_days, email_reminders')
  if (error) return json({ error: error.message }, 500)
  const { data: links } = await db.from('telegram_links').select('user_id, chat_id').not('chat_id', 'is', null)
  const chatOf = new Map((links ?? []).map((l) => [l.user_id as string, l.chat_id as number]))

  let telegram = 0, email = 0
  for (const p of profiles ?? []) {
    const chatId = chatOf.get(p.id)
    if (!chatId && !(p.email_reminders && resendKey)) continue

    const until = new Date(Date.now() + p.reminder_days * 86400000).toISOString().slice(0, 10)
    const { data } = await db
      .from('debts')
      .select('id, title, due_date, original_amount, currency, payments(amount)')
      .eq('user_id', p.id)
      .eq('status', 'ACTIVE')
      .eq('direction', 'OWED')
      .lte('due_date', until)
      .order('due_date')
    const open = ((data ?? []) as DebtRow[])
      .map((d) => ({ ...d, left: Number(d.original_amount) - d.payments.reduce((a, x) => a + Number(x.amount), 0) }))
      .filter((d) => d.left > 0)
    if (open.length === 0) continue

    const today = new Date().toISOString().slice(0, 10)
    const lines = open.map((d) => `${d.due_date < today ? '🔴' : '🟡'} ${escapeHtml(d.title)} — ${d.left.toLocaleString('ru-RU')} ${SYMBOL[d.currency] ?? d.currency}, срок ${d.due_date}`)

    if (chatId) {
      const ok = await sendTelegram(chatId, `<b>Kvit: сроки в ближайшие ${p.reminder_days} дн.</b>\n\n${lines.join('\n')}\n\n<a href="${site}/debts">Открыть Kvit</a>`)
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
    }
  }
  return json({ telegram, email })
})
