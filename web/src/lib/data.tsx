// Loads debts and payments, keeps them in memory and exposes derived values.
// Every mutation goes through the api/ layer and then calls reload().
// Data is also refreshed when the tab becomes visible again, when the network comes back
// and when a loan changes, so changes made elsewhere show up without reloading the page.
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import type { Debt, Payment } from '../api/types'
import { listDebts, listPayments } from '../api/debts'
import { useAuth } from './auth'
import { supabase } from './supabase'
import { summarize, type Summary } from './calc'

interface DataCtx {
  debts: Debt[]
  payments: Payment[]
  summary: Summary
  loading: boolean
  error: string | null
  reload: () => Promise<void>
}
const Ctx = createContext<DataCtx | null>(null)

export function DataProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  // The session object is replaced on every token refresh; only a different account should reset the data.
  const userId = user?.id ?? null
  const [debts, setDebts] = useState<Debt[]>([])
  const [payments, setPayments] = useState<Payment[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  // Only the newest request may write its result, so a slow old response cannot overwrite fresh data.
  const requestNo = useRef(0)

  const reload = useCallback(async () => {
    if (!userId) return
    const no = ++requestNo.current
    try {
      const [d, p] = await Promise.all([listDebts(), listPayments()])
      if (no !== requestNo.current) return
      setDebts(d)
      setPayments(p)
      setError(null)
    } catch (e) {
      if (no !== requestNo.current) return
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      if (no === requestNo.current) setLoading(false)
    }
  }, [userId])

  useEffect(() => {
    setDebts([])
    setPayments([])
    setError(null)
    setLoading(true)
    void reload()
  }, [reload])

  useEffect(() => {
    if (!userId) return
    const refresh = () => {
      if (document.visibilityState === 'visible') void reload()
    }
    document.addEventListener('visibilitychange', refresh)
    window.addEventListener('online', refresh)
    // A friend confirming or repaying a loan creates or changes debts on this account.
    const channel = supabase
      .channel(`data-${userId}`)
      .on('postgres_changes', { event: '*', schema: 'public', table: 'loans' }, refresh)
      .on('postgres_changes', { event: '*', schema: 'public', table: 'installments' }, refresh)
      .subscribe()
    return () => {
      document.removeEventListener('visibilitychange', refresh)
      window.removeEventListener('online', refresh)
      void supabase.removeChannel(channel)
    }
  }, [userId, reload])

  const summary = useMemo(() => summarize(debts, payments), [debts, payments])
  const value = useMemo<DataCtx>(() => ({ debts, payments, summary, loading, error, reload }), [debts, payments, summary, loading, error, reload])
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>
}

export function useData(): DataCtx {
  const v = useContext(Ctx)
  if (!v) throw new Error('useData outside provider')
  return v
}
