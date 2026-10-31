// Import of debts from CSV in the same format the export writes:
// title;creditor;amount;paid;remaining;due;status;notes (first line is a header).
import { parseAmount } from './money'

export interface CsvRow {
  title: string
  creditor: string
  amount: number
  paid: number
  due: string | null
  status: 'ACTIVE' | 'PAID' | 'CANCELLED'
  notes: string | null
}
export interface CsvError {
  line: number
  reason: 'columns' | 'title' | 'creditor' | 'amount' | 'paid' | 'due'
}

// Splits one line by ";" honouring "quoted; values" and "" escapes.
function splitLine(line: string): string[] {
  const out: string[] = []
  let cur = ''
  let quoted = false
  for (let i = 0; i < line.length; i++) {
    const ch = line[i]
    if (quoted) {
      if (ch === '"' && line[i + 1] === '"') {
        cur += '"'
        i++
      } else if (ch === '"') quoted = false
      else cur += ch
    } else if (ch === '"') quoted = true
    else if (ch === ';') {
      out.push(cur)
      cur = ''
    } else cur += ch
  }
  out.push(cur)
  return out.map((c) => c.trim())
}

export function parseCsv(text: string): { rows: CsvRow[]; errors: CsvError[] } {
  const lines = text.replace(/^﻿/, '').split(/\r?\n/)
  const rows: CsvRow[] = []
  const errors: CsvError[] = []
  lines.forEach((raw, i) => {
    if (i === 0 || !raw.trim()) return
    const line = i + 1
    const c = splitLine(raw)
    if (c.length < 3) return errors.push({ line, reason: 'columns' })
    const [title, creditor, amountS, paidS = '', , dueS = '', statusS = '', notes = ''] = c
    if (!title || title.length > 200) return errors.push({ line, reason: 'title' })
    if (!creditor || creditor.length > 200) return errors.push({ line, reason: 'creditor' })
    const amount = parseAmount(amountS)
    if (amount === null) return errors.push({ line, reason: 'amount' })
    const paid = paidS === '' || Number(paidS.replace(',', '.')) === 0 ? 0 : parseAmount(paidS)
    if (paid === null || paid > amount) return errors.push({ line, reason: 'paid' })
    if (dueS && !/^\d{4}-\d{2}-\d{2}$/.test(dueS)) return errors.push({ line, reason: 'due' })
    const status = statusS === 'PAID' || statusS === 'CANCELLED' ? statusS : 'ACTIVE'
    rows.push({ title, creditor, amount, paid, due: dueS || null, status, notes: notes ? notes.slice(0, 1000) : null })
  })
  return { rows, errors }
}
