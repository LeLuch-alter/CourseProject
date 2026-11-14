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

interface CsvRecord {
  line: number // file line where the record starts
  cells: string[]
}

// Splits the whole text into records by ";" and line breaks, honouring "quoted; values",
// "" escapes and line breaks inside quotes (the export quotes notes that contain them).
function splitRecords(text: string): CsvRecord[] {
  const out: CsvRecord[] = []
  let cells: string[] = []
  let cur = ''
  let quoted = false
  let line = 1
  let start = 1
  const endRecord = () => {
    cells.push(cur)
    out.push({ line: start, cells: cells.map((c) => c.trim()) })
    cells = []
    cur = ''
  }
  for (let i = 0; i < text.length; i++) {
    const ch = text[i]
    if (ch === '\n') line++
    if (quoted) {
      if (ch === '"' && text[i + 1] === '"') {
        cur += '"'
        i++
      } else if (ch === '"') quoted = false
      else cur += ch
    } else if (ch === '"') quoted = true
    else if (ch === ';') {
      cells.push(cur)
      cur = ''
    } else if (ch === '\n') {
      endRecord()
      start = line
    } else cur += ch
  }
  endRecord()
  return out
}

export function parseCsv(text: string): { rows: CsvRow[]; errors: CsvError[] } {
  // Line endings are normalised first, so a Windows line break inside quoted notes becomes "\n".
  const records = splitRecords(text.replace(/^﻿/, '').replace(/\r\n?/g, '\n'))
  const rows: CsvRow[] = []
  const errors: CsvError[] = []
  records.forEach(({ line, cells: c }, i) => {
    if (i === 0 || c.every((x) => x === '')) return
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
