import { useCallback, useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { motion } from 'framer-motion'
import { FileSignature, HandCoins, HandHelping, ChevronRight } from 'lucide-react'
import { useI18n } from '../lib/i18n'
import { useAuth } from '../lib/auth'
import { useToast } from '../lib/toast'
import { supabase } from '../lib/supabase'
import { formatMoney } from '../lib/money'
import { formatDate } from '../lib/dates'
import { listLoans, getProfiles } from '../api/social'
import type { Loan, Profile } from '../api/types'
import { Avatar, Empty, PageHeader, Skeleton, StatusChip, stagger } from '../components/ui'

export function LoansPage() {
  const { t, locale } = useI18n()
  const { user } = useAuth()
  const toast = useToast()
  const navigate = useNavigate()
  const me = user!.id
  const [loans, setLoans] = useState<Loan[]>([])
  const [profiles, setProfiles] = useState<Record<string, Profile>>({})
  const [loading, setLoading] = useState(true)

  const load = useCallback(async () => {
    try {
      const ls = await listLoans()
      const ids = [...new Set(ls.flatMap((l) => [l.lender_id, l.borrower_id]))]
      setProfiles(await getProfiles(ids))
      setLoans(ls)
    } catch (e) {
      toast.error(t('common.loadFailed'), e instanceof Error ? e.message : undefined)
    } finally {
      setLoading(false)
    }
  }, [t, toast])

  useEffect(() => {
    void load()
  }, [load])
  useEffect(() => {
    const ch = supabase
      .channel('loans-page')
      .on('postgres_changes', { event: '*', schema: 'public', table: 'loans' }, () => void load())
      .subscribe()
    return () => {
      void supabase.removeChannel(ch)
    }
  }, [load])

  const groups = useMemo(
    () => ({
      pending: loans.filter((l) => l.status === 'PENDING'),
      active: loans.filter((l) => l.status === 'ACTIVE'),
      closed: loans.filter((l) => ['REPAID', 'DECLINED', 'CANCELLED'].includes(l.status)),
    }),
    [loans],
  )

  if (loading)
    return (
      <div className="space-y-3">
        <Skeleton className="h-20" />
        <Skeleton className="h-20" />
      </div>
    )

  const Row = ({ l, i }: { l: Loan; i: number }) => {
    const lend = l.lender_id === me,
      other = profiles[lend ? l.borrower_id : l.lender_id]
    const needsMe = l.status === 'PENDING' && l.initiator_id !== me
    return (
      <motion.button
        {...stagger(i)}
        whileHover={{ x: 3 }}
        onClick={() => navigate(`/loans/${l.id}`)}
        className={`w-full card p-4 flex items-center gap-4 text-left hover:border-accent/40 transition ${needsMe ? 'ring-2 ring-warn/40' : ''}`}
      >
        <div className={`w-11 h-11 rounded-xl flex items-center justify-center shrink-0 ${lend ? 'bg-accent2/10 text-accent2' : 'bg-accent/10 text-accent'}`}>
          {lend ? <HandCoins size={20} /> : <HandHelping size={20} />}
        </div>
        <Avatar profile={other} size={36} />
        <div className="flex-1 min-w-0">
          <div className="font-medium truncate">
            {lend ? t('loans.youLend') : t('loans.youBorrow')} · {other?.display_name}
          </div>
          <div className="text-xs text-muted truncate">
            {l.purpose} · {t('loans.due')}: {formatDate(l.due_date, locale)}
            {l.receipt_no && ` · ${l.receipt_no}`}
          </div>
          {needsMe && <div className="text-xs text-warn font-medium mt-0.5">{t('loans.waitYou')}</div>}
        </div>
        <div className="text-right">
          <div className="num">{formatMoney(l.amount, locale)}</div>
          <StatusChip status={l.status} label={t(`loans.status.${l.status}`)} />
        </div>
        <ChevronRight size={18} className="text-faint" />
      </motion.button>
    )
  }

  return (
    <div>
      <PageHeader
        title={t('loans.title')}
        actions={
          <>
            <button className="btn-outline" onClick={() => navigate('/friends?action=borrow')}>
              <HandHelping size={16} />
              {t('friends.borrow')}
            </button>
            <button className="btn-primary" onClick={() => navigate('/friends?action=lend')}>
              <HandCoins size={16} />
              {t('friends.lend')}
            </button>
          </>
        }
      />
      {loans.length === 0 ? (
        <div className="card">
          <Empty icon={<FileSignature />} title={t('loans.none')} text={t('loans.noneText')} />
        </div>
      ) : (
        <div className="space-y-6">
          {(['pending', 'active', 'closed'] as const).map(
            (g) =>
              groups[g].length > 0 && (
                <section key={g}>
                  <h2 className="text-xs text-muted uppercase tracking-wide mb-2">
                    {t(`loans.${g}`)} · {groups[g].length}
                  </h2>
                  <div className="space-y-2">
                    {groups[g].map((l, i) => (
                      <Row key={l.id} l={l} i={i} />
                    ))}
                  </div>
                </section>
              ),
          )}
        </div>
      )}
    </div>
  )
}
