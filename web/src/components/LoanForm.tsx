import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { HandCoins, HandHelping } from 'lucide-react'
import { useI18n } from '../lib/i18n'
import { useToast } from '../lib/toast'
import { parseAmount } from '../lib/money'
import { todayIso } from '../lib/dates'
import { createLoan } from '../api/social'
import type { Profile } from '../api/types'
import { Field, Modal } from './ui'
import { Select } from './Select'

export const cardOk = (c: string) => /^\d{16}$/.test(c.replace(/\s/g, ''))
export const formatCard = (c: string) =>
  c
    .replace(/\D/g, '')
    .slice(0, 16)
    .replace(/(\d{4})(?=\d)/g, '$1 ')

export function LoanForm({ open, onClose, kind, friends, initialFriendId }: { open: boolean; onClose: () => void; kind: 'lend' | 'borrow'; friends: Profile[]; initialFriendId?: string }) {
  const { t } = useI18n()
  const toast = useToast()
  const navigate = useNavigate()
  const [friendId, setFriendId] = useState(initialFriendId ?? friends[0]?.id ?? '')
  const [amount, setAmount] = useState('')
  const [purpose, setPurpose] = useState('')
  const [due, setDue] = useState('')
  const [rate, setRate] = useState('')
  const [card, setCard] = useState('')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [busy, setBusy] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    const parsed = parseAmount(amount)
    const errs: Record<string, string> = {}
    if (!friendId) errs.friend = 'required'
    if (parsed === null) errs.amount = 'amount'
    if (!purpose.trim()) errs.purpose = 'required'
    if (!due) errs.due = 'required'
    else if (due <= todayIso()) errs.due = 'pastDue'
    if (kind === 'borrow' && !cardOk(card)) errs.card = 'card'
    const rateNum = rate.trim() ? Number(rate.replace(',', '.')) : null
    if (rateNum !== null && (!Number.isFinite(rateNum) || rateNum < 0 || rateNum > 100)) errs.rate = 'rate'
    setErrors(errs)
    if (Object.keys(errs).length) return
    setBusy(true)
    try {
      const loan = await createLoan({ friendId, kind, amount: parsed!, purpose: purpose.trim(), due_date: due, interest_rate: rate.trim() ? Number(rate.replace(',', '.')) : null, card: kind === 'borrow' ? card.replace(/\s/g, '') : undefined })
      toast.success(t('loans.new.sent'))
      onClose()
      navigate(`/loans/${loan.id}`)
    } catch (err) {
      toast.error(t('common.saveFailed'), err instanceof Error ? err.message : undefined)
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal open={open} onClose={onClose} title={kind === 'lend' ? t('loans.new.lend') : t('loans.new.borrow')}>
      <form onSubmit={submit} className="space-y-4">
        <div className={`flex items-center gap-3 p-3 rounded-xl ${kind === 'lend' ? 'bg-accent2/10 text-accent2' : 'bg-accent/10 text-accent'}`}>
          {kind === 'lend' ? <HandCoins /> : <HandHelping />}
          <div className="text-sm font-medium text-ink">{kind === 'lend' ? t('loans.youLend') : t('loans.youBorrow')}</div>
        </div>
        <Field label={t('loans.new.friend')} error={errors.friend && t('err.required')}>
          <Select<string> value={friendId} onChange={setFriendId} options={friends.map((f) => ({ value: f.id, label: f.display_name, hint: '@' + f.username }))} />
        </Field>
        <div className="grid sm:grid-cols-2 gap-4">
          <Field label={t('loans.amount')} error={errors.amount && t('err.amount')}>
            <input className="input num" inputMode="decimal" value={amount} onChange={(e) => setAmount(e.target.value)} placeholder="0" autoFocus />
          </Field>
          <Field label={t('loans.due')} error={errors.due && t(`err.${errors.due}`)}>
            <input className="input" type="date" min={todayIso()} value={due} onChange={(e) => setDue(e.target.value)} />
          </Field>
        </div>
        <div className="grid sm:grid-cols-[1fr_140px] gap-4">
          <Field label={t('loans.purpose')} error={errors.purpose && t('err.required')}>
            <input className="input" value={purpose} onChange={(e) => setPurpose(e.target.value)} maxLength={200} />
          </Field>
          <Field label={t('form.rate')} optional={t('common.optional')} error={errors.rate && t('err.rate')}>
            <input className="input num" inputMode="decimal" value={rate} onChange={(e) => setRate(e.target.value)} placeholder="0" />
          </Field>
        </div>
        <div className="text-xs text-faint -mt-2">{t('loans.rateHint')}</div>
        {kind === 'borrow' && (
          <Field label={t('loans.card')} hint={t('loans.cardHint')} error={errors.card && t('err.card')}>
            <input
              className="input font-mono tracking-wider"
              inputMode="numeric"
              value={card}
              onChange={(e) => setCard(formatCard(e.target.value))}
              placeholder="0000 0000 0000 0000"
              autoComplete="off"
            />
          </Field>
        )}
        <div className="flex justify-end gap-2 pt-1">
          <button type="button" className="btn-ghost" onClick={onClose}>
            {t('common.cancel')}
          </button>
          <button className="btn-primary" disabled={busy || friends.length === 0}>
            {busy ? t('common.loading') : t('loans.create')}
          </button>
        </div>
      </form>
    </Modal>
  )
}
