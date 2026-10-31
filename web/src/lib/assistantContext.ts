// Builds the DATA block the assistant sees. Every figure is computed here with the
// project's own rules, so the model explains numbers instead of inventing them.
// Card numbers, e-mails and other personal details are deliberately left out.
import type { Debt, Payment, Profile } from '../api/types'
import { averageMonthlyPayment, balanceWith, currencyOf, effectiveStatus, forecastMonths, paidFor, payoffPlan, remainingFor, summarize, type Plan, type Strategy } from './calc'
import { daysBetween, todayIso } from './dates'
import { sum } from './money'

const clip = (s: string | null | undefined, n: number) => (s ? s.replace(/\s+/g, ' ').slice(0, n) : undefined)

export function planForModel(plan: Plan | null) {
  if (!plan) return null
  return {
    strategy: plan.strategy,
    monthly_budget: plan.budget,
    months_to_pay_all: plan.months,
    finish_date: plan.finishDate,
    order: plan.items.map((i) => ({ debt_id: i.debtId, title: clip(i.title, 60), remaining: i.remaining, due_date: i.dueDate, paid_off_by: i.payoffDate, misses_due_date: i.late })),
  }
}

export function buildSnapshot(debts: Debt[], payments: Payment[], friends: Profile[], today = todayIso()) {
  const s = summarize(debts, payments)
  const own = payments.filter((p) => debts.find((d) => d.id === p.debt_id)?.direction === 'OWED')
  const avg = averageMonthlyPayment(own, today)
  const lastMonth = today.slice(0, 7)
  const strategies: Strategy[] = ['deadline', 'snowball', 'avalanche']
  return {
    today,
    rules: 'Totals are in KZT; other currencies are listed separately and never converted. Kvit does not charge interest.',
    summary: {
      i_owe_total: s.total,
      i_paid: s.paid,
      i_owe_remaining: s.remaining,
      active_debts: s.active,
      overdue_debts: s.overdue,
      owed_to_me_remaining: s.lentRemaining,
      owed_to_me_returned: s.lentReturned,
      other_currencies_i_owe: s.foreignOwed,
      other_currencies_owed_to_me: s.foreignLent,
    },
    payments_stats: {
      average_paid_per_month_last_3_months: avg,
      paid_this_month: sum(own.filter((p) => p.payment_date.startsWith(lastMonth)).map((p) => p.amount)),
      forecast_months_to_pay_all_at_that_average: forecastMonths(s.remaining, own, today),
    },
    debts: debts.slice(0, 80).map((d) => ({
      id: d.id,
      title: clip(d.title, 80),
      counterparty: clip(d.creditor_name, 60),
      direction: d.direction,
      currency: currencyOf(d),
      amount: Number(d.original_amount),
      paid: paidFor(d, payments),
      remaining: remainingFor(d, payments),
      due_date: d.due_date,
      days_until_due: d.due_date ? daysBetween(today, d.due_date) : null,
      status: effectiveStatus(d, payments),
      stored_rate_percent: d.interest_rate ? Number(d.interest_rate) : null,
      with_friend: d.counterparty_id ? friends.find((f) => f.id === d.counterparty_id)?.username ?? true : false,
      kind: d.loan_id ? 'iou' : d.split_id ? 'split_bill' : 'own',
      note: clip(d.notes, 100),
    })),
    recent_payments: payments.filter((p) => !p.mirror_of).slice(0, 15).map((p) => ({ debt_id: p.debt_id, amount: Number(p.amount), date: p.payment_date })),
    friends: friends.map((f) => ({ username: f.username, name: clip(f.display_name, 60), balance_kzt_positive_means_they_owe_me: balanceWith(f.id, debts, payments) })),
    payoff_plans_at_average_budget: avg > 0 ? Object.fromEntries(strategies.map((st) => [st, planForModel(payoffPlan(debts, payments, avg, st, today))])) : 'no payments in the last 3 months, ask the user for a monthly amount and call compute_plan',
  }
}
