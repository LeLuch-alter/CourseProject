import { useMemo, useState, type FormEvent } from 'react'
import { useI18n } from '../lib/i18n'
import { useToast } from '../lib/toast'
import { useData } from '../lib/data'
import { parseAmount, formatMoney, CURRENCIES, CURRENCY_SYMBOL, type Currency } from '../lib/money'
import { useAuth } from '../lib/auth'
import { Camera, X } from 'lucide-react'
import { todayIso } from '../lib/dates'
import { validateDebt, validatePayment, remainingFor, effectiveStatus } from '../lib/calc'
import { createDebt, updateDebt, createPayment, syncStatus, uploadReceipt } from '../api/debts'
import type { Debt, DebtStatus } from '../api/types'
import { Field, Modal } from './ui'
import { Select } from './Select'

export function DebtForm({ open, onClose, debt }: { open: boolean; onClose: () => void; debt?: Debt | null }) {
  const { t } = useI18n()
  const toast = useToast()
  const { reload } = useData()
  const [title, setTitle] = useState(debt?.title ?? '')
  const [creditor, setCreditor] = useState(debt?.creditor_name ?? '')
  const [amount, setAmount] = useState(debt ? String(debt.original_amount) : '')
  const [rate, setRate] = useState(debt?.interest_rate ?? '')
  const [dueDate, setDueDate] = useState(debt?.due_date ?? '')
  const [status, setStatus] = useState<DebtStatus>(debt?.status ?? 'ACTIVE')
  const [notes, setNotes] = useState(debt?.notes ?? '')
  const [currency, setCurrency] = useState<Currency>((debt?.currency as Currency) ?? 'KZT')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [busy, setBusy] = useState(false)
  const linked = !!debt?.loan_id

  async function submit(e: FormEvent) {
    e.preventDefault()
    const parsed = parseAmount(amount)
    // Editing keeps a past due date; only new dates are checked against today.
    const errs = validateDebt({ title, creditor, amount: parsed, dueDate: debt && dueDate === debt.due_date ? '' : dueDate, rate: String(rate) })
    setErrors(errs)
    if (Object.keys(errs).length) return
    setBusy(true)
    try {
      const input = {
        title: title.trim(),
        creditor_name: creditor.trim(),
        original_amount: parsed!,
        interest_rate: String(rate).trim() ? Number(String(rate).replace(',', '.')) : null,
        due_date: dueDate || null,
        status,
        notes: notes.trim() || null,
        currency,
      }
      if (debt) await updateDebt(debt.id, input)
      else await createDebt(input)
      await reload()
      toast.success(t('common.saved'), title.trim())
      onClose()
    } catch (err) {
      toast.error(t('common.saveFailed'), err instanceof Error ? err.message : undefined)
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal open={open} onClose={onClose} title={debt ? t('form.debt.edit') : t('form.debt.new')}>
      <form onSubmit={submit} className="space-y-4">
        <Field label={t('form.title')} error={errors.title && t(`err.${errors.title}`)}>
          <input className="input" value={title} onChange={(e) => setTitle(e.target.value)} placeholder={t('form.title.ph')} maxLength={200} autoFocus />
        </Field>
        <Field label={t('form.creditor')} error={errors.creditor && t(`err.${errors.creditor}`)}>
          <input className="input" value={creditor} onChange={(e) => setCreditor(e.target.value)} placeholder={t('form.creditor.ph')} maxLength={200} disabled={linked} />
        </Field>
        <div className="grid sm:grid-cols-2 gap-4">
          <Field label={t('form.amount')} error={errors.amount && t(`err.${errors.amount}`)}>
            <div className="flex gap-2">
              <input className="input num" inputMode="decimal" value={amount} onChange={(e) => setAmount(e.target.value)} placeholder="0" disabled={linked} />
              <Select<Currency> className="w-24 shrink-0" value={currency} onChange={setCurrency} disabled={linked} options={CURRENCIES.map((c) => ({ value: c, label: `${CURRENCY_SYMBOL[c]} ${c}` }))} />
            </div>
          </Field>
          <Field label={t('form.rate')} optional={t('common.optional')} error={errors.rate && t(`err.${errors.rate}`)} hint={t('form.rate.hint')}>
            <input className="input num" inputMode="decimal" value={rate} onChange={(e) => setRate(e.target.value)} placeholder="0" />
          </Field>
        </div>
        <div className="grid sm:grid-cols-2 gap-4">
          <Field label={t('form.dueDate')} optional={t('common.optional')} error={errors.dueDate && t(`err.${errors.dueDate}`)}>
            <input className="input" type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} />
          </Field>
          <Field label={t('form.status')}>
            <Select<DebtStatus> value={status} onChange={setStatus} disabled={linked} options={(['ACTIVE', 'PAID', 'CANCELLED'] as DebtStatus[]).map((s) => ({ value: s, label: t(`status.${s}`) }))} />
          </Field>
        </div>
        <Field label={t('form.notes')} optional={t('common.optional')}>
          <textarea className="input h-24 py-2.5 resize-none" value={notes} onChange={(e) => setNotes(e.target.value)} maxLength={1000} />
        </Field>
        <div className="flex justify-end gap-2 pt-1">
          <button type="button" className="btn-ghost" onClick={onClose}>
            {t('common.cancel')}
          </button>
          <button className="btn-primary" disabled={busy}>
            {busy ? t('common.loading') : t('common.save')}
          </button>
        </div>
      </form>
    </Modal>
  )
}

export function PaymentForm({ open, onClose, debtId }: { open: boolean; onClose: () => void; debtId?: number | null }) {
  const { t } = useI18n()
  const toast = useToast()
  const { debts, payments, reload } = useData()
  const payable = useMemo(
    () => debts.filter((d) => (d.direction === 'OWED' ? effectiveStatus(d, payments) !== 'PAID' && d.status !== 'CANCELLED' : d.status !== 'CANCELLED' && effectiveStatus(d, payments) !== 'PAID')),
    [debts, payments],
  )
  const [selected, setSelected] = useState<number>(debtId ?? payable[0]?.id ?? 0)
  const [amount, setAmount] = useState('')
  const [date, setDate] = useState(todayIso())
  const [notes, setNotes] = useState('')
  const [photo, setPhoto] = useState<File | null>(null)
  const { user } = useAuth()
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [busy, setBusy] = useState(false)

  const debt = debts.find((d) => d.id === selected)
  const cur = debt?.currency ?? 'KZT'
  const remaining = debt ? remainingFor(debt, payments) : 0

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (!debt) return
    const parsed = parseAmount(amount)
    const errs = validatePayment(parsed, date, remaining, debt.status)
    setErrors(errs)
    if (Object.keys(errs).length) return
    setBusy(true)
    try {
      const receipt_path = photo && user ? await uploadReceipt(user.id, photo) : null
      await createPayment({ debt_id: debt.id, amount: parsed!, payment_date: date, notes: notes.trim() || null, receipt_path })
      const left = remaining - parsed!
      await syncStatus(debt, Math.max(0, Math.round(left * 100) / 100))
      await reload()
      if (left <= 0) toast.success(t('toast.paidOff', debt.title))
      else toast.success(t('common.saved'), formatMoney(parsed!, undefined, cur))
      onClose()
    } catch (err) {
      toast.error(t('common.saveFailed'), err instanceof Error ? err.message : undefined)
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal open={open} onClose={onClose} title={t('form.payment.new')}>
      <form onSubmit={submit} className="space-y-4">
        <Field label={t('form.payment.debt')} hint={debt ? t('form.payment.remaining', formatMoney(remaining, undefined, cur)) : undefined}>
          <Select<number> value={selected} onChange={setSelected} options={payable.map((d) => ({ value: d.id, label: d.title, hint: d.creditor_name }))} />
        </Field>
        <Field label={t('form.amount')} error={(errors.amount && t(`err.${errors.amount}`)) || (errors.form && t(`err.${errors.form}`))}>
          <div className="flex gap-2">
            <input className="input num" inputMode="decimal" value={amount} onChange={(e) => setAmount(e.target.value)} placeholder="0" autoFocus />
            <button type="button" className="btn-outline shrink-0" onClick={() => setAmount(String(remaining))}>
              {t('form.payment.all')}
            </button>
          </div>
        </Field>
        <Field label={t('form.date')} error={errors.date && t(`err.${errors.date}`)}>
          <input className="input" type="date" value={date} max={todayIso()} onChange={(e) => setDate(e.target.value)} />
        </Field>
        <Field label={t('form.notes')} optional={t('common.optional')}>
          <input className="input" value={notes} onChange={(e) => setNotes(e.target.value)} maxLength={1000} />
        </Field>
        <Field label={t('receipt.label')} optional={t('common.optional')} hint={t('receipt.hint')}>
          {photo ? (
            <div className="flex items-center gap-2 h-11 px-3 rounded-lg border border-line text-sm">
              <Camera size={15} className="text-accent2" />
              <span className="flex-1 truncate">{photo.name}</span>
              <button type="button" className="text-faint hover:text-ink" onClick={() => setPhoto(null)} aria-label={t('common.delete')}><X size={15} /></button>
            </div>
          ) : (
            <label className="btn-outline w-full cursor-pointer">
              <Camera size={15} />
              {t('receipt.add')}
              <input type="file" accept="image/png,image/jpeg,image/webp" capture="environment" className="hidden" onChange={(e) => {
                const f = e.target.files?.[0]
                if (f && f.size > 5 * 1024 * 1024) toast.error(t('receipt.tooBig'))
                else setPhoto(f ?? null)
              }} />
            </label>
          )}
        </Field>
        <div className="flex justify-end gap-2 pt-1">
          <button type="button" className="btn-ghost" onClick={onClose}>
            {t('common.cancel')}
          </button>
          <button className="btn-primary" disabled={busy || !debt}>
            {busy ? t('common.loading') : t('common.save')}
          </button>
        </div>
      </form>
    </Modal>
  )
}
