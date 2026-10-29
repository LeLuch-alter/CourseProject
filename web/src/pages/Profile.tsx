import { useEffect, useRef, useState, type FormEvent } from 'react'
import { motion } from 'framer-motion'
import { Camera, Copy, KeyRound, Users, FileSignature, Wallet, Check } from 'lucide-react'
import { useI18n } from '../lib/i18n'
import { useAuth } from '../lib/auth'
import { useToast } from '../lib/toast'
import { useData } from '../lib/data'
import { supabase } from '../lib/supabase'
import { formatDate } from '../lib/dates'
import { updateProfile, uploadAvatar, listFriendships, listLoans } from '../api/social'
import { Avatar, Field, PageHeader, stagger } from '../components/ui'
import { QrCard } from '../components/QrCard'

export function ProfilePage() {
  const { t, locale } = useI18n()
  const { user, profile, refreshProfile } = useAuth()
  const { debts } = useData()
  const toast = useToast()
  const fileRef = useRef<HTMLInputElement>(null)
  const [username, setUsername] = useState(profile?.username ?? '')
  const [displayName, setDisplayName] = useState(profile?.display_name ?? '')
  const [bio, setBio] = useState(profile?.bio ?? '')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [busy, setBusy] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [password, setPassword] = useState('')
  const [pwBusy, setPwBusy] = useState(false)
  const [copied, setCopied] = useState(false)
  const [stats, setStats] = useState({ friends: 0, loans: 0 })

  useEffect(() => {
    if (profile) {
      setUsername(profile.username)
      setDisplayName(profile.display_name)
      setBio(profile.bio ?? '')
    }
  }, [profile])

  useEffect(() => {
    Promise.all([listFriendships(), listLoans()])
      .then(([f, l]) => setStats({ friends: f.filter((x) => x.status === 'ACCEPTED').length, loans: l.length }))
      .catch(() => undefined)
  }, [])

  async function save(e: FormEvent) {
    e.preventDefault()
    const errs: Record<string, string> = {}
    if (!/^[a-z0-9_]{3,24}$/.test(username)) errs.username = 'username'
    if (!displayName.trim()) errs.displayName = 'required'
    setErrors(errs)
    if (Object.keys(errs).length) return
    setBusy(true)
    try {
      await updateProfile(user!.id, { username, display_name: displayName.trim(), bio: bio.trim() || null })
      await refreshProfile()
      toast.success(t('common.saved'))
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err)
      if (/unique|duplicate/i.test(msg)) setErrors({ username: 'usernameTaken' })
      else toast.error(t('common.saveFailed'), msg)
    } finally {
      setBusy(false)
    }
  }

  async function onFile(file: File | undefined) {
    if (!file) return
    if (file.size > 2 * 1024 * 1024) {
      toast.error(t('profile.photoHint'))
      return
    }
    setUploading(true)
    try {
      const resized = await shrink(file)
      const url = await uploadAvatar(user!.id, resized)
      await updateProfile(user!.id, { avatar_url: url })
      await refreshProfile()
      toast.success(t('common.saved'))
    } catch (err) {
      toast.error(t('common.saveFailed'), err instanceof Error ? err.message : undefined)
    } finally {
      setUploading(false)
    }
  }

  async function sendReset() {
    try {
      const { error } = await supabase.auth.resetPasswordForEmail(user?.email ?? '', { redirectTo: `${window.location.origin}/reset-password` })
      if (error) throw error
      toast.success(t('auth.resetSent'))
    } catch (err) {
      toast.error(t('common.saveFailed'), err instanceof Error ? err.message : undefined)
    }
  }

  async function changePassword(e: FormEvent) {
    e.preventDefault()
    if (password.length < 8 || !/\d/.test(password) || !/\D/.test(password)) {
      toast.error(t('err.password'))
      return
    }
    setPwBusy(true)
    try {
      const { error } = await supabase.auth.updateUser({ password })
      if (error) throw error
      setPassword('')
      toast.success(t('auth.passwordChanged'))
    } catch (err) {
      toast.error(t('common.saveFailed'), err instanceof Error ? err.message : undefined)
    } finally {
      setPwBusy(false)
    }
  }

  const copy = async () => {
    try {
      await navigator.clipboard.writeText('@' + (profile?.username ?? ''))
      setCopied(true)
      setTimeout(() => setCopied(false), 1500)
    } catch {
      /* clipboard blocked */
    }
  }

  return (
    <div className="max-w-4xl">
      <PageHeader title={t('profile.title')} />
      <div className="grid gap-4 lg:grid-cols-[320px_1fr]">
        <motion.div {...stagger(0)} className="card p-6 flex flex-col items-center text-center relative overflow-hidden">
          <div className="absolute inset-x-0 top-0 h-24 border-b border-line" style={{ backgroundImage: 'radial-gradient(rgb(var(--line)) 1px, transparent 1px)', backgroundSize: '10px 10px' }} />
          <div className="relative mt-8 group">
            <Avatar profile={profile} size={112} className="ring-4 ring-surface" />
            <button
              onClick={() => fileRef.current?.click()}
              disabled={uploading}
              className="absolute inset-0 rounded-full bg-black/50 text-white flex items-center justify-center opacity-0 group-hover:opacity-100 transition"
              aria-label={t('profile.changePhoto')}
            >
              {uploading ? '…' : <Camera size={22} />}
            </button>
            <input ref={fileRef} type="file" accept="image/png,image/jpeg,image/webp" className="hidden" onChange={(e) => void onFile(e.target.files?.[0])} />
          </div>
          <div className="text-xl mt-3">{profile?.display_name}</div>
          <button onClick={copy} className="text-sm text-muted hover:text-ink flex items-center gap-1 mt-0.5">
            @{profile?.username} {copied ? <Check size={13} className="text-good" /> : <Copy size={13} />}
          </button>
          {profile?.bio && <p className="text-sm text-muted mt-2">{profile.bio}</p>}
          <div className="text-xs text-faint mt-3">{t('profile.since', formatDate(profile?.created_at, locale))}</div>
          <div className="text-[11px] text-faint mt-1">{t('profile.photoHint')}</div>
          <div className="grid grid-cols-3 gap-2 w-full mt-5">
            {[
              { v: stats.friends, k: 'profile.friendsCount', i: Users },
              { v: stats.loans, k: 'profile.loansCount', i: FileSignature },
              { v: debts.length, k: 'profile.debtsCount', i: Wallet },
            ].map((s) => (
              <div key={s.k} className="bg-raised rounded-xl p-2.5">
                <s.i size={14} className="mx-auto text-accent" />
                <div className="text-lg mt-1">{s.v}</div>
                <div className="text-[10px] text-muted">{t(s.k)}</div>
              </div>
            ))}
          </div>
        </motion.div>

        <div className="space-y-4">
          <motion.div {...stagger(1)} className="card p-6 flex flex-col sm:flex-row items-center gap-6">
            {profile && <QrCard username={profile.username} size={150} />}
            <div className="text-sm text-muted"><div className="text-ink text-lg mb-1">{t('qr.title')}</div>{t('qr.text')}</div>
          </motion.div>
          <motion.form {...stagger(1)} onSubmit={save} className="card p-6 space-y-4">
            <Field label={t('profile.username')} hint={t('profile.shareHint')} error={errors.username && t(`err.${errors.username}`)}>
              <div className="relative">
                <span className="absolute left-3.5 top-1/2 -translate-y-1/2 text-faint">@</span>
                <input className="input pl-8" value={username} onChange={(e) => setUsername(e.target.value.toLowerCase())} maxLength={24} autoComplete="off" />
              </div>
            </Field>
            <Field label={t('profile.displayName')} error={errors.displayName && t('err.required')}>
              <input className="input" value={displayName} onChange={(e) => setDisplayName(e.target.value)} maxLength={60} />
            </Field>
            <Field label={t('profile.bio')} optional={t('common.optional')}>
              <textarea className="input h-20 py-2.5 resize-none" value={bio} onChange={(e) => setBio(e.target.value)} maxLength={200} />
            </Field>
            <Field label={t('profile.email')}>
              <input className="input opacity-70" value={user?.email ?? ''} disabled />
            </Field>
            <div className="flex justify-end">
              <button className="btn-primary" disabled={busy}>
                {busy ? t('common.loading') : t('common.save')}
              </button>
            </div>
          </motion.form>

          <motion.form {...stagger(2)} onSubmit={changePassword} className="card p-6">
            <div className="flex items-center gap-2 mb-3">
              <KeyRound size={18} className="text-accent" />
              {t('profile.password')}
            </div>
            <div className="flex flex-col sm:flex-row gap-2">
              <input className="input" type="password" placeholder={t('auth.newPassword')} value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="new-password" />
              <button className="btn-outline shrink-0" disabled={pwBusy || !password}>
                {t('auth.setPassword')}
              </button>
            </div>
            <div className="text-xs text-faint mt-2">{t('auth.passwordHint')}</div>
            <div className="hairline mt-4 pt-3 flex flex-wrap items-center justify-between gap-2 text-sm">
              <span className="text-muted">{t('profile.forgotHint')}</span>
              <button type="button" className="btn-ghost h-8" onClick={() => void sendReset()}>{t('auth.resetSend')}</button>
            </div>
          </motion.form>
        </div>
      </div>
    </div>
  )
}

// Downscale to 512px so the avatar stays small and loads fast everywhere.
function shrink(file: File): Promise<File> {
  return new Promise((resolve, reject) => {
    const img = new Image()
    img.onload = () => {
      const max = 512,
        scale = Math.min(1, max / Math.max(img.width, img.height))
      const side = Math.min(img.width, img.height) * scale
      const canvas = document.createElement('canvas')
      canvas.width = side
      canvas.height = side
      const ctx = canvas.getContext('2d')!
      const sx = (img.width - img.width * scale) / 2 / scale,
        sy = (img.height - img.height * scale) / 2 / scale
      const s = Math.min(img.width, img.height)
      ctx.drawImage(img, (img.width - s) / 2, (img.height - s) / 2, s, s, 0, 0, side, side)
      void sx
      void sy
      canvas.toBlob((b) => (b ? resolve(new File([b], 'avatar.webp', { type: 'image/webp' })) : reject(new Error('resize failed'))), 'image/webp', 0.86)
    }
    img.onerror = () => reject(new Error('bad image'))
    img.src = URL.createObjectURL(file)
  })
}
