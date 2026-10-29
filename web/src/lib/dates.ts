function localIso(d: Date): string {
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

export const todayIso = (): string => localIso(new Date())

export function daysBetween(fromIso: string, toIso: string): number {
  const a = new Date(fromIso + 'T00:00:00'),
    b = new Date(toIso + 'T00:00:00')
  return Math.round((b.getTime() - a.getTime()) / 86400000)
}

export function formatDate(iso: string | null | undefined, locale = 'ru-RU'): string {
  if (!iso) return '—'
  return new Intl.DateTimeFormat(locale, { day: 'numeric', month: 'short', year: 'numeric' }).format(new Date(iso.slice(0, 10) + 'T00:00:00'))
}

export function formatDateTime(iso: string, locale = 'ru-RU'): string {
  return new Intl.DateTimeFormat(locale, { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }).format(new Date(iso))
}

export const monthKey = (iso: string): string => iso.slice(0, 7)

export function addMonths(iso: string, n: number): string {
  const d = new Date(iso + 'T00:00:00')
  d.setMonth(d.getMonth() + n)
  return localIso(d)
}

// Whole calendar months between two dates, at least 1 (used by the payoff planner).
export function monthsUntil(fromIso: string, toIso: string): number {
  const a = new Date(fromIso + 'T00:00:00'),
    b = new Date(toIso + 'T00:00:00')
  const m = (b.getFullYear() - a.getFullYear()) * 12 + (b.getMonth() - a.getMonth())
  return Math.max(1, m)
}
