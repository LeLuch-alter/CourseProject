import { useEffect, useMemo, useRef, useState } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { AnimatePresence, motion } from 'framer-motion'
import { LayoutDashboard, Wallet, Receipt, BarChart3, CalendarDays, Users, FileSignature, Settings, LogOut, Bell, Sun, Moon, Monitor, Search, Menu, X, User as UserIcon, Check } from 'lucide-react'
import { useI18n, LANGS, type Lang } from '../lib/i18n'
import { useTheme } from '../lib/theme'
import { useAuth } from '../lib/auth'
import { useToast } from '../lib/toast'
import { useData } from '../lib/data'
import { supabase } from '../lib/supabase'
import { listNotifications, markNotificationsRead, clearNotifications } from '../api/social'
import type { Notification } from '../api/types'
import { Avatar } from './ui'
import { formatMoney } from '../lib/money'
import { formatDateTime } from '../lib/dates'
import { isOverdue, isDueSoon } from '../lib/calc'
import { CommandPalette } from './CommandPalette'
import { Assistant } from './Assistant'
import { DotField } from './DotField'
import { FloatingShapes } from './FloatingShapes'
import { Mark } from '../pages/Login'

const NAV = [
  { to: '/', key: 'nav.dashboard', icon: LayoutDashboard },
  { to: '/debts', key: 'nav.debts', icon: Wallet },
  { to: '/payments', key: 'nav.payments', icon: Receipt },
  { to: '/reports', key: 'nav.reports', icon: BarChart3 },
  { to: '/calendar', key: 'nav.calendar', icon: CalendarDays },
  { to: '/friends', key: 'nav.friends', icon: Users },
  { to: '/loans', key: 'nav.loans', icon: FileSignature },
]

export function Shell() {
  const { t, lang, setLang } = useI18n()
  const { theme, resolved, cycle } = useTheme()
  const { profile, signOut } = useAuth()
  const toast = useToast()
  const { debts, payments, loading } = useData()
  const navigate = useNavigate()
  const location = useLocation()
  const [mobileOpen, setMobileOpen] = useState(false)
  const [notifOpen, setNotifOpen] = useState(false)
  const [userOpen, setUserOpen] = useState(false)
  const [cmdOpen, setCmdOpen] = useState(false)
  const [notifs, setNotifs] = useState<Notification[]>([])
  const warnedRef = useRef(false)

  // Notifications: initial load + realtime inserts become toasts.
  useEffect(() => {
    let alive = true
    listNotifications()
      .then((n) => {
        if (alive) setNotifs(n)
      })
      .catch(() => undefined)
    const channel = supabase
      .channel('notifications')
      .on('postgres_changes', { event: 'INSERT', schema: 'public', table: 'notifications' }, (payload) => {
        const n = payload.new as Notification
        setNotifs((xs) => [n, ...xs])
        toast.push({ kind: 'notice', title: n.title, text: describe(n), onClick: () => n.link && navigate(n.link) })
      })
      .subscribe()
    return () => {
      alive = false
      void supabase.removeChannel(channel)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // Overdue / due-soon reminders arrive as a toast once per session instead of a block on the dashboard.
  useEffect(() => {
    if (loading || warnedRef.current) return
    warnedRef.current = true
    const owed = debts.filter((d) => d.direction === 'OWED')
    const window = profile?.reminder_days ?? 14
    const over = owed.filter((d) => isOverdue(d, payments)),
      soon = owed.filter((d) => isDueSoon(d, payments, undefined, window))
    if (over.length)
      toast.push({
        kind: 'error',
        title: t('notif.overdue', over.length),
        text: over
          .map((d) => d.title)
          .slice(0, 3)
          .join(', '),
        onClick: () => navigate('/debts?f=OVERDUE'),
      })
    else if (soon.length)
      toast.push({
        kind: 'info',
        title: t('notif.dueSoon', soon.length),
        text: soon
          .map((d) => d.title)
          .slice(0, 3)
          .join(', '),
        onClick: () => navigate('/debts'),
      })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loading])

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault()
        setCmdOpen((v) => !v)
      }
      if ((e.ctrlKey || e.metaKey) && /^[1-7]$/.test(e.key)) {
        e.preventDefault()
        navigate(NAV[Number(e.key) - 1].to)
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [navigate])

  useEffect(() => {
    setMobileOpen(false)
    setNotifOpen(false)
    setUserOpen(false)
  }, [location.pathname])

  function describe(n: Notification): string {
    if (n.kind === 'friend_request' || n.kind === 'friend_accepted') return t(`notif.${n.kind}`)
    if (n.kind.startsWith('loan_')) return t(`notif.${n.kind}`, formatMoney(n.body ?? 0))
    if (n.kind === 'message') return t('notif.message', n.body ?? '')
    if (n.kind === 'due_soon') return t('notif.due_soon_one', n.body ?? '')
    if (n.kind === 'split') return t('notif.split', formatMoney(n.body ?? 0))
    if (n.kind === 'room') return t('notif.room')
    return n.body ?? ''
  }

  const unread = useMemo(() => notifs.filter((n) => !n.read).length, [notifs])

  async function openNotifs() {
    setNotifOpen((v) => !v)
    setUserOpen(false)
    const ids = notifs.filter((n) => !n.read).map((n) => n.id)
    if (ids.length) {
      try {
        await markNotificationsRead(ids)
        setNotifs((xs) => xs.map((n) => ({ ...n, read: true })))
      } catch {
        /* shown as unread next time */
      }
    }
  }

  const ThemeIcon = theme === 'system' ? Monitor : resolved === 'dark' ? Moon : Sun

  const sidebar = (
    <div className="flex flex-col h-full">
      <NavLink to="/" className="flex items-center gap-3 px-4 h-16">
        <motion.div whileHover={{ rotate: -6 }}>
          <Mark />
        </motion.div>
        <span className="text-[17px]">Kvit</span>
      </NavLink>
      <nav className="flex-1 px-3 py-2 space-y-0.5">
        {NAV.map((n, i) => (
          <NavLink
            key={n.to}
            to={n.to}
            end={n.to === '/'}
            className={({ isActive }) =>
              `group relative flex items-center gap-3 h-10 px-3 rounded-lg text-sm transition ${isActive ? 'text-ink' : 'text-muted hover:text-ink hover:bg-raised'}`
            }
          >
            {({ isActive }) => (
              <>
                {isActive && <motion.span layoutId="nav-pill" className="absolute inset-0 rounded-lg border border-line bg-raised/60" transition={{ type: 'spring', stiffness: 500, damping: 38 }} />}
                <n.icon size={18} className={`relative z-10 ${isActive ? 'text-accent2' : ''}`} />
                <span className="relative z-10">{t(n.key)}</span>
                <kbd className="relative z-10 ml-auto text-[10px] text-faint opacity-0 group-hover:opacity-100 transition hidden lg:inline">Ctrl+{i + 1}</kbd>
              </>
            )}
          </NavLink>
        ))}
      </nav>
      <div className="p-3 border-t border-line">
        <NavLink
          to="/settings"
          className={({ isActive }) =>
            `flex items-center gap-3 h-10 px-3 rounded-lg text-sm transition ${isActive ? 'bg-accent/10 text-ink' : 'text-muted hover:text-ink hover:bg-raised'}`
          }
        >
          <Settings size={18} />
          {t('nav.settings')}
        </NavLink>
      </div>
    </div>
  )

  return (
    <div className="relative min-h-full lg:grid lg:grid-cols-[240px_1fr]">
      <DotField className="fixed inset-0 w-full h-full opacity-40" density={22} intensity={0.6} />
      <FloatingShapes />
      <aside className="relative hidden lg:block sticky top-0 h-screen border-r border-line bg-bg/70 backdrop-blur">{sidebar}</aside>

      <AnimatePresence>
        {mobileOpen && (
          <motion.div className="fixed inset-0 z-[80] lg:hidden" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
            <div className="absolute inset-0 bg-black/50" onClick={() => setMobileOpen(false)} />
            <motion.aside
              initial={{ x: -280 }}
              animate={{ x: 0 }}
              exit={{ x: -280 }}
              transition={{ type: 'spring', stiffness: 400, damping: 36 }}
              className="absolute left-0 top-0 bottom-0 w-[260px] bg-surface border-r border-line"
            >
              {sidebar}
            </motion.aside>
          </motion.div>
        )}
      </AnimatePresence>

      <div className="relative min-w-0 flex flex-col min-h-screen">
        <header className="sticky top-0 z-40 h-16 flex items-center gap-2 px-4 sm:px-6 border-b border-line bg-bg/60 backdrop-blur-xl">
          <button className="btn-icon btn-ghost lg:hidden" onClick={() => setMobileOpen(true)} aria-label="Menu">
            <Menu size={20} />
          </button>
          <button onClick={() => setCmdOpen(true)} className="hidden sm:flex items-center gap-2 h-9 px-3.5 rounded-lg border border-line text-muted text-sm w-64 hover:border-ink/50 transition">
            <Search size={16} />
            <span className="flex-1 text-left truncate">{t('cmd.placeholder')}</span>
            <kbd className="text-[10px] text-faint border border-line rounded px-1">{t('cmd.hint')}</kbd>
          </button>
          <div className="flex-1" />

          <div className="hidden sm:inline-flex p-0.5 rounded-lg border border-line">
            {LANGS.map((l) => (
              <button
                key={l}
                onClick={() => setLang(l as Lang)}
                className={`relative h-8 px-2.5 rounded font-mono text-[11px] uppercase transition ${lang === l ? 'text-bg' : 'text-muted hover:text-ink'}`}
              >
                {lang === l && <motion.span layoutId="lang-pill" className="absolute inset-0 rounded bg-ink" />}
                <span className="relative z-10">{l}</span>
              </button>
            ))}
          </div>

          <button className="btn-icon btn-ghost" onClick={cycle} title={t(`theme.${theme}`)} aria-label={t(`theme.${theme}`)}>
            <AnimatePresence mode="wait" initial={false}>
              <motion.span
                key={theme + resolved}
                initial={{ rotate: -90, opacity: 0, scale: 0.6 }}
                animate={{ rotate: 0, opacity: 1, scale: 1 }}
                exit={{ rotate: 90, opacity: 0, scale: 0.6 }}
                transition={{ duration: 0.2 }}
              >
                <ThemeIcon size={19} />
              </motion.span>
            </AnimatePresence>
          </button>

          <div className="relative">
            <button className="btn-icon btn-ghost relative" onClick={openNotifs} aria-label={t('notif.title')}>
              <Bell size={19} />
              {unread > 0 && (
                <motion.span
                  initial={{ scale: 0 }}
                  animate={{ scale: 1 }}
                  className="absolute -top-0.5 -right-0.5 min-w-[18px] h-[18px] px-1 rounded-full bg-bad text-white text-[10px] font-bold flex items-center justify-center"
                >
                  {unread}
                </motion.span>
              )}
            </button>
            <AnimatePresence>
              {notifOpen && (
                <motion.div
                  initial={{ opacity: 0, y: -6, scale: 0.98 }}
                  animate={{ opacity: 1, y: 0, scale: 1 }}
                  exit={{ opacity: 0, y: -6, scale: 0.98 }}
                  className="absolute right-0 mt-2 w-[340px] max-w-[calc(100vw-2rem)] card overflow-hidden"
                >
                  <div className="flex items-center justify-between px-4 py-3 border-b border-line">
                    <div className="font-semibold text-sm">{t('notif.title')}</div>
                    {notifs.length > 0 && (
                      <button
                        className="text-xs text-muted hover:text-ink"
                        onClick={async () => {
                          try {
                            await clearNotifications()
                            setNotifs([])
                          } catch (e) {
                            toast.error(t('common.saveFailed'), String(e))
                          }
                        }}
                      >
                        {t('notif.clear')}
                      </button>
                    )}
                  </div>
                  <div className="max-h-[360px] overflow-y-auto">
                    {notifs.length === 0 ? (
                      <div className="p-6 text-sm text-muted text-center">{t('notif.empty')}</div>
                    ) : (
                      notifs.map((n) => (
                        <button
                          key={n.id}
                          onClick={() => {
                            if (n.link) navigate(n.link)
                            setNotifOpen(false)
                          }}
                          className="w-full text-left px-4 py-3 hover:bg-raised transition border-b border-line/60 last:border-0"
                        >
                          <div className="text-sm">
                            <span className="font-semibold">{n.title}</span> <span className="text-muted">{describe(n)}</span>
                          </div>
                          <div className="text-[11px] text-faint mt-0.5">{formatDateTime(n.created_at)}</div>
                        </button>
                      ))
                    )}
                  </div>
                </motion.div>
              )}
            </AnimatePresence>
          </div>

          <div className="relative">
            <button
              onClick={() => {
                setUserOpen((v) => !v)
                setNotifOpen(false)
              }}
              className="flex items-center gap-2 h-10 pl-1 pr-2 rounded-lg hover:bg-raised transition"
            >
              <Avatar profile={profile} size={32} />
              <span className="hidden md:block text-sm max-w-[140px] truncate">{profile?.display_name}</span>
            </button>
            <AnimatePresence>
              {userOpen && (
                <motion.div
                  initial={{ opacity: 0, y: -6, scale: 0.98 }}
                  animate={{ opacity: 1, y: 0, scale: 1 }}
                  exit={{ opacity: 0, y: -6, scale: 0.98 }}
                  className="absolute right-0 mt-2 w-60 card p-1.5"
                >
                  <div className="px-3 py-2">
                    <div className="text-sm truncate">{profile?.display_name}</div>
                    <div className="font-mono text-[11px] text-faint">@{profile?.username}</div>
                  </div>
                  <button className="w-full flex items-center gap-2 px-3 h-9 rounded-lg text-sm hover:bg-raised" onClick={() => navigate('/profile')}>
                    <UserIcon size={16} />
                    {t('nav.profile')}
                  </button>
                  <button className="w-full flex items-center gap-2 px-3 h-9 rounded-lg text-sm hover:bg-raised" onClick={() => navigate('/settings')}>
                    <Settings size={16} />
                    {t('nav.settings')}
                  </button>
                  <div className="sm:hidden px-3 py-2 flex gap-1">
                    {LANGS.map((l) => (
                      <button key={l} onClick={() => setLang(l as Lang)} className={`chip ${lang === l ? 'bg-accent/10 text-accent border-accent/30' : 'border-line text-muted'}`}>
                        {lang === l && <Check size={12} />}
                        {l.toUpperCase()}
                      </button>
                    ))}
                  </div>
                  <div className="h-px bg-line my-1" />
                  <button className="w-full flex items-center gap-2 px-3 h-9 rounded-lg text-sm text-bad hover:bg-bad/10" onClick={() => void signOut()}>
                    <LogOut size={16} />
                    {t('nav.signOut')}
                  </button>
                </motion.div>
              )}
            </AnimatePresence>
          </div>
        </header>

        <main className="flex-1 px-4 sm:px-6 py-6 max-w-[1400px] w-full mx-auto">
          {/* No exit animation on purpose: with AnimatePresence mode="wait" the next page waited for the old one
              to finish fading out, and when that never finished (fast clicks, tab in background) the page stayed blank. */}
          <motion.div key={location.pathname.split('/')[1]} initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.22 }}>
            <Outlet />
          </motion.div>
        </main>
      </div>

      <CommandPalette open={cmdOpen} onClose={() => setCmdOpen(false)} />
      <Assistant />
      {(notifOpen || userOpen) && (
        <div
          className="fixed inset-0 z-30"
          onClick={() => {
            setNotifOpen(false)
            setUserOpen(false)
          }}
        />
      )}
      <X className="hidden" />
    </div>
  )
}
