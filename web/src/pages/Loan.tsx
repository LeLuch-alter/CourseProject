import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { motion } from 'framer-motion'
import { ArrowLeft, Download, Printer, Eye, EyeOff, ShieldCheck, Wallet, Receipt } from 'lucide-react'
import { useI18n, fill } from '../lib/i18n'
import { useAuth } from '../lib/auth'
import { useToast } from '../lib/toast'
import { useData } from '../lib/data'
import { supabase } from '../lib/supabase'
import { formatMoney } from '../lib/money'
import { formatDate, formatDateTime } from '../lib/dates'
import { getLoan, getProfiles, acceptLoan, setLoanStatus } from '../api/social'
import { remainingFor, progressFor } from '../lib/calc'
import type { Loan, Profile } from '../api/types'
import { Avatar, Confirm, Empty, Field, Progress, Skeleton, StatusChip } from '../components/ui'
import { PaymentForm } from '../components/forms'
import { cardOk, formatCard } from '../components/LoanForm'
import { downloadReceiptPdf } from '../lib/receipt'

export function LoanPage() {
  const { id } = useParams()
  const { t, locale, lang } = useI18n()
  const { user } = useAuth()
  const toast = useToast()
  const navigate = useNavigate()
  const { debts, payments, reload } = useData()
  const me = user!.id
  const [loan, setLoan] = useState<Loan | null>(null)
  const [profiles, setProfiles] = useState<Record<string, Profile>>({})
  const [loading, setLoading] = useState(true)
  const [card, setCard] = useState('')
  const [showCard, setShowCard] = useState(false)
  const [busy, setBusy] = useState(false)
  const [confirm, setConfirm] = useState<'DECLINED' | 'CANCELLED' | null>(null)
  const [paying, setPaying] = useState(false)
  const receiptRef = useRef<HTMLDivElement>(null)

  const load = useCallback(async () => {
    if (!id) return
    try {
      const l = await getLoan(id)
      setLoan(l)
      if (l) setProfiles(await getProfiles([l.lender_id, l.borrower_id]))
    } catch (e) {
      toast.error(t('common.loadFailed'), e instanceof Error ? e.message : undefined)
    } finally {
      setLoading(false)
    }
  }, [id, t, toast])

  useEffect(() => {
    void load()
  }, [load])
  useEffect(() => {
    const ch = supabase
      .channel(`loan-${id}`)
      .on('postgres_changes', { event: 'UPDATE', schema: 'public', table: 'loans', filter: `id=eq.${id}` }, () => {
        void load()
        void reload()
      })
      .subscribe()
    return () => {
      void supabase.removeChannel(ch)
    }
  }, [id, load, reload])

  if (loading) return <Skeleton className="h-96" />
  if (!loan)
    return (
      <Empty
        title={t('common.nothing')}
        action={
          <Link to="/loans" className="btn-outline">
            {t('common.back')}
          </Link>
        }
      />
    )

  const lender = profiles[loan.lender_id],
    borrower = profiles[loan.borrower_id]
  const iAmLender = loan.lender_id === me,
    iAmBorrower = loan.borrower_id === me
  const needsMe = loan.status === 'PENDING' && loan.initiator_id !== me
  const myDebt = debts.find((d) => d.loan_id === loan.id)
  const remaining = myDebt ? remainingFor(myDebt, payments) : Number(loan.amount)
  const progress = myDebt ? progressFor(myDebt, payments) : 0
  const masked = loan.borrower_card ? `•••• •••• •••• ${loan.borrower_card.slice(-4)}` : '—'

  async function accept() {
    if (iAmBorrower && !loan!.borrower_card && !cardOk(card)) {
      toast.error(t('err.card'))
      return
    }
    setBusy(true)
    try {
      await acceptLoan(loan!.id, iAmBorrower ? card.replace(/\s/g, '') : undefined)
      await Promise.all([load(), reload()])
      toast.success(t('loans.status.ACTIVE'), t('loans.receipt'))
    } catch (e) {
      toast.error(t('common.saveFailed'), e instanceof Error ? e.message : undefined)
    } finally {
      setBusy(false)
    }
  }

  async function close(status: 'DECLINED' | 'CANCELLED') {
    setBusy(true)
    try {
      await setLoanStatus(loan!.id, status)
      await load()
      toast.info(t(`loans.status.${status}`))
    } catch (e) {
      toast.error(t('common.saveFailed'), e instanceof Error ? e.message : undefined)
    } finally {
      setBusy(false)
      setConfirm(null)
    }
  }

  const receiptText = fill(t('loans.receiptText'), {
    borrower: borrower?.display_name ?? '',
    busr: borrower?.username ?? '',
    lender: lender?.display_name ?? '',
    lusr: lender?.username ?? '',
    amount: formatMoney(loan.amount, locale),
    purpose: loan.purpose,
    due: formatDate(loan.due_date, locale),
    card: loan.borrower_card ? formatCard(loan.borrower_card) : '—',
    rate: loan.interest_rate && Number(loan.interest_rate) > 0 ? t('loans.rateClause', loan.interest_rate) : '',
    date: loan.accepted_at ? formatDateTime(loan.accepted_at, locale) : '…',
  })

  return (
    <div className="max-w-3xl mx-auto">
      <button className="btn-ghost mb-4 -ml-3" onClick={() => navigate(-1)}>
        <ArrowLeft size={16} />
        {t('common.back')}
      </button>

      <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} className="card p-6 sm:p-8 relative overflow-hidden">
        <div className="relative flex flex-wrap items-start justify-between gap-3">
          <div>
            <div className="text-xs text-muted uppercase tracking-wide">{iAmLender ? t('loans.youLend') : t('loans.youBorrow')}</div>
            <div className="text-4xl num mt-1">{formatMoney(loan.amount, locale)}</div>
            <div className="text-muted mt-1">{loan.purpose}</div>
          </div>
          <StatusChip status={loan.status} label={t(`loans.status.${loan.status}`)} />
        </div>

        <div className="relative grid sm:grid-cols-2 gap-3 mt-6">
          <Party label={t('loans.lender')} p={lender} highlight={iAmLender} />
          <Party label={t('loans.borrower')} p={borrower} highlight={iAmBorrower} />
          <div className="bg-raised rounded-xl p-3">
            <div className="text-xs text-muted">{t('loans.due')}</div>
            <div className="mt-0.5">{formatDate(loan.due_date, locale)}{loan.interest_rate && Number(loan.interest_rate) > 0 ? <span className="text-muted"> · {loan.interest_rate} %</span> : null}</div>
          </div>
          <div className="bg-raised rounded-xl p-3">
            <div className="text-xs text-muted">{t('loans.card')}</div>
            <div className="font-mono mt-0.5 flex items-center gap-2">
              {showCard && loan.borrower_card ? formatCard(loan.borrower_card) : masked}
              {loan.borrower_card && (
                <button className="text-faint hover:text-ink" onClick={() => setShowCard((v) => !v)} title={showCard ? t('loans.cardHide') : t('loans.cardShow')}>
                  {showCard ? <EyeOff size={14} /> : <Eye size={14} />}
                </button>
              )}
            </div>
          </div>
        </div>

        {loan.status === 'PENDING' && (
          <div className="relative mt-6 p-4 rounded-xl border border-warn/30 bg-warn/5">
            {needsMe ? (
              <>
                <div className="flex items-center gap-2">
                  <ShieldCheck size={18} className="text-warn" />
                  {t('loans.waitYou')}
                </div>
                <p className="text-sm text-muted mt-1">{t('loans.confirmText')}</p>
                {iAmBorrower && !loan.borrower_card && (
                  <div className="mt-3">
                    <Field label={t('loans.card')} hint={t('loans.cardHint')}>
                      <input
                        className="input font-mono tracking-wider"
                        inputMode="numeric"
                        value={card}
                        onChange={(e) => setCard(formatCard(e.target.value))}
                        placeholder="0000 0000 0000 0000"
                        autoComplete="off"
                      />
                    </Field>
                  </div>
                )}
                <div className="flex gap-2 mt-4">
                  <button className="btn-primary" onClick={() => void accept()} disabled={busy}>
                    {t('common.confirm')}
                  </button>
                  <button className="btn-ghost" onClick={() => setConfirm('DECLINED')} disabled={busy}>
                    {t('common.decline')}
                  </button>
                </div>
              </>
            ) : (
              <>
                <div className="">{t('loans.waitOther', (iAmLender ? borrower : lender)?.display_name ?? '')}</div>
                <button className="btn-ghost mt-3" onClick={() => setConfirm('CANCELLED')} disabled={busy}>
                  {t('loans.withdraw')}
                </button>
              </>
            )}
          </div>
        )}

        {(loan.status === 'ACTIVE' || loan.status === 'REPAID') && (
          <div className="relative mt-6">
            <div className="flex justify-between text-sm mb-1.5">
              <span className="text-muted">{t('loans.repaid')}</span>
              <span className="num ">
                {formatMoney(Number(loan.amount) - remaining, locale)} / {formatMoney(loan.amount, locale)}
              </span>
            </div>
            <Progress value={progress} color={loan.status === 'REPAID' ? 'bg-good' : 'bg-accent'} />
            <div className="flex flex-wrap gap-2 mt-4">
              {loan.status === 'ACTIVE' && myDebt && (
                <button className="btn-primary" onClick={() => setPaying(true)}>
                  {iAmLender ? <Receipt size={16} /> : <Wallet size={16} />}
                  {iAmLender ? t('loans.markPaid') : t('debts.pay')}
                </button>
              )}
              {myDebt && (
                <Link to={`/debts?id=${myDebt.id}`} className="btn-outline">
                  <Wallet size={16} />
                  {t('debts.details')}
                </Link>
              )}
            </div>
          </div>
        )}
      </motion.div>

      {loan.status !== 'PENDING' && loan.status !== 'DECLINED' && loan.status !== 'CANCELLED' && (
        <motion.div initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.15 }} className="mt-6">
          <div className="flex items-center justify-between mb-3">
            <h2 className="text-lg">{t('loans.receipt')}</h2>
            <div className="flex gap-2">
              <button className="btn-outline h-9" onClick={() => downloadReceiptPdf({ loan, lender, borrower, text: receiptText, t, locale, lang })}>
                <Download size={15} />
                {t('loans.download')}
              </button>
              <button className="btn-ghost h-9" onClick={() => window.print()}>
                <Printer size={15} />
                {t('loans.print')}
              </button>
            </div>
          </div>
          <div ref={receiptRef} className="print-area bg-white text-[#111] rounded-2xl border border-line p-8 sm:p-10 shadow-soft font-serif">
            <div className="flex items-start justify-between">
              <div>
                <div className="text-xs tracking-[0.3em] uppercase text-[#666]">Kvit</div>
                <h3 className="text-2xl mt-1">{t('loans.receipt')}</h3>
              </div>
              <div className="text-right text-sm">
                <div className="text-[#666]">{t('loans.receiptNo')}</div>
                <div className="font-mono ">{loan.receipt_no}</div>
              </div>
            </div>
            <p className="mt-6 leading-relaxed text-[15px]">{receiptText}</p>
            <div className="grid grid-cols-2 gap-6 mt-8 text-sm">
              <Sign label={t('loans.lender')} p={lender} date={loan.created_at} initiator={loan.initiator_id === loan.lender_id} accepted={loan.accepted_at} />
              <Sign label={t('loans.borrower')} p={borrower} date={loan.created_at} initiator={loan.initiator_id === loan.borrower_id} accepted={loan.accepted_at} />
            </div>
            <div className="mt-8 pt-4 border-t border-dashed border-[#ccc] text-[11px] text-[#777] flex justify-between">
              <span>
                {t('loans.signedBy')}: {lender?.display_name}, {borrower?.display_name}
              </span>
              <span className="font-mono">{loan.id}</span>
            </div>
          </div>
        </motion.div>
      )}

      {paying && myDebt && (
        <PaymentForm
          open
          onClose={() => {
            setPaying(false)
            void load()
          }}
          debtId={myDebt.id}
        />
      )}
      <Confirm
        open={!!confirm}
        onClose={() => setConfirm(null)}
        onConfirm={() => confirm && void close(confirm)}
        busy={busy}
        title={confirm === 'DECLINED' ? t('common.decline') : t('loans.withdraw')}
        text={t('loans.confirmText')}
        confirmLabel={confirm === 'DECLINED' ? t('common.decline') : t('loans.withdraw')}
        cancelLabel={t('common.cancel')}
      />
      <style>{`@media print { body * { visibility: hidden } .print-area, .print-area * { visibility: visible } .print-area { position: absolute; inset: 0; border: 0; box-shadow: none } }`}</style>
    </div>
  )
}

function Party({ label, p, highlight }: { label: string; p?: Profile; highlight: boolean }) {
  return (
    <div className={`rounded-xl p-3 flex items-center gap-3 ${highlight ? 'bg-accent/10 border border-accent/20' : 'bg-raised'}`}>
      <Avatar profile={p} size={40} />
      <div className="min-w-0">
        <div className="text-xs text-muted">{label}</div>
        <div className="font-medium truncate">{p?.display_name}</div>
        <div className="text-xs text-faint">@{p?.username}</div>
      </div>
    </div>
  )
}

function Sign({ label, p, date, initiator, accepted }: { label: string; p?: Profile; date: string; initiator: boolean; accepted: string | null }) {
  const { locale } = useI18n()
  const when = initiator ? date : accepted
  return (
    <div>
      <div className="text-[#666]">{label}</div>
      <div className="mt-1">{p?.display_name}</div>
      <div className="text-[#666]">@{p?.username}</div>
      <div className="mt-3 h-10 border-b border-[#999] font-['Brush_Script_MT',cursive] text-xl text-[#333] italic flex items-end pb-1">{p?.display_name}</div>
      <div className="text-[11px] text-[#777] mt-1">{when ? formatDateTime(when, locale) : ''}</div>
    </div>
  )
}
