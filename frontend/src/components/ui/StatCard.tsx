import clsx from 'clsx'
import { ArrowDownRight, ArrowUpRight, Minus } from 'lucide-react'
import { formatPercent } from '@/lib/formatting'

/** KPI com variacao. Variacao nula = sem base de comparacao (nunca exibimos um numero inventado). */
export function StatCard({
  label,
  value,
  variation,
  hint,
}: {
  label: string
  value: string
  variation?: number | null
  hint?: string
}) {
  const Icon = variation == null || variation === 0 ? Minus : variation > 0 ? ArrowUpRight : ArrowDownRight
  return (
    <div className="rounded-xl border border-line bg-surface p-4">
      <p className="text-xs font-medium text-ink-3">{label}</p>
      <p className="num mt-1 text-2xl font-semibold tracking-tight">{value}</p>
      <p className={clsx('mt-1 flex items-center gap-1 text-xs', variation == null ? 'text-ink-3' : variation > 0 ? 'text-ok' : variation < 0 ? 'text-bad' : 'text-ink-3')}>
        <Icon className="size-3.5" aria-hidden />
        {variation == null ? (hint ?? 'Sem base de comparacao') : `${formatPercent(variation, true)} vs periodo anterior`}
      </p>
    </div>
  )
}
