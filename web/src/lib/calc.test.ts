import { describe, expect, it } from 'vitest'
import type { Debt, Payment } from '../api/types'
import {
  averageMonthlyPayment,
  balanceWith,
  debtorsOf,
  effectiveStatus,
  forecastMonths,
  isDueSoon,
  isOverdue,
  monthsToPayoff,
  paidFor,
  paymentToMeetDeadline,
  payoffPlan,
  remainingFor,
  summarize,
  validateDebt,
  validatePayment,
} from './calc'

const TODAY = '2026-10-15'

let nextId = 1
function debt(p: Partial<Debt> = {}): Debt {
  return {
    id: nextId++,
    user_id: 'me',
    title: 'Debt',
    creditor_name: 'Bank',
    original_amount: '1000',
    interest_rate: null,
    created_at: '2026-01-01T00:00:00Z',
    due_date: null,
    status: 'ACTIVE',
    notes: null,
    direction: 'OWED',
    loan_id: null,
    counterparty_id: null,
    currency: 'KZT',
    ...p,
  }
}
function pay(debtId: number, amount: string, date = '2026-10-01'): Payment {
  return { id: nextId++, debt_id: debtId, amount, payment_date: date, notes: null, created_at: date, mirror_of: null, created_by: 'me' }
}

describe('paid and remaining', () => {
  it('sums payments exactly, without floating point drift', () => {
    const d = debt({ original_amount: '0.30' })
    const ps = [pay(d.id, '0.10'), pay(d.id, '0.20')]
    expect(paidFor(d, ps)).toBe(0.3)
    expect(remainingFor(d, ps)).toBe(0)
  })

  it('ignores payments of other debts and never goes below zero', () => {
    const a = debt({ original_amount: '500' })
    const b = debt()
    expect(remainingFor(a, [pay(a.id, '200'), pay(b.id, '900')])).toBe(300)
    expect(remainingFor(a, [pay(a.id, '600')])).toBe(0)
  })
})

describe('overdue', () => {
  it('is overdue when the due date passed and money is left', () => {
    const d = debt({ due_date: '2026-10-14' })
    expect(isOverdue(d, [], TODAY)).toBe(true)
  })

  it('is not overdue on the due date itself', () => {
    expect(isOverdue(debt({ due_date: TODAY }), [], TODAY)).toBe(false)
  })

  it('is not overdue when fully paid or cancelled', () => {
    const paid = debt({ due_date: '2026-09-01' })
    expect(isOverdue(paid, [pay(paid.id, '1000')], TODAY)).toBe(false)
    expect(isOverdue(debt({ due_date: '2026-09-01', status: 'CANCELLED' }), [], TODAY)).toBe(false)
  })

  it('is not overdue without a due date', () => {
    expect(isOverdue(debt({ due_date: null }), [], TODAY)).toBe(false)
  })

  it('due soon covers today .. today + N days', () => {
    expect(isDueSoon(debt({ due_date: '2026-10-29' }), [], TODAY)).toBe(true)
    expect(isDueSoon(debt({ due_date: '2026-10-30' }), [], TODAY)).toBe(false)
    expect(isDueSoon(debt({ due_date: '2026-10-20' }), [], TODAY, 3)).toBe(false)
  })

  it('effective status: paid beats overdue, cancelled beats everything', () => {
    const d = debt({ due_date: '2026-01-01' })
    expect(effectiveStatus(d, [pay(d.id, '1000')])).toBe('PAID')
    expect(effectiveStatus(debt({ status: 'CANCELLED', due_date: '2026-01-01' }), [])).toBe('CANCELLED')
  })
})

describe('forecast', () => {
  it('divides the remaining by the 3-month average and rounds up', () => {
    // 900 paid over 3 months = 300 per month; 1000 left -> 3.33 -> 4 months
    const ps = [pay(1, '300', '2026-08-20'), pay(1, '300', '2026-09-20'), pay(1, '300', '2026-10-10')]
    expect(forecastMonths(1000, ps, TODAY)).toBe(4)
  })

  it('ignores payments older than 3 months and in the future', () => {
    const ps = [pay(1, '9000', '2026-06-01'), pay(1, '9000', '2026-12-01'), pay(1, '300', '2026-10-01')]
    // only 300 counts: 100 per month, 250 left -> 3 months
    expect(forecastMonths(250, ps, TODAY)).toBe(3)
  })

  it('returns null with no recent payments or nothing left', () => {
    expect(forecastMonths(1000, [], TODAY)).toBeNull()
    expect(forecastMonths(0, [pay(1, '100', '2026-10-01')], TODAY)).toBeNull()
  })

  it('average monthly payment uses the same 3-month window', () => {
    expect(averageMonthlyPayment([pay(1, '100', '2026-10-01'), pay(1, '200', '2026-09-01')], TODAY)).toBe(100)
  })
})

describe('planner helpers', () => {
  it('months to payoff rounds up', () => {
    expect(monthsToPayoff(1000, 300)).toBe(4)
    expect(monthsToPayoff(1000, 0)).toBeNull()
  })

  it('payment to meet a deadline uses whole calendar months, at least 1', () => {
    expect(paymentToMeetDeadline(1000, '2027-01-20', TODAY)).toBe(334)
    expect(paymentToMeetDeadline(1000, '2026-10-20', TODAY)).toBe(1000)
  })
})

describe('summary', () => {
  it('excludes cancelled debts and keeps directions and currencies apart', () => {
    const owed = debt({ original_amount: '1000' })
    const lent = debt({ original_amount: '400', direction: 'LENT', creditor_name: 'Aisha' })
    const usd = debt({ original_amount: '50', currency: 'USD' })
    const cancelled = debt({ original_amount: '9999', status: 'CANCELLED' })
    const s = summarize([owed, lent, usd, cancelled], [pay(owed.id, '250'), pay(lent.id, '100')])
    expect(s.total).toBe(1000)
    expect(s.paid).toBe(250)
    expect(s.remaining).toBe(750)
    expect(s.lentRemaining).toBe(300)
    expect(s.lentReturned).toBe(100)
    expect(s.foreignOwed).toEqual({ USD: 50 })
  })

  it('debtors are grouped by name, largest first', () => {
    const a1 = debt({ direction: 'LENT', creditor_name: 'Aisha', original_amount: '100' })
    const a2 = debt({ direction: 'LENT', creditor_name: 'Aisha', original_amount: '50' })
    const d1 = debt({ direction: 'LENT', creditor_name: 'Daniyar', original_amount: '500' })
    expect(debtorsOf([a1, a2, d1], [])).toEqual([
      { name: 'Daniyar', remaining: 500, count: 1 },
      { name: 'Aisha', remaining: 150, count: 2 },
    ])
  })

  it('balance with a friend nets what they owe me against what I owe them', () => {
    const lent = debt({ direction: 'LENT', counterparty_id: 'f', original_amount: '12000' })
    const owed = debt({ direction: 'OWED', counterparty_id: 'f', original_amount: '5000' })
    const other = debt({ direction: 'LENT', counterparty_id: 'x', original_amount: '700' })
    expect(balanceWith('f', [lent, owed, other], [pay(owed.id, '1000')])).toBe(8000)
  })
})

describe('payoff plan', () => {
  const a = debt({ title: 'A', original_amount: '300', due_date: '2026-12-31', interest_rate: '5' })
  const b = debt({ title: 'B', original_amount: '100', due_date: '2026-10-31', interest_rate: '30' })
  const c = debt({ title: 'C', original_amount: '600', due_date: null, interest_rate: '18' })
  const all = [a, b, c]

  it('deadline: earliest due date first, no due date last', () => {
    const plan = payoffPlan(all, [], 200, 'deadline', TODAY)!
    expect(plan.items.map((i) => i.title)).toEqual(['B', 'A', 'C'])
    expect(plan.months).toBe(5) // 1000 / 200
    expect(plan.items[0].payoffMonth).toBe(1)
  })

  it('snowball: smallest balance first; avalanche: highest rate first', () => {
    expect(payoffPlan(all, [], 200, 'snowball', TODAY)!.items.map((i) => i.title)).toEqual(['B', 'A', 'C'])
    expect(payoffPlan(all, [], 200, 'avalanche', TODAY)!.items.map((i) => i.title)).toEqual(['B', 'C', 'A'])
  })

  it('does not grow balances with interest and flags debts that miss their due date', () => {
    const plan = payoffPlan(all, [], 100, 'avalanche', TODAY)!
    expect(plan.months).toBe(10)
    const late = plan.items.find((i) => i.title === 'A')!
    expect(late.payoffMonth).toBe(10)
    expect(late.late).toBe(true)
    expect(plan.finishDate).toBe('2027-07-31')
  })

  it('returns null without a budget or open debts', () => {
    expect(payoffPlan(all, [], 0, 'deadline', TODAY)).toBeNull()
    expect(payoffPlan([], [], 100, 'deadline', TODAY)).toBeNull()
  })
})

describe('validation', () => {
  it('debt form', () => {
    expect(validateDebt({ title: '', creditor: 'x', amount: 1, dueDate: '', rate: '' }, TODAY)).toEqual({ title: 'required' })
    expect(validateDebt({ title: 'a', creditor: 'x', amount: 1, dueDate: '2026-10-01', rate: '101' }, TODAY)).toEqual({ dueDate: 'pastDue', rate: 'rate' })
  })

  it('payment form: not above the balance, not in the future, not on cancelled', () => {
    expect(validatePayment(500, TODAY, 400, 'ACTIVE', TODAY)).toEqual({ amount: 'exceeds' })
    expect(validatePayment(100, '2026-10-16', 400, 'ACTIVE', TODAY)).toEqual({ date: 'future' })
    expect(validatePayment(100, TODAY, 400, 'CANCELLED', TODAY)).toEqual({ form: 'cancelled' })
    expect(validatePayment(100, TODAY, 400, 'ACTIVE', TODAY)).toEqual({})
  })
})
