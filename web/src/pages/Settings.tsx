import { motion } from 'framer-motion'
import { Download, Keyboard, Info, BellRing } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useAuth } from '../lib/auth'
import { useToast } from '../lib/toast'
import { updateReminderSettings } from '../api/extra'
import { useI18n } from '../lib/i18n'
import { useData } from '../lib/data'
import { exportCsv, exportPaymentsCsv } from '../lib/csv'
import { PageHeader, stagger } from '../components/ui'

export function SettingsPage() {
  const { t } = useI18n()
  const { debts, payments } = useData()
  const { user, profile, refreshProfile } = useAuth()
  const toast = useToast()
  const [days, setDays] = useState(String(profile?.reminder_days ?? 14))
  const [email, setEmail] = useState(!!profile?.email_reminders)
  useEffect(() => {
    setDays(String(profile?.reminder_days ?? 14))
    setEmail(!!profile?.email_reminders)
  }, [profile])
  async function saveReminders() {
    const n = Number(days)
    if (!Number.isInteger(n) || n < 0 || n > 90) return
    try {
      await updateReminderSettings(user!.id, n, email)
      try { localStorage.setItem('kvit.reminderDays', String(n)) } catch { /* ignore */ }
      await refreshProfile()
      toast.success(t('common.saved'))
    } catch (e) {
      toast.error(t('common.saveFailed'), e instanceof Error ? e.message : undefined)
    }
  }

  const shortcuts: Array<[string, string]> = [
    ['Ctrl+K', t('cmd.placeholder')],
    ['Ctrl+1…7', t('cmd.pages')],
    ['Esc', t('common.close')],
  ]

  return (
    <div className="max-w-3xl">
      <PageHeader title={t('nav.settings')} />
      <div className="space-y-4">
        <motion.section {...stagger(0)} className="card p-6">
          <h2 className="mb-1 flex items-center gap-2"><BellRing size={18} className="text-accent2" strokeWidth={1.5} />{t('rem.title')}</h2>
          <p className="text-sm text-muted mb-4">{t('rem.text')}</p>
          <div className="flex flex-wrap items-end gap-3">
            <div><label className="label">{t('rem.days')}</label><input className="input h-10 w-28 num" inputMode="numeric" value={days} onChange={(e) => setDays(e.target.value)} /></div>
            <label className="flex items-center gap-2 h-10 text-sm cursor-pointer"><input type="checkbox" className="accent-current" checked={email} onChange={(e) => setEmail(e.target.checked)} />{t('rem.email')}</label>
            <button className="btn-outline h-10" onClick={() => void saveReminders()}>{t('common.save')}</button>
          </div>
          <div className="text-xs text-faint mt-3">{t('rem.emailHint')}</div>
        </motion.section>

        <motion.section {...stagger(1)} className="card p-6">
          <h2 className="mb-3">{t('settings.data')}</h2>
          <div className="flex flex-wrap gap-2">
            <button className="btn-outline" onClick={() => exportCsv(debts, payments, t)}>
              <Download size={16} />
              {t('nav.debts')} · CSV
            </button>
            <button className="btn-outline" onClick={() => exportPaymentsCsv(payments, debts, t)}>
              <Download size={16} />
              {t('nav.payments')} · CSV
            </button>
          </div>
        </motion.section>

        <motion.section {...stagger(2)} className="card p-6">
          <h2 className="mb-3 flex items-center gap-2">
            <Keyboard size={18} className="text-accent" />
            {t('settings.shortcuts')}
          </h2>
          <ul className="divide-y divide-line/60">
            {shortcuts.map(([k, v]) => (
              <li key={k} className="flex items-center justify-between py-2 text-sm">
                <span className="text-muted">{v}</span>
                <kbd className="font-mono text-xs border border-line rounded-md px-2 py-1 bg-raised">{k}</kbd>
              </li>
            ))}
          </ul>
        </motion.section>

        <motion.section {...stagger(3)} className="card p-6">
          <h2 className="mb-2 flex items-center gap-2">
            <Info size={18} className="text-accent" />
            {t('settings.about')}
          </h2>
          <p className="text-sm text-muted">{t('settings.aboutText')}</p>
        </motion.section>
      </div>
    </div>
  )
}
