// Telegram bot for Kvit.
//  1. POST {action:"link"} from the site (with the user's token): creates a one-time code
//     and returns https://t.me/<bot>?start=<code>.
//  2. POST from Telegram (webhook, checked by the secret header): "/start <code>" links the chat.
// Deploy with --no-verify-jwt because Telegram does not send a Supabase token;
// the site request is verified inside instead.
import { admin, cors, json, sendTelegram, userFrom } from '../_shared/common.ts'

let botUsername: string | null = null
async function getBotUsername(): Promise<string> {
  if (botUsername) return botUsername
  const res = await fetch(`https://api.telegram.org/bot${Deno.env.get('TELEGRAM_BOT_TOKEN')}/getMe`)
  const body = await res.json()
  if (!body.ok) throw new Error('Telegram getMe failed')
  botUsername = body.result.username as string
  return botUsername
}

function randomCode(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(9))
  return btoa(String.fromCharCode(...bytes)).replace(/[+/=]/g, '').slice(0, 12)
}

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers: cors })
  if (req.method !== 'POST') return json({ error: 'method' }, 405)
  const db = admin()

  // ---- webhook from Telegram
  const secret = req.headers.get('X-Telegram-Bot-Api-Secret-Token')
  if (secret !== null) {
    if (secret !== Deno.env.get('TELEGRAM_WEBHOOK_SECRET')) return new Response('forbidden', { status: 403 })
    const update = await req.json().catch(() => null)
    const msg = update?.message
    const text: string = msg?.text ?? ''
    const chatId: number | undefined = msg?.chat?.id
    if (!chatId) return new Response('ok')
    // the code arrives with /start from the deep link, or as a plain message when
    // Telegram Desktop opens an existing chat and drops the start parameter
    const m = text.trim().match(/^(?:\/start\s+)?([A-Za-z0-9]{6,20})$/)
    if (!m) {
      await sendTelegram(chatId, 'Это бот Kvit. Откройте сайт → Настройки → «Подключить Telegram» и отправьте сюда код, который покажет сайт.')
      return new Response('ok')
    }
    const { data: row } = await db.from('telegram_links').select('user_id, code_until').eq('link_code', m[1]).maybeSingle()
    if (!row || !row.code_until || new Date(row.code_until) < new Date()) {
      await sendTelegram(chatId, 'Код устарел. Нажмите «Подключить Telegram» на сайте ещё раз.')
      return new Response('ok')
    }
    // one chat belongs to one account: detach it from any previous account first
    await db.from('telegram_links').update({ chat_id: null }).eq('chat_id', chatId)
    await db.from('telegram_links').update({ chat_id: chatId, link_code: null, code_until: null, linked_at: new Date().toISOString() }).eq('user_id', row.user_id)
    await sendTelegram(chatId, '✅ Kvit подключён. Сюда будут приходить напоминания о сроках долгов.')
    return new Response('ok')
  }

  // ---- request from the site
  const user = await userFrom(req, db)
  if (!user) return json({ error: 'unauthorized' }, 401)
  const body = await req.json().catch(() => ({}))
  if (body?.action !== 'link') return json({ error: 'action' }, 400)
  if (!Deno.env.get('TELEGRAM_BOT_TOKEN')) return json({ error: 'not_configured' }, 500)

  const code = randomCode()
  const until = new Date(Date.now() + 15 * 60 * 1000).toISOString()
  const { error } = await db.from('telegram_links').upsert({ user_id: user.id, link_code: code, code_until: until }, { onConflict: 'user_id' })
  if (error) return json({ error: error.message }, 500)
  return json({ url: `https://t.me/${await getBotUsername()}?start=${code}`, code })
})
