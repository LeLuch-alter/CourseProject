// Loads debts and payments once, keeps them in memory and exposes derived values.
// Every mutation goes through the api/ layer and then calls reload().
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import type { Debt, Payment } from '../api/types'
import { listDebts, listPayments } from '../api/debts'
import { useAuth } from './auth'
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
  const [debts, setDebts] = useState<Debt[]>([])
  const [payments, setPayments] = useState<Payment[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const reload = useCallback(async () => {
    if (!user) return
    setError(null)
    try {
      const [d, p] = await Promise.all([listDebts(), listPayments()])
      setDebts(d)
      setPayments(p)
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setLoading(false)
    }
  }, [user])

  useEffect(() => {
    setLoading(true)
    void reload()
  }, [reload])

  const summary = useMemo(() => summarize(debts, payments), [debts, payments])
  const value = useMemo<DataCtx>(() => ({ debts, payments, summary, loading, error, reload }), [debts, payments, summary, loading, error, reload])
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>
}

export function useData(): DataCtx {
  const v = useContext(Ctx)
  if (!v) throw new Error('useData outside provider')
  return v
}
