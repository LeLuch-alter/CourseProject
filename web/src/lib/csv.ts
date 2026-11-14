import type { Debt, Payment } from '../api/types'
import { remainingFor } from './calc'
import { todayIso } from './dates'

function cell(v: unknown): string {
  const s = v === null || v === undefined ? '' : String(v)
  return /[";\r\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s
}

function download(name: string, lines: string[]) {
  // BOM so Excel opens Cyrillic text correctly; ";" separator matches the desktop app export
  const blob = new Blob(['﻿' + lines.join('\r\n')], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = name
  a.click()
  // Revoking right after click() can cancel the download in some browsers.
  setTimeout(() => URL.revokeObjectURL(a.href), 1000)
}

export function exportCsv(debts: Debt[], payments: Payment[], t: (k: string) => string) {
  const head = [t('debts.col.title'), t('debts.col.creditor'), t('debts.col.amount'), t('debts.col.paid'), t('debts.col.remaining'), t('debts.col.due'), t('debts.col.status'), t('form.notes')]
  const lines = [
    head.join(';'),
    ...debts.map((d) => {
      const rem = remainingFor(d, payments)
      return [d.title, d.creditor_name, d.original_amount, (Number(d.original_amount) - rem).toFixed(2), rem.toFixed(2), d.due_date ?? '', d.status, d.notes ?? ''].map(cell).join(';')
    }),
  ]
  download(`kvit-debts-${todayIso()}.csv`, lines)
}

export function exportPaymentsCsv(payments: Payment[], debts: Debt[], t: (k: string) => string) {
  const byId = Object.fromEntries(debts.map((d) => [d.id, d]))
  const head = [t('payments.col.date'), t('payments.col.debt'), t('debts.col.creditor'), t('payments.col.amount'), t('form.notes')]
  const lines = [head.join(';'), ...payments.map((p) => [p.payment_date, byId[p.debt_id]?.title ?? '', byId[p.debt_id]?.creditor_name ?? '', p.amount, p.notes ?? ''].map(cell).join(';'))]
  download(`kvit-payments-${todayIso()}.csv`, lines)
}
