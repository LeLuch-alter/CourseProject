// PDF of the IOU. Built from the same data the page shows, so the document and the screen never differ.
// jsPDF is loaded only when a PDF is requested, to keep the main bundle small
import type { Loan, Profile } from '../api/types'
import { formatMoney } from './money'
import { formatDate, formatDateTime } from './dates'

interface Args {
  loan: Loan
  lender?: Profile
  borrower?: Profile
  text: string
  t: (k: string) => string
  locale: string
  lang: string
}

let fontCache: string | null = null
export async function loadFont(): Promise<string> {
  if (fontCache) return fontCache
  // jsPDF reads only TrueType fonts. Noto Sans is bundled with the site and covers Cyrillic,
  // the Kazakh letters and the tenge sign; the built-in Helvetica has none of them.
  const res = await fetch('/fonts/NotoSans-Regular.ttf')
  if (!res.ok) throw new Error(`Font download failed: ${res.status}`)
  const bytes = new Uint8Array(await res.arrayBuffer())
  let bin = ''
  for (let i = 0; i < bytes.length; i += 0x8000) bin += String.fromCharCode(...bytes.subarray(i, i + 0x8000))
  fontCache = btoa(bin)
  return fontCache
}

export async function downloadReceiptPdf({ loan, lender, borrower, text, t, locale }: Args) {
  const { jsPDF } = await import('jspdf')
  const doc = new jsPDF({ unit: 'mm', format: 'a4' })
  const font = await loadFont()
  doc.addFileToVFS('NotoSans.ttf', font)
  doc.addFont('NotoSans.ttf', 'NotoSans', 'normal')
  doc.setFont('NotoSans')
  const W = 210,
    M = 20
  doc.setFontSize(9)
  doc.setTextColor(120)
  doc.text('KVIT', M, 20)
  doc.setFontSize(22)
  doc.setTextColor(20)
  doc.text(t('loans.receipt'), M, 30)
  doc.setFontSize(10)
  doc.setTextColor(90)
  doc.text(`${t('loans.receiptNo')} ${loan.receipt_no ?? ''}`, W - M, 20, { align: 'right' })
  doc.setDrawColor(200)
  doc.line(M, 36, W - M, 36)
  doc.setFontSize(12)
  doc.setTextColor(30)
  const lines = doc.splitTextToSize(text, W - 2 * M)
  doc.text(lines, M, 46)
  let y = 46 + lines.length * 6 + 10
  const row = (k: string, v: string) => {
    doc.setTextColor(120)
    doc.text(k, M, y)
    doc.setTextColor(30)
    doc.text(v, M + 55, y)
    y += 7
  }
  row(t('loans.amount'), formatMoney(loan.amount, locale))
  row(t('loans.purpose'), loan.purpose)
  row(t('loans.due'), formatDate(loan.due_date, locale))
  row(t('loans.lender'), `${lender?.display_name ?? ''} (@${lender?.username ?? ''})`)
  row(t('loans.borrower'), `${borrower?.display_name ?? ''} (@${borrower?.username ?? ''})`)
  row(t('loans.card'), loan.borrower_card ? loan.borrower_card.replace(/(\d{4})(?=\d)/g, '$1 ') : '—')
  y += 10
  doc.setDrawColor(150)
  doc.line(M, y + 12, M + 70, y + 12)
  doc.line(W - M - 70, y + 12, W - M, y + 12)
  doc.setFontSize(10)
  doc.setTextColor(90)
  doc.text(lender?.display_name ?? '', M, y + 17)
  doc.text(borrower?.display_name ?? '', W - M - 70, y + 17)
  doc.text(loan.accepted_at ? formatDateTime(loan.accepted_at, locale) : '', M, y + 22)
  doc.text(loan.accepted_at ? formatDateTime(loan.accepted_at, locale) : '', W - M - 70, y + 22)
  doc.setFontSize(8)
  doc.setTextColor(150)
  doc.text(loan.id, M, 285)
  doc.save(`${loan.receipt_no ?? 'kvit-receipt'}.pdf`)
}
