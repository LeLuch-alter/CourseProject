import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { AnimatePresence, motion } from 'framer-motion'
import { Search, UserPlus, Check, X, MessageCircle, HandCoins, HandHelping, Send, UserMinus, Users } from 'lucide-react'
import { useI18n } from '../lib/i18n'
import { useData } from '../lib/data'
import { balanceWith } from '../lib/calc'
import { formatMoney } from '../lib/money'
import { useAuth } from '../lib/auth'
import { useToast } from '../lib/toast'
import { supabase } from '../lib/supabase'
import { formatDate, formatDateTime } from '../lib/dates'
import { listFriendships, getProfiles, searchProfiles, sendFriendRequest, acceptFriendRequest, removeFriendship, listMessages, sendMessage, markRead, unreadCounts } from '../api/social'
import type { Friendship, Message, Profile } from '../api/types'
import { Avatar, Confirm, Empty, PageHeader, Skeleton } from '../components/ui'
import { LoanForm } from '../components/LoanForm'
import { SplitForm } from '../components/SplitForm'
import { GroupChat, RoomForm } from '../components/GroupChat'
import { listRooms, type Room } from '../api/extra'
import { Users2, Receipt } from 'lucide-react'

export function FriendsPage() {
  const { t, locale } = useI18n()
  const { user } = useAuth()
  const { debts, payments } = useData()
  const toast = useToast()
  const navigate = useNavigate()
  const { id: activeId, roomId } = useParams()
  const [params, setParams] = useSearchParams()
  const me = user!.id

  const [friendships, setFriendships] = useState<Friendship[]>([])
  const [profiles, setProfiles] = useState<Record<string, Profile>>({})
  const [unread, setUnread] = useState<Record<string, number>>({})
  const [loading, setLoading] = useState(true)
  const [q, setQ] = useState('')
  const [results, setResults] = useState<Profile[]>([])
  const [removing, setRemoving] = useState<Friendship | null>(null)
  const [loanKind, setLoanKind] = useState<'lend' | 'borrow' | null>((params.get('action') as 'lend' | 'borrow') || null)
  const [splitOpen, setSplitOpen] = useState(params.get('action') === 'split')
  const [roomOpen, setRoomOpen] = useState(false)
  const [rooms, setRooms] = useState<Array<Room & { members: string[] }>>([])

  const load = useCallback(async () => {
    try {
      const fs = await listFriendships()
      const ids = [...new Set(fs.flatMap((f) => [f.requester_id, f.addressee_id]).filter((x) => x !== me))]
      const rs = await listRooms().catch(() => [] as Array<Room & { members: string[] }>)
      const roomIds = rs.flatMap((r) => r.members)
      const [ps, un] = await Promise.all([getProfiles([...new Set([...ids, ...roomIds])]), unreadCounts()])
      setFriendships(fs)
      setRooms(rs)
      setProfiles((old) => ({ ...old, ...ps }))
      setUnread(un)
    } catch (e) {
      toast.error(t('common.loadFailed'), e instanceof Error ? e.message : undefined)
    } finally {
      setLoading(false)
    }
  }, [me, t, toast])

  useEffect(() => {
    void load()
  }, [load])

  useEffect(() => {
    const ch = supabase
      .channel('friends-page')
      .on('postgres_changes', { event: '*', schema: 'public', table: 'friendships' }, () => void load())
      .on('postgres_changes', { event: 'INSERT', schema: 'public', table: 'messages' }, (p) => {
        const m = p.new as Message
        if (m.receiver_id === me && m.sender_id !== activeId) setUnread((u) => ({ ...u, [m.sender_id]: (u[m.sender_id] ?? 0) + 1 }))
      })
      .subscribe()
    return () => {
      void supabase.removeChannel(ch)
    }
  }, [load, me, activeId])

  useEffect(() => {
    const h = setTimeout(() => {
      if (q.trim().length >= 2)
        searchProfiles(q, me)
          .then(setResults)
          .catch(() => setResults([]))
      else setResults([])
    }, 250)
    return () => clearTimeout(h)
  }, [q, me])

  const other = (f: Friendship) => (f.requester_id === me ? f.addressee_id : f.requester_id)
  const friends = useMemo(() => friendships.filter((f) => f.status === 'ACCEPTED'), [friendships])
  const incoming = useMemo(() => friendships.filter((f) => f.status === 'PENDING' && f.addressee_id === me), [friendships, me])
  const outgoing = useMemo(() => friendships.filter((f) => f.status === 'PENDING' && f.requester_id === me), [friendships, me])
  const relation = (id: string) => friendships.find((f) => other(f) === id)

  async function act(fn: () => Promise<void>, ok?: string) {
    try {
      await fn()
      await load()
      if (ok) toast.success(ok)
    } catch (e) {
      toast.error(t('common.saveFailed'), e instanceof Error ? e.message : undefined)
    }
  }

  const activeFriend = activeId ? profiles[activeId] : null
  const openLoan = (kind: 'lend' | 'borrow') => {
    setLoanKind(kind)
    const p = new URLSearchParams(params)
    p.delete('action')
    setParams(p, { replace: true })
  }

  return (
    <div>
      <PageHeader
        title={t('friends.title')}
        actions={
          <>
            <button className="btn-outline h-9" onClick={() => setSplitOpen(true)} disabled={friends.length === 0}><Receipt size={15} />{t('split.title')}</button>
            <button className="btn-outline h-9" onClick={() => setRoomOpen(true)} disabled={friends.length === 0}><Users2 size={15} />{t('room.new')}</button>
          </>
        }
      />
      <div className="grid gap-4 lg:grid-cols-[360px_1fr]">
        <div className="space-y-4">
          <div className="card p-3">
            <div className="relative">
              <Search size={16} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-faint" />
              <input className="input pl-10" placeholder={t('friends.search')} value={q} onChange={(e) => setQ(e.target.value)} />
            </div>
            <AnimatePresence>
              {results.length > 0 && (
                <motion.ul initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: 'auto' }} exit={{ opacity: 0, height: 0 }} className="mt-2 overflow-hidden">
                  {results.map((p) => {
                    const rel = relation(p.id)
                    return (
                      <li key={p.id} className="flex items-center gap-3 p-2 rounded-xl hover:bg-raised">
                        <Avatar profile={p} size={36} />
                        <div className="flex-1 min-w-0">
                          <div className="text-sm font-medium truncate">{p.display_name}</div>
                          <div className="text-xs text-faint">@{p.username}</div>
                        </div>
                        {rel?.status === 'ACCEPTED' ? (
                          <Check size={16} className="text-good" />
                        ) : rel ? (
                          <span className="text-xs text-muted">{t('friends.requested')}</span>
                        ) : (
                          <button
                            className="btn-primary h-8 px-3 text-xs"
                            onClick={() => {
                              void act(() => sendFriendRequest(p.id), t('friends.requested'))
                              setQ('')
                            }}
                          >
                            <UserPlus size={14} />
                            {t('friends.add')}
                          </button>
                        )}
                      </li>
                    )
                  })}
                </motion.ul>
              )}
            </AnimatePresence>
          </div>

          {incoming.length > 0 && (
            <div className="card p-3">
              <div className="text-xs text-muted uppercase tracking-wide px-2 mb-1">
                {t('friends.incoming')} · {incoming.length}
              </div>
              {incoming.map((f) => {
                const p = profiles[other(f)]
                return (
                  <div key={f.id} className="flex items-center gap-3 p-2 rounded-xl">
                    <Avatar profile={p} size={36} />
                    <div className="flex-1 min-w-0">
                      <div className="text-sm font-medium truncate">{p?.display_name}</div>
                      <div className="text-xs text-faint">@{p?.username}</div>
                    </div>
                    <button className="btn-primary h-8 w-8 px-0" onClick={() => void act(() => acceptFriendRequest(f.id))}>
                      <Check size={15} />
                    </button>
                    <button className="btn-ghost h-8 w-8 px-0" onClick={() => void act(() => removeFriendship(f.id))}>
                      <X size={15} />
                    </button>
                  </div>
                )
              })}
            </div>
          )}

          <div className="card p-3">
            <div className="text-xs text-muted uppercase tracking-wide px-2 mb-1">
              {t('friends.list')} · {friends.length}
            </div>
            {loading ? (
              <div className="space-y-2 p-1">
                {[0, 1, 2].map((i) => (
                  <Skeleton key={i} className="h-12" />
                ))}
              </div>
            ) : friends.length === 0 ? (
              <Empty icon={<Users />} title={t('friends.none')} text={t('friends.noneText')} />
            ) : (
              friends.map((f) => {
                const id = other(f),
                  p = profiles[id]
                return (
                  <motion.button
                    key={f.id}
                    layout
                    whileHover={{ x: 2 }}
                    onClick={() => navigate(`/friends/${id}`)}
                    className={`w-full flex items-center gap-3 p-2 rounded-xl text-left transition ${activeId === id ? 'bg-accent/10' : 'hover:bg-raised'}`}
                  >
                    <Avatar profile={p} size={40} />
                    <div className="flex-1 min-w-0">
                      <div className="text-sm font-medium truncate">{p?.display_name}</div>
                      <div className="text-xs text-faint truncate">
                        @{p?.username}
                        {p?.bio && ` · ${p.bio}`}
                      </div>
                    </div>
                    {unread[id] > 0 && <span className="min-w-[20px] h-5 px-1.5 rounded-full bg-ink text-bg text-[11px] flex items-center justify-center">{unread[id]}</span>}
                  </motion.button>
                )
              })
            )}
            {rooms.length > 0 && (
              <>
                <div className="text-xs text-muted uppercase tracking-wide px-2 mt-4 mb-1 font-mono">{t('room.list')} · {rooms.length}</div>
                {rooms.map((r) => (
                  <motion.button key={r.id} layout whileHover={{ x: 2 }} onClick={() => navigate(`/friends/room/${r.id}`)} className={`w-full flex items-center gap-3 p-2 rounded-xl text-left transition ${roomId === r.id ? 'bg-accent/10' : 'hover:bg-raised'}`}>
                    <div className="w-10 h-10 rounded-full border border-line flex items-center justify-center text-accent2"><Users2 size={18} strokeWidth={1.5} /></div>
                    <div className="flex-1 min-w-0"><div className="text-sm truncate">{r.name}</div><div className="text-xs text-faint truncate">{r.members.length} · {r.members.map((id) => profiles[id]?.display_name ?? '…').join(', ')}</div></div>
                  </motion.button>
                ))}
              </>
            )}
            {outgoing.length > 0 && (
              <div className="mt-2 px-2 text-xs text-faint">
                {t('friends.outgoing')}: {outgoing.map((f) => '@' + (profiles[other(f)]?.username ?? '…')).join(', ')}
              </div>
            )}
          </div>
        </div>

        <div className="card min-h-[520px] flex flex-col overflow-hidden">
          {roomId && rooms.find((r) => r.id === roomId) ? (
            <GroupChat room={rooms.find((r) => r.id === roomId)!} me={me} profiles={profiles} onLeft={() => { navigate('/friends'); void load() }} />
          ) : activeFriend ? (
            <>
              <div className="flex items-center gap-3 px-4 h-16 border-b border-line">
                <Avatar profile={activeFriend} size={38} />
                <div className="flex-1 min-w-0">
                  <div className="truncate">{activeFriend.display_name}</div>
                  <div className="text-xs text-faint">
                    @{activeFriend.username} · {t('friends.online', formatDate(activeFriend.created_at, locale))}
                  </div>
                  {(() => {
                    const bal = balanceWith(activeFriend.id, debts, payments)
                    if (bal === 0) return <div className="text-xs text-muted mt-0.5">{t('bal.even')}</div>
                    return <div className={`text-xs mt-0.5 num ${bal > 0 ? 'text-good' : 'text-warn'}`}>{bal > 0 ? t('bal.owesMe', formatMoney(bal, locale)) : t('bal.iOwe', formatMoney(-bal, locale))}</div>
                  })()}
                </div>
                <button className="btn-outline h-9 hidden sm:inline-flex" onClick={() => openLoan('lend')}>
                  <HandCoins size={15} />
                  {t('friends.lend')}
                </button>
                <button className="btn-outline h-9 hidden sm:inline-flex" onClick={() => openLoan('borrow')}>
                  <HandHelping size={15} />
                  {t('friends.borrow')}
                </button>
                <button className="btn-ghost btn-icon h-9 w-9 text-muted" onClick={() => setRemoving(relation(activeFriend.id) ?? null)} title={t('friends.remove')}>
                  <UserMinus size={16} />
                </button>
              </div>
              <div className="sm:hidden flex gap-2 p-3 border-b border-line">
                <button className="btn-outline h-9 flex-1" onClick={() => openLoan('lend')}>
                  <HandCoins size={15} />
                  {t('friends.lend')}
                </button>
                <button className="btn-outline h-9 flex-1" onClick={() => openLoan('borrow')}>
                  <HandHelping size={15} />
                  {t('friends.borrow')}
                </button>
              </div>
              <Chat key={activeFriend.id} friendId={activeFriend.id} me={me} onRead={() => setUnread((u) => ({ ...u, [activeFriend.id]: 0 }))} />
            </>
          ) : (
            <Empty icon={<MessageCircle />} title={t('chat.pick')} text={friends.length ? undefined : t('friends.noneText')} />
          )}
        </div>
      </div>

      {loanKind && <LoanForm open kind={loanKind} friends={friends.map((f) => profiles[other(f)]).filter(Boolean)} initialFriendId={activeId} onClose={() => setLoanKind(null)} />}
      {splitOpen && <SplitForm open friends={friends.map((f) => profiles[other(f)]).filter(Boolean)} onClose={() => setSplitOpen(false)} />}
      {roomOpen && <RoomForm open friends={friends.map((f) => profiles[other(f)]).filter(Boolean)} onClose={() => setRoomOpen(false)} onCreated={(id) => { void load().then(() => navigate(`/friends/room/${id}`)) }} />}
      <Confirm
        open={!!removing}
        onClose={() => setRemoving(null)}
        onConfirm={() => {
          if (removing) {
            void act(() => removeFriendship(removing.id))
            setRemoving(null)
            navigate('/friends')
          }
        }}
        title={t('friends.remove.title')}
        text={t('friends.remove.text')}
        confirmLabel={t('common.delete')}
        cancelLabel={t('common.cancel')}
      />
    </div>
  )
}

function Chat({ friendId, me, onRead }: { friendId: string; me: string; onRead: () => void }) {
  const [online, setOnline] = useState(false)
  const [typing, setTyping] = useState(false)
  const typingRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const roomRef = useRef<ReturnType<typeof supabase.channel> | null>(null)
  const { t, locale } = useI18n()
  const toast = useToast()
  const [messages, setMessages] = useState<Message[]>([])
  // a draft prepared by the assistant arrives through sessionStorage and is used once
  const [text, setText] = useState(() => {
    try {
      const key = `kvit.draft.${friendId}`
      const v = sessionStorage.getItem(key) ?? ''
      sessionStorage.removeItem(key)
      return v
    } catch {
      return ''
    }
  })
  const [loading, setLoading] = useState(true)
  const endRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    let alive = true
    setLoading(true)
    listMessages(friendId)
      .then((m) => {
        if (alive) {
          setMessages(m)
          setLoading(false)
        }
      })
      .catch((e) => toast.error(t('common.loadFailed'), String(e)))
    void markRead(friendId)
      .then(onRead)
      .catch(() => undefined)
    const ch = supabase
      .channel(`chat-${friendId}`)
      .on('postgres_changes', { event: 'INSERT', schema: 'public', table: 'messages' }, (p) => {
        const m = p.new as Message
        if ((m.sender_id === friendId && m.receiver_id === me) || (m.sender_id === me && m.receiver_id === friendId)) {
          setMessages((xs) => (xs.some((x) => x.id === m.id) ? xs : [...xs, m]))
          if (m.sender_id === friendId) {
            void markRead(friendId)
              .then(onRead)
              .catch(() => undefined)
          }
        }
      })
      .subscribe()
    return () => {
      alive = false
      void supabase.removeChannel(ch)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [friendId, me])

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages.length, typing])

  // Presence tells both sides who is in the room; 'typing' is a broadcast that expires after 2.5 s.
  useEffect(() => {
    const key = [me, friendId].sort().join('-')
    const room = supabase.channel(`room-${key}`, { config: { presence: { key: me }, broadcast: { self: false } } })
    room
      .on('presence', { event: 'sync' }, () => setOnline(Object.keys(room.presenceState()).includes(friendId)))
      .on('broadcast', { event: 'typing' }, () => {
        setTyping(true)
        if (typingRef.current) clearTimeout(typingRef.current)
        typingRef.current = setTimeout(() => setTyping(false), 2500)
      })
      .subscribe((status) => {
        if (status === 'SUBSCRIBED') void room.track({ at: Date.now() })
      })
    roomRef.current = room
    return () => {
      roomRef.current = null
      void supabase.removeChannel(room)
    }
  }, [friendId, me])

  const lastTypingSent = useRef(0)
  const onType = (v: string) => {
    setText(v)
    const now = Date.now()
    if (now - lastTypingSent.current > 1200 && roomRef.current) {
      lastTypingSent.current = now
      void roomRef.current.send({ type: 'broadcast', event: 'typing', payload: {} })
    }
  }

  async function send() {
    const body = text.trim()
    if (!body) return
    setText('')
    try {
      const m = await sendMessage(friendId, body)
      setMessages((xs) => (xs.some((x) => x.id === m.id) ? xs : [...xs, m]))
    } catch (e) {
      setText(body)
      toast.error(t('common.saveFailed'), e instanceof Error ? e.message : undefined)
    }
  }

  let lastDay = ''
  return (
    <>
      <div className="px-4 py-1.5 border-b border-line flex items-center gap-2 font-mono text-[11px] uppercase tracking-wide text-muted">
        <span className={`w-1.5 h-1.5 rounded-full ${online ? 'bg-good pulse-dot' : 'bg-faint'}`} />
        {online ? t('chat.online') : t('chat.offline')}
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
                {showDay && <div className="text-center text-[11px] text-faint my-3">{formatDate(day, locale)}</div>}
                <motion.div initial={{ opacity: 0, y: 6, scale: 0.98 }} animate={{ opacity: 1, y: 0, scale: 1 }} className={`flex ${mine ? 'justify-end' : 'justify-start'}`}>
                  <div
                    className={`max-w-[75%] px-3.5 py-2 rounded-2xl text-sm whitespace-pre-wrap break-words ${mine ? 'bg-ink text-bg rounded-br-md' : 'bg-raised border border-line rounded-bl-md'}`}
                  >
                    {m.body}
                    <div className={`text-[10px] mt-1 ${mine ? 'text-bg/60' : 'text-faint'} text-right`}>{formatDateTime(m.created_at, locale).split(',').pop()}</div>
                  </div>
                </motion.div>
              </div>
            )
          })
        )}
        {typing && (
          <div className="flex justify-start">
            <div className="px-3.5 py-2 rounded-2xl rounded-bl-md bg-raised border border-line flex gap-1 items-center h-8">
              {[0, 1, 2].map((i) => (
                <motion.span key={i} className="w-1.5 h-1.5 rounded-full bg-muted" animate={{ opacity: [0.3, 1, 0.3] }} transition={{ duration: 1, repeat: Infinity, delay: i * 0.2 }} />
              ))}
            </div>
          </div>
        )}
        <div ref={endRef} />
      </div>
      <form
        onSubmit={(e) => {
          e.preventDefault()
          void send()
        }}
        className="flex items-center gap-2 p-3 border-t border-line"
      >
        <input className="input" placeholder={t('chat.placeholder')} value={text} onChange={(e) => onType(e.target.value)} maxLength={2000} />
        <motion.button whileTap={{ scale: 0.9 }} className="btn-primary btn-icon h-11 w-11 shrink-0" disabled={!text.trim()} aria-label={t('common.send')}>
          <Send size={18} />
        </motion.button>
      </form>
    </>
  )
}
