import { useEffect, useRef, useState } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { ChevronDown, Check } from 'lucide-react'

export interface SelectOption<T> {
  value: T
  label: string
  hint?: string
}

// Styled replacement for <select>: same hairline look as the rest of the site,
// keyboard friendly (arrows, Enter, Esc), closes on outside click.
export function Select<T extends string | number>({ value, onChange, options, className = '', disabled, placeholder }: { value: T; onChange: (v: T) => void; options: SelectOption<T>[]; className?: string; disabled?: boolean; placeholder?: string }) {
  const [open, setOpen] = useState(false)
  const [idx, setIdx] = useState(0)
  const ref = useRef<HTMLDivElement>(null)
  const current = options.find((o) => o.value === value)

  useEffect(() => {
    if (!open) return
    setIdx(Math.max(0, options.findIndex((o) => o.value === value)))
    const onDoc = (e: MouseEvent) => {
      if (!ref.current?.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', onDoc)
    return () => document.removeEventListener('mousedown', onDoc)
  }, [open, options, value])

  const onKey = (e: React.KeyboardEvent) => {
    if (disabled) return
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault()
      if (open && options[idx]) {
        onChange(options[idx].value)
        setOpen(false)
      } else setOpen(true)
    }
    if (e.key === 'Escape') setOpen(false)
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      if (!open) setOpen(true)
      else setIdx((i) => Math.min(options.length - 1, i + 1))
    }
    if (e.key === 'ArrowUp') {
      e.preventDefault()
      setIdx((i) => Math.max(0, i - 1))
    }
  }

  return (
    <div ref={ref} className={`relative ${className}`}>
      <button type="button" disabled={disabled} onClick={() => setOpen((v) => !v)} onKeyDown={onKey} aria-haspopup="listbox" aria-expanded={open} className="input flex items-center justify-between gap-2 text-left disabled:opacity-50">
        <span className={`truncate ${current ? '' : 'text-faint'}`}>{current?.label ?? placeholder ?? '—'}</span>
        <ChevronDown size={15} className={`shrink-0 text-muted transition ${open ? 'rotate-180' : ''}`} />
      </button>
      <AnimatePresence>
        {open && (
          <motion.ul role="listbox" initial={{ opacity: 0, y: -4, scale: 0.98 }} animate={{ opacity: 1, y: 0, scale: 1 }} exit={{ opacity: 0, y: -4, scale: 0.98 }} transition={{ duration: 0.14 }} className="absolute z-50 mt-1 left-0 right-0 min-w-[160px] max-h-64 overflow-y-auto card p-1">
            {options.map((o, i) => (
              <li
                key={String(o.value)}
                role="option"
                aria-selected={o.value === value}
                onMouseEnter={() => setIdx(i)}
                onClick={() => {
                  onChange(o.value)
                  setOpen(false)
                }}
                className={`flex items-center gap-2 px-3 h-9 rounded-lg text-sm cursor-pointer ${i === idx ? 'bg-ink/10' : ''}`}
              >
                <span className="flex-1 truncate">
                  {o.label}
                  {o.hint && <span className="text-muted text-xs ml-2">{o.hint}</span>}
                </span>
                {o.value === value && <Check size={14} className="text-accent2" />}
              </li>
            ))}
          </motion.ul>
        )}
      </AnimatePresence>
    </div>
  )
}
