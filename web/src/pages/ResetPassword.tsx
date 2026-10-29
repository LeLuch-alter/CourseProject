import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { motion } from 'framer-motion'
import { supabase } from '../lib/supabase'
import { useI18n } from '../lib/i18n'
import { useToast } from '../lib/toast'
import { Field } from '../components/ui'

// Opened from the link in the reset email. Supabase puts a recovery session in the URL,
// after which updateUser() is allowed to set a new password.
export function ResetPasswordPage() {
  const { t } = useI18n()
  const toast = useToast()
  const navigate = useNavigate()
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (password.length < 8 || !/\d/.test(password) || !/\D/.test(password)) {
      setError(t('err.password'))
      return
    }
    setBusy(true)
    setError(null)
    try {
      const { error } = await supabase.auth.updateUser({ password })
      if (error) throw error
      toast.success(t('auth.passwordChanged'))
      navigate('/', { replace: true })
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="min-h-full flex items-center justify-center p-6">
      <motion.form initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} onSubmit={submit} className="card p-6 w-full max-w-md space-y-4">
        <h1 className="font-display text-2xl font-extrabold">{t('auth.setPassword')}</h1>
        <Field label={t('auth.newPassword')} hint={t('auth.passwordHint')} error={error ?? undefined}>
          <input className="input" type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="new-password" autoFocus />
        </Field>
        <button className="btn-primary w-full" disabled={busy}>
          {busy ? t('common.loading') : t('auth.setPassword')}
        </button>
      </motion.form>
    </div>
  )
}
