import { useEffect, useRef, useState, type FormEvent } from 'react'
import { motion } from 'framer-motion'
import { Send, LogOut, Trash2, Check } from 'lucide-react'
import { useI18n } from '../lib/i18n'
import { useToast } from '../lib/toast'
import { supabase } from '../lib/supabase'
import { formatDate, formatDateTime } from '../lib/dates'
import { listRoomMessages, sendRoomMessage, leaveRoom, deleteRoom, createRoom, type Room, type RoomMessage } from '../api/extra'
import type { Profile } from '../api/types'
import { Avatar, Confirm, Field, Modal, Skeleton } from './ui'

export function GroupChat({ room, me, profiles, onLeft }: { room: Room & { members: string[] }; me: string; profiles: Record<string, Profile>; onLeft: () => void }) {
  const { t, locale } = useI18n()
  const toast = useToast()
  const [messages, setMessages] = useState<RoomMessage[]>([])
  const [text, setText] = useState('')
  const [loading, setLoading] = useState(true)
  const [confirm, setConfirm] = useState<'leave' | 'delete' | null>(null)
  const endRef = useRef<HTMLDivElement>(null)
  const owner = room.created_by === me

  useEffect(() => {
    let alive = true
    setLoading(true)
    listRoomMessages(room.id)
      .then((m) => {
        if (alive) {
          setMessages(m)
          setLoading(false)
        }
      })
      .catch((e) => toast.error(t('common.loadFailed'), String(e)))
    const ch = supabase
      .channel(`room-msgs-${room.id}`)
      .on('postgres_changes', { event: 'INSERT', schema: 'public', table: 'room_messages', filter: `room_id=eq.${room.id}` }, (p) => {
        const m = p.new as RoomMessage
        setMessages((xs) => (xs.some((x) => x.id === m.id) ? xs : [...xs, m]))
      })
      .subscribe()
    return () => {
      alive = false
      void supabase.removeChannel(ch)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [room.id])

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages.length])

  async function send(e: FormEvent) {
    e.preventDefault()
    const body = text.trim()
    if (!body) return
    setText('')
    try {
      const m = await sendRoomMessage(room.id, body)
      setMessages((xs) => (xs.some((x) => x.id === m.id) ? xs : [...xs, m]))
    } catch (err) {
      setText(body)
      toast.error(t('common.saveFailed'), err instanceof Error ? err.message : undefined)
    }
  }

  async function act() {
    try {
      if (confirm === 'delete') await deleteRoom(room.id)
      else await leaveRoom(room.id, me)
      onLeft()
    } catch (err) {
      toast.error(t('common.saveFailed'), err instanceof Error ? err.message : undefined)
    } finally {
      setConfirm(null)
    }
  }

  let lastDay = ''
  return (
    <>
      <div className="flex items-center gap-3 px-4 h-16 border-b border-line">
        <div className="flex -space-x-2">
          {room.members.slice(0, 4).map((id) => (
            <Avatar key={id} profile={profiles[id]} size={30} className="ring-2 ring-surface" />
          ))}
        </div>
        <div className="flex-1 min-w-0">
          <div className="truncate">{room.name}</div>
          <div className="font-mono text-[11px] text-faint truncate">{room.members.map((id) => profiles[id]?.display_name ?? '…').join(', ')}</div>
        </div>
        <button className="btn-ghost btn-icon h-9 w-9 text-muted" onClick={() => setConfirm(owner ? 'delete' : 'leave')} title={owner ? t('common.delete') : t('room.leave')}>
          {owner ? <Trash2 size={16} /> : <LogOut size={16} />}
        </button>
      </div>
      <div className="flex-1 overflow-y-auto p-4 space-y-1.5 max-h-[60vh]">
        {loading ? (
          <div className="space-y-2">
            {[0, 1, 2].map((i) => (
              <Skeleton key={i} className={`h-10 w-2/3 ${i % 2 ? 'ml-auto' : ''}`} />
            ))}
          </div>
        ) : messages.length === 0 ? (
          <div className="h-full flex items-center justify-center text-sm text-muted">{t('chat.empty')}</div>
        ) : (
          messages.map((m) => {
            const day = m.created_at.slice(0, 10),
              showDay = day !== lastDay
            lastDay = day
            const mine = m.sender_id === me
            return (
              <div key={m.id}>
                {showDay && <div className="text-center font-mono text-[11px] text-faint my-3">{formatDate(day, locale)}</div>}
                <motion.div initial={{ opacity: 0, y: 6 }} animate={{ opacity: 1, y: 0 }} className={`flex items-end gap-2 ${mine ? 'justify-end' : 'justify-start'}`}>
                  {!mine && <Avatar profile={profiles[m.sender_id]} size={24} />}
                  <div className={`max-w-[75%] px-3.5 py-2 rounded-2xl text-sm whitespace-pre-wrap break-words ${mine ? 'bg-ink text-bg rounded-br-md' : 'bg-raised border border-line rounded-bl-md'}`}>
                    {!mine && <div className="font-mono text-[10px] text-muted mb-0.5">{profiles[m.sender_id]?.display_name}</div>}
                    {m.body}
                    <div className={`text-[10px] mt-1 text-right ${mine ? 'text-bg/60' : 'text-faint'}`}>{formatDateTime(m.created_at, locale).split(',').pop()}</div>
                  </div>
                </motion.div>
              </div>
            )
          })
        )}
        <div ref={endRef} />
      </div>
      <form onSubmit={send} className="flex items-center gap-2 p-3 border-t border-line">
        <input className="input" placeholder={t('chat.placeholder')} value={text} onChange={(e) => setText(e.target.value)} maxLength={2000} />
        <motion.button whileTap={{ scale: 0.9 }} className="btn-primary btn-icon h-11 w-11 shrink-0 rounded-full" disabled={!text.trim()} aria-label={t('common.send')}>
          <Send size={18} />
        </motion.button>
      </form>
      <Confirm open={!!confirm} onClose={() => setConfirm(null)} onConfirm={() => void act()} title={confirm === 'delete' ? t('room.delete.title') : t('room.leave.title')} text={confirm === 'delete' ? t('room.delete.text') : t('room.leave.text')} confirmLabel={confirm === 'delete' ? t('common.delete') : t('room.leave')} cancelLabel={t('common.cancel')} />
    </>
  )
}

export function RoomForm({ open, onClose, friends, onCreated }: { open: boolean; onClose: () => void; friends: Profile[]; onCreated: (id: string) => void }) {
  const { t } = useI18n()
  const toast = useToast()
  const [name, setName] = useState('')
  const [picked, setPicked] = useState<string[]>([])
  const [busy, setBusy] = useState(false)
  const toggle = (id: string) => setPicked((xs) => (xs.includes(id) ? xs.filter((x) => x !== id) : [...xs, id]))

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (!name.trim() || picked.length === 0) return
    setBusy(true)
    try {
      const id = await createRoom(name.trim(), picked)
      onCreated(id)
      onClose()
    } catch (err) {
      toast.error(t('common.saveFailed'), err instanceof Error ? err.message : undefined)
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal open={open} onClose={onClose} title={t('room.new')}>
      <form onSubmit={submit} className="space-y-4">
        <Field label={t('room.name')}>
          <input className="input" value={name} onChange={(e) => setName(e.target.value)} maxLength={60} autoFocus />
        </Field>
        <Field label={t('split.members')}>
          <div className="flex flex-wrap gap-2">
            {friends.map((f) => {
              const on = picked.includes(f.id)
              return (
                <button type="button" key={f.id} onClick={() => toggle(f.id)} className={`flex items-center gap-2 h-9 pl-1 pr-3 rounded-full border transition ${on ? 'border-ink bg-ink text-bg' : 'border-line hover:bg-raised'}`}>
                  <Avatar profile={f} size={26} />
                  <span className="text-sm">{f.display_name}</span>
                  {on && <Check size={14} />}
                </button>
              )
            })}
          </div>
        </Field>
        <div className="flex justify-end gap-2">
          <button type="button" className="btn-ghost" onClick={onClose}>
            {t('common.cancel')}
          </button>
          <button className="btn-primary" disabled={busy || !name.trim() || picked.length === 0}>
            {t('room.create')}
          </button>
        </div>
      </form>
    </Modal>
  )
}
