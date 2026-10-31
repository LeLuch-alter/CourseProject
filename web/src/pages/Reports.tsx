import { useMemo, useState } from 'react'
import { motion } from 'framer-motion'
import { BarChart3, Download } from 'lucide-react'
import { useI18n } from '../lib/i18n'
import { useData } from '../lib/data'
import { formatMoney, formatCompact, sum } from '../lib/money'
import { paymentsByMonth, remainingFor, effectiveStatus, forecastMonths, currencyOf } from '../lib/calc'
import { todayIso } from '../lib/dates'
import { PlanCard } from '../components/PlanCard'
import { exportReportPdf } from '../lib/reportPdf'
import { Empty, PageHeader, Progress, Segmented, Skeleton, stagger } from '../components/ui'

const BAR_MAX = 150

export function ReportsPage() {
  const { t, locale } = useI18n()
  const { debts, payments, summary, loading } = useData()

  const [period, setPeriod] = useState<'3' | '6' | '12' | 'custom'>('6')
  const thisMonth = todayIso().slice(0, 7)
  const [from, setFrom] = useState(() => { const d = new Date(); d.setDate(1); d.setMonth(d.getMonth() - 5); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}` })
  const [to, setTo] = useState(thisMonth)
  // custom period: whole months from..to; the chart ends on the last day of 'to'
  const span = period === 'custom' ? Math.max(1, (Number(to.slice(0, 4)) - Number(from.slice(0, 4))) * 12 + Number(to.slice(5)) - Number(from.slice(5)) + 1) : Number(period)
  const endDay = period === 'custom' ? `${to}-${String(new Date(Number(to.slice(0, 4)), Number(to.slice(5)), 0).getDate()).padStart(2, '0')}` : todayIso()
  const owed = useMemo(() => debts.filter((d) => d.direction === 'OWED' && d.status !== 'CANCELLED' && currencyOf(d) === 'KZT'), [debts])
  const owedPayments = useMemo(() => payments.filter((p) => owed.some((d) => d.id === p.debt_id)), [payments, owed])
  const months = useMemo(() => paymentsByMonth(owedPayments, span, endDay), [owedPayments, span, endDay])
  const periodTotal = sum(months.map((m) => m.total))
  const maxMonth = Math.max(1, ...months.map((m) => m.total))

  const byCreditor = useMemo(() => {
    const map = new Map<string, number>()
    for (const d of owed) map.set(d.creditor_name, (map.get(d.creditor_name) ?? 0) + remainingFor(d, payments))
    return [...map.entries()].filter(([, v]) => v > 0).sort((a, b) => b[1] - a[1])
  }, [owed, payments])
  const creditorTotal = sum(byCreditor.map(([, v]) => v))

  const statuses = useMemo(() => {
    const counts: Record<string, number> = { ACTIVE: 0, OVERDUE: 0, PAID: 0, CANCELLED: 0 }
    for (const d of debts.filter((d) => d.direction === 'OWED')) counts[effectiveStatus(d, payments)]++
    return counts
  }, [debts, payments])
  const statusTotal = Object.values(statuses).reduce((a, b) => a + b, 0)
  const forecast = forecastMonths(summary.remaining, owedPayments)

  const lent = debts.filter((d) => d.direction === 'LENT' && d.status !== 'CANCELLED' && currencyOf(d) === 'KZT')
  const lentBack = sum(lent.map((d) => Number(d.original_amount) - remainingFor(d, payments)))

  if (loading)
    return (
      <div className="grid gap-4 lg:grid-cols-2">
        <Skeleton className="h-72" />
        <Skeleton className="h-72" />
      </div>
    )
  if (debts.length === 0) return <Empty icon={<BarChart3 />} title={t('reports.empty')} />

  const colors: Record<string, string> = { ACTIVE: 'bg-accent', OVERDUE: 'bg-bad', PAID: 'bg-good', CANCELLED: 'bg-faint' }

  return (
    <div>
      <PageHeader
        title={t('nav.reports')}
        actions={
          <>
            <Segmented<'3' | '6' | '12' | 'custom'> value={period} onChange={setPeriod} options={[{ value: '3', label: t('rep.m3') }, { value: '6', label: t('rep.m6') }, { value: '12', label: t('rep.m12') }, { value: 'custom', label: t('rep.custom') }]} />
            <button className="btn-outline h-9" onClick={() => void exportReportPdf({ t, locale, months, byCreditor, statuses, summary, forecast, periodTotal })}><Download size={15} />PDF</button>
          </>
        }
      />
      {period === 'custom' && (
        <div className="flex flex-wrap items-end gap-3 mb-4">
          <div><label className="label">{t('rep.from')}</label><input className="input h-10" type="month" value={from} max={to} onChange={(e) => e.target.value && setFrom(e.target.value)} /></div>
          <div><label className="label">{t('rep.to')}</label><input className="input h-10" type="month" value={to} min={from} max={thisMonth} onChange={(e) => e.target.value && setTo(e.target.value)} /></div>
        </div>
      )}
      <div className="grid gap-4 lg:grid-cols-2">
        <motion.section {...stagger(0)} className="card p-5">
          <div className="flex items-baseline justify-between mb-4"><h2>{t('reports.byMonth')}</h2><span className="text-sm text-muted num">{t('rep.total', formatMoney(periodTotal, locale))}</span></div>
          <div className="relative h-52">
            {[0, 0.5, 1].map((f) => (
              <div key={f} className="absolute left-0 right-0 border-t border-dashed border-line" style={{ bottom: 24 + f * BAR_MAX }} />
            ))}
            <div className="absolute inset-0 flex items-end gap-3">
              {months.map((m, i) => {
                const h = m.total > 0 ? Math.max(6, Math.round((m.total / maxMonth) * BAR_MAX)) : 2
                const last = i === months.length - 1
                return (
                  <div key={m.month} className="flex-1 flex flex-col items-center justify-end gap-1.5">
                    {m.total > 0 && (
                      <motion.div initial={{ opacity: 0, y: 4 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.3 + i * 0.06 }} className="font-mono text-[10px] num text-muted whitespace-nowrap">
                        {formatCompact(m.total, locale)}
                      </motion.div>
                    )}
                    <motion.div
                      initial={{ height: 0 }}
                      animate={{ height: h }}
                      transition={{ delay: i * 0.06, type: 'spring', stiffness: 120, damping: 18 }}
                      className={`w-full max-w-[56px] rounded-t ${m.total > 0 ? (last ? 'bg-ink' : 'bg-ink/45') : 'bg-line'}`}
                    />
                    <div className={`h-4 text-[11px] ${last ? 'text-ink' : 'text-muted'}`}>{new Intl.DateTimeFormat(locale, { month: 'short' }).format(new Date(m.month + '-01T00:00:00'))}</div>
                  </div>
                )
              })}
            </div>
          </div>
        </motion.section>

        <motion.section {...stagger(1)} className="card p-5">
          <h2 className="mb-4">{t('reports.byCreditor')}</h2>
          {byCreditor.length === 0 ? (
            <div className="text-sm text-muted">—</div>
          ) : (
            <ul className="space-y-3">
              {byCreditor.slice(0, 8).map(([name, v]) => (
                <li key={name}>
                  <div className="flex justify-between text-sm mb-1">
                    <span className="font-medium truncate">{name}</span>
                    <span className="num text-muted">
                      {formatMoney(v, locale)} · {Math.round((v / creditorTotal) * 100)}%
                    </span>
                  </div>
                  <Progress value={(v / creditorTotal) * 100} className="h-1.5" />
                </li>
              ))}
            </ul>
          )}
        </motion.section>

        <motion.section {...stagger(2)} className="card p-5">
          <h2 className="mb-4">{t('reports.statuses')}</h2>
          <div className="flex h-3 rounded-full overflow-hidden bg-line">
            {Object.entries(statuses).map(
              ([k, v]) => v > 0 && <motion.div key={k} initial={{ width: 0 }} animate={{ width: `${(v / statusTotal) * 100}%` }} transition={{ duration: 0.6 }} className={colors[k]} />,
            )}
          </div>
          <ul className="grid grid-cols-2 gap-2 mt-4 text-sm">
            {Object.entries(statuses).map(([k, v]) => (
              <li key={k} className="flex items-center gap-2">
                <span className={`w-2.5 h-2.5 rounded-full ${colors[k]}`} />
                {t(`status.${k}`)}
                <span className="ml-auto num ">{v}</span>
              </li>
            ))}
          </ul>
        </motion.section>

        <motion.section {...stagger(3)} className="card p-5 space-y-5">
          <div>
            <h2 className="mb-2">{t('reports.forecast')}</h2>
            <p className="text-sm text-muted">{forecast ? t('reports.forecastText', forecast) : t('reports.forecastNone')}</p>
          </div>
          <div className="border-t border-line pt-4">
            <h2 className="mb-3">{t('reports.friends')}</h2>
            <div className="grid grid-cols-2 gap-3">
              <div className="bg-raised rounded-xl p-3">
                <div className="text-xs text-muted">{t('reports.lentOut')}</div>
                <div className="text-lg num">{formatMoney(summary.lentTotal, locale)}</div>
              </div>
              <div className="bg-raised rounded-xl p-3">
                <div className="text-xs text-muted">{t('reports.lentBack')}</div>
                <div className="text-lg num text-good">{formatMoney(lentBack, locale)}</div>
              </div>
            </div>
          </div>
        </motion.section>
        <PlanCard />
      </div>
    </div>
  )
}
