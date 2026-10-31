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
  lentReturned: number
  // Remaining amounts in other currencies; Kvit never converts them into tenge.
  foreignOwed: Record<string, number>
  foreignLent: Record<string, number>
}

export const currencyOf = (d: Debt): string => d.currency ?? 'KZT'

function remainingByCurrency(debts: Debt[], payments: Payment[]): Record<string, number> {
  const out: Record<string, number> = {}
  for (const d of debts) {
    const rem = remainingFor(d, payments)
    if (rem > 0) out[currencyOf(d)] = fromTiyn(toTiyn(out[currencyOf(d)] ?? 0) + toTiyn(rem))
  }
  return out
}

// Cancelled debts are excluded from all totals. Totals are split by direction:
// "OWED" (what I owe) and "LENT" (what friends owe me). Money totals are in tenge;
// debts in other currencies are counted separately and never added to tenge.
export function summarize(debts: Debt[], payments: Payment[]): Summary {
  const live = debts.filter((d) => d.status !== 'CANCELLED')
  const owedAll = live.filter((d) => d.direction === 'OWED')
  const lentAll = live.filter((d) => d.direction === 'LENT')
  const owed = owedAll.filter((d) => currencyOf(d) === 'KZT')
  const lent = lentAll.filter((d) => currencyOf(d) === 'KZT')
  const total = sum(owed.map((d) => d.original_amount))
  const paid = sum(owed.map((d) => paidFor(d, payments)))
  const lentTotal = sum(lent.map((d) => d.original_amount))
  const lentRemaining = sum(lent.map((d) => remainingFor(d, payments)))
  return {
    total,
    paid,
    remaining: fromTiyn(toTiyn(total) - toTiyn(paid)),
    active: owedAll.filter((d) => effectiveStatus(d, payments) === 'ACTIVE' || effectiveStatus(d, payments) === 'OVERDUE').length,
    overdue: owedAll.filter((d) => isOverdue(d, payments)).length,
    dueSoon: owedAll.filter((d) => isDueSoon(d, payments)).length,
    lentTotal,
    lentRemaining,
    lentReturned: fromTiyn(toTiyn(lentTotal) - toTiyn(lentRemaining)),
    foreignOwed: remainingByCurrency(owedAll.filter((d) => currencyOf(d) !== 'KZT'), payments),
    foreignLent: remainingByCurrency(lentAll.filter((d) => currencyOf(d) !== 'KZT'), payments),
  }
}

// Who owes me how much (LENT debts in tenge with a balance), largest first.
export function debtorsOf(debts: Debt[], payments: Payment[]): Array<{ name: string; remaining: number; count: number }> {
  const map = new Map<string, { remaining: number; count: number }>()
  for (const d of debts) {
    if (d.direction !== 'LENT' || d.status === 'CANCELLED' || currencyOf(d) !== 'KZT') continue
    const rem = remainingFor(d, payments)
    if (rem <= 0) continue
    const cur = map.get(d.creditor_name) ?? { remaining: 0, count: 0 }
    map.set(d.creditor_name, { remaining: fromTiyn(toTiyn(cur.remaining) + toTiyn(rem)), count: cur.count + 1 })
  }
  return [...map.entries()].map(([name, v]) => ({ name, ...v })).sort((a, b) => b.remaining - a.remaining)
}

// Net balance with one friend across IOUs and split bills (tenge only).
// Positive = the friend owes me, negative = I owe the friend.
export function balanceWith(friendId: string, debts: Debt[], payments: Payment[]): number {
  let t = 0
  for (const d of debts) {
    if (d.counterparty_id !== friendId || d.status === 'CANCELLED' || currencyOf(d) !== 'KZT') continue
    const rem = toTiyn(remainingFor(d, payments))
    t += d.direction === 'LENT' ? rem : -rem
  }
  return fromTiyn(t)
}

// Average paid per month over the last 3 months (0 when there were no payments).
export function averageMonthlyPayment(payments: Payment[], today = todayIso()): number {
  const from = new Date(today + 'T00:00:00')
  from.setMonth(from.getMonth() - 3)
  const fromIso = from.toISOString().slice(0, 10)
  const recent = payments.filter((p) => p.payment_date >= fromIso && p.payment_date <= today)
  return fromTiyn(Math.round(toTiyn(sum(recent.map((p) => p.amount))) / 3))
}

// ---------------------------------------------------------------- payoff plan
// The order in which debts receive the monthly budget:
//  deadline  - earliest due date first (debts without a due date go last)
//  snowball  - smallest remaining balance first
//  avalanche - highest stored rate first. The rate is only used for ordering:
//              Kvit does not accrue interest, so balances do not grow.
export type Strategy = 'deadline' | 'snowball' | 'avalanche'

export interface PlanItem {
  debtId: number
  title: string
  remaining: number
  dueDate: string | null
  payoffMonth: number // 1 = the first month of the plan
  payoffDate: string // last day of that month
  late: boolean // paid off after its due date
}
export interface Plan {
  strategy: Strategy
  budget: number
  months: number
  finishDate: string
  items: PlanItem[]
}

const MAX_PLAN_MONTHS = 600

interface PlanRow {
  remaining: number
  dueDate: string | null
  rate: number
}

function orderFor(strategy: Strategy) {
  return (a: PlanRow, b: PlanRow) => {
    if (strategy === 'snowball') return a.remaining - b.remaining
    if (strategy === 'avalanche') return b.rate - a.rate || a.remaining - b.remaining
    return (a.dueDate ?? '9999-12-31').localeCompare(b.dueDate ?? '9999-12-31') || a.remaining - b.remaining
  }
}

function endOfMonth(today: string, offset: number): string {
  const d = new Date(today + 'T00:00:00')
  const e = new Date(d.getFullYear(), d.getMonth() + offset, 0)
  return `${e.getFullYear()}-${String(e.getMonth() + 1).padStart(2, '0')}-${String(e.getDate()).padStart(2, '0')}`
}

// Simulates month by month: the whole budget goes to the first debt in the order,
// what is left over spills to the next one. Works for open OWED debts in tenge.
// Month 1 is the current month, so its payoff date is the end of the current month.
export function payoffPlan(debts: Debt[], payments: Payment[], budget: number, strategy: Strategy, today = todayIso()): Plan | null {
  if (!(budget > 0)) return null
  const open = debts
    .filter((d) => d.direction === 'OWED' && d.status !== 'CANCELLED' && currencyOf(d) === 'KZT')
    .map((d) => ({ debtId: d.id, title: d.title, remaining: remainingFor(d, payments), dueDate: d.due_date, rate: Number(d.interest_rate ?? 0) }))
    .filter((d) => d.remaining > 0)
    .sort(orderFor(strategy))
  if (open.length === 0) return null
  const left = open.map((d) => toTiyn(d.remaining))
  const doneAt: number[] = open.map(() => 0)
  const perMonth = toTiyn(budget)
  let month = 0
  while (left.some((x) => x > 0) && month < MAX_PLAN_MONTHS) {
    month++
    let pot = perMonth
    for (let i = 0; i < open.length && pot > 0; i++) {
      if (left[i] === 0) continue
      const pay = Math.min(pot, left[i])
      left[i] -= pay
      pot -= pay
      if (left[i] === 0) doneAt[i] = month
    }
  }
  if (left.some((x) => x > 0)) return null
  const items: PlanItem[] = open.map((d, i) => {
    const payoffDate = endOfMonth(today, doneAt[i])
    return { debtId: d.debtId, title: d.title, remaining: d.remaining, dueDate: d.dueDate, payoffMonth: doneAt[i], payoffDate, late: !!d.dueDate && payoffDate > d.dueDate }
  })
  return { strategy, budget, months: month, finishDate: endOfMonth(today, month), items }
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
