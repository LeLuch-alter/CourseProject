import { supabase } from '../lib/supabase'
import { addMonths } from '../lib/dates'
import { toTiyn, fromTiyn } from '../lib/money'

function fail(error: { message: string } | null): never {
  throw new Error(error?.message ?? 'Request failed')
}

// ---------------------------------------------------------------- installments

export interface Installment {
  id: number
  debt_id: number
  seq: number
  due_date: string
  amount: string
  payment_id: number | null
}

export async function listInstallments(debtId: number): Promise<Installment[]> {
  const { data, error } = await supabase.from('installments').select('*').eq('debt_id', debtId).order('seq')
  if (error) fail(error)
  return data as Installment[]
}

// Equal parts in tiyn; the last instalment takes the rounding remainder so the sum is exact.
export function splitEqually(total: number, parts: number): number[] {
  const t = toTiyn(total)
  const base = Math.floor(t / parts)
  return Array.from({ length: parts }, (_, i) => fromTiyn(i === parts - 1 ? t - base * (parts - 1) : base))
}

export async function createSchedule(debtId: number, remaining: number, parts: number, firstDate: string): Promise<void> {
  await supabase.from('installments').delete().eq('debt_id', debtId)
  const rows = splitEqually(remaining, parts).map((amount, i) => ({ debt_id: debtId, seq: i + 1, due_date: addMonths(firstDate, i), amount }))
  const { error } = await supabase.from('installments').insert(rows)
  if (error) fail(error)
}

export async function deleteSchedule(debtId: number): Promise<void> {
  const { error } = await supabase.from('installments').delete().eq('debt_id', debtId)
  if (error) fail(error)
}

export async function markInstallmentPaid(inst: Installment, paymentId: number): Promise<void> {
  const { error } = await supabase.from('installments').update({ payment_id: paymentId }).eq('id', inst.id)
  if (error) fail(error)
}

// ---------------------------------------------------------------- splits

export async function createSplit(title: string, total: number, memberIds: string[], due: string | null): Promise<string> {
  const { data, error } = await supabase.rpc('create_split', { p_title: title, p_total: total, p_members: memberIds, p_due: due })
  if (error) fail(error)
  return data as string
}

// ---------------------------------------------------------------- rooms

export interface Room {
  id: string
  name: string
  created_by: string
  created_at: string
}
export interface RoomMessage {
  id: number
  room_id: string
  sender_id: string
  body: string
  created_at: string
}

export async function listRooms(): Promise<Array<Room & { members: string[] }>> {
  const { data, error } = await supabase.from('rooms').select('*, room_members(user_id)').order('created_at', { ascending: false })
  if (error) fail(error)
  return (data as Array<Room & { room_members: Array<{ user_id: string }> }>).map((r) => ({ ...r, members: r.room_members.map((m) => m.user_id) }))
}

export async function createRoom(name: string, memberIds: string[]): Promise<string> {
  const { data, error } = await supabase.rpc('create_room', { p_name: name, p_members: memberIds })
  if (error) fail(error)
  return data as string
}

export async function leaveRoom(roomId: string, userId: string): Promise<void> {
  const { error } = await supabase.from('room_members').delete().eq('room_id', roomId).eq('user_id', userId)
  if (error) fail(error)
}

export async function deleteRoom(roomId: string): Promise<void> {
  const { error } = await supabase.from('rooms').delete().eq('id', roomId)
  if (error) fail(error)
}

export async function listRoomMessages(roomId: string): Promise<RoomMessage[]> {
  const { data, error } = await supabase.from('room_messages').select('*').eq('room_id', roomId).order('created_at').limit(500)
  if (error) fail(error)
  return data as RoomMessage[]
}

export async function sendRoomMessage(roomId: string, body: string): Promise<RoomMessage> {
  const { data: u } = await supabase.auth.getUser()
  const { data, error } = await supabase.from('room_messages').insert({ room_id: roomId, sender_id: u.user?.id, body }).select().single()
  if (error) fail(error)
  return data as RoomMessage
}

// ---------------------------------------------------------------- reminders

export async function updateReminderSettings(userId: string, days: number, email: boolean): Promise<void> {
  const { error } = await supabase.from('profiles').update({ reminder_days: days, email_reminders: email }).eq('id', userId)
  if (error) fail(error)
}

export async function findByUsername(username: string) {
  const { data, error } = await supabase.from('profiles').select('*').eq('username', username.toLowerCase()).maybeSingle()
  if (error) fail(error)
  return data
}
