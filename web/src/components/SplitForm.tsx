import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { Users, Check } from 'lucide-react'
import { useI18n } from '../lib/i18n'
import { useToast } from '../lib/toast'
import { useData } from '../lib/data'
import { parseAmount, formatMoney } from '../lib/money'
import { todayIso } from '../lib/dates'
import { createSplit, splitEqually } from '../api/extra'
import type { Profile } from '../api/types'
import { Avatar, Field, Modal } from './ui'

// "We chipped in": the payer enters the total and picks friends; everyone, payer included,
// owes an equal share. Debts for each friend appear immediately on both sides.
export function SplitForm({ open, onClose, friends }: { open: boolean; onClose: () => void; friends: Profile[] }) {
  const { t, locale } = useI18n()
  const toast = useToast()
  const navigate = useNavigate()
  const { reload } = useData()
  const [title, setTitle] = useState('')
  const [total, setTotal] = useState('')
  const [due, setDue] = useState('')
  const [picked, setPicked] = useState<string[]>([])
  const [busy, setBusy] = useState(false)
  const [errors, setErrors] = useState<Record<string, string>>({})

  const parsed = parseAmount(total)
  const shares = parsed && picked.length ? splitEqually(parsed, picked.length + 1) : []
  const toggle = (id: string) => setPicked((xs) => (xs.includes(id) ? xs.filter((x) => x !== id) : [...xs, id]))

  async function submit(e: FormEvent) {
    e.preventDefault()
    const errs: Record<string, string> = {}
    if (!title.trim()) errs.title = 'required'
    if (parsed === null) errs.total = 'amount'
    if (picked.length === 0) errs.members = 'required'
    if (due && due < todayIso()) errs.due = 'pastDue'
    setErrors(errs)
    if (Object.keys(errs).length) return
    setBusy(true)
    try {
      await createSplit(title.trim(), parsed!, picked, due || null)
      await reload()
      toast.success(t('split.done'))
      onClose()
      navigate('/debts?f=LENT')
    } catch (err) {
      toast.error(t('common.saveFailed'), err instanceof Error ? err.message : undefined)
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal open={open} onClose={onClose} title={t('split.title')}>
      <form onSubmit={submit} className="space-y-4">
        <div className="flex items-center gap-3 p-3 rounded-lg border border-line text-sm text-muted">
          <Users size={18} className="text-accent2" strokeWidth={1.5} />
          {t('split.hint')}
        </div>
        <Field label={t('form.title')} error={errors.title && t('err.required')}>
          <input className="input" value={title} onChange={(e) => setTitle(e.target.value)} maxLength={200} placeholder={t('split.placeholder')} autoFocus />
        </Field>
        <div className="grid sm:grid-cols-2 gap-4">
          <Field label={t('split.total')} error={errors.total && t('err.amount')}>
            <input className="input num" inputMode="decimal" value={total} onChange={(e) => setTotal(e.target.value)} placeholder="0" />
          </Field>
          <Field label={t('form.dueDate')} optional={t('common.optional')} error={errors.due && t('err.pastDue')}>
            <input className="input" type="date" min={todayIso()} value={due} onChange={(e) => setDue(e.target.value)} />
          </Field>
        </div>
        <Field label={t('split.members')} error={errors.members && t('err.required')} hint={shares.length ? t('split.each', formatMoney(shares[0], locale)) : undefined}>
          <div className="flex flex-wrap gap-2">
            {friends.map((f) => {
              const on = picked.includes(f.id)
              return (
                <button type="button" key={f.id} onClick={() => toggle(f.id)} className={`flex items-center gap-2 h-9 pl-1 pr-3 rounded-full border transition ${on ? 'border-ink bg-ink text-bg' : 'border-line hover:bg-raised'}`}>
                  <Avatar profile={f} size={26} />
                  <span className="text-sm">{f.display_name}</span>
                  {on && <Check size={14} />}
                </button>
              )
            })}
            {friends.length === 0 && <span className="text-sm text-muted">{t('friends.none')}</span>}
          </div>
        </Field>
        <div className="flex justify-end gap-2 pt-1">
          <button type="button" className="btn-ghost" onClick={onClose}>
            {t('common.cancel')}
          </button>
          <button className="btn-primary" disabled={busy || friends.length === 0}>
            {busy ? t('common.loading') : t('split.create')}
          </button>
        </div>
      </form>
    </Modal>
  )
}
