export type DebtStatus = 'ACTIVE' | 'PAID' | 'CANCELLED'
export type Direction = 'OWED' | 'LENT'

export interface Debt {
  id: number
  user_id: string
  title: string
  creditor_name: string
  original_amount: string
  interest_rate: string | null
  created_at: string
  due_date: string | null
  status: DebtStatus
  notes: string | null
  direction: Direction
  loan_id: string | null
  counterparty_id: string | null
  currency?: string
  split_id?: string | null
}

export interface Payment {
  id: number
  debt_id: number
  amount: string
  payment_date: string
  notes: string | null
  created_at: string
  mirror_of: number | null
  created_by: string | null
  receipt_path?: string | null
}

export interface Profile {
  id: string
  username: string
  display_name: string
  avatar_url: string | null
  bio: string | null
  created_at: string
  reminder_days?: number
  email_reminders?: boolean
  reminder_time?: string
  reminder_tz?: string
  reminder_mode?: ReminderMode
  reminder_weekdays?: number[]
  reminder_dates?: string[]
}

export type ReminderMode = 'daily' | 'weekdays' | 'dates'

export type FriendshipStatus = 'PENDING' | 'ACCEPTED'
export interface Friendship {
  id: number
  requester_id: string
  addressee_id: string
  status: FriendshipStatus
  created_at: string
}

export interface Message {
  id: number
  sender_id: string
  receiver_id: string
  body: string
  created_at: string
  read_at: string | null
}

export type LoanStatus = 'PENDING' | 'ACTIVE' | 'DECLINED' | 'CANCELLED' | 'REPAID'
export interface Loan {
  id: string
  lender_id: string
  borrower_id: string
  initiator_id: string
  amount: string
  purpose: string
  due_date: string
  borrower_card: string | null
  interest_rate: string | null
  status: LoanStatus
  receipt_no: string | null
  accepted_at: string | null
  created_at: string
}

export interface Notification {
  id: number
  user_id: string
  kind: string
  title: string
  body: string | null
  link: string | null
  read: boolean
  created_at: string
}
