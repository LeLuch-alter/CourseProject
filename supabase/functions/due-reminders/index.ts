// Supabase Edge Function: e-mails users about debts due within their reminder window.
// Runs on the server, so it may use the service_role key (never put that key in the client).
// Deploy:  supabase functions deploy due-reminders --no-verify-jwt
// Secrets: supabase secrets set RESEND_API_KEY=... SITE_URL=https://your-site
// Schedule (SQL editor, after pg_cron + pg_net are enabled):
//   select cron.schedule('kvit-due-emails', '15 6 * * *', $$
//     select net.http_post('https://<project>.supabase.co/functions/v1/due-reminders',
//                          '{}'::jsonb, '{"Content-Type":"application/json"}'::jsonb) $$);
import { createClient } from 'npm:@supabase/supabase-js@2'

Deno.serve(async () => {
  const supabase = createClient(Deno.env.get('SUPABASE_URL')!, Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!)
  const resendKey = Deno.env.get('RESEND_API_KEY')
  const site = Deno.env.get('SITE_URL') ?? ''
  if (!resendKey) return new Response('RESEND_API_KEY is not set', { status: 500 })

  const { data: profiles, error } = await supabase.from('profiles').select('id, display_name, reminder_days').eq('email_reminders', true)
  if (error) return new Response(error.message, { status: 500 })

  let sent = 0
  for (const p of profiles ?? []) {
    const { data: user } = await supabase.auth.admin.getUserById(p.id)
    const email = user?.user?.email
    if (!email) continue
    const until = new Date(Date.now() + p.reminder_days * 86400000).toISOString().slice(0, 10)
    const { data: debts } = await supabase
      .from('debts')
      .select('id, title, due_date, original_amount, payments(amount)')
      .eq('user_id', p.id)
      .eq('status', 'ACTIVE')
      .eq('direction', 'OWED')
      .lte('due_date', until)
    const open = (debts ?? []).filter((d) => d.original_amount > (d.payments as Array<{ amount: number }>).reduce((a, x) => a + Number(x.amount), 0))
    if (open.length === 0) continue

    const rows = open.map((d) => `<li>${d.title} — до ${d.due_date}</li>`).join('')
    const res = await fetch('https://api.resend.com/emails', {
      method: 'POST',
      headers: { Authorization: `Bearer ${resendKey}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({
        from: 'Kvit <onboarding@resend.dev>',
        to: email,
        subject: `Kvit: ${open.length} срок(а) в ближайшие ${p.reminder_days} дн.`,
        html: `<p>Здравствуйте, ${p.display_name}!</p><ul>${rows}</ul><p><a href="${site}/debts">Открыть Kvit</a></p>`,
      }),
    })
    if (res.ok) sent++
  }
  return new Response(JSON.stringify({ sent }), { headers: { 'Content-Type': 'application/json' } })
})
