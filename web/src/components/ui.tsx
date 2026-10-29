import { useEffect, type ReactNode } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { X, Inbox } from 'lucide-react'
import type { Profile } from '../api/types'

export function Modal({ open, onClose, title, children, wide }: { open: boolean; onClose: () => void; title?: string; children: ReactNode; wide?: boolean }) {
  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKey)
    document.body.style.overflow = 'hidden'
    return () => {
      window.removeEventListener('keydown', onKey)
      document.body.style.overflow = ''
    }
  }, [open, onClose])
  return (
    <AnimatePresence>
      {open && (
        <motion.div className="fixed inset-0 z-[90] flex items-end sm:items-center justify-center p-0 sm:p-4" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
          <div className="absolute inset-0 bg-black/50 backdrop-blur-sm" onClick={onClose} />
          <motion.div
            role="dialog"
            aria-modal="true"
            initial={{ y: 40, opacity: 0, scale: 0.98 }}
            animate={{ y: 0, opacity: 1, scale: 1 }}
            exit={{ y: 30, opacity: 0, scale: 0.98 }}
            transition={{ type: 'spring', stiffness: 380, damping: 32 }}
            className={`relative card w-full ${wide ? 'sm:max-w-3xl' : 'sm:max-w-lg'} max-h-[92vh] overflow-y-auto rounded-b-none sm:rounded-b-2xl`}
          >
            {title && (
              <div className="flex items-center justify-between px-5 pt-5 pb-3">
                <h2 className="text-lg">{title}</h2>
                <button className="btn-icon btn-ghost h-9 w-9" onClick={onClose} aria-label="Close">
                  <X size={18} />
                </button>
              </div>
            )}
            <div className="px-5 pb-5">{children}</div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  )
}

export function Confirm({
  open,
  onClose,
  onConfirm,
  title,
  text,
  confirmLabel,
  cancelLabel,
  busy,
}: {
  open: boolean
  onClose: () => void
  onConfirm: () => void
  title: string
  text: string
  confirmLabel: string
  cancelLabel: string
  busy?: boolean
}) {
  return (
    <Modal open={open} onClose={onClose} title={title}>
      <p className="text-sm text-muted">{text}</p>
      <div className="flex justify-end gap-2 mt-5">
        <button className="btn-ghost" onClick={onClose}>
          {cancelLabel}
        </button>
        <button className="btn-danger" onClick={onConfirm} disabled={busy}>
          {confirmLabel}
        </button>
      </div>
    </Modal>
  )
}

export function Field({ label, error, hint, children, optional }: { label: string; error?: string; hint?: string; children: ReactNode; optional?: string }) {
  return (
    <div>
      <label className="label">
        {label} {optional && <span className="normal-case tracking-normal text-faint">· {optional}</span>}
      </label>
      {children}
      {error ? <div className="text-xs text-bad mt-1.5">{error}</div> : hint ? <div className="text-xs text-faint mt-1.5">{hint}</div> : null}
    </div>
  )
}

export function Empty({ icon, title, text, action }: { icon?: ReactNode; title: string; text?: string; action?: ReactNode }) {
  return (
    <motion.div initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} className="flex flex-col items-center text-center py-14 px-6">
      <div className="w-14 h-14 rounded-lg border border-line text-accent2 flex items-center justify-center mb-4 animate-float [&>svg]:stroke-[1.5]">{icon ?? <Inbox size={26} />}</div>
      <div className="text-lg">{title}</div>
      {text && <p className="text-sm text-muted mt-1.5 max-w-sm">{text}</p>}
      {action && <div className="mt-5">{action}</div>}
    </motion.div>
  )
}

export function Skeleton({ className = '' }: { className?: string }) {
  return <div className={`shimmer ${className}`} />
}

export function Progress({ value, className = '', color = 'bg-accent' }: { value: number; className?: string; color?: string }) {
  return (
    <div className={`h-2 rounded-full bg-line/70 overflow-hidden ${className}`}>
      <motion.div
        className={`h-full rounded-full ${color}`}
        initial={{ width: 0 }}
        animate={{ width: `${Math.min(100, Math.max(0, value))}%` }}
        transition={{ type: 'spring', stiffness: 80, damping: 20 }}
      />
    </div>
  )
}

export function Ring({ value, size = 120, stroke = 10, children }: { value: number; size?: number; stroke?: number; children?: ReactNode }) {
  const r = (size - stroke) / 2,
    c = 2 * Math.PI * r
  return (
    <div className="relative" style={{ width: size, height: size }}>
      <svg width={size} height={size} className="-rotate-90">
        <circle cx={size / 2} cy={size / 2} r={r} stroke="rgb(var(--line))" strokeWidth={stroke} fill="none" />
        <motion.circle
          cx={size / 2}
          cy={size / 2}
          r={r}
          stroke="url(#ringGrad)"
          strokeWidth={stroke}
          fill="none"
          strokeLinecap="round"
          initial={{ strokeDasharray: c, strokeDashoffset: c }}
          animate={{ strokeDashoffset: c - (c * Math.min(100, value)) / 100 }}
          transition={{ type: 'spring', stiffness: 60, damping: 18 }}
        />
        <defs>
          <linearGradient id="ringGrad" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stopColor="rgb(var(--ink))" />
            <stop offset="100%" stopColor="rgb(var(--accent2))" />
          </linearGradient>
        </defs>
      </svg>
      <div className="absolute inset-0 flex items-center justify-center">{children}</div>
    </div>
  )
}

export function StatusChip({ status, label }: { status: string; label: string }) {
  const dot =
    status === 'OVERDUE'
      ? 'bg-bad'
      : status === 'PAID' || status === 'REPAID'
        ? 'bg-good'
        : status === 'CANCELLED' || status === 'DECLINED'
          ? 'bg-faint'
          : status === 'PENDING'
            ? 'bg-warn pulse-dot'
            : 'bg-accent2'
  return (
    <span className="chip">
      <span className={`w-1.5 h-1.5 rounded-full ${dot}`} />
      {label}
    </span>
  )
}

export function Avatar({ profile, size = 36, className = '' }: { profile: Pick<Profile, 'display_name' | 'avatar_url'> | null | undefined; size?: number; className?: string }) {
  const initials = (profile?.display_name ?? '?')
    .split(/\s+/)
    .map((w) => w[0])
    .slice(0, 2)
    .join('')
    .toUpperCase()
  const hue = [...(profile?.display_name ?? '')].reduce((a, ch) => a + ch.charCodeAt(0), 0) % 360
  return profile?.avatar_url ? (
    <img src={profile.avatar_url} alt="" width={size} height={size} className={`rounded-full object-cover shrink-0 ${className}`} style={{ width: size, height: size }} />
  ) : (
    <div
      className={`rounded-full shrink-0 flex items-center justify-center font-semibold text-white ${className}`}
      style={{ width: size, height: size, fontSize: size * 0.38, background: `hsl(${hue} 8% 30%)` }}
    >
      {initials}
    </div>
  )
}

export function Segmented<T extends string>({ value, onChange, options }: { value: T; onChange: (v: T) => void; options: Array<{ value: T; label: ReactNode }> }) {
  return (
    <div className="inline-flex p-0.5 rounded-lg border border-line gap-0.5">
      {options.map((o) => (
        <button key={o.value} onClick={() => onChange(o.value)} className={`relative h-8 px-3 rounded text-sm transition ${value === o.value ? 'text-bg' : 'text-muted hover:text-ink'}`}>
          {value === o.value && (
            <motion.span layoutId={`seg-${options.map((x) => x.value).join('')}`} className="absolute inset-0 rounded bg-ink" transition={{ type: 'spring', stiffness: 500, damping: 35 }} />
          )}
          <span className="relative z-10 flex items-center gap-1.5">{o.label}</span>
        </button>
      ))}
    </div>
  )
}

export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: string; actions?: ReactNode }) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-3 mb-5 min-h-[40px]">
      <div>
        <h1 className="sr-only">{title}</h1>
        {subtitle && <p className="eyebrow">{subtitle}</p>}
      </div>
      {actions && <div className="flex items-center gap-2">{actions}</div>}
    </div>
  )
}

export const fadeUp = { initial: { opacity: 0, y: 12 }, animate: { opacity: 1, y: 0 }, transition: { duration: 0.35, ease: [0.22, 1, 0.36, 1] as const } }
export const stagger = (i: number) => ({ ...fadeUp, transition: { ...fadeUp.transition, delay: i * 0.05 } })
