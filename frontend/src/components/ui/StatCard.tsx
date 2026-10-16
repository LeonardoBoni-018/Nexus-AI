import clsx from 'clsx'
import { ArrowDownRight, ArrowUpRight, Minus } from 'lucide-react'
import { formatPercent } from '@/lib/formatting'

/**
 * Entrada de livro-razao: numero grande, regra lateral, variacao.
 * Variacao nula = sem base de comparacao (nunca exibimos um numero inventado).
 */
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
    <div className="border-l-[5px] border-ink py-1 pl-3">
      <p className="text-sm font-medium text-ink-2">{label}</p>
      <p className="num font-display text-[1.9rem] font-extrabold leading-tight">{value}</p>
      <p className={clsx('flex items-center gap-1 text-xs font-medium', variation == null ? 'text-ink-3' : variation > 0 ? 'text-ok' : variation < 0 ? 'text-bad' : 'text-ink-3')}>
        <Icon className="size-3.5" aria-hidden />
        {variation == null ? (hint ?? 'Sem base de comparacao') : `${formatPercent(variation, true)} contra o periodo anterior`}
      </p>
    </div>
  )
}
