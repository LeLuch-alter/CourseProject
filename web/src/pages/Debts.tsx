import { useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { AnimatePresence, motion } from 'framer-motion'
import { Plus, Search, Pencil, Trash2, Receipt, X, FileSignature, Download, Calculator, HandCoins } from 'lucide-react'
import { useI18n } from '../lib/i18n'
import { useData } from '../lib/data'
import { useToast } from '../lib/toast'
import { formatMoney, parseAmount } from '../lib/money'
import { formatDate, daysBetween, todayIso } from '../lib/dates'
import { effectiveStatus, remainingFor, paidFor, progressFor, monthsToPayoff, paymentToMeetDeadline, type EffectiveStatus } from '../lib/calc'
import { deleteDebt, deletePayment, syncStatus } from '../api/debts'
import { exportCsv } from '../lib/csv'
import type { Debt } from '../api/types'
import { Confirm, Empty, PageHeader, Progress, Skeleton, StatusChip, Segmented } from '../components/ui'
import { DebtForm, PaymentForm } from '../components/forms'
import { Schedule } from '../components/Schedule'
import { ReceiptLink } from '../components/ReceiptLink'

type Filter = 'ALL' | 'OWED' | 'LENT' | 'ACTIVE' | 'OVERDUE' | 'PAID' | 'CANCELLED'

export function DebtsPage() {
  const { t, locale } = useI18n()
  const { debts, payments, loading, error, reload } = useData()
  const toast = useToast()
  const [params, setParams] = useSearchParams()
  const [q, setQ] = useState('')
  const [filter, setFilter] = useState<Filter>((params.get('f') as Filter) || 'ALL')
  const selectedId = Number(params.get('id')) || null
  const [editing, setEditing] = useState<Debt | null | undefined>(undefined)
  const [paying, setPaying] = useState<number | null>(null)
  const [deleting, setDeleting] = useState<Debt | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    const f = params.get('f') as Filter | null
    if (f) setFilter(f)
  }, [params])

  const rows = useMemo(() => {
    const s = q.trim().toLowerCase()
    return debts.filter((d) => {
      const st = effectiveStatus(d, payments)
      if (filter === 'OWED' && d.direction !== 'OWED') return false
      if (filter === 'LENT' && d.direction !== 'LENT') return false
      if (['ACTIVE', 'OVERDUE', 'PAID', 'CANCELLED'].includes(filter) && st !== filter) return false
      if (s && !`${d.title} ${d.creditor_name} ${d.notes ?? ''}`.toLowerCase().includes(s)) return false
      return true
    })
  }, [debts, payments, q, filter])

  const selected = debts.find((d) => d.id === selectedId) ?? null
  const select = (id: number | null) => {
    const p = new URLSearchParams(params)
    if (id) p.set('id', String(id))
    else p.delete('id')
    setParams(p, { replace: true })
  }

  async function doDelete() {
    if (!deleting) return
    setBusy(true)
    try {
      await deleteDebt(deleting.id)
      await reload()
      toast.success(t('common.deleted'), deleting.title)
      if (selectedId === deleting.id) select(null)
      setDeleting(null)
    } catch (e) {
      toast.error(t('common.saveFailed'), e instanceof Error ? e.message : undefined)
    } finally {
      setBusy(false)
    }
  }

  const filters: Filter[] = ['ALL', 'OWED', 'LENT', 'ACTIVE', 'OVERDUE', 'PAID', 'CANCELLED']

  return (
    <div>
      <PageHeader
        title={t('nav.debts')}
        subtitle={t('debts.count', rows.length, debts.length)}
        actions={
          <>
            <button className="btn-outline hidden sm:inline-flex" onClick={() => exportCsv(debts, payments, t)}>
              <Download size={16} />
              {t('common.exportCsv')}
            </button>
            <button className="btn-primary" onClick={() => setEditing(null)}>
              <Plus size={16} />
              {t('debts.new')}
            </button>
          </>
        }
      />

      <div className="flex flex-col md:flex-row gap-3 mb-4">
        <div className="relative flex-1">
          <Search size={16} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-faint" />
          <input className="input pl-10" placeholder={t('debts.search')} value={q} onChange={(e) => setQ(e.target.value)} />
        </div>
        <div className="overflow-x-auto -mx-1 px-1">
          <Segmented value={filter} onChange={setFilter} options={filters.map((f) => ({ value: f, label: t(`debts.filter.${f}`) }))} />
        </div>
      </div>

      <div className={`grid gap-4 ${selected ? 'xl:grid-cols-[1fr_400px]' : ''}`}>
        <div className="card overflow-hidden">
          {loading ? (
            <div className="p-4 space-y-3">
              {[0, 1, 2, 3].map((i) => (
                <Skeleton key={i} className="h-14" />
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
            <Empty
              title={debts.length ? t('common.nothing') : t('debts.empty.title')}
              text={debts.length ? undefined : t('debts.empty.text')}
              action={
                !debts.length && (
                  <button className="btn-primary" onClick={() => setEditing(null)}>
                    <Plus size={16} />
                    {t('debts.new')}
                  </button>
                )
              }
            />
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[720px]">
                <thead>
                  <tr>
                    <th className="th">{t('debts.col.title')}</th>
                    <th className="th">{t('debts.col.creditor')}</th>
                    <th className="th text-right">{t('debts.col.amount')}</th>
                    <th className="th w-[180px]">{t('debts.col.remaining')}</th>
                    <th className="th">{t('debts.col.due')}</th>
                    <th className="th">{t('debts.col.status')}</th>
                  </tr>
                </thead>
                <tbody>
                  <AnimatePresence initial={false}>
                    {rows.map((d) => {
                      const st = effectiveStatus(d, payments),
                        rem = remainingFor(d, payments),
                        prog = progressFor(d, payments)
                      return (
                        <motion.tr
                          key={d.id}
                          layout
                          initial={{ opacity: 0 }}
                          animate={{ opacity: 1 }}
                          exit={{ opacity: 0 }}
                          onClick={() => select(d.id)}
                          className={`cursor-pointer transition ${selectedId === d.id ? 'bg-accent/10' : 'hover:bg-raised'}`}
                        >
                          <td className="td">
                            <div className="flex items-center gap-2">
                              <span className={`w-1.5 h-6 rounded-full ${d.direction === 'LENT' ? 'bg-accent2' : 'bg-accent'}`} />
                              <div>
                                <div className="font-medium">{d.title}</div>
                                {d.loan_id && (
                                  <div className="text-[11px] text-faint flex items-center gap-1">
                                    <FileSignature size={11} />
                                    {t('debts.linked')}
                                  </div>
                                )}
                              </div>
                            </div>
                          </td>
                          <td className="td text-muted">
                            {d.creditor_name}
                            {d.direction === 'LENT' && <span className="chip h-5 px-1.5 text-[10px] whitespace-nowrap mt-1 block w-fit text-accent2">{t("debts.lentBadge")}</span>}
                          </td>
                          <td className="td text-right num">{formatMoney(d.original_amount, locale, d.currency)}</td>
                          <td className="td">
                            <div className="num ">{formatMoney(rem, locale, d.currency)}</div>
                            <Progress value={prog} className="mt-1.5 h-1.5" color={st === 'OVERDUE' ? 'bg-bad' : st === 'PAID' ? 'bg-good' : 'bg-accent'} />
                          </td>
                          <td className="td text-muted whitespace-nowrap">{d.due_date ? formatDate(d.due_date, locale) : t('due.none')}</td>
                          <td className="td">
                            <StatusChip status={st} label={t(`status.${st}`)} />
                          </td>
                        </motion.tr>
                      )
                    })}
                  </AnimatePresence>
                </tbody>
              </table>
            </div>
          )}
        </div>

        <AnimatePresence>
          {selected && (
            <motion.aside
              key={selected.id}
              initial={{ opacity: 0, x: 24 }}
              animate={{ opacity: 1, x: 0 }}
              exit={{ opacity: 0, x: 24 }}
              transition={{ type: 'spring', stiffness: 300, damping: 30 }}
              className="card p-5 h-fit xl:sticky xl:top-20"
            >
              <Inspector debt={selected} onClose={() => select(null)} onEdit={() => setEditing(selected)} onPay={() => setPaying(selected.id)} onDelete={() => setDeleting(selected)} />
            </motion.aside>
          )}
        </AnimatePresence>
      </div>

      {editing !== undefined && <DebtForm open onClose={() => setEditing(undefined)} debt={editing} />}
      {paying !== null && <PaymentForm open onClose={() => setPaying(null)} debtId={paying} />}
      <Confirm
        open={!!deleting}
        onClose={() => setDeleting(null)}
        onConfirm={doDelete}
        busy={busy}
        title={t('debts.delete.title')}
        text={t('debts.delete.text', deleting?.title ?? '')}
        confirmLabel={t('common.delete')}
        cancelLabel={t('common.cancel')}
      />
    </div>
  )
}

function Inspector({ debt, onClose, onEdit, onPay, onDelete }: { debt: Debt; onClose: () => void; onEdit: () => void; onPay: () => void; onDelete: () => void }) {
  const { t, locale } = useI18n()
  const { payments, reload } = useData()
  const toast = useToast()
  const [perMonth, setPerMonth] = useState('')
  const [delPay, setDelPay] = useState<number | null>(null)
  const st: EffectiveStatus = effectiveStatus(debt, payments)
  const rem = remainingFor(debt, payments),
    paid = paidFor(debt, payments),
    prog = progressFor(debt, payments)
  const history = payments.filter((p) => p.debt_id === debt.id)
  const months = monthsToPayoff(rem, parseAmount(perMonth) ?? 0)
  const days = debt.due_date ? daysBetween(todayIso(), debt.due_date) : null
  const canPay = st !== 'PAID' && st !== 'CANCELLED'

  async function removePayment(id: number) {
    try {
      await deletePayment(id)
      const p = payments.find((x) => x.id === id)
      await syncStatus(debt, rem + Number(p?.amount ?? 0))
      await reload()
      toast.success(t('common.deleted'))
    } catch (e) {
      toast.error(t('common.saveFailed'), e instanceof Error ? e.message : undefined)
    } finally {
      setDelPay(null)
    }
  }

  return (
    <div>
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="text-xs text-muted">
            {debt.direction === 'LENT' ? t('debts.col.debtor') : t('debts.col.creditor')}: {debt.creditor_name}
          </div>
          <h2 className="text-xl truncate">{debt.title}</h2>
        </div>
        <button className="btn-icon btn-ghost h-8 w-8" onClick={onClose}>
          <X size={16} />
        </button>
      </div>
      <div className="mt-4 flex items-end justify-between">
        <div>
          <div className="text-xs text-muted">{t('debts.remaining')}</div>
          <div className="text-3xl num">{formatMoney(rem, locale, debt.currency)}</div>
        </div>
        <StatusChip status={st} label={t(`status.${st}`)} />
      </div>
      <Progress value={prog} className="mt-3" color={st === 'OVERDUE' ? 'bg-bad' : st === 'PAID' ? 'bg-good' : 'bg-accent'} />
      <div className="flex justify-between text-xs text-muted mt-1.5">
        <span>{t('debts.repaid', prog)}</span>
        <span className="num">
          {formatMoney(paid, locale, debt.currency)} / {formatMoney(debt.original_amount, locale, debt.currency)}
        </span>
      </div>

      <dl className="grid grid-cols-2 gap-3 mt-5 text-sm">
        <div className="bg-raised rounded-xl p-3">
          <dt className="text-xs text-muted">{t('form.dueDate')}</dt>
          <dd className="font-medium mt-0.5">
            {debt.due_date ? formatDate(debt.due_date, locale) : t('due.none')}
            {days !== null && canPay && (
              <div className={`text-xs ${days < 0 ? 'text-bad' : days <= 14 ? 'text-warn' : 'text-muted'}`}>
                {days < 0 ? t('due.overdue', -days) : days === 0 ? t('due.today') : t('due.inDays', days)}
              </div>
            )}
          </dd>
        </div>
        <div className="bg-raised rounded-xl p-3">
          <dt className="text-xs text-muted">{t('debts.created')}</dt>
          <dd className="font-medium mt-0.5">{formatDate(debt.created_at, locale)}</dd>
        </div>
        {debt.interest_rate && (
          <div className="bg-raised rounded-xl p-3">
            <dt className="text-xs text-muted">{t('form.rate')}</dt>
            <dd className="font-medium mt-0.5 num">{debt.interest_rate} %</dd>
          </div>
        )}
        {debt.notes && (
          <div className="bg-raised rounded-xl p-3 col-span-2">
            <dt className="text-xs text-muted">{t('form.notes')}</dt>
            <dd className="mt-0.5 whitespace-pre-wrap">{debt.notes}</dd>
          </div>
        )}
      </dl>

      {debt.loan_id && (
        <Link to={`/loans/${debt.loan_id}`} className="btn-outline w-full mt-4">
          <FileSignature size={16} />
          {t('debts.openReceipt')}
        </Link>
      )}

      <div className="flex gap-2 mt-4">
        {canPay && (
          <button className="btn-primary flex-1" onClick={onPay}>
            {debt.direction === 'LENT' ? <HandCoins size={16} /> : <Receipt size={16} />}
            {debt.direction === 'LENT' ? t('loans.markPaid') : t('debts.pay')}
          </button>
        )}
        <button className="btn-outline" onClick={onEdit}>
          <Pencil size={16} />
          {t('common.edit')}
        </button>
        {!debt.loan_id && (
          <button className="btn-danger btn-icon" onClick={onDelete} aria-label={t('common.delete')}>
            <Trash2 size={16} />
          </button>
        )}
      </div>

      {canPay && debt.direction === 'OWED' && <Schedule debt={debt} remaining={rem} />}

      {canPay && rem > 0 && (
        <div className="mt-5 p-4 rounded-xl border border-dashed border-line">
          <div className="flex items-center gap-2 text-sm ">
            <Calculator size={15} className="text-accent" />
            {t('debts.planner')}
          </div>
          <div className="flex items-center gap-2 mt-3">
            <input className="input h-9 num" inputMode="decimal" placeholder={t('debts.planner.perMonth')} value={perMonth} onChange={(e) => setPerMonth(e.target.value)} />
            <span className="text-sm text-muted whitespace-nowrap">{months ? t('debts.planner.months', months) : '—'}</span>
          </div>
          {debt.due_date && days !== null && days > 0 && <div className="text-xs text-muted mt-2">{t('debts.planner.toDeadline', formatMoney(paymentToMeetDeadline(rem, debt.due_date), locale))}</div>}
        </div>
      )}

      <div className="mt-5">
        <div className="text-sm mb-2">{t('debts.history')}</div>
        {history.length === 0 ? (
          <div className="text-sm text-muted">{t('debts.noPayments')}</div>
        ) : (
          <ul className="space-y-1">
            {history.map((p) => (
              <li key={p.id} className="group flex items-center gap-3 px-2 py-1.5 rounded-lg hover:bg-raised">
                <div className="w-7 h-7 rounded-lg bg-good/10 text-good flex items-center justify-center">
                  <Receipt size={13} />
                </div>
                <div className="flex-1 min-w-0 text-sm">
                  <span className="font-medium num">{formatMoney(p.amount, locale, debt.currency)}</span>
                  <span className="text-muted"> · {formatDate(p.payment_date, locale)}</span>
                  {p.notes && <div className="text-xs text-muted truncate">{p.notes}</div>}
                  {p.mirror_of && <div className="text-[10px] text-faint">{t('payments.mirror')}</div>}
                </div>
                {p.receipt_path && <ReceiptLink path={p.receipt_path} />}
                {!p.mirror_of && (
                  <button className="opacity-0 group-hover:opacity-100 text-faint hover:text-bad transition" onClick={() => setDelPay(p.id)}>
                    <Trash2 size={14} />
                  </button>
                )}
              </li>
            ))}
          </ul>
        )}
      </div>
      <Confirm
        open={delPay !== null}
        onClose={() => setDelPay(null)}
        onConfirm={() => delPay && void removePayment(delPay)}
        title={t('payments.delete.title')}
        text={t('payments.delete.text', formatMoney(payments.find((p) => p.id === delPay)?.amount ?? 0, locale), formatDate(payments.find((p) => p.id === delPay)?.payment_date, locale))}
        confirmLabel={t('common.delete')}
        cancelLabel={t('common.cancel')}
      />
    </div>
  )
}
