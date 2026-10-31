import { useState } from 'react'
import { Upload } from 'lucide-react'
import { useI18n } from '../lib/i18n'
import { useToast } from '../lib/toast'
import { useData } from '../lib/data'
import { formatMoney } from '../lib/money'
import { todayIso } from '../lib/dates'
import { parseCsv, type CsvError, type CsvRow } from '../lib/csvImport'
import { createDebt, createPayment } from '../api/debts'
import { Modal } from './ui'

// Reads a CSV in the export format, shows a preview, and only then creates the debts.
// The "paid" column becomes one payment dated today so the remaining balance matches.
export function CsvImport() {
  const { t, locale } = useI18n()
  const toast = useToast()
  const { reload } = useData()
  const [rows, setRows] = useState<CsvRow[] | null>(null)
  const [errors, setErrors] = useState<CsvError[]>([])
  const [busy, setBusy] = useState(false)

  async function onFile(file: File | undefined) {
    if (!file) return
    if (file.size > 1024 * 1024) {
      toast.error(t('csv.tooBig'))
      return
    }
    const parsed = parseCsv(await file.text())
    setRows(parsed.rows)
    setErrors(parsed.errors)
  }

  async function doImport() {
    if (!rows) return
    setBusy(true)
    let done = 0
    try {
      for (const r of rows) {
        const d = await createDebt({ title: r.title, creditor_name: r.creditor, original_amount: r.amount, interest_rate: null, due_date: r.due, status: r.status, notes: r.notes })
        if (r.paid > 0) await createPayment({ debt_id: d.id, amount: r.paid, payment_date: todayIso(), notes: t('csv.paidNote') })
        done++
      }
      toast.success(t('csv.done', done))
      setRows(null)
    } catch (e) {
      toast.error(t('csv.partial', done), e instanceof Error ? e.message : undefined)
    } finally {
      await reload()
      setBusy(false)
    }
  }

  return (
    <>
      <label className="btn-outline cursor-pointer">
        <Upload size={16} />
        {t('csv.import')}
        <input type="file" accept=".csv,text/csv" className="hidden" onChange={(e) => {
          void onFile(e.target.files?.[0])
          e.target.value = ''
        }} />
      </label>
      <Modal open={rows !== null} onClose={() => setRows(null)} title={t('csv.title')} wide>
        <p className="text-sm text-muted">{t('csv.found', rows?.length ?? 0)}</p>
        {errors.length > 0 && (
          <div className="mt-3 text-sm text-bad border border-bad/40 rounded-lg p-3">
            {t('csv.errors')} {errors.map((e) => `${e.line} (${t(`csv.reason.${e.reason}`)})`).join(', ')}
          </div>
        )}
        <div className="mt-4 max-h-72 overflow-y-auto border border-line rounded-lg">
          <table className="w-full">
            <tbody>
              {rows?.slice(0, 100).map((r, i) => (
                <tr key={i}>
                  <td className="td">{r.title}</td>
                  <td className="td text-muted">{r.creditor}</td>
                  <td className="td text-right num">{formatMoney(r.amount, locale)}</td>
                  <td className="td text-right num text-muted">{r.paid > 0 ? formatMoney(r.paid, locale) : ''}</td>
                  <td className="td text-muted whitespace-nowrap">{r.due ?? ''}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <p className="text-xs text-faint mt-3">{t('csv.format')}</p>
        <div className="flex justify-end gap-2 mt-4">
          <button className="btn-ghost" onClick={() => setRows(null)}>{t('common.cancel')}</button>
          <button className="btn-primary" disabled={busy || !rows?.length} onClick={() => void doImport()}>{busy ? t('common.loading') : t('csv.confirm', rows?.length ?? 0)}</button>
        </div>
      </Modal>
    </>
  )
}
