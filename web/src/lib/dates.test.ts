import { describe, expect, it } from 'vitest'
import { addMonths, localIso } from './dates'

describe('addMonths', () => {
  it('keeps the day when the target month has it', () => {
    expect(addMonths('2026-01-15', 1)).toBe('2026-02-15')
    expect(addMonths('2026-11-30', 2)).toBe('2027-01-30')
  })

  it('clamps to the last day of a shorter month instead of overflowing', () => {
    expect(addMonths('2026-01-31', 1)).toBe('2026-02-28')
    expect(addMonths('2028-01-31', 1)).toBe('2028-02-29')
    expect(addMonths('2026-03-31', 1)).toBe('2026-04-30')
    expect(addMonths('2026-05-31', -3)).toBe('2026-02-28')
  })

  it('builds a monthly schedule without skipping February', () => {
    const dates = [0, 1, 2, 3].map((i) => addMonths('2026-01-31', i))
    expect(dates).toEqual(['2026-01-31', '2026-02-28', '2026-03-31', '2026-04-30'])
  })
})

describe('localIso', () => {
  it('uses the local calendar day, not UTC', () => {
    expect(localIso(new Date(2026, 10, 14, 23, 30))).toBe('2026-11-14')
  })
})
