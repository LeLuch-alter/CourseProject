import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'

export type Theme = 'system' | 'light' | 'dark'

interface ThemeCtx {
  theme: Theme
  resolved: 'light' | 'dark'
  setTheme: (t: Theme) => void
  cycle: () => void
}
const Ctx = createContext<ThemeCtx | null>(null)

function readTheme(): Theme {
  try {
    const t = localStorage.getItem('kvit.theme') as Theme | null
    if (t === 'light' || t === 'dark' || t === 'system') return t
  } catch {
    /* ignore */
  }
  return 'system'
}

const media = () => window.matchMedia('(prefers-color-scheme: dark)')

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setThemeState] = useState<Theme>(readTheme)
  const [systemDark, setSystemDark] = useState(() => media().matches)

  useEffect(() => {
    const m = media()
    const onChange = (e: MediaQueryListEvent) => setSystemDark(e.matches)
    m.addEventListener('change', onChange)
    return () => m.removeEventListener('change', onChange)
  }, [])

  const resolved: 'light' | 'dark' = theme === 'system' ? (systemDark ? 'dark' : 'light') : theme

  useEffect(() => {
    document.documentElement.classList.toggle('dark', resolved === 'dark')
    document.querySelector('meta[name=theme-color]')?.setAttribute('content', resolved === 'dark' ? '#101010' : '#f6f6f4')
  }, [resolved])

  const value = useMemo<ThemeCtx>(
    () => ({
      theme,
      resolved,
      setTheme: (t) => {
        setThemeState(t)
        try {
          localStorage.setItem('kvit.theme', t)
        } catch {
          /* ignore */
        }
      },
      cycle: () => {
        const next: Theme = theme === 'system' ? 'light' : theme === 'light' ? 'dark' : 'system'
        setThemeState(next)
        try {
          localStorage.setItem('kvit.theme', next)
        } catch {
          /* ignore */
        }
      },
    }),
    [theme, resolved],
  )

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>
}

export function useTheme(): ThemeCtx {
  const v = useContext(Ctx)
  if (!v) throw new Error('useTheme outside provider')
  return v
}
