// Amounts arrive as strings from numeric(14,2). Sums are computed in tiyn (integers)
// so that floating point drift cannot appear, mirroring BigDecimal in the desktop app.
export const toTiyn = (v: number | string): number => Math.round(Number(v) * 100)
export const fromTiyn = (t: number): number => t / 100

export type Currency = 'KZT' | 'USD' | 'EUR' | 'RUB'
export const CURRENCIES: Currency[] = ['KZT', 'USD', 'EUR', 'RUB']
export const CURRENCY_SYMBOL: Record<Currency, string> = { KZT: '₸', USD: '$', EUR: '€', RUB: '₽' }

export function sum(values: Array<number | string>): number {
  return fromTiyn(values.reduce<number>((acc, v) => acc + toTiyn(v), 0))
}

const numberLocale = (locale: string) => (locale.startsWith('kk') ? 'ru-RU' : locale)

export function formatMoney(v: number | string, locale = 'ru-RU', currency: Currency | string = 'KZT'): string {
  const symbol = CURRENCY_SYMBOL[currency as Currency] ?? currency
  return new Intl.NumberFormat(numberLocale(locale), { maximumFractionDigits: 2 }).format(Number(v)) + ' ' + symbol
}

export function formatCompact(v: number, locale = 'ru-RU', currency: Currency | string = 'KZT'): string {
  const symbol = CURRENCY_SYMBOL[currency as Currency] ?? currency
  return new Intl.NumberFormat(numberLocale(locale), { notation: 'compact', maximumFractionDigits: 1 }).format(v) + ' ' + symbol
}

// Accepts "12 500", "12500.50", "12500,5". Returns null when not a positive amount with <= 2 decimals.
export function parseAmount(s: string): number | null {
  const cleaned = s.replace(/\s/g, '').replace(',', '.')
  if (!/^\d+(\.\d{1,2})?$/.test(cleaned)) return null
  const n = Number(cleaned)
  return n > 0 && n < 1e12 ? n : null
}
