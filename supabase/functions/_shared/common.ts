// Helpers shared by the Kvit Edge Functions. Runs on Supabase servers (Deno), never in the browser,
// so the service role key and the bot / AI keys stay secret.
import { createClient, type SupabaseClient } from 'npm:@supabase/supabase-js@2'

export const cors = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
}

export function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { ...cors, 'Content-Type': 'application/json' } })
}

export function admin(): SupabaseClient {
  return createClient(Deno.env.get('SUPABASE_URL')!, Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!, { auth: { persistSession: false } })
}

// Returns the signed-in user for a request from the site, or null when the token is missing or invalid.
export async function userFrom(req: Request, db: SupabaseClient): Promise<{ id: string } | null> {
  const token = (req.headers.get('Authorization') ?? '').replace(/^Bearer\s+/i, '')
  if (!token) return null
  const { data, error } = await db.auth.getUser(token)
  if (error || !data.user) return null
  return { id: data.user.id }
}

// Public address of the site for links in Telegram messages and e-mails.
// The fallback keeps links working when the SITE_URL secret is missing or still holds the example value.
export function siteUrl(): string {
  const url = Deno.env.get('SITE_URL')?.trim() ?? ''
  if (!url || url.includes('your-site')) return 'https://kvit-best-project.vercel.app'
  return url.replace(/\/+$/, '')
}

// button: optional link button shown under the message
export async function sendTelegram(chatId: number | string, text: string, button?: { text: string; url: string }): Promise<boolean> {
  const token = Deno.env.get('TELEGRAM_BOT_TOKEN')
  if (!token) return false
  const body: Record<string, unknown> = { chat_id: chatId, text, parse_mode: 'HTML', disable_web_page_preview: true }
  if (button) body.reply_markup = { inline_keyboard: [[button]] }
  const res = await fetch(`https://api.telegram.org/bot${token}/sendMessage`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!res.ok) console.error('Telegram sendMessage failed', res.status, await res.text())
  return res.ok
}

export const escapeHtml = (s: string) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
