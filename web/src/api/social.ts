import { supabase } from '../lib/supabase'
import type { Profile, Friendship, Message, Loan, Notification } from './types'

function fail(error: { message: string } | null): never {
  throw new Error(error?.message ?? 'Request failed')
}

// ---------------------------------------------------------------- profiles

export async function getProfile(id: string): Promise<Profile | null> {
  const { data, error } = await supabase.from('profiles').select('*').eq('id', id).maybeSingle()
  if (error) fail(error)
  return data as Profile | null
}

export async function getProfiles(ids: string[]): Promise<Record<string, Profile>> {
  if (ids.length === 0) return {}
  const { data, error } = await supabase.from('profiles').select('*').in('id', ids)
  if (error) fail(error)
  return Object.fromEntries((data as Profile[]).map((p) => [p.id, p]))
}

export async function updateProfile(id: string, input: Partial<Pick<Profile, 'username' | 'display_name' | 'bio' | 'avatar_url'>>): Promise<Profile> {
  const { data, error } = await supabase
    .from('profiles')
    .update({ ...input, updated_at: new Date().toISOString() })
    .eq('id', id)
    .select()
    .single()
  if (error) fail(error)
  return data as Profile
}

export async function uploadAvatar(userId: string, file: File): Promise<string> {
  const ext = file.type === 'image/png' ? 'png' : file.type === 'image/webp' ? 'webp' : 'jpg'
  const path = `${userId}/avatar.${ext}`
  const { error } = await supabase.storage.from('avatars').upload(path, file, { upsert: true, contentType: file.type })
  if (error) fail(error)
  const { data } = supabase.storage.from('avatars').getPublicUrl(path)
  // cache-busting query so the new photo shows immediately
  return `${data.publicUrl}?v=${Date.now()}`
}

export async function searchProfiles(query: string, excludeId: string): Promise<Profile[]> {
  const q = query.trim().toLowerCase().replace(/^@/, '')
  if (q.length < 2) return []
  const { data, error } = await supabase.from('profiles').select('*').neq('id', excludeId).or(`username.ilike.%${q}%,display_name.ilike.%${q}%`).limit(10)
  if (error) fail(error)
  return data as Profile[]
}

// ---------------------------------------------------------------- friends

export async function listFriendships(): Promise<Friendship[]> {
  const { data, error } = await supabase.from('friendships').select('*').order('created_at', { ascending: false })
  if (error) fail(error)
  return data as Friendship[]
}

export async function sendFriendRequest(addresseeId: string): Promise<void> {
  const { data: u } = await supabase.auth.getUser()
  const { error } = await supabase.from('friendships').insert({ requester_id: u.user?.id, addressee_id: addresseeId })
  if (error) fail(error)
}

export async function acceptFriendRequest(id: number): Promise<void> {
  const { error } = await supabase.from('friendships').update({ status: 'ACCEPTED' }).eq('id', id)
  if (error) fail(error)
}

export async function removeFriendship(id: number): Promise<void> {
  const { error } = await supabase.from('friendships').delete().eq('id', id)
  if (error) fail(error)
}

// ---------------------------------------------------------------- chat

export async function listMessages(friendId: string): Promise<Message[]> {
  const { data: u } = await supabase.auth.getUser()
  const me = u.user?.id
  const { data, error } = await supabase
    .from('messages')
    .select('*')
    .or(`and(sender_id.eq.${me},receiver_id.eq.${friendId}),and(sender_id.eq.${friendId},receiver_id.eq.${me})`)
    .order('created_at', { ascending: true })
    .limit(500)
  if (error) fail(error)
  return data as Message[]
}

export async function sendMessage(receiverId: string, body: string): Promise<Message> {
  const { data: u } = await supabase.auth.getUser()
  const { data, error } = await supabase.from('messages').insert({ sender_id: u.user?.id, receiver_id: receiverId, body }).select().single()
  if (error) fail(error)
  return data as Message
}

export async function markRead(friendId: string): Promise<void> {
  const { data: u } = await supabase.auth.getUser()
  const { error } = await supabase.from('messages').update({ read_at: new Date().toISOString() }).eq('sender_id', friendId).eq('receiver_id', u.user?.id).is('read_at', null)
  if (error) fail(error)
}

export async function unreadCounts(): Promise<Record<string, number>> {
  const { data: u } = await supabase.auth.getUser()
  const { data, error } = await supabase.from('messages').select('sender_id').eq('receiver_id', u.user?.id).is('read_at', null)
  if (error) fail(error)
  const out: Record<string, number> = {}
  for (const m of data as Array<{ sender_id: string }>) out[m.sender_id] = (out[m.sender_id] ?? 0) + 1
  return out
}

// ---------------------------------------------------------------- loans

export async function listLoans(): Promise<Loan[]> {
  const { data, error } = await supabase.from('loans').select('*').order('created_at', { ascending: false })
  if (error) fail(error)
  return data as Loan[]
}

export async function getLoan(id: string): Promise<Loan | null> {
  const { data, error } = await supabase.from('loans').select('*').eq('id', id).maybeSingle()
  if (error) fail(error)
  return data as Loan | null
}

export interface LoanInput {
  friendId: string
  // 'lend' = I give money to the friend; 'borrow' = I ask the friend for money
  kind: 'lend' | 'borrow'
  amount: number
  purpose: string
  due_date: string
  interest_rate?: number | null
  // Only when I am the borrower: my card for the transfer (16 digits, nothing else)
  card?: string
}

export async function createLoan(input: LoanInput): Promise<Loan> {
  const { data: u } = await supabase.auth.getUser()
  const me = u.user!.id
  const row = {
    lender_id: input.kind === 'lend' ? me : input.friendId,
    borrower_id: input.kind === 'lend' ? input.friendId : me,
    initiator_id: me,
    amount: input.amount,
    purpose: input.purpose,
    due_date: input.due_date,
    borrower_card: input.kind === 'borrow' ? (input.card ?? null) : null,
    interest_rate: input.interest_rate ?? null,
  }
  const { data, error } = await supabase.from('loans').insert(row).select().single()
  if (error) fail(error)
  return data as Loan
}

export async function acceptLoan(id: string, card?: string): Promise<void> {
  const { error } = await supabase.rpc('accept_loan', { p_loan_id: id, p_card: card ?? null })
  if (error) fail(error)
}

export async function setLoanStatus(id: string, status: 'DECLINED' | 'CANCELLED'): Promise<void> {
  const { error } = await supabase.from('loans').update({ status }).eq('id', id)
  if (error) fail(error)
}

// ---------------------------------------------------------------- notifications

export async function listNotifications(): Promise<Notification[]> {
  const { data, error } = await supabase.from('notifications').select('*').order('created_at', { ascending: false }).limit(50)
  if (error) fail(error)
  return data as Notification[]
}

export async function markNotificationsRead(ids: number[]): Promise<void> {
  if (ids.length === 0) return
  const { error } = await supabase.from('notifications').update({ read: true }).in('id', ids)
  if (error) fail(error)
}

export async function clearNotifications(): Promise<void> {
  const { data: u } = await supabase.auth.getUser()
  const { error } = await supabase.from('notifications').delete().eq('user_id', u.user?.id)
  if (error) fail(error)
}
