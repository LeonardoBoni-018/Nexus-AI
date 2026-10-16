import { describe, expect, it } from 'vitest'
import { addDays, formatBRL, formatDate, formatPercent, todayYmd } from './formatting'

describe('formatting', () => {
  it('formata moeda em BRL e traco para ausente', () => {
    expect(formatBRL(1234.5).replace(/\s/g, ' ')).toBe('R$ 1.234,50')
    expect(formatBRL(null)).toBe('—')
  })
  it('formata percentual com sinal e trata nulo', () => {
    expect(formatPercent(5.6, true)).toBe('+5,6%')
    expect(formatPercent(-12.34, true)).toBe('-12,3%')
    expect(formatPercent(null, true)).toBe('—')
  })
  it('formata datas sem deslocar por fuso', () => {
    expect(formatDate('2026-03-08')).toBe('08/03/2026')
  })
  it('soma dias atravessando fim de mes e ano', () => {
    expect(addDays('2026-03-01', -1)).toBe('2026-02-28')
    expect(addDays('2026-12-31', 1)).toBe('2027-01-01')
  })
  it('usa o fuso de Sao Paulo para "hoje"', () => {
    // 02:00 UTC ainda e o dia anterior em Sao Paulo (UTC-3)
    expect(todayYmd(new Date('2026-03-08T02:00:00Z'))).toBe('2026-03-07')
    expect(todayYmd(new Date('2026-03-08T12:00:00Z'))).toBe('2026-03-08')
  })
})
