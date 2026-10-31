import { useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { motion } from 'framer-motion'
import { Plus, Receipt, HandCoins, ArrowUpRight, TrendingDown, Wallet, Clock, AlertTriangle, Users } from 'lucide-react'
import { useI18n } from '../lib/i18n'
import { useAuth } from '../lib/auth'
import { useData } from '../lib/data'
import { formatMoney } from '../lib/money'
import { formatDate, daysBetween, todayIso } from '../lib/dates'
import { remainingFor, forecastMonths, isOverdue, effectiveStatus, debtorsOf } from '../lib/calc'
import { Ring, Skeleton, Empty, stagger, StatusChip, Progress } from '../components/ui'
import { DebtForm, PaymentForm } from '../components/forms'

function greetingKey(): string {
  const h = new Date().getHours()
  return h < 5 ? 'dash.greeting.night' : h < 12 ? 'dash.greeting.morning' : h < 18 ? 'dash.greeting.day' : 'dash.greeting.evening'
}

function Counter({ value, locale }: { value: number; locale: string }) {
  return (
    <motion.span key={value} initial={{ opacity: 0, y: 6 }} animate={{ opacity: 1, y: 0 }} className="num">
      {formatMoney(value, locale)}
    </motion.span>
  )
}

export function DashboardPage() {
  const { t, locale } = useI18n()
  const { profile } = useAuth()
  const { debts, payments, summary, loading, error, reload } = useData()
  const debtors = useMemo(() => debtorsOf(debts, payments), [debts, payments])
  const navigate = useNavigate()
  const [debtOpen, setDebtOpen] = useState(false)
  const [payOpen, setPayOpen] = useState(false)

  const progress = summary.total > 0 ? Math.round((summary.paid / summary.total) * 100) : 0
  const forecast = useMemo(
    () =>
      forecastMonths(
        summary.remaining,
        payments.filter((p) => debts.find((d) => d.id === p.debt_id)?.direction === 'OWED'),
      ),
    [summary.remaining, payments, debts],
  )

  const upcoming = useMemo(
    () =>
      debts
        .filter((d) => d.due_date && d.status === 'ACTIVE' && remainingFor(d, payments) > 0)
        .sort((a, b) => a.due_date!.localeCompare(b.due_date!))
        .slice(0, 5),
    [debts, payments],
  )

  const recent = useMemo(() => payments.filter((p) => !p.mirror_of).slice(0, 6), [payments])
  const debtById = useMemo(() => Object.fromEntries(debts.map((d) => [d.id, d])), [debts])

  if (loading)
    return (
      <div className="grid gap-4 lg:grid-cols-3">
        <Skeleton className="h-64 lg:col-span-2" />
        <Skeleton className="h-64" />
        <Skeleton className="h-48 lg:col-span-3" />
      </div>
    )
  if (error)
    return (
      <Empty
        icon={<AlertTriangle />}
        title={t('common.loadFailed')}
        text={error}
        action={
          <button className="btn-primary" onClick={() => void reload()}>
            {t('common.retry')}
          </button>
        }
      />
    )

  const stats = [
    { k: 'dash.total', v: summary.total, icon: Wallet, tone: 'text-accent2' },
    { k: 'dash.paid', v: summary.paid, icon: TrendingDown, tone: 'text-accent2' },
    { k: 'dash.active', v: summary.active, icon: Clock, tone: 'text-accent2', plain: true },
    { k: 'dash.overdue', v: summary.overdue, icon: AlertTriangle, tone: summary.overdue ? 'text-bad' : 'text-accent2', plain: true },
  ]

  return (
    <div className="space-y-6">
      <motion.div {...stagger(0)}>
        <div className="text-muted text-sm">{t(greetingKey())},</div>
        <h1 className="text-[34px] leading-tight">{profile?.display_name ?? ''}</h1>
      </motion.div>

      {debts.length === 0 ? (
        <div className="card">
          <Empty
            icon={<Wallet />}
            title={t('dash.empty.title')}
            text={t('dash.empty.text')}
            action={
              <div className="flex gap-2">
                <button className="btn-primary" onClick={() => setDebtOpen(true)}>
                  <Plus size={16} />
                  {t('dash.addDebt')}
                </button>
                <Link to="/friends" className="btn-outline">
                  <Users size={16} />
                  {t('nav.friends')}
                </Link>
              </div>
            }
          />
        </div>
      ) : (
        <>
          <div className="grid gap-4 lg:grid-cols-3">
            <motion.section {...stagger(1)} className="card p-6 lg:col-span-2 relative overflow-hidden">
              <div className="relative flex flex-col sm:flex-row sm:items-center gap-6">
                <Ring value={progress} size={150} stroke={12}>
                  <div className="text-center">
                    <div className="text-3xl num">{progress}%</div>
                    <div className="text-[11px] text-muted uppercase tracking-wide">{t('dash.progress')}</div>
                  </div>
                </Ring>
                <div className="flex-1">
                  <div className="text-sm text-muted">{t('dash.remaining')}</div>
                  <div className="text-[44px] sm:text-[56px] leading-none tracking-[-0.02em] mt-2">
                    <Counter value={summary.remaining} locale={locale} />
                  </div>
                  <div className="text-sm text-muted mt-1">{t('dash.ofTotal', formatMoney(summary.total, locale))}</div>
                  {Object.keys(summary.foreignOwed).length > 0 && <div className="text-xs text-muted mt-1">{t('dash.foreign')} {Object.entries(summary.foreignOwed).map(([c, v]) => formatMoney(v, locale, c)).join(', ')}</div>}
                  <div className="mt-3 text-sm">
                    {forecast ? (
                      <span className="chip">
                        <span className="w-1.5 h-1.5 rounded-full bg-good pulse-dot" />
                        {t('dash.forecast', forecast)}
                      </span>
                    ) : (
                      <span className="text-faint text-xs">{t('dash.forecastNone')}</span>
                    )}
                  </div>
                </div>
              </div>
            </motion.section>

            <motion.section {...stagger(2)} className="card p-6">
              <div className="flex items-center justify-between">
                <div className="eyebrow">{t('dash.lent')}</div>
                <HandCoins className="text-accent2" size={20} strokeWidth={1.5} />
              </div>
              <div className="text-[34px] leading-none mt-3 num">{formatMoney(summary.lentRemaining, locale)}</div>
              {summary.lentTotal > 0 ? (
                <>
                  <Progress value={(summary.lentReturned / summary.lentTotal) * 100} className="mt-3 h-1.5" color="bg-good" />
                  <div className="flex justify-between text-xs text-muted mt-1.5"><span>{t('dash.lentBack', formatMoney(summary.lentReturned, locale))}</span><span className="num">{formatMoney(summary.lentTotal, locale)}</span></div>
                  <ul className="mt-4 space-y-1.5">
                    {debtors.slice(0, 3).map((d) => (
                      <li key={d.name} className="flex items-center justify-between text-sm"><span className="truncate">{d.name}</span><span className="num text-muted">{formatMoney(d.remaining, locale)}</span></li>
                    ))}
                  </ul>
                </>
              ) : (
                <div className="text-xs text-muted mt-1">{t('dash.lentHint')}</div>
              )}
              {Object.keys(summary.foreignLent).length > 0 && <div className="text-xs text-muted mt-2">+ {Object.entries(summary.foreignLent).map(([c, v]) => formatMoney(v, locale, c)).join(', ')}</div>}
              <Link to="/debts?f=LENT" className="btn-outline mt-5 w-full">
                {t('debts.filter.LENT')} <ArrowUpRight size={16} />
              </Link>
            </motion.section>
          </div>

          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            {stats.map((s, i) => (
              <motion.div key={s.k} {...stagger(3 + i)} whileHover={{ y: -3 }} className="card p-5 flex items-center gap-4">
                <div className={`w-11 h-11 rounded-lg border border-line flex items-center justify-center ${s.tone}`}>
                  <s.icon size={20} strokeWidth={1.5} />
                </div>
                <div className="min-w-0">
                  <div className="eyebrow">{t(s.k)}</div>
                  <div className="text-xl num truncate mt-0.5">{s.plain ? s.v : formatMoney(s.v, locale)}</div>
                </div>
              </motion.div>
            ))}
          </div>

          <div className="grid gap-4 lg:grid-cols-3">
            <motion.section {...stagger(7)} className="card lg:col-span-2">
              <div className="flex items-center justify-between px-5 pt-5 pb-3">
                <h2 className="eyebrow">{t('dash.upcoming')}</h2>
                <Link to="/debts" className="text-sm text-accent">
                  {t('common.more')}
                </Link>
              </div>
              {upcoming.length === 0 ? (
                <div className="px-5 pb-6 text-sm text-muted">{t('dash.noUpcoming')}</div>
              ) : (
                <ul className="divide-y divide-line/60">
                  {upcoming.map((d) => {
                    const days = daysBetween(todayIso(), d.due_date!),
                      rem = remainingFor(d, payments),
                      over = isOverdue(d, payments)
                    return (
                      <li key={d.id}>
                        <button onClick={() => navigate(`/debts?id=${d.id}`)} className="w-full flex items-center gap-4 px-5 py-3 hover:bg-raised transition text-left">
                          <div className={`w-11 h-11 rounded-lg border border-line flex flex-col items-center justify-center shrink-0 ${over ? 'text-bad' : days <= 14 ? 'text-warn' : 'text-muted'}`}>
                            <span className="text-base leading-none num">{new Date(d.due_date! + 'T00:00:00').getDate()}</span>
                            <span className="text-[10px] uppercase">{new Intl.DateTimeFormat(locale, { month: 'short' }).format(new Date(d.due_date! + 'T00:00:00'))}</span>
                          </div>
                          <div className="flex-1 min-w-0">
                            <div className="truncate">{d.title}</div>
                            <div className="text-xs text-muted truncate">
                              {d.creditor_name} · {over ? t('due.overdue', -days) : days === 0 ? t('due.today') : t('due.inDays', days)}
                            </div>
                          </div>
                          <div className="text-right">
                            <div className="num">{formatMoney(rem, locale, d.currency)}</div>
                            <StatusChip status={effectiveStatus(d, payments)} label={t(`status.${effectiveStatus(d, payments)}`)} />
                          </div>
                        </button>
                      </li>
                    )
                  })}
                </ul>
              )}
            </motion.section>

            <motion.section {...stagger(8)} className="card">
              <div className="px-5 pt-5 pb-4 grid grid-cols-3 gap-2 [&>button]:h-auto [&>button]:py-3 [&>button]:px-1 [&>button]:flex-col [&>button]:gap-1.5 [&>button]:text-xs [&>button]:rounded-lg [&>button]:whitespace-normal [&>button]:text-center [&>button]:leading-tight">
                <button className="btn-primary flex-1" onClick={() => setDebtOpen(true)}>
                  <Plus size={15} />
                  {t('dash.addDebt')}
                </button>
                <button className="btn-outline flex-1" onClick={() => navigate('/friends?action=split')} title={t('split.title')}>
                  <Users size={15} />
                  {t('split.short')}
                </button>
                <button className="btn-outline flex-1" onClick={() => setPayOpen(true)}>
                  <Receipt size={15} />
                  {t('dash.addPayment')}
                </button>
              </div>
              <div className="px-5 pb-3 pt-4 border-t border-line">
                <h2 className="eyebrow">{t('dash.recent')}</h2>
              </div>
              {recent.length === 0 ? (
                <div className="px-5 pb-5 text-sm text-muted">{t('dash.noPayments')}</div>
              ) : (
                <ul className="px-2 pb-3">
                  {recent.map((p) => (
                    <li key={p.id} className="flex items-center gap-3 px-3 py-2 rounded-xl hover:bg-raised transition">
                      <div className="w-8 h-8 rounded-lg border border-line text-accent2 flex items-center justify-center">
                        <Receipt size={14} strokeWidth={1.5} />
                      </div>
                      <div className="flex-1 min-w-0">
                        <div className="text-sm font-medium truncate">{debtById[p.debt_id]?.title ?? '—'}</div>
                        <div className="text-xs text-muted">{formatDate(p.payment_date, locale)}</div>
                      </div>
                      <div className="text-sm font-semibold num">{formatMoney(p.amount, locale, debtById[p.debt_id]?.currency)}</div>
                    </li>
                  ))}
                </ul>
              )}
            </motion.section>
          </div>
        </>
      )}

      {debtOpen && <DebtForm open onClose={() => setDebtOpen(false)} />}
      {payOpen && <PaymentForm open onClose={() => setPayOpen(false)} />}
    </div>
  )
}
