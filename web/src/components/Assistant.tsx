import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { AnimatePresence, motion } from 'framer-motion'
import { Sparkles, X, Mic, MicOff, Send, Volume2, VolumeX, Check, MessageCircle, Copy, RotateCcw } from 'lucide-react'
import { useI18n } from '../lib/i18n'
import { useData } from '../lib/data'
import { useAuth } from '../lib/auth'
import { useToast } from '../lib/toast'
import { supabase } from '../lib/supabase'
import { formatMoney, parseAmount, CURRENCIES, type Currency } from '../lib/money'
import { todayIso } from '../lib/dates'
import { payoffPlan, remainingFor, validateDebt, validatePayment, type Strategy } from '../lib/calc'
import { buildSnapshot, planForModel } from '../lib/assistantContext'
import { canListen, canSpeak, listen, speak, stopSpeaking } from '../lib/speech'
import { createDebt, createPayment, syncStatus } from '../api/debts'
import { getProfiles, listFriendships } from '../api/social'
import type { Profile } from '../api/types'

type Part = { text?: string; functionCall?: { name: string; args: Record<string, unknown>; id?: string }; functionResponse?: { name: string; response: Record<string, unknown>; id?: string }; thoughtSignature?: string }
type Content = { role: 'user' | 'model'; parts: Part[] }

interface DebtDraft { title: string; counterparty: string; amount: number; currency: Currency; due_date: string; direction: 'OWED' | 'LENT'; notes: string }
interface PaymentDraft { debt_id: number; amount: number; payment_date: string; notes: string }
interface MessageDraft { friend_username: string; text: string }
type Card =
  | { kind: 'debt'; data: DebtDraft; state: 'pending' | 'saved' | 'cancelled' }
  | { kind: 'payment'; data: PaymentDraft; state: 'pending' | 'saved' | 'cancelled' }
  | { kind: 'message'; data: MessageDraft }
interface Msg { id: number; role: 'user' | 'assistant'; text?: string; card?: Card; tone?: 'error' }

const PAGES: Record<string, string> = { dashboard: '/', debts: '/debts', payments: '/payments', reports: '/reports', calendar: '/calendar', friends: '/friends', loans: '/loans', profile: '/profile', settings: '/settings' }
let seq = 1

// Keeps the history short without separating a function call from its response.
function trimHistory(c: Content[]): Content[] {
  if (c.length <= 24) return c
  let start = c.length - 24
  while (start < c.length && !(c[start].role === 'user' && c[start].parts.some((p) => p.text))) start++
  return c.slice(start)
}

export function Assistant() {
  const { t, lang } = useI18n()
  const { debts, payments } = useData()
  const { user } = useAuth()
  const toast = useToast()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const [msgs, setMsgs] = useState<Msg[]>([])
  const [input, setInput] = useState('')
  const [busy, setBusy] = useState(false)
  const [listening, setListening] = useState(false)
  const [tts, setTts] = useState(() => {
    try {
      return localStorage.getItem('kvit.tts') === '1'
    } catch {
      return false
    }
  })
  const [friends, setFriends] = useState<Profile[]>([])
  const history = useRef<Content[]>([])
  const stopRef = useRef<(() => void) | null>(null)
  const endRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'j') {
        e.preventDefault()
        setOpen((v) => !v)
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  useEffect(() => {
    if (!open || !user) return
    listFriendships()
      .then(async (fs) => {
        const ids = fs.filter((f) => f.status === 'ACCEPTED').map((f) => (f.requester_id === user.id ? f.addressee_id : f.requester_id))
        setFriends(Object.values(await getProfiles(ids)))
      })
      .catch(() => setFriends([]))
  }, [open, user])

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [msgs.length, busy])

  useEffect(() => {
    if (!open) {
      stopRef.current?.()
      stopSpeaking()
    }
  }, [open])

  const add = useCallback((m: Omit<Msg, 'id'>) => setMsgs((xs) => [...xs, { ...m, id: seq++ }]), [])
  const setCard = (id: number, card: Card) => setMsgs((xs) => xs.map((m) => (m.id === id ? { ...m, card } : m)))

  // Turns one function call from the model into a card, a page change or a computed plan.
  function handleCall(name: string, args: Record<string, unknown>): Record<string, unknown> {
    if (name === 'compute_plan') {
      const budget = Number(args.monthly_budget)
      const strategy = (['deadline', 'snowball', 'avalanche'].includes(String(args.strategy)) ? args.strategy : 'deadline') as Strategy
      const plan = payoffPlan(debts, payments, budget, strategy)
      return { result: planForModel(plan) ?? 'no open debts in tenge or budget is zero' }
    }
    if (name === 'open_page') {
      const path = PAGES[String(args.page)]
      if (path) {
        navigate(path)
        return { opened: args.page }
      }
      return { error: 'unknown page' }
    }
    if (name === 'propose_debt') {
      const cur = String(args.currency ?? 'KZT') as Currency
      add({ role: 'assistant', card: { kind: 'debt', state: 'pending', data: { title: String(args.title ?? ''), counterparty: String(args.counterparty ?? ''), amount: Number(args.amount) || 0, currency: CURRENCIES.includes(cur) ? cur : 'KZT', due_date: String(args.due_date ?? ''), direction: args.direction === 'LENT' ? 'LENT' : 'OWED', notes: String(args.notes ?? '') } } })
      return { status: 'shown to the user for confirmation; not saved yet' }
    }
    if (name === 'propose_payment') {
      add({ role: 'assistant', card: { kind: 'payment', state: 'pending', data: { debt_id: Number(args.debt_id), amount: Number(args.amount) || 0, payment_date: String(args.payment_date ?? todayIso()), notes: String(args.notes ?? '') } } })
      return { status: 'shown to the user for confirmation; not saved yet' }
    }
    if (name === 'draft_message') {
      add({ role: 'assistant', card: { kind: 'message', data: { friend_username: String(args.friend_username ?? '').replace(/^@/, ''), text: String(args.text ?? '') } } })
      return { status: 'draft shown to the user' }
    }
    return { error: 'unknown function' }
  }

  async function ask(text: string) {
    const q = text.trim()
    if (!q || busy) return
    add({ role: 'user', text: q })
    setInput('')
    setBusy(true)
    history.current = trimHistory([...history.current, { role: 'user', parts: [{ text: q }] }])
    const snapshot = buildSnapshot(debts, payments, friends)
    try {
      for (let round = 0; round < 3; round++) {
        const { data, error } = await supabase.functions.invoke('assistant', { body: { contents: history.current, snapshot, lang, today: todayIso() } })
        if (error) throw error
        const res = data as { parts?: Part[]; error?: string }
        if (res.error) {
          const key = res.error === 'quota' ? 'ai.quota' : res.error === 'limit' ? 'ai.limit' : res.error === 'not_configured' ? 'ai.notConfigured' : res.error === 'busy' ? 'ai.busy' : 'ai.failed'
          add({ role: 'assistant', text: t(key), tone: 'error' })
          if (res.error === 'quota') toast.error(t('ai.quotaToast'))
          history.current = history.current.slice(0, -1)
          break
        }
        const parts = res.parts ?? []
        history.current.push({ role: 'model', parts })
        const said = parts.map((p) => p.text ?? '').join('').trim()
        if (said) {
          add({ role: 'assistant', text: said })
          if (tts) speak(said, lang)
        }
        const calls = parts.filter((p) => p.functionCall).map((p) => p.functionCall!)
        if (calls.length === 0) break
        const responses = calls.map((c) => ({ functionResponse: { name: c.name, response: handleCall(c.name, c.args ?? {}), ...(c.id ? { id: c.id } : {}) } }))
        history.current.push({ role: 'user', parts: responses })
        // plans need a second round so the model can explain the computed result
        if (!calls.some((c) => c.name === 'compute_plan')) break
      }
    } catch (e) {
      add({ role: 'assistant', text: t('ai.failed'), tone: 'error' })
      console.error(e)
    } finally {
      setBusy(false)
    }
  }

  function toggleMic() {
    if (listening) {
      stopRef.current?.()
      return
    }
    stopSpeaking()
    setListening(true)
    stopRef.current = listen(
      lang,
      (live) => setInput(live),
      (final) => {
        setListening(false)
        stopRef.current = null
        if (final) void ask(final)
      },
      (code) => {
        setListening(false)
        if (code !== 'no-speech' && code !== 'aborted') toast.error(t(code === 'not-allowed' ? 'ai.micDenied' : 'ai.micFailed'))
      },
    )
  }

  const suggestions = useMemo(() => [t('ai.s1'), t('ai.s2'), t('ai.s3'), t('ai.s4'), t('ai.s5'), t('ai.s6')], [t])

  return (
    <>
      <motion.button
        onClick={() => setOpen((v) => !v)}
        whileHover={{ scale: 1.06 }}
        whileTap={{ scale: 0.94 }}
        className="fixed z-[70] bottom-5 right-5 w-14 h-14 rounded-full bg-ink text-bg flex items-center justify-center border border-line"
        aria-label={t('ai.title')}
        title={`${t('ai.title')} (Ctrl+J)`}
      >
        <AnimatePresence mode="wait" initial={false}>
          <motion.span key={open ? 'x' : 's'} initial={{ rotate: -90, opacity: 0 }} animate={{ rotate: 0, opacity: 1 }} exit={{ rotate: 90, opacity: 0 }} transition={{ duration: 0.15 }}>
            {open ? <X size={22} /> : <Sparkles size={22} />}
          </motion.span>
        </AnimatePresence>
        {!open && <span className="absolute inset-0 rounded-full border border-ink/40 animate-ping pointer-events-none" style={{ animationDuration: '3s' }} />}
      </motion.button>

      <AnimatePresence>
        {open && (
          <motion.div
            initial={{ opacity: 0, y: 20, scale: 0.97 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: 20, scale: 0.97 }}
            transition={{ type: 'spring', stiffness: 380, damping: 32 }}
            className="fixed z-[75] left-2 right-2 top-16 bottom-24 sm:left-auto sm:right-5 sm:top-auto sm:bottom-24 sm:w-[420px] sm:h-[620px] sm:max-h-[calc(100vh-8rem)] glass bg-surface/95 flex flex-col overflow-hidden"
            role="dialog"
            aria-label={t('ai.title')}
          >
            <div className="flex items-center gap-2 px-4 h-14 border-b border-line">
              <Sparkles size={17} className="text-accent2" />
              <div className="flex-1 min-w-0">
                <div className="text-sm">{t('ai.title')}</div>
                <div className="font-mono text-[10px] text-faint uppercase tracking-wide">{t('ai.subtitle')}</div>
              </div>
              {canSpeak() && (
                <button
                  className="btn-icon btn-ghost h-8 w-8"
                  onClick={() => {
                    const v = !tts
                    setTts(v)
                    if (!v) stopSpeaking()
                    try {
                      localStorage.setItem('kvit.tts', v ? '1' : '0')
                    } catch {
                      /* ignore */
                    }
                  }}
                  title={tts ? t('ai.ttsOff') : t('ai.ttsOn')}
                  aria-pressed={tts}
                >
                  {tts ? <Volume2 size={16} /> : <VolumeX size={16} />}
                </button>
              )}
              {msgs.length > 0 && (
                <button
                  className="btn-icon btn-ghost h-8 w-8"
                  onClick={() => {
                    setMsgs([])
                    history.current = []
                    stopSpeaking()
                  }}
                  title={t('ai.reset')}
                >
                  <RotateCcw size={15} />
                </button>
              )}
            </div>

            <div className="flex-1 overflow-y-auto p-4 space-y-3">
              {msgs.length === 0 ? (
                <div className="h-full flex flex-col justify-center">
                  <div className="text-lg leading-snug">{t('ai.hello')}</div>
                  <p className="text-sm text-muted mt-1">{t('ai.helloText')}</p>
                  <div className="flex flex-wrap gap-2 mt-5">
                    {suggestions.map((s, i) => (
                      <motion.button key={s} initial={{ opacity: 0, y: 6 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.05 * i }} onClick={() => void ask(s)} className="text-left text-sm px-3 py-2 rounded-lg border border-line hover:bg-raised hover:border-ink/40 transition">
                        {s}
                      </motion.button>
                    ))}
                  </div>
                </div>
              ) : (
                msgs.map((m) => (
                  <motion.div key={m.id} initial={{ opacity: 0, y: 6 }} animate={{ opacity: 1, y: 0 }} className={`flex ${m.role === 'user' ? 'justify-end' : 'justify-start'}`}>
                    {m.card ? (
                      <ActionCard card={m.card} friends={friends} onChange={(c) => setCard(m.id, c)} onClose={() => setOpen(false)} />
                    ) : (
                      <div className={`max-w-[88%] px-3.5 py-2 rounded-2xl text-sm whitespace-pre-wrap break-words ${m.role === 'user' ? 'bg-ink text-bg rounded-br-md' : m.tone === 'error' ? 'border border-bad/40 text-bad rounded-bl-md' : 'bg-raised border border-line rounded-bl-md'}`}>
                        {(m.text ?? '').replace(/\*\*(.+?)\*\*/g, '$1')}
                      </div>
                    )}
                  </motion.div>
                ))
              )}
              {busy && (
                <div className="flex gap-1 px-3 py-2">
                  {[0, 1, 2].map((i) => (
                    <motion.span key={i} className="w-1.5 h-1.5 rounded-full bg-muted" animate={{ opacity: [0.3, 1, 0.3] }} transition={{ duration: 1, repeat: Infinity, delay: i * 0.2 }} />
                  ))}
                </div>
              )}
              <div ref={endRef} />
            </div>

            <form
              onSubmit={(e: FormEvent) => {
                e.preventDefault()
                void ask(input)
              }}
              className="p-3 border-t border-line"
            >
              <div className="flex items-center gap-2">
                {canListen() && (
                  <button type="button" onClick={toggleMic} className={`btn-icon h-11 w-11 shrink-0 rounded-full border ${listening ? 'bg-bad text-white border-bad' : 'border-line hover:bg-raised'}`} aria-label={listening ? t('ai.micStop') : t('ai.mic')} title={listening ? t('ai.micStop') : t('ai.mic')}>
                    {listening ? <MicOff size={17} /> : <Mic size={17} />}
                  </button>
                )}
                <input className="input" placeholder={listening ? t('ai.listening') : t('ai.placeholder')} value={input} onChange={(e) => setInput(e.target.value)} maxLength={1000} disabled={busy} />
                <button className="btn-primary btn-icon h-11 w-11 shrink-0 rounded-full" disabled={busy || !input.trim()} aria-label={t('common.send')}>
                  <Send size={17} />
                </button>
              </div>
              <div className="text-[10px] text-faint mt-2 text-center">{t('ai.disclaimer')}</div>
            </form>
          </motion.div>
        )}
      </AnimatePresence>
    </>
  )
}

// ---------------------------------------------------------------- confirmation cards

function ActionCard({ card, friends, onChange, onClose }: { card: Card; friends: Profile[]; onChange: (c: Card) => void; onClose: () => void }) {
  if (card.kind === 'debt') return <DebtCard card={card} onChange={onChange} />
  if (card.kind === 'payment') return <PaymentCard card={card} onChange={onChange} />
  return <MessageCard draft={card.data} friends={friends} onClose={onClose} />
}

function DebtCard({ card, onChange }: { card: Extract<Card, { kind: 'debt' }>; onChange: (c: Card) => void }) {
  const { t } = useI18n()
  const { reload } = useData()
  const toast = useToast()
  const [d, setD] = useState(card.data)
  const [err, setErr] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const done = card.state !== 'pending'

  async function save() {
    const amount = parseAmount(String(d.amount))
    const errs = validateDebt({ title: d.title, creditor: d.counterparty, amount, dueDate: d.due_date, rate: '' })
    if (Object.keys(errs).length) {
      setErr(Object.values(errs).map((k) => t(`err.${k}`)).join('. '))
      return
    }
    setSaving(true)
    try {
      await createDebt({ title: d.title.trim(), creditor_name: d.counterparty.trim(), original_amount: amount!, interest_rate: null, due_date: d.due_date || null, status: 'ACTIVE', notes: d.notes.trim() || null, currency: d.currency, direction: d.direction })
      await reload()
      onChange({ ...card, data: d, state: 'saved' })
      toast.success(t('common.saved'), d.title)
    } catch (e) {
      setErr(e instanceof Error ? e.message : t('common.saveFailed'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="w-full card p-4 text-sm">
      <div className="eyebrow mb-2">{d.direction === 'OWED' ? t('ai.cardOwed') : t('ai.cardLent')}</div>
      <div className="space-y-2">
        <input className="input h-9" value={d.title} onChange={(e) => setD({ ...d, title: e.target.value })} disabled={done} aria-label={t('form.title')} />
        <input className="input h-9" value={d.counterparty} onChange={(e) => setD({ ...d, counterparty: e.target.value })} disabled={done} aria-label={t('form.creditor')} />
        <div className="grid grid-cols-[1fr_auto] gap-2">
          <input className="input h-9 num" inputMode="decimal" value={d.amount} onChange={(e) => setD({ ...d, amount: Number(e.target.value.replace(',', '.')) || 0 })} disabled={done} aria-label={t('form.amount')} />
          <span className="h-9 px-3 flex items-center border border-line rounded-lg font-mono text-xs">{d.currency}</span>
        </div>
        <input className="input h-9" type="date" value={d.due_date} onChange={(e) => setD({ ...d, due_date: e.target.value })} disabled={done} aria-label={t('form.dueDate')} />
        <div className="flex gap-1">
          {(['OWED', 'LENT'] as const).map((dir) => (
            <button key={dir} type="button" disabled={done} onClick={() => setD({ ...d, direction: dir })} className={`flex-1 h-8 rounded text-xs border ${d.direction === dir ? 'bg-ink text-bg border-ink' : 'border-line text-muted'}`}>
              {t(dir === 'OWED' ? 'debts.owedBadge' : 'debts.lentBadge')}
            </button>
          ))}
        </div>
      </div>
      {err && <div className="text-xs text-bad mt-2">{err}</div>}
      <CardButtons state={card.state} saving={saving} onSave={() => void save()} onCancel={() => onChange({ ...card, state: 'cancelled' })} />
    </div>
  )
}

function PaymentCard({ card, onChange }: { card: Extract<Card, { kind: 'payment' }>; onChange: (c: Card) => void }) {
  const { t, locale } = useI18n()
  const { debts, payments, reload } = useData()
  const toast = useToast()
  const [p, setP] = useState(card.data)
  const [err, setErr] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const debt = debts.find((x) => x.id === p.debt_id)
  const remaining = debt ? remainingFor(debt, payments) : 0
  const done = card.state !== 'pending'

  async function save() {
    if (!debt) {
      setErr(t('ai.noDebt'))
      return
    }
    const amount = parseAmount(String(p.amount))
    const errs = validatePayment(amount, p.payment_date, remaining, debt.status)
    if (Object.keys(errs).length) {
      setErr(Object.values(errs).map((k) => t(`err.${k}`)).join('. '))
      return
    }
    setSaving(true)
    try {
      await createPayment({ debt_id: debt.id, amount: amount!, payment_date: p.payment_date, notes: p.notes.trim() || null })
      await syncStatus(debt, Math.max(0, Math.round((remaining - amount!) * 100) / 100))
      await reload()
      onChange({ ...card, data: p, state: 'saved' })
      toast.success(t('common.saved'), formatMoney(amount!, locale, debt.currency))
    } catch (e) {
      setErr(e instanceof Error ? e.message : t('common.saveFailed'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="w-full card p-4 text-sm">
      <div className="eyebrow mb-2">{t('ai.cardPayment')}</div>
      {debt ? (
        <div className="mb-2">
          <div>{debt.title}</div>
          <div className="text-xs text-muted">
            {debt.creditor_name} · {t('form.payment.remaining', formatMoney(remaining, locale, debt.currency))}
          </div>
        </div>
      ) : (
        <div className="text-bad text-xs mb-2">{t('ai.noDebt')}</div>
      )}
      <div className="grid grid-cols-2 gap-2">
        <input className="input h-9 num" inputMode="decimal" value={p.amount} onChange={(e) => setP({ ...p, amount: Number(e.target.value.replace(',', '.')) || 0 })} disabled={done} aria-label={t('form.amount')} />
        <input className="input h-9" type="date" max={todayIso()} value={p.payment_date} onChange={(e) => setP({ ...p, payment_date: e.target.value })} disabled={done} aria-label={t('form.date')} />
      </div>
      {err && <div className="text-xs text-bad mt-2">{err}</div>}
      <CardButtons state={card.state} saving={saving} onSave={() => void save()} onCancel={() => onChange({ ...card, state: 'cancelled' })} />
    </div>
  )
}

function MessageCard({ draft, friends, onClose }: { draft: MessageDraft; friends: Profile[]; onClose: () => void }) {
  const { t } = useI18n()
  const toast = useToast()
  const navigate = useNavigate()
  const friend = friends.find((f) => f.username.toLowerCase() === draft.friend_username.toLowerCase() || f.display_name.toLowerCase() === draft.friend_username.toLowerCase())
  return (
    <div className="w-full card p-4 text-sm">
      <div className="eyebrow mb-2">{t('ai.cardMessage')}{friend ? ` · ${friend.display_name}` : ''}</div>
      <div className="whitespace-pre-wrap border border-line rounded-lg p-3">{draft.text}</div>
      <div className="flex gap-2 mt-3">
        {friend && (
          <button
            className="btn-primary h-9 flex-1"
            onClick={() => {
              try {
                sessionStorage.setItem(`kvit.draft.${friend.id}`, draft.text)
              } catch {
                /* ignore */
              }
              navigate(`/friends/${friend.id}`)
              onClose()
            }}
          >
            <MessageCircle size={15} />
            {t('ai.openChat')}
          </button>
        )}
        <button className="btn-outline h-9 flex-1" onClick={() => void navigator.clipboard.writeText(draft.text).then(() => toast.success(t('common.copied')))}>
          <Copy size={15} />
          {t('common.copy')}
        </button>
      </div>
      {!friend && <div className="text-xs text-muted mt-2">{t('ai.noFriend', draft.friend_username)}</div>}
    </div>
  )
}

function CardButtons({ state, saving, onSave, onCancel }: { state: 'pending' | 'saved' | 'cancelled'; saving: boolean; onSave: () => void; onCancel: () => void }) {
  const { t } = useI18n()
  if (state === 'saved')
    return (
      <div className="flex items-center gap-1.5 text-good text-xs mt-3">
        <Check size={14} />
        {t('common.saved')}
      </div>
    )
  if (state === 'cancelled') return <div className="text-xs text-muted mt-3">{t('ai.cancelled')}</div>
  return (
    <div className="flex gap-2 mt-3">
      <button className="btn-ghost h-9 flex-1 border border-line" onClick={onCancel} disabled={saving}>
        {t('common.cancel')}
      </button>
      <button className="btn-primary h-9 flex-1" onClick={onSave} disabled={saving}>
        {saving ? t('common.loading') : t('common.save')}
      </button>
    </div>
  )
}
