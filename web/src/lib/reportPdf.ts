// Report PDF built from the numbers already shown on the Reports page.
import { loadFont } from './receipt'
import { formatMoney } from './money'
import { todayIso, formatDate } from './dates'
import type { Summary } from './calc'

interface Args {
  t: (k: string, ...a: Array<string | number>) => string
  locale: string
  months: Array<{ month: string; total: number }>
  byCreditor: Array<[string, number]>
  statuses: Record<string, number>
  summary: Summary
  forecast: number | null
  periodTotal: number
}

export async function exportReportPdf({ t, locale, months, byCreditor, statuses, summary, forecast, periodTotal }: Args) {
  const { jsPDF } = await import('jspdf')
  const doc = new jsPDF({ unit: 'mm', format: 'a4' })
  const font = await loadFont()
  doc.addFileToVFS('NotoSans.ttf', font)
  doc.addFont('NotoSans.ttf', 'NotoSans', 'normal')
  doc.setFont('NotoSans')
  const M = 20
  let y = 22
  const line = (k: string, v: string, size = 11) => {
    doc.setFontSize(size)
    doc.setTextColor(110)
    doc.text(k, M, y)
    doc.setTextColor(20)
    doc.text(v, 190, y, { align: 'right' })
    y += 7
  }
  const heading = (s: string) => {
    y += 4
    doc.setFontSize(13)
    doc.setTextColor(20)
    doc.text(s, M, y)
    doc.setDrawColor(210)
    doc.line(M, y + 2, 190, y + 2)
    y += 10
  }

  doc.setFontSize(9)
  doc.setTextColor(120)
  doc.text('KVIT', M, y)
  doc.text(formatDate(todayIso(), locale), 190, y, { align: 'right' })
  y += 10
  doc.setFontSize(20)
  doc.setTextColor(20)
  doc.text(t('nav.reports'), M, y)
  y += 6

  heading(t('dash.remaining'))
  line(t('dash.total'), formatMoney(summary.total, locale))
  line(t('dash.paid'), formatMoney(summary.paid, locale))
  line(t('dash.remaining'), formatMoney(summary.remaining, locale))
  line(t('dash.lent'), formatMoney(summary.lentRemaining, locale))
  line(t('reports.forecast'), forecast ? t('dash.forecast', forecast) : '—')

  heading(t('reports.byMonth'))
  for (const m of months) line(new Intl.DateTimeFormat(locale, { month: 'long', year: 'numeric' }).format(new Date(m.month + '-01T00:00:00')), formatMoney(m.total, locale))
  line(t('rep.total', ''), formatMoney(periodTotal, locale), 12)

  heading(t('reports.byCreditor'))
  for (const [name, v] of byCreditor.slice(0, 15)) line(name, formatMoney(v, locale))

  heading(t('reports.statuses'))
  for (const [k, v] of Object.entries(statuses)) line(t(`status.${k}`), String(v))

  doc.setFontSize(8)
  doc.setTextColor(150)
  doc.text(t('plan.disclaimer'), M, 285)
  doc.save(`kvit-report-${todayIso()}.pdf`)
}
