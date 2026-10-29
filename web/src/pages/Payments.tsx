import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { AnimatePresence, motion } from 'framer-motion'
import { Plus, Search, Trash2, Download, Receipt } from 'lucide-react'
import { useI18n } from '../lib/i18n'
import { useData } from '../lib/data'
import { useToast } from '../lib/toast'
import { formatMoney, sum } from '../lib/money'
import { formatDate, todayIso } from '../lib/dates'
import { deletePayment, syncStatus } from '../api/debts'
import { remainingFor } from '../lib/calc'
import { exportPaymentsCsv } from '../lib/csv'
import { Confirm, Empty, PageHeader, Segmented, Skeleton } from '../components/ui'
import { PaymentForm } from '../components/forms'

type Period = 'ALL' | 'MONTH' | 'QUARTER' | 'YEAR'

export function PaymentsPage() {
  const { t, locale } = useI18n()
  const { debts, payments, loading, error, reload } = useData()
  const toast = useToast()
  const navigate = useNavigate()
  const [q, setQ] = useState('')
  const [period, setPeriod] = useState<Period>('ALL')
  const [adding, setAdding] = useState(false)
  const [deleting, setDeleting] = useState<number | null>(null)

  const byId = useMemo(() => Object.fromEntries(debts.map((d) => [d.id, d])), [debts])

  const rows = useMemo(() => {
    const today = todayIso()
    const from = new Date(today + 'T00:00:00')
    if (period === 'MONTH') from.setDate(1)
    if (period === 'QUARTER') from.setMonth(from.getMonth() - 3)
    if (period === 'YEAR') from.setFullYear(from.getFullYear() - 1)
    const fromIso = from.toISOString().slice(0, 10)
    const s = q.trim().toLowerCase()
    return payments.filter((p) => {
      if (period !== 'ALL' && p.payment_date < fromIso) return false
      const d = byId[p.debt_id]
      if (s && !`${d?.title ?? ''} ${d?.creditor_name ?? ''} ${p.notes ?? ''}`.toLowerCase().includes(s)) return false
      return true
    })
  }, [payments, byId, q, period])

  const total = sum(rows.map((r) => r.amount))
  const target = payments.find((p) => p.id === deleting)

  async function doDelete() {
    if (!target) return
    try {
      await deletePayment(target.id)
      const d = byId[target.debt_id]
      if (d) await syncStatus(d, remainingFor(d, payments) + Number(target.amount))
      await reload()
      toast.success(t('common.deleted'))
    } catch (e) {
      toast.error(t('common.saveFailed'), e instanceof Error ? e.message : undefined)
    } finally {
      setDeleting(null)
    }
  }

  return (
    <div>
      <PageHeader
        title={t('nav.payments')}
        actions={
          <>
            <button className="btn-outline hidden sm:inline-flex" onClick={() => exportPaymentsCsv(rows, debts, t)}>
              <Download size={16} />
              {t('common.exportCsv')}
            </button>
            <button className="btn-primary" onClick={() => setAdding(true)}>
              <Plus size={16} />
              {t('payments.new')}
            </button>
          </>
        }
      />

      <div className="grid gap-3 sm:grid-cols-3 mb-4">
        {[
          { k: 'payments.total', v: formatMoney(total, locale) },
          { k: 'payments.count', v: rows.length },
          { k: 'payments.average', v: formatMoney(rows.length ? total / rows.length : 0, locale) },
        ].map((s, i) => (
          <motion.div key={s.k} initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: i * 0.05 }} className="card p-4">
            <div className="text-xs text-muted">{t(s.k)}</div>
            <div className="text-xl num mt-1">{s.v}</div>
          </motion.div>
        ))}
      </div>

      <div className="flex flex-col md:flex-row gap-3 mb-4">
        <div className="relative flex-1">
          <Search size={16} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-faint" />
          <input className="input pl-10" placeholder={t('payments.search')} value={q} onChange={(e) => setQ(e.target.value)} />
        </div>
        <Segmented value={period} onChange={setPeriod} options={(['ALL', 'MONTH', 'QUARTER', 'YEAR'] as Period[]).map((p) => ({ value: p, label: t(`payments.period.${p}`) }))} />
      </div>

      <div className="card overflow-hidden">
        {loading ? (
          <div className="p-4 space-y-3">
            {[0, 1, 2].map((i) => (
              <Skeleton key={i} className="h-12" />
            ))}
          </div>
        ) : error ? (
          <Empty
            title={t('common.loadFailed')}
            text={error}
            action={
              <button className="btn-primary" onClick={() => void reload()}>
                {t('common.retry')}
              </button>
            }
          />
        ) : rows.length === 0 ? (
          <Empty icon={<Receipt />} title={payments.length ? t('common.nothing') : t('payments.empty.title')} text={payments.length ? undefined : t('payments.empty.text')} />
        ) : (
          <table className="w-full">
            <thead>
              <tr>
                <th className="th">{t('payments.col.date')}</th>
                <th className="th">{t('payments.col.debt')}</th>
                <th className="th text-right">{t('payments.col.amount')}</th>
                <th className="th w-12" />
              </tr>
            </thead>
            <tbody>
              <AnimatePresence initial={false}>
                {rows.map((p) => (
                  <motion.tr key={p.id} layout initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} className="group hover:bg-raised transition">
                    <td className="td whitespace-nowrap text-muted">{formatDate(p.payment_date, locale)}</td>
                    <td className="td">
                      <button className="text-left hover:text-accent" onClick={() => navigate(`/debts?id=${p.debt_id}`)}>
                        <div className="font-medium">{byId[p.debt_id]?.title ?? '—'}</div>
                        <div className="text-xs text-muted">
                          {byId[p.debt_id]?.creditor_name}
                          {p.notes && ` · ${p.notes}`}
                          {p.mirror_of && ` · ${t('payments.mirror')}`}
                        </div>
                      </button>
                    </td>
                    <td className="td text-right num text-good">{formatMoney(p.amount, locale)}</td>
                    <td className="td">
                      {!p.mirror_of && (
                        <button className="opacity-0 group-hover:opacity-100 text-faint hover:text-bad transition" onClick={() => setDeleting(p.id)}>
                          <Trash2 size={15} />
                        </button>
                      )}
                    </td>
                  </motion.tr>
                ))}
              </AnimatePresence>
            </tbody>
          </table>
        )}
      </div>

      {adding && <PaymentForm open onClose={() => setAdding(false)} />}
      <Confirm
        open={!!target}
        onClose={() => setDeleting(null)}
        onConfirm={doDelete}
        title={t('payments.delete.title')}
        text={t('payments.delete.text', formatMoney(target?.amount ?? 0, locale), formatDate(target?.payment_date, locale))}
        confirmLabel={t('common.delete')}
        cancelLabel={t('common.cancel')}
      />
    </div>
  )
}
