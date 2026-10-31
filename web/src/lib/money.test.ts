import { describe, expect, it } from 'vitest'
import { formatMoney, fromTiyn, parseAmount, sum, toTiyn } from './money'
import { parseCsv } from './csvImport'

describe('money', () => {
  it('converts to tiyn and back without drift', () => {
    expect(toTiyn('0.1') + toTiyn('0.2')).toBe(30)
    expect(fromTiyn(30)).toBe(0.3)
    expect(sum(['0.10', 0.2, '1000.05'])).toBe(1000.35)
  })

  it('parses amounts written in different ways', () => {
    expect(parseAmount('12 500')).toBe(12500)
    expect(parseAmount('12500,5')).toBe(12500.5)
    expect(parseAmount('0.01')).toBe(0.01)
  })

  it('rejects zero, negatives, text and more than two decimals', () => {
    for (const bad of ['0', '-5', 'abc', '1.234', '', '1e5']) expect(parseAmount(bad)).toBeNull()
  })

  it('formats with the currency symbol', () => {
    expect(formatMoney(1500, 'en-US')).toBe('1,500 ₸')
    expect(formatMoney('20.5', 'en-US', 'USD')).toBe('20.5 $')
  })
})

describe('csv import', () => {
  it('reads the same format the export writes, with quotes and a BOM', () => {
    const text = '﻿Долг;Кредитор;Сумма;Оплачено;Остаток;Срок;Статус;Заметка\r\n"Кредит; ноутбук";Kaspi;450000;75000;375000;2027-02-26;ACTIVE;"он сказал ""ок"""\r\n'
    const { rows, errors } = parseCsv(text)
    expect(errors).toEqual([])
    expect(rows).toEqual([{ title: 'Кредит; ноутбук', creditor: 'Kaspi', amount: 450000, paid: 75000, due: '2027-02-26', status: 'ACTIVE', notes: 'он сказал "ок"' }])
  })

  it('reports bad lines with their numbers instead of importing them', () => {
    const { rows, errors } = parseCsv('h\n;Bank;100;0;100;;ACTIVE;\nRent;Marat;abc;0;0;;ACTIVE;\nOk;X;10;20;0;;ACTIVE;')
    expect(rows).toEqual([])
    expect(errors.map((e) => e.line)).toEqual([2, 3, 4])
  })
})
