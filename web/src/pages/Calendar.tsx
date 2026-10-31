import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { AnimatePresence, motion } from 'framer-motion'
import { ChevronLeft, ChevronRight, Receipt, CalendarClock, X } from 'lucide-react'
import { useI18n } from '../lib/i18n'
import { useData } from '../lib/data'
import { formatMoney, sum } from '../lib/money'
import { todayIso, formatDate } from '../lib/dates'
import { PageHeader, Segmented } from '../components/ui'
import { Select } from '../components/Select'

const pad = (n: number) => String(n).padStart(2, '0')
const iso = (y: number, m: number, d: number) => `${y}-${pad(m + 1)}-${pad(d)}`

// Selection is a date range [from, to]. Click = one day, Shift+click = extend from the anchor.
export function CalendarPage() {
  const { t, locale } = useI18n()
  const { debts, payments } = useData()
  const navigate = useNavigate()
  const today = todayIso()
  const [cursor, setCursor] = useState(() => new Date(today.slice(0, 7) + '-01T00:00:00'))
  const [range, setRange] = useState<{ from: string; to: string }>({ from: today, to: today })
  const [dir, setDir] = useState(1)

  const y = cursor.getFullYear(),
    m = cursor.getMonth()
  const ym = `${y}-${pad(m + 1)}`
  const daysInMonth = new Date(y, m + 1, 0).getDate()
  const firstDow = (new Date(y, m, 1).getDay() + 6) % 7

  const byDay = useMemo(() => {
    const map: Record<string, { due: typeof debts; pay: typeof payments }> = {}
    for (const d of debts) if (d.due_date && d.status === 'ACTIVE') (map[d.due_date] ??= { due: [], pay: [] }).due.push(d)
    for (const p of payments) if (!p.mirror_of) (map[p.payment_date] ??= { due: [], pay: [] }).pay.push(p)
    return map
  }, [debts, payments])

  const pick = (day: string, shift: boolean) => {
    setRange((r) => {
      if (!shift) return { from: day, to: day }
      const a = r.from
      return a <= day ? { from: a, to: day } : { from: day, to: a }
    })
  }
  const move = (n: number) => {
    setDir(n)
    setCursor(new Date(y, m + n, 1))
  }
  const preset = (kind: 'today' | 'week' | 'month') => {
    const d = new Date(today + 'T00:00:00')
    if (kind === 'today') setRange({ from: today, to: today })
    if (kind === 'week') {
      const dow = (d.getDay() + 6) % 7
      const from = new Date(d)
      from.setDate(d.getDate() - dow)
      const to = new Date(from)
      to.setDate(from.getDate() + 6)
      setRange({ from: iso(from.getFullYear(), from.getMonth(), from.getDate()), to: iso(to.getFullYear(), to.getMonth(), to.getDate()) })
    }
    if (kind === 'month') setRange({ from: iso(y, m, 1), to: iso(y, m, daysInMonth) })
    if (kind !== 'month') setCursor(new Date(today.slice(0, 7) + '-01T00:00:00'))
  }

  const inRange = (day: string) => day >= range.from && day <= range.to
  const selectedDays = Object.keys(byDay).filter(inRange).sort()
  const selDue = selectedDays.flatMap((d) => byDay[d].due)
  const selPay = selectedDays.flatMap((d) => byDay[d].pay)
  const debtById = Object.fromEntries(debts.map((d) => [d.id, d]))
  const weekdays = Array.from({ length: 7 }, (_, i) => new Intl.DateTimeFormat(locale, { weekday: 'short' }).format(new Date(2024, 0, i + 1)))
  const months = Array.from({ length: 12 }, (_, i) => new Intl.DateTimeFormat(locale, { month: 'long' }).format(new Date(2024, i, 1)))
  const years = Array.from({ length: 7 }, (_, i) => new Date().getFullYear() - 3 + i)
  const single = range.from === range.to
  const presetValue: 'today' | 'week' | 'month' = single && range.from === today ? 'today' : range.from === iso(y, m, 1) && range.to === iso(y, m, daysInMonth) ? 'month' : 'week'

  return (
    <div>
      <PageHeader
        title={t('nav.calendar')}
        subtitle={t('cal.rangeHint')}
        actions={
          <Segmented<'today' | 'week' | 'month'>
            value={presetValue}
            onChange={preset}
            options={[
              { value: 'today', label: t('cal.today') },
              { value: 'week', label: t('cal.week') },
              { value: 'month', label: t('cal.month') },
            ]}
          />
        }
      />
      <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_360px] items-start">
        <div className="card p-5">
          <div className="flex items-center justify-between mb-4 gap-2">
            <button className="btn-icon btn-ghost" onClick={() => move(-1)} aria-label="prev">
              <ChevronLeft size={18} />
            </button>
            <div className="flex items-center gap-2">
              <Select<number> className="w-40 capitalize [&>button]:h-9" value={m} onChange={(v) => setCursor(new Date(y, v, 1))} options={months.map((name, i) => ({ value: i, label: name }))} />
              <Select<number> className="w-24 num [&>button]:h-9" value={y} onChange={(v) => setCursor(new Date(v, m, 1))} options={years.map((yy) => ({ value: yy, label: String(yy) }))} />
            </div>
            <button className="btn-icon btn-ghost" onClick={() => move(1)} aria-label="next">
              <ChevronRight size={18} />
            </button>
          </div>
          <div className="grid grid-cols-7 gap-1.5 text-center font-mono text-[11px] text-muted uppercase mb-2">
            {weekdays.map((w) => (
              <div key={w}>{w}</div>
            ))}
          </div>
          <AnimatePresence mode="wait" initial={false}>
            <motion.div key={ym} initial={{ opacity: 0, x: 24 * dir }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -24 * dir }} transition={{ duration: 0.2 }} className="grid grid-cols-7 gap-1.5 select-none">
              {Array.from({ length: firstDow }).map((_, i) => (
                <div key={`e${i}`} />
              ))}
              {Array.from({ length: daysInMonth }, (_, i) => {
                const day = iso(y, m, i + 1),
                  ev = byDay[day]
                const isToday = day === today,
                  sel = inRange(day)
                const edge = day === range.from || day === range.to
                return (
                  <motion.button
                    key={day}
                    whileHover={{ scale: 1.04 }}
                    whileTap={{ scale: 0.96 }}
                    onClick={(e) => pick(day, e.shiftKey)}
                    className={`relative h-14 sm:h-16 rounded-lg border text-sm flex flex-col items-start justify-between p-2 transition ${sel ? (edge ? 'border-ink bg-ink text-bg' : 'border-line bg-ink/10') : 'border-line hover:bg-raised'} ${isToday && !sel ? 'ring-1 ring-accent2 border-accent2' : ''}`}
                  >
                    <span className="num leading-none">{i + 1}</span>
                    
                    {ev && (
                      <div className="flex gap-0.5">
                        {ev.due.length > 0 && <span className={`w-1.5 h-1.5 rounded-full ${day < today ? 'bg-bad' : 'bg-warn'}`} />}
                        {ev.pay.length > 0 && <span className={`w-1.5 h-1.5 rounded-full ${sel && edge ? 'bg-bg' : 'bg-good'}`} />}
                      </div>
                    )}
                  </motion.button>
                )
              })}
            </motion.div>
          </AnimatePresence>
        </div>

        <div className="card p-5 h-fit">
          <div className="flex items-start justify-between gap-2">
            <div>
              {!single && <div className="eyebrow">{t('cal.range')}</div>}
              <h3 className="text-lg leading-tight capitalize">
                {single ? new Intl.DateTimeFormat(locale, { day: 'numeric', month: 'long', weekday: 'long' }).format(new Date(range.from + 'T00:00:00')) : `${formatDate(range.from, locale)} — ${formatDate(range.to, locale)}`}
              </h3>
            </div>
            {!single && (
              <button className="btn-ghost btn-icon h-8 w-8" onClick={() => preset('today')} aria-label={t('cal.clear')}>
                <X size={15} />
              </button>
            )}
          </div>
          <div className="grid grid-cols-2 gap-2 mt-4">
            <div className="border border-line rounded-lg p-3">
              <div className="eyebrow">{t('cal.due')}</div>
              <div className="text-xl num mt-1">{selDue.length}</div>
            </div>
            <div className="border border-line rounded-lg p-3">
              <div className="eyebrow">{t('cal.paid')}</div>
              <div className="text-xl num mt-1">{formatMoney(sum(selPay.map((p) => p.amount)), locale)}</div>
            </div>
          </div>
          <AnimatePresence mode="wait">
            <motion.div key={range.from + range.to} initial={{ opacity: 0, y: 6 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }} className="mt-4 space-y-1.5 max-h-[50vh] overflow-y-auto">
              {selectedDays.length === 0 ? (
                <div className="text-sm text-muted">{t('cal.nothing')}</div>
              ) : (
                selectedDays.map((day) => (
                  <div key={day}>
                    {!single && <div className="font-mono text-[11px] text-faint uppercase mt-2 mb-1">{formatDate(day, locale)}</div>}
                    {byDay[day].due.map((d) => (
                      <button key={`d${d.id}`} onClick={() => navigate(`/debts?id=${d.id}`)} className="w-full flex items-center gap-3 p-2.5 rounded-lg border border-line hover:bg-raised text-left transition mb-1">
                        <CalendarClock size={16} className="text-warn" strokeWidth={1.5} />
                        <div className="flex-1 min-w-0">
                          <div className="text-sm truncate">{d.title}</div>
                          <div className="text-xs text-muted">
                            {t('cal.dueLabel')} · {d.creditor_name}
                          </div>
                        </div>
                        <span className="text-sm num">{formatMoney(d.original_amount, locale, d.currency)}</span>
                      </button>
                    ))}
                    {byDay[day].pay.map((p) => (
                      <button key={`p${p.id}`} onClick={() => navigate(`/debts?id=${p.debt_id}`)} className="w-full flex items-center gap-3 p-2.5 rounded-lg border border-line hover:bg-raised text-left transition mb-1">
                        <Receipt size={16} className="text-good" strokeWidth={1.5} />
                        <div className="flex-1 min-w-0">
                          <div className="text-sm truncate">{debtById[p.debt_id]?.title}</div>
                          <div className="text-xs text-muted">{t('cal.payLabel')}</div>
                        </div>
                        <span className="text-sm num">{formatMoney(p.amount, locale, debtById[p.debt_id]?.currency)}</span>
                      </button>
                    ))}
                  </div>
                ))
              )}
            </motion.div>
          </AnimatePresence>
        </div>
      </div>
    </div>
  )
}
