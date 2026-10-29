import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { AnimatePresence, motion } from 'framer-motion'
import { Search, LayoutDashboard, Wallet, Receipt, BarChart3, CalendarDays, Users, FileSignature, Settings, User, CornerDownLeft } from 'lucide-react'
import { useI18n } from '../lib/i18n'
import { useData } from '../lib/data'
import { formatMoney } from '../lib/money'

interface Item {
  id: string
  group: string
  label: string
  hint?: string
  icon: React.ReactNode
  go: () => void
}

export function CommandPalette({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { t } = useI18n()
  const { debts } = useData()
  const navigate = useNavigate()
  const [q, setQ] = useState('')
  const [idx, setIdx] = useState(0)

  useEffect(() => {
    if (open) {
      setQ('')
      setIdx(0)
    }
  }, [open])

  const items = useMemo<Item[]>(() => {
    const pages: Item[] = [
      { id: 'p1', group: t('cmd.pages'), label: t('nav.dashboard'), icon: <LayoutDashboard size={16} />, go: () => navigate('/') },
      { id: 'p2', group: t('cmd.pages'), label: t('nav.debts'), icon: <Wallet size={16} />, go: () => navigate('/debts') },
      { id: 'p3', group: t('cmd.pages'), label: t('nav.payments'), icon: <Receipt size={16} />, go: () => navigate('/payments') },
      { id: 'p4', group: t('cmd.pages'), label: t('nav.reports'), icon: <BarChart3 size={16} />, go: () => navigate('/reports') },
      { id: 'p5', group: t('cmd.pages'), label: t('nav.calendar'), icon: <CalendarDays size={16} />, go: () => navigate('/calendar') },
      { id: 'p6', group: t('cmd.pages'), label: t('nav.friends'), icon: <Users size={16} />, go: () => navigate('/friends') },
      { id: 'p7', group: t('cmd.pages'), label: t('nav.loans'), icon: <FileSignature size={16} />, go: () => navigate('/loans') },
      { id: 'p8', group: t('cmd.pages'), label: t('nav.profile'), icon: <User size={16} />, go: () => navigate('/profile') },
      { id: 'p9', group: t('cmd.pages'), label: t('nav.settings'), icon: <Settings size={16} />, go: () => navigate('/settings') },
    ]
    const ds: Item[] = debts.map((d) => ({
      id: `d${d.id}`,
      group: t('cmd.debts'),
      label: d.title,
      hint: `${d.creditor_name} · ${formatMoney(d.original_amount)}`,
      icon: <Wallet size={16} />,
      go: () => navigate(`/debts?id=${d.id}`),
    }))
    const all = [...pages, ...ds]
    const s = q.trim().toLowerCase()
    return s ? all.filter((i) => (i.label + ' ' + (i.hint ?? '')).toLowerCase().includes(s)).slice(0, 12) : pages
  }, [q, debts, t, navigate])

  useEffect(() => {
    setIdx(0)
  }, [q])

  const run = (i: Item) => {
    i.go()
    onClose()
  }

  return (
    <AnimatePresence>
      {open && (
        <motion.div className="fixed inset-0 z-[95] flex items-start justify-center pt-[12vh] px-4" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
          <div className="absolute inset-0 bg-black/50 backdrop-blur-sm" onClick={onClose} />
          <motion.div
            initial={{ y: -12, scale: 0.98, opacity: 0 }}
            animate={{ y: 0, scale: 1, opacity: 1 }}
            exit={{ y: -12, scale: 0.98, opacity: 0 }}
            className="relative card w-full max-w-xl overflow-hidden"
          >
            <div className="flex items-center gap-3 px-4 h-14 border-b border-line">
              <Search size={18} className="text-muted" />
              <input
                autoFocus
                value={q}
                onChange={(e) => setQ(e.target.value)}
                placeholder={t('cmd.placeholder')}
                className="flex-1 bg-transparent outline-none text-base"
                onKeyDown={(e) => {
                  if (e.key === 'ArrowDown') {
                    e.preventDefault()
                    setIdx((i) => Math.min(items.length - 1, i + 1))
                  }
                  if (e.key === 'ArrowUp') {
                    e.preventDefault()
                    setIdx((i) => Math.max(0, i - 1))
                  }
                  if (e.key === 'Enter' && items[idx]) run(items[idx])
                }}
              />
              <kbd className="text-[10px] text-faint border border-line rounded px-1.5 py-0.5">Esc</kbd>
            </div>
            <div className="max-h-[50vh] overflow-y-auto p-2">
              {items.length === 0 && <div className="p-6 text-center text-sm text-muted">{t('common.nothing')}</div>}
              {items.map((i, n) => (
                <button
                  key={i.id}
                  onMouseEnter={() => setIdx(n)}
                  onClick={() => run(i)}
                  className={`w-full flex items-center gap-3 px-3 h-11 rounded-xl text-left text-sm transition ${n === idx ? 'bg-accent/10' : 'hover:bg-raised'}`}
                >
                  <span className={`${n === idx ? 'text-accent' : 'text-muted'}`}>{i.icon}</span>
                  <span className="flex-1 min-w-0">
                    <span className="font-medium">{i.label}</span>
                    {i.hint && <span className="text-muted ml-2 text-xs truncate">{i.hint}</span>}
                  </span>
                  <span className="text-[10px] text-faint">{i.group}</span>
                  {n === idx && <CornerDownLeft size={14} className="text-faint" />}
                </button>
              ))}
            </div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  )
}
