import { supabase } from '../lib/supabase'
import type { Debt, Payment, DebtStatus } from './types'

function fail(error: { message: string } | null): never {
  throw new Error(error?.message ?? 'Request failed')
}

export async function listDebts(): Promise<Debt[]> {
  const { data, error } = await supabase.from('debts').select('*').order('created_at', { ascending: false })
  if (error) fail(error)
  return data as Debt[]
}

export async function listPayments(): Promise<Payment[]> {
  const { data, error } = await supabase.from('payments').select('*').order('payment_date', { ascending: false }).order('id', { ascending: false })
  if (error) fail(error)
  return data as Payment[]
}

export interface DebtInput {
  title: string
  creditor_name: string
  original_amount: number
  interest_rate: number | null
  due_date: string | null
  status: DebtStatus
  notes: string | null
}

export async function createDebt(input: DebtInput): Promise<Debt> {
  const { data, error } = await supabase.from('debts').insert(input).select().single()
  if (error) fail(error)
  return data as Debt
}

export async function updateDebt(id: number, input: Partial<DebtInput>): Promise<Debt> {
  const { data, error } = await supabase.from('debts').update(input).eq('id', id).select().single()
  if (error) fail(error)
  return data as Debt
}

export async function deleteDebt(id: number): Promise<void> {
  const { error } = await supabase.from('debts').delete().eq('id', id)
  if (error) fail(error)
}

export async function createPayment(input: { debt_id: number; amount: number; payment_date: string; notes: string | null }): Promise<Payment> {
  const { data, error } = await supabase.from('payments').insert(input).select().single()
  if (error) fail(error)
  return data as Payment
}

export async function deletePayment(id: number): Promise<void> {
  const { error } = await supabase.from('payments').delete().eq('id', id)
  if (error) fail(error)
}

// Status "PAID" is set automatically when the balance reaches 0 and removed when a payment is deleted.
export async function syncStatus(debt: Debt, remaining: number): Promise<void> {
  if (debt.status === 'CANCELLED') return
  const wanted: DebtStatus = remaining === 0 ? 'PAID' : 'ACTIVE'
  if (debt.status !== wanted) await updateDebt(debt.id, { status: wanted })
}
