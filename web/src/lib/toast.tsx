import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { CheckCircle2, AlertTriangle, Info, X, Bell } from 'lucide-react'

export type ToastKind = 'success' | 'error' | 'info' | 'notice'
export interface Toast {
  id: number
  kind: ToastKind
  title: string
  text?: string
  link?: string
  onClick?: () => void
}

interface ToastCtx {
  push: (t: Omit<Toast, 'id'>) => void
  success: (title: string, text?: string) => void
  error: (title: string, text?: string) => void
  info: (title: string, text?: string) => void
}
const Ctx = createContext<ToastCtx | null>(null)

let seq = 1

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<Toast[]>([])

  const dismiss = useCallback((id: number) => setItems((xs) => xs.filter((x) => x.id !== id)), [])

  const push = useCallback(
    (t: Omit<Toast, 'id'>) => {
      const id = seq++
      setItems((xs) => [...xs.slice(-4), { ...t, id }])
      window.setTimeout(() => dismiss(id), t.kind === 'error' ? 7000 : 4500)
    },
    [dismiss],
  )

  const value = useMemo<ToastCtx>(
    () => ({
      push,
      success: (title, text) => push({ kind: 'success', title, text }),
      error: (title, text) => push({ kind: 'error', title, text }),
      info: (title, text) => push({ kind: 'info', title, text }),
    }),
    [push],
  )

  const icon = (k: ToastKind) =>
    k === 'success' ? (
      <CheckCircle2 className="text-good" size={18} />
    ) : k === 'error' ? (
      <AlertTriangle className="text-bad" size={18} />
    ) : k === 'notice' ? (
      <Bell className="text-accent" size={18} />
    ) : (
      <Info className="text-accent2" size={18} />
    )

  return (
    <Ctx.Provider value={value}>
      {children}
      <div className="fixed z-[100] bottom-4 right-4 left-4 sm:left-auto sm:w-[380px] flex flex-col gap-2 pointer-events-none">
        <AnimatePresence initial={false}>
          {items.map((t) => (
            <motion.div
              key={t.id}
              layout
              initial={{ opacity: 0, y: 24, scale: 0.96 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, x: 40, scale: 0.96 }}
              transition={{ type: 'spring', stiffness: 400, damping: 30 }}
              className={`pointer-events-auto card p-3.5 flex items-start gap-3 ${t.onClick ? 'cursor-pointer hover:border-accent/50' : ''}`}
              onClick={() => {
                t.onClick?.()
                dismiss(t.id)
              }}
            >
              <div className="mt-0.5 shrink-0">{icon(t.kind)}</div>
              <div className="min-w-0 flex-1">
                <div className="text-sm leading-tight">{t.title}</div>
                {t.text && <div className="text-sm text-muted mt-0.5 break-words">{t.text}</div>}
              </div>
              <button
                className="text-faint hover:text-ink transition shrink-0"
                onClick={(e) => {
                  e.stopPropagation()
                  dismiss(t.id)
                }}
                aria-label="Close"
              >
                <X size={16} />
              </button>
            </motion.div>
          ))}
        </AnimatePresence>
      </div>
    </Ctx.Provider>
  )
}

export function useToast(): ToastCtx {
  const v = useContext(Ctx)
  if (!v) throw new Error('useToast outside provider')
  return v
}
