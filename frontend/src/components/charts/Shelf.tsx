import type { CoverageItem } from '@/lib/api/types'
import { formatDecimal } from '@/lib/formatting'

const MAX_CRATES = 30

/**
 * Prateleira de cobertura: cada caixa e um dia de estoque. Vermelho = cobertura menor ou igual ao lead time
 * de reposicao; tracejado = prateleira vazia. A informacao tambem esta em texto (nao depende de cor).
 */
export function Shelf({ items }: { items: CoverageItem[] }) {
  return (
    <ul className="shelf-in divide-y divide-line" aria-label="Cobertura de estoque por produto">
      {items.map((c, row) => {
        const days = c.coverageDays
        const crates = c.quantity === 0 ? 1 : days == null ? 0 : Math.min(MAX_CRATES, Math.max(1, Math.round(days)))
        const low = c.quantity === 0 || (days != null && days <= c.leadTimeDays)
        return (
          <li key={c.storeId + c.productId} className="grid grid-cols-[minmax(0,9rem)_1fr_auto] items-end gap-3 py-2.5 sm:grid-cols-[minmax(0,15rem)_1fr_7rem]">
            <div className="min-w-0">
              <p className="truncate text-sm font-semibold">{c.productName}</p>
              <p className="truncate text-xs text-ink-3">{c.storeName}</p>
            </div>
            <div className="shelf" role="img" aria-label={days == null ? `${c.productName}: sem vendas recentes` : `${c.productName}: ${formatDecimal(days)} dias de estoque`}>
              {c.quantity === 0 ? (
                <span className="crate" data-gone="true" style={{ ['--i' as string]: 0, ['--row' as string]: row }} />
              ) : (
                Array.from({ length: crates }, (_, i) => (
                  <span key={i} className="crate" data-low={low} style={{ ['--i' as string]: i, ['--row' as string]: row, height: `${55 + ((i * 37) % 45)}%` }} />
                ))
              )}
            </div>
            <p className="num text-right text-sm font-bold">
              {c.quantity === 0 ? <span className="text-bad">Esgotado</span> : days == null ? <span className="text-ink-3">sem vendas</span> : <span className={low ? 'text-bad' : ''}>{formatDecimal(days)} dias</span>}
            </p>
          </li>
        )
      })}
    </ul>
  )
}
