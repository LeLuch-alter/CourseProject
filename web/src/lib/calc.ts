// Business rules, same as DebtCalculator / PayoffPlanner in the desktop app.
// Pure functions: no React, no Supabase, so they are easy to test.
import type { Debt, Payment } from '../api/types'
import { sum, toTiyn, fromTiyn } from './money'
import { daysBetween, todayIso, monthsUntil, monthKey } from './dates'

export const ATTENTION_DAYS = 14

export function paidFor(debt: Debt, payments: Payment[]): number {
  return sum(payments.filter((p) => p.debt_id === debt.id).map((p) => p.amount))
}

export function remainingFor(debt: Debt, payments: Payment[]): number {
  return fromTiyn(Math.max(0, toTiyn(debt.original_amount) - toTiyn(paidFor(debt, payments))))
}

export function progressFor(debt: Debt, payments: Payment[]): number {
  const total = toTiyn(debt.original_amount)
  if (total === 0) return 0
  return Math.min(100, Math.round((toTiyn(paidFor(debt, payments)) / total) * 100))
}

// Overdue = due date is in the past, there is a remaining balance and the debt is not cancelled.
export function isOverdue(debt: Debt, payments: Payment[], today = todayIso()): boolean {
  return !!debt.due_date && debt.due_date < today && debt.status !== 'CANCELLED' && remainingFor(debt, payments) > 0
}

export function isDueSoon(debt: Debt, payments: Payment[], today = todayIso(), days = ATTENTION_DAYS): boolean {
  if (!debt.due_date || debt.status !== 'ACTIVE') return false
  const d = daysBetween(today, debt.due_date)
  return d >= 0 && d <= days && remainingFor(debt, payments) > 0
}

export type EffectiveStatus = 'ACTIVE' | 'PAID' | 'CANCELLED' | 'OVERDUE'
export function effectiveStatus(debt: Debt, payments: Payment[]): EffectiveStatus {
  if (debt.status === 'CANCELLED') return 'CANCELLED'
  if (debt.status === 'PAID' || remainingFor(debt, payments) === 0) return 'PAID'
  return isOverdue(debt, payments) ? 'OVERDUE' : 'ACTIVE'
}

export interface Summary {
  total: number
  paid: number
  remaining: number
  active: number
  overdue: number
  dueSoon: number
  lentTotal: number
  lentRemaining: number
}

// Cancelled debts are excluded from all totals. Totals are split by direction:
// "OWED" (what I owe) and "LENT" (what friends owe me).
export function summarize(debts: Debt[], payments: Payment[]): Summary {
  const live = debts.filter((d) => d.status !== 'CANCELLED')
  const owed = live.filter((d) => d.direction === 'OWED')
  const lent = live.filter((d) => d.direction === 'LENT')
  const total = sum(owed.map((d) => d.original_amount))
  const paid = sum(owed.map((d) => paidFor(d, payments)))
  return {
    total,
    paid,
    remaining: fromTiyn(toTiyn(total) - toTiyn(paid)),
    active: owed.filter((d) => effectiveStatus(d, payments) === 'ACTIVE' || effectiveStatus(d, payments) === 'OVERDUE').length,
    overdue: owed.filter((d) => isOverdue(d, payments)).length,
    dueSoon: owed.filter((d) => isDueSoon(d, payments)).length,
    lentTotal: sum(lent.map((d) => d.original_amount)),
    lentRemaining: sum(lent.map((d) => remainingFor(d, payments))),
  }
}

// Forecast = remaining ÷ average monthly payment over the last 3 months, rounded up.
export function forecastMonths(remaining: number, payments: Payment[], today = todayIso()): number | null {
  const from = new Date(today + 'T00:00:00')
  from.setMonth(from.getMonth() - 3)
  const fromIso = from.toISOString().slice(0, 10)
  const recent = payments.filter((p) => p.payment_date >= fromIso && p.payment_date <= today)
  if (recent.length === 0 || remaining <= 0) return null
  const avg = sum(recent.map((p) => p.amount)) / 3
  if (avg <= 0) return null
  return Math.ceil(remaining / avg)
}

export function monthsToPayoff(remaining: number, perMonth: number): number | null {
  if (perMonth <= 0 || remaining <= 0) return null
  return Math.ceil(remaining / perMonth)
}

export function paymentToMeetDeadline(remaining: number, dueIso: string, today = todayIso()): number {
  return Math.ceil(remaining / monthsUntil(today, dueIso))
}

export function paymentsByMonth(payments: Payment[], months = 6, today = todayIso()): Array<{ month: string; total: number }> {
  const out: Array<{ month: string; total: number }> = []
  const d = new Date(today + 'T00:00:00')
  for (let i = months - 1; i >= 0; i--) {
    const m = new Date(d.getFullYear(), d.getMonth() - i, 1)
    const key = `${m.getFullYear()}-${String(m.getMonth() + 1).padStart(2, '0')}`
    out.push({ month: key, total: sum(payments.filter((p) => monthKey(p.payment_date) === key).map((p) => p.amount)) })
  }
  return out
}

export function validateDebt(input: { title: string; creditor: string; amount: number | null; dueDate: string; rate: string }, today = todayIso()) {
  const errors: Record<string, string> = {}
  if (!input.title.trim()) errors.title = 'required'
  else if (input.title.trim().length > 200) errors.title = 'tooLong'
  if (!input.creditor.trim()) errors.creditor = 'required'
  if (input.amount === null) errors.amount = 'amount'
  if (input.rate.trim()) {
    const r = Number(input.rate.replace(',', '.'))
    if (!Number.isFinite(r) || r < 0 || r > 100) errors.rate = 'rate'
  }
  if (input.dueDate && input.dueDate < today) errors.dueDate = 'pastDue'
  return errors
}

export function validatePayment(amount: number | null, date: string, remaining: number, debtStatus: string, today = todayIso()) {
  const errors: Record<string, string> = {}
  if (debtStatus === 'CANCELLED') errors.form = 'cancelled'
  if (amount === null) errors.amount = 'amount'
  else if (amount > remaining) errors.amount = 'exceeds'
  if (!date) errors.date = 'required'
  else if (date > today) errors.date = 'future'
  return errors
}
