import { useState, type FormEvent } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { Eye, EyeOff, Sun, Moon, Monitor, ArrowUpRight } from 'lucide-react'
import { supabase } from '../lib/supabase'
import { useI18n, LANGS, type Lang } from '../lib/i18n'
import { useTheme } from '../lib/theme'
import { useToast } from '../lib/toast'
import { Field } from '../components/ui'
import { DotField } from '../components/DotField'
import { TapeHero } from '../components/TapeHero'
import { Starfield, Planet } from '../components/Starfield'

type Mode = 'signin' | 'signup' | 'reset'

const strongPassword = (p: string) => p.length >= 8 && /\D/.test(p) && /\d/.test(p)

export function LoginPage() {
  const { t, lang, setLang } = useI18n()
  const { theme, resolved, cycle } = useTheme()
  const toast = useToast()
  const [mode, setMode] = useState<Mode>('signin')
  const [email, setEmail] = useState(() => {
    try {
      return localStorage.getItem('kvit.email') ?? ''
    } catch {
      return ''
    }
  })
  const [password, setPassword] = useState('')
  const [name, setName] = useState('')
  const [show, setShow] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState<string | null>(null)

  const ThemeIcon = theme === 'system' ? Monitor : resolved === 'dark' ? Moon : Sun

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setBusy(true)
    try {
      try {
        localStorage.setItem('kvit.email', email.trim())
      } catch {
        /* ignore */
      }
      if (mode === 'signin') {
        const { error } = await supabase.auth.signInWithPassword({ email: email.trim(), password })
        if (error) throw error
      } else if (mode === 'signup') {
        if (!strongPassword(password)) throw new Error(t('err.password'))
        const { data, error } = await supabase.auth.signUp({
          email: email.trim(),
          password,
          options: { data: { display_name: name.trim() || email.split('@')[0] }, emailRedirectTo: window.location.origin },
        })
        if (error) throw /already|registered|exists/i.test(error.message) ? new Error(t('auth.exists')) : error
        // When confirmation is on, Supabase hides existing accounts behind a user with no identities.
        if (data.user && data.user.identities && data.user.identities.length === 0) throw new Error(t('auth.exists'))
        // With email confirmation switched off Supabase returns a session right away.
        if (!data.session) setDone(t('auth.checkEmail', email.trim()))
      } else {
        const { error } = await supabase.auth.resetPasswordForEmail(email.trim(), { redirectTo: `${window.location.origin}/reset-password` })
        if (error) throw error
        setDone(t('auth.resetSent'))
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    } finally {
      setBusy(false)
      setPassword('')
    }
  }

  const switchMode = (m: Mode) => {
    setMode(m)
    setError(null)
    setDone(null)
  }
  void toast

  return (
    <div className="relative min-h-full flex flex-col bg-bg overflow-hidden">
      <Starfield className="absolute inset-0 w-full h-full" />
      <Planet className="w-[560px] h-[560px] -left-56 -bottom-64 opacity-80 hidden md:block" />
      <Planet className="w-[260px] h-[260px] -right-20 top-24 opacity-60 hidden lg:block [&_svg]:rotate-[30deg]" />
      <DotField className="absolute inset-0 w-full h-full" density={14} themed intensity={0.55} />
      <TapeHero className="opacity-70" />

      <header className="relative z-10 flex items-center justify-between px-6 sm:px-10 h-16">
        <div className="flex items-center gap-2.5">
          <Mark />
          <span className="text-[17px]">Kvit</span>
        </div>
        <div className="flex items-center gap-2">
          <div className="inline-flex border border-line rounded-lg p-0.5">
            {LANGS.map((l) => (
              <button
                key={l}
                onClick={() => setLang(l as Lang)}
                className={`h-8 px-2.5 rounded font-mono text-[11px] uppercase transition ${lang === l ? 'bg-ink text-bg' : 'text-muted hover:text-ink'}`}
              >
                {l}
              </button>
            ))}
          </div>
          <button className="btn-icon btn-ghost border border-line h-9 w-9" onClick={cycle} aria-label={t(`theme.${theme}`)}>
            <ThemeIcon size={16} />
          </button>
        </div>
      </header>

      <main className="relative z-10 flex-1 flex items-center justify-center px-4 py-10">
        <motion.div initial={{ opacity: 0, y: 18 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.5, ease: [0.22, 1, 0.36, 1] }} className="w-full max-w-[460px]">
          <div className="glass p-6 sm:p-8 bg-surface/80">
            <div className="text-center mb-7">
              <div className="inline-flex items-center gap-2 chip mb-4">
                <span className="w-1.5 h-1.5 rounded-full bg-good pulse-dot" />
                {t('auth.badge')}
              </div>
              <h1 className="text-[32px] sm:text-[38px] leading-[1.05] tracking-[-0.02em]">{t('app.tagline')}</h1>
            </div>
            <div className="hairline mb-6" />
            <AnimatePresence mode="wait">
              <motion.div key={mode} initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -8 }} transition={{ duration: 0.2 }}>
                <div className="eyebrow">{mode === 'signin' ? t('auth.signIn') : mode === 'signup' ? t('auth.signUp') : t('auth.resetTitle')}</div>
                <p className="text-muted text-sm mt-2">{mode === 'signin' ? t('auth.welcomeText') : mode === 'signup' ? t('auth.newText') : t('auth.resetText')}</p>
              </motion.div>
            </AnimatePresence>

            {done ? (
              <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="mt-6 border border-line rounded-lg p-4 text-sm">
                {done}
              </motion.div>
            ) : (
              <form onSubmit={submit} className="mt-6 space-y-4">
                {mode === 'signup' && (
                  <Field label={t('auth.name')}>
                    <input className="input" value={name} onChange={(e) => setName(e.target.value)} maxLength={60} autoComplete="name" />
                  </Field>
                )}
                <Field label={t('auth.email')}>
                  <input className="input" type="email" required value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" autoFocus />
                </Field>
                {mode !== 'reset' && (
                  <Field label={t('auth.password')} hint={mode === 'signup' ? t('auth.passwordHint') : undefined}>
                    <div className="relative">
                      <input
                        className="input pr-11"
                        type={show ? 'text' : 'password'}
                        required
                        value={password}
                        onChange={(e) => setPassword(e.target.value)}
                        autoComplete={mode === 'signup' ? 'new-password' : 'current-password'}
                      />
                      <button type="button" className="absolute right-1.5 top-1/2 -translate-y-1/2 btn-icon btn-ghost h-8 w-8" onClick={() => setShow((v) => !v)} aria-label="Show">
                        {show ? <EyeOff size={15} /> : <Eye size={15} />}
                      </button>
                    </div>
                  </Field>
                )}
                {error && (
                  <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="text-sm text-bad border border-bad/40 rounded-lg px-3 py-2">
                    {error}
                  </motion.div>
                )}
                <button className="btn-primary w-full h-11 uppercase font-mono text-[12px] tracking-wide" disabled={busy}>
                  {busy ? t('common.loading') : mode === 'signin' ? t('auth.signIn') : mode === 'signup' ? t('auth.signUp') : t('auth.resetSend')}
                  {!busy && <ArrowUpRight size={15} />}
                </button>
              </form>
            )}

            <div className="hairline mt-6 pt-4 flex flex-wrap items-center justify-between gap-2 text-sm">
              {mode === 'signin' && (
                <>
                  <button className="text-muted hover:text-ink" onClick={() => switchMode('reset')}>
                    {t('auth.forgot')}
                  </button>
                  <button className="text-ink underline-offset-4 hover:underline" onClick={() => switchMode('signup')}>
                    {t('auth.signUp')}
                  </button>
                </>
              )}
              {mode === 'signup' && (
                <button className="text-ink underline-offset-4 hover:underline ml-auto" onClick={() => switchMode('signin')}>
                  {t('auth.haveAccount')} {t('auth.signIn')}
                </button>
              )}
              {mode === 'reset' && (
                <button className="text-ink underline-offset-4 hover:underline ml-auto" onClick={() => switchMode('signin')}>
                  {t('common.back')}
                </button>
              )}
            </div>
          </div>
        </motion.div>
      </main>

      <footer className="relative z-10 flex items-center justify-between px-6 sm:px-10 h-12 font-mono text-[11px] text-faint uppercase tracking-wide">
        <span>© {new Date().getFullYear()} Kvit</span>
      </footer>
    </div>
  )
}

export function Mark({ size = 34 }: { size?: number }) {
  return <img src="/logo.png" alt="" width={size} height={size} className="shrink-0 select-none" draggable={false} />
}
