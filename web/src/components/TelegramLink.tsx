import { useCallback, useEffect, useState } from 'react'
import { Send, Unlink, Copy } from 'lucide-react'
import { useI18n } from '../lib/i18n'
import { useAuth } from '../lib/auth'
import { useToast } from '../lib/toast'
import { supabase } from '../lib/supabase'

// Linking goes through the "telegram" Edge Function: it creates a one-time code and
// returns a t.me deep link. The bot token never reaches the browser.
export function TelegramLink() {
  const { t } = useI18n()
  const { user } = useAuth()
  const toast = useToast()
  const [busy, setBusy] = useState(false)
  const [linked, setLinked] = useState(false)
  const [code, setCode] = useState<string | null>(null)
  const refresh = useCallback(async () => {
    const { data } = await supabase.from('telegram_links').select('chat_id').eq('user_id', user!.id).maybeSingle()
    setLinked(!!data?.chat_id)
    if (data?.chat_id) setCode(null)
  }, [user])
  useEffect(() => {
    void refresh()
  }, [refresh])

  async function link() {
    setBusy(true)
    try {
      const { data, error } = await supabase.functions.invoke('telegram', { body: { action: 'link' } })
      if (error) throw error
      const res = data as { url: string; code: string }
      setCode(res.code)
      window.open(res.url, '_blank', 'noopener')
      toast.info(t('tg.opened'))
    } catch (e) {
      toast.error(t('tg.failed'), e instanceof Error ? e.message : undefined)
    } finally {
      setBusy(false)
    }
  }

  async function unlink() {
    setBusy(true)
    try {
      const { error } = await supabase.from('telegram_links').delete().eq('user_id', user!.id)
      if (error) throw error
      await refresh()
      toast.success(t('tg.unlinked'))
    } catch (e) {
      toast.error(t('common.saveFailed'), e instanceof Error ? e.message : undefined)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="flex flex-wrap items-center gap-3 mt-4 pt-4 hairline">
      <Send size={16} className="text-accent2" strokeWidth={1.5} />
      <div className="flex-1 min-w-[200px] text-sm">
        <div>{t('tg.title')}</div>
        <div className="text-xs text-muted">{linked ? t('tg.linked') : t('tg.hint')}</div>
      </div>
      {linked ? (
        <>
          <button className="btn-ghost h-9" onClick={() => void refresh()}>{t('common.refresh')}</button>
          <button className="btn-outline h-9" onClick={() => void unlink()} disabled={busy}><Unlink size={14} />{t('tg.unlink')}</button>
        </>
      ) : (
        <>
          <button className="btn-ghost h-9" onClick={() => void refresh()}>{t('tg.check')}</button>
          <button className="btn-primary h-9" onClick={() => void link()} disabled={busy}><Send size={14} />{t('tg.connect')}</button>
        </>
      )}
      {code && !linked && (
        <div className="w-full flex flex-wrap items-center gap-2 text-xs text-muted">
          <span>{t('tg.codeHint')}</span>
          <button className="chip h-7 font-mono text-ink normal-case tracking-normal hover:border-ink/50" onClick={() => void navigator.clipboard.writeText(code).then(() => toast.success(t('common.copied')))}>
            {code}
            <Copy size={12} />
          </button>
        </div>
      )}
    </div>
  )
}
