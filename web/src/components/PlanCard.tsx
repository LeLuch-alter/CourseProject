import { useMemo, useState } from 'react'
import { motion } from 'framer-motion'
import { Route } from 'lucide-react'
import { useI18n } from '../lib/i18n'
import { useData } from '../lib/data'
import { formatMoney, parseAmount } from '../lib/money'
import { formatDate } from '../lib/dates'
import { averageMonthlyPayment, payoffPlan, type Strategy } from '../lib/calc'
import { Segmented } from './ui'

// Plan for all debts at once: one monthly budget, three possible orders.
export function PlanCard() {
  const { t, locale } = useI18n()
  const { debts, payments } = useData()
  const ownPayments = useMemo(() => payments.filter((p) => debts.find((d) => d.id === p.debt_id)?.direction === 'OWED'), [debts, payments])
  const avg = averageMonthlyPayment(ownPayments)
  const [budget, setBudget] = useState(avg > 0 ? String(Math.round(avg)) : '')
  const [strategy, setStrategy] = useState<Strategy>('deadline')
  const value = parseAmount(budget) ?? 0
  const plan = useMemo(() => payoffPlan(debts, payments, value, strategy), [debts, payments, value, strategy])
  const late = plan?.items.filter((i) => i.late) ?? []

  return (
    <section className="card p-5 lg:col-span-2">
      <div className="flex flex-wrap items-start justify-between gap-3 mb-4">
        <div>
          <h2 className="flex items-center gap-2">
            <Route size={18} className="text-accent2" strokeWidth={1.5} />
            {t('plan.title')}
          </h2>
          <p className="text-xs text-muted mt-1 max-w-xl">{t('plan.hint')}</p>
        </div>
        <Segmented<Strategy>
          value={strategy}
          onChange={setStrategy}
          options={[
            { value: 'deadline', label: t('plan.deadline') },
            { value: 'snowball', label: t('plan.snowball') },
            { value: 'avalanche', label: t('plan.avalanche') },
          ]}
        />
      </div>
      <div className="flex flex-wrap items-end gap-4">
        <div>
          <label className="label">{t('plan.budget')}</label>
          <input className="input h-10 w-44 num" inputMode="decimal" value={budget} onChange={(e) => setBudget(e.target.value)} placeholder="0" />
        </div>
        {avg > 0 && <div className="text-xs text-muted pb-2.5">{t('plan.avg', formatMoney(avg, locale))}</div>}
      </div>

      {!plan ? (
        <div className="text-sm text-muted mt-4">{value > 0 ? t('plan.nothing') : t('plan.needBudget')}</div>
      ) : (
        <motion.div key={strategy + value} initial={{ opacity: 0, y: 6 }} animate={{ opacity: 1, y: 0 }} className="mt-5">
          <div className="grid sm:grid-cols-3 gap-3">
            <div className="border border-line rounded-lg p-3">
              <div className="eyebrow">{t('plan.months')}</div>
              <div className="text-2xl num mt-1">{plan.months}</div>
            </div>
            <div className="border border-line rounded-lg p-3">
              <div className="eyebrow">{t('plan.finish')}</div>
              <div className="text-2xl mt-1">{formatDate(plan.finishDate, locale)}</div>
            </div>
            <div className="border border-line rounded-lg p-3">
              <div className="eyebrow">{t('plan.late')}</div>
              <div className={`text-2xl num mt-1 ${late.length ? 'text-bad' : 'text-good'}`}>{late.length}</div>
            </div>
          </div>
          <ol className="mt-4 divide-y divide-line">
            {plan.items.map((i, n) => (
              <li key={i.debtId} className="flex items-center gap-3 py-2.5 text-sm">
                <span className="w-6 h-6 rounded-full border border-line flex items-center justify-center font-mono text-[11px] text-muted">{n + 1}</span>
                <span className="flex-1 min-w-0 truncate">{i.title}</span>
                <span className="num text-muted hidden sm:inline">{formatMoney(i.remaining, locale)}</span>
                <span className={`num w-32 text-right ${i.late ? 'text-bad' : ''}`}>{formatDate(i.payoffDate, locale)}</span>
              </li>
            ))}
          </ol>
          {late.length > 0 && <p className="text-xs text-bad mt-3">{t('plan.lateText', late.map((i) => i.title).join(', '))}</p>}
          <p className="text-[11px] text-faint mt-3">{t('plan.disclaimer')}</p>
        </motion.div>
      )}
    </section>
  )
}
