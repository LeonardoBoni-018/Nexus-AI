import { useState } from 'react'
import { FilterBar } from '@/components/layout/FilterBar'
import { DataTable, Pagination } from '@/components/ui/DataTable'
import { Dialog } from '@/components/ui/Dialog'
import { QueryState } from '@/components/ui/QueryState'
import { Card, PageHeader } from '@/components/ui/primitives'
import { StatCard } from '@/components/ui/StatCard'
import { useApiQuery } from '@/hooks/useApi'
import { queryParams, useFilters } from '@/hooks/useFilters'
import type { Page, PeriodComparison, SaleDetail, SaleRow } from '@/lib/api/types'
import { formatBRL, formatDateTime, formatInt } from '@/lib/formatting'

export function SalesPage() {
  const { filters } = useFilters()
  const [page, setPage] = useState(0)
  const [selected, setSelected] = useState<string | null>(null)
  const p = queryParams(filters)
  const cmp = useApiQuery<PeriodComparison>(['sales'], '/sales/comparison', p)
  const list = useApiQuery<Page<SaleRow>>(['sales', page], '/sales', { from: filters.from, to: filters.to, storeId: filters.storeId || undefined, page, size: 15 })

  return (
    <>
      <PageHeader title="Vendas" description="Vendas registradas, com resumo e comparacao com o periodo anterior." />
      <FilterBar showCategory />
      <QueryState query={cmp}>
        {(c) => (
          <div className="mb-4 grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4">
            <StatCard label="Faturamento" value={formatBRL(c.currentMetrics.revenue)} variation={c.revenueVariationPercent} />
            <StatCard label="Vendas" value={formatInt(c.currentMetrics.salesCount)} variation={c.salesCountVariationPercent} />
            <StatCard label="Itens vendidos" value={formatInt(c.currentMetrics.itemsSold)} />
            <StatCard label="Ticket medio" value={formatBRL(c.currentMetrics.averageTicket)} variation={c.averageTicketVariationPercent} hint={c.note ?? undefined} />
          </div>
        )}
      </QueryState>
      <Card>
        <QueryState query={list} isEmpty={(d) => d.items.length === 0} empty={{ title: 'Nenhuma venda no periodo', hint: 'Altere o periodo ou a loja nos filtros.' }}>
          {(d) => (
            <>
              <DataTable
                caption="Vendas"
                rows={d.items}
                rowKey={(r) => r.id}
                onRowClick={(r) => setSelected(r.id)}
                columns={[
                  { header: 'Data', cell: (r) => formatDateTime(r.occurredAt) },
                  { header: 'Loja', cell: (r) => r.storeName },
                  { header: 'Itens', align: 'right', cell: (r) => formatInt(r.itemsCount) },
                  { header: 'Total', align: 'right', cell: (r) => <span className="font-medium">{formatBRL(r.total)}</span> },
                ]}
              />
              <Pagination page={d.page} totalPages={d.totalPages} totalItems={d.totalItems} onPage={setPage} />
            </>
          )}
        </QueryState>
      </Card>
      <SaleDialog id={selected} onClose={() => setSelected(null)} />
    </>
  )
}

function SaleDialog({ id, onClose }: { id: string | null; onClose: () => void }) {
  const q = useApiQuery<SaleDetail>(['sale', id], `/sales/${id}`, undefined, id !== null)
  return (
    <Dialog open={id !== null} onClose={onClose} title="Detalhes da venda">
      {id && (
        <QueryState query={q}>
          {(d) => (
            <div className="space-y-3">
              <p className="text-sm text-ink-2">{d.sale.storeName} · {formatDateTime(d.sale.occurredAt)}</p>
              <DataTable
                caption="Itens da venda"
                rows={d.items}
                rowKey={(r) => r.productId}
                columns={[
                  { header: 'Produto', cell: (r) => <div><p className="font-medium">{r.productName}</p><p className="text-xs text-ink-3">{r.sku}</p></div> },
                  { header: 'Qtd', align: 'right', cell: (r) => r.quantity },
                  { header: 'Unitario', align: 'right', cell: (r) => formatBRL(r.unitPrice) },
                  { header: 'Total', align: 'right', cell: (r) => formatBRL(r.lineTotal) },
                ]}
              />
              <p className="text-right text-sm font-semibold">Total: {formatBRL(d.sale.total)}</p>
            </div>
          )}
        </QueryState>
      )}
    </Dialog>
  )
}
