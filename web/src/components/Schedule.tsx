import { useCallback, useEffect, useState } from 'react'
import { motion } from 'framer-motion'
import { CalendarRange, Check, Trash2 } from 'lucide-react'
import { useI18n } from '../lib/i18n'
import { useToast } from '../lib/toast'
import { useData } from '../lib/data'
import { formatMoney } from '../lib/money'
import { formatDate, todayIso, addMonths } from '../lib/dates'
import { createPayment, syncStatus } from '../api/debts'
import { listInstallments, createSchedule, deleteSchedule, markInstallmentPaid, splitEqually, type Installment } from '../api/extra'
import type { Debt } from '../api/types'
import { Field } from './ui'

// Payment schedule inside the debt card: split the remaining balance into N equal
// monthly instalments; ticking one records a real payment and links it.
export function Schedule({ debt, remaining }: { debt: Debt; remaining: number }) {
  const { t, locale } = useI18n()
  const toast = useToast()
  const { reload } = useData()
  const [items, setItems] = useState<Installment[] | null>(null)
  const [parts, setParts] = useState('3')
  const [first, setFirst] = useState(addMonths(todayIso(), 1))
  const [busy, setBusy] = useState(false)

  const load = useCallback(() => listInstallments(debt.id).then(setItems).catch(() => setItems([])), [debt.id])
  useEffect(() => {
    void load()
  }, [load])

  async function create() {
    const n = Number(parts)
    if (!Number.isInteger(n) || n < 1 || n > 60 || remaining <= 0) return
    setBusy(true)
    try {
      await createSchedule(debt.id, remaining, n, first)
      await load()
      toast.success(t('sched.created'))
    } catch (e) {
      toast.error(t('common.saveFailed'), e instanceof Error ? e.message : undefined)
    } finally {
      setBusy(false)
    }
  }

  async function pay(inst: Installment) {
    setBusy(true)
    try {
      const amount = Math.min(Number(inst.amount), remaining)
      const p = await createPayment({ debt_id: debt.id, amount, payment_date: todayIso(), notes: `${t('sched.title')} ${inst.seq}` })
      await markInstallmentPaid(inst, p.id)
      await syncStatus(debt, Math.max(0, Math.round((remaining - amount) * 100) / 100))
      await Promise.all([load(), reload()])
      toast.success(t('common.saved'), formatMoney(amount, locale))
    } catch (e) {
      toast.error(t('common.saveFailed'), e instanceof Error ? e.message : undefined)
    } finally {
      setBusy(false)
    }
  }

  async function remove() {
    try {
      await deleteSchedule(debt.id)
      await load()
    } catch (e) {
      toast.error(t('common.saveFailed'), e instanceof Error ? e.message : undefined)
    }
  }

  if (items === null) return null
  const preview = Number(parts) >= 1 && Number(parts) <= 60 ? splitEqually(remaining, Number(parts)) : []
  const today = todayIso()

  return (
    <div className="mt-5 p-4 rounded-lg border border-line">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2 text-sm">
          <CalendarRange size={15} className="text-accent2" strokeWidth={1.5} />
          {t('sched.title')}
        </div>
        {items.length > 0 && (
          <button className="text-faint hover:text-bad transition" onClick={() => void remove()} title={t('common.delete')}>
            <Trash2 size={14} />
          </button>
        )}
      </div>

      {items.length === 0 ? (
        remaining > 0 && (
          <div className="mt-3 space-y-3">
            <div className="grid grid-cols-2 gap-2">
              <Field label={t('sched.parts')}>
                <input className="input h-9 num" inputMode="numeric" value={parts} onChange={(e) => setParts(e.target.value)} />
              </Field>
              <Field label={t('sched.first')}>
                <input className="input h-9" type="date" value={first} min={today} onChange={(e) => setFirst(e.target.value)} />
              </Field>
            </div>
            {preview.length > 0 && (
              <div className="text-xs text-muted">
                {t('sched.preview', preview.length, formatMoney(preview[0], locale))}
              </div>
            )}
            <button className="btn-outline h-9 w-full" onClick={() => void create()} disabled={busy || preview.length === 0}>
              {t('sched.create')}
            </button>
          </div>
        )
      ) : (
        <ul className="mt-3 space-y-1">
          {items.map((inst, i) => {
            const done = !!inst.payment_id
            const late = !done && inst.due_date < today
            return (
              <motion.li key={inst.id} initial={{ opacity: 0, x: -6 }} animate={{ opacity: 1, x: 0 }} transition={{ delay: i * 0.03 }} className={`flex items-center gap-3 px-2 py-1.5 rounded-lg ${done ? 'opacity-60' : 'hover:bg-raised'}`}>
                <button
                  className={`w-6 h-6 rounded-full border flex items-center justify-center transition ${done ? 'bg-good border-good text-bg' : 'border-line hover:border-ink'}`}
                  disabled={done || busy || remaining <= 0}
                  onClick={() => void pay(inst)}
                  aria-label={t('sched.markPaid')}
                >
                  {done && <Check size={13} strokeWidth={2.5} />}
                </button>
                <div className="flex-1 min-w-0 text-sm">
                  <span className={done ? 'line-through' : ''}>{formatDate(inst.due_date, locale)}</span>
                  <span className="text-muted"> · {inst.seq}/{items.length}</span>
                  {late && <span className="ml-2 chip h-5 text-bad border-bad/40">{t('status.OVERDUE')}</span>}
                </div>
                <span className="text-sm num">{formatMoney(inst.amount, locale)}</span>
              </motion.li>
            )
          })}
        </ul>
      )}
    </div>
  )
}
