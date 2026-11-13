import { motion } from 'framer-motion'
import { Download, Keyboard, Info, BellRing, Plus, X } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useAuth } from '../lib/auth'
import { useToast } from '../lib/toast'
import { updateReminderSettings } from '../api/extra'
import { useI18n } from '../lib/i18n'
import { useData } from '../lib/data'
import { exportCsv, exportPaymentsCsv } from '../lib/csv'
import { PageHeader, Segmented, stagger } from '../components/ui'
import { todayIso, formatDate } from '../lib/dates'
import type { ReminderMode } from '../api/types'
import { CsvImport } from '../components/CsvImport'
import { TelegramLink } from '../components/TelegramLink'

// Short weekday name for an ISO weekday number (1 = Monday). 2024-01-01 was a Monday.
function weekdayName(isoDay: number, locale: string): string {
  return new Intl.DateTimeFormat(locale, { weekday: 'short' }).format(new Date(2024, 0, isoDay))
}

export function SettingsPage() {
  const { t, locale } = useI18n()
  const { debts, payments } = useData()
  const { user, profile, refreshProfile } = useAuth()
  const toast = useToast()
  const [days, setDays] = useState(String(profile?.reminder_days ?? 14))
  const [email, setEmail] = useState(!!profile?.email_reminders)
  const [time, setTime] = useState(profile?.reminder_time?.slice(0, 5) ?? '08:00')
  const [mode, setMode] = useState<ReminderMode>(profile?.reminder_mode ?? 'daily')
  const [weekdays, setWeekdays] = useState<number[]>(profile?.reminder_weekdays ?? [])
  const [dates, setDates] = useState<string[]>(profile?.reminder_dates ?? [])
  const [newDate, setNewDate] = useState('')
  const [remError, setRemError] = useState<string | null>(null)
  useEffect(() => {
    setDays(String(profile?.reminder_days ?? 14))
    setEmail(!!profile?.email_reminders)
    setTime(profile?.reminder_time?.slice(0, 5) ?? '08:00')
    setMode(profile?.reminder_mode ?? 'daily')
    setWeekdays(profile?.reminder_weekdays ?? [])
    setDates(profile?.reminder_dates ?? [])
  }, [profile])

  const toggleWeekday = (d: number) => setWeekdays((w) => (w.includes(d) ? w.filter((x) => x !== d) : [...w, d].sort()))
  function addDate() {
    if (!newDate) return
    if (newDate < todayIso()) return setRemError(t('err.pastDate'))
    if (!dates.includes(newDate)) setDates([...dates, newDate].sort())
    setNewDate('')
    setRemError(null)
  }

  async function saveReminders() {
    const n = Number(days)
    if (!Number.isInteger(n) || n < 0 || n > 90) return setRemError(t('rem.err.days'))
    if (!/^\d{2}:\d{2}$/.test(time)) return setRemError(t('rem.err.time'))
    if (mode === 'weekdays' && weekdays.length === 0) return setRemError(t('rem.err.weekdays'))
    if (mode === 'dates' && dates.length === 0) return setRemError(t('rem.err.dates'))
    setRemError(null)
    try {
      await updateReminderSettings(user!.id, {
        reminder_days: n,
        email_reminders: email,
        reminder_time: time,
        // Reminders are sent by the server, so it needs to know which clock "08:00" refers to.
        reminder_tz: Intl.DateTimeFormat().resolvedOptions().timeZone || 'Asia/Almaty',
        reminder_mode: mode,
        reminder_weekdays: weekdays,
        reminder_dates: dates,
      })
      try { localStorage.setItem('kvit.reminderDays', String(n)) } catch { /* ignore */ }
      await refreshProfile()
      toast.success(t('common.saved'))
    } catch (e) {
      toast.error(t('common.saveFailed'), e instanceof Error ? e.message : undefined)
    }
  }

  const shortcuts: Array<[string, string]> = [
    ['Ctrl+K', t('cmd.placeholder')],
    ['Ctrl+J', t('ai.title')],
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
            <div><label className="label">{t('rem.time')}</label><input type="time" className="input h-10 w-32 num" value={time} onChange={(e) => setTime(e.target.value)} /></div>
            <label className="flex items-center gap-2 h-10 text-sm cursor-pointer"><input type="checkbox" className="accent-current" checked={email} onChange={(e) => setEmail(e.target.checked)} />{t('rem.email')}</label>
          </div>

          <div className="mt-4">
            <label className="label">{t('rem.when')}</label>
            <Segmented<ReminderMode>
              value={mode}
              onChange={setMode}
              options={[
                { value: 'daily', label: t('rem.mode.daily') },
                { value: 'weekdays', label: t('rem.mode.weekdays') },
                { value: 'dates', label: t('rem.mode.dates') },
              ]}
            />
          </div>

          {mode === 'weekdays' && (
            <div className="mt-3 flex flex-wrap gap-1.5">
              {[1, 2, 3, 4, 5, 6, 7].map((d) => (
                <button
                  key={d}
                  type="button"
                  onClick={() => toggleWeekday(d)}
                  aria-pressed={weekdays.includes(d)}
                  className={`h-9 min-w-11 px-3 rounded-lg border text-sm transition ${weekdays.includes(d) ? 'bg-ink text-bg border-ink' : 'border-line text-muted hover:text-ink'}`}
                >
                  {weekdayName(d, locale)}
                </button>
              ))}
            </div>
          )}

          {mode === 'dates' && (
            <div className="mt-3">
              <div className="flex flex-wrap items-center gap-2">
                <input type="date" className="input h-10 w-44" min={todayIso()} value={newDate} onChange={(e) => setNewDate(e.target.value)} />
                <button type="button" className="btn-outline h-10" onClick={addDate} disabled={!newDate}><Plus size={15} />{t('rem.addDate')}</button>
              </div>
              {dates.length > 0 && (
                <div className="mt-3 flex flex-wrap gap-1.5">
                  {dates.map((d) => (
                    <span key={d} className="inline-flex items-center gap-1 h-8 pl-3 pr-1 rounded-lg border border-line text-sm num">
                      {formatDate(d, locale)}
                      <button type="button" className="p-1 text-faint hover:text-ink" onClick={() => setDates(dates.filter((x) => x !== d))} aria-label={t('common.delete')}><X size={14} /></button>
                    </span>
                  ))}
                </div>
              )}
            </div>
          )}

          <p className="text-xs text-faint mt-3">{t('rem.timeHint')}</p>
          {remError && <p className="text-sm text-bad mt-2" role="alert">{remError}</p>}
          <button className="btn-outline h-10 mt-4" onClick={() => void saveReminders()}>{t('common.save')}</button>
          <TelegramLink />
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
            <CsvImport />
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
