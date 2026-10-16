const TZ = 'America/Sao_Paulo'

const brl = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })
const int = new Intl.NumberFormat('pt-BR')
const dec1 = new Intl.NumberFormat('pt-BR', { minimumFractionDigits: 1, maximumFractionDigits: 1 })

export const formatBRL = (v: number | null | undefined): string => (v == null ? '—' : brl.format(v))
export const formatInt = (v: number | null | undefined): string => (v == null ? '—' : int.format(v))
export const formatDecimal = (v: number | null | undefined): string => (v == null ? '—' : dec1.format(v))

export function formatPercent(v: number | null | undefined, signed = false): string {
  if (v == null) return '—'
  const s = dec1.format(Math.abs(v)) + '%'
  if (!signed) return (v < 0 ? '-' : '') + s
  return (v > 0 ? '+' : v < 0 ? '-' : '') + s
}

export function formatDateTime(iso: string): string {
  return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short', timeZone: TZ }).format(new Date(iso))
}

/** Data AAAA-MM-DD (sem fuso) para dd/MM/aaaa. */
export function formatDate(ymd: string): string {
  const [y, m, d] = ymd.split('-')
  return `${d}/${m}/${y}`
}

/** Data de hoje no fuso da aplicacao, AAAA-MM-DD. */
export function todayYmd(now: Date = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: TZ }).format(now)
}

export function addDays(ymd: string, days: number): string {
  const [y, m, d] = ymd.split('-').map(Number)
  const dt = new Date(Date.UTC(y, m - 1, d + days))
  return dt.toISOString().slice(0, 10)
}
