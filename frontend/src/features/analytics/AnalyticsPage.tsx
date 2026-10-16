import { TopProductsChart } from '@/components/charts/Charts'
import { FilterBar } from '@/components/layout/FilterBar'
import { DataTable } from '@/components/ui/DataTable'
import { QueryState } from '@/components/ui/QueryState'
import { Badge, Card, CardHeader, PageHeader } from '@/components/ui/primitives'
import { useApiQuery } from '@/hooks/useApi'
import { queryParams, useFilters } from '@/hooks/useFilters'
import type { CoverageItem, PeriodComparison, TopProduct } from '@/lib/api/types'
import { formatBRL, formatDate, formatDecimal, formatInt, formatPercent } from '@/lib/formatting'

export function AnalyticsPage() {
  const { filters } = useFilters()
  const p = queryParams(filters)
  const cmp = useApiQuery<PeriodComparison>(['an'], '/sales/comparison', p)
  const top = useApiQuery<TopProduct[]>(['an'], '/dashboard/top-products', { ...p, orderBy: 'revenue', limit: 8 })
  const cov = useApiQuery<CoverageItem[]>(['an'], '/analytics/coverage', { storeId: filters.storeId || undefined, windowDays: 28, limit: 12 })

  return (
    <>
      <PageHeader title="Analises" description="Comparacao de periodos, receita por produto e cobertura de estoque." />
      <FilterBar />
      <div className="grid gap-4 lg:grid-cols-2">
        <Card className="lg:col-span-2">
          <CardHeader title="Periodo atual vs. anterior" />
          <QueryState query={cmp}>
            {(c) => (
              <div className="space-y-2 p-4">
                <p className="text-sm text-ink-2">
                  Atual: {formatDate(c.current.from)} a {formatDate(c.current.to)} · Anterior: {formatDate(c.previous.from)} a {formatDate(c.previous.to)}
                </p>
                {c.note && <p className="rounded-lg bg-warn-bg px-3 py-2 text-sm text-warn">{c.note}</p>}
                <DataTable
                  caption="Comparacao de periodos"
                  rows={[
                    { m: 'Faturamento', a: formatBRL(c.currentMetrics.revenue), b: formatBRL(c.previousMetrics.revenue), v: c.revenueVariationPercent },
                    { m: 'Vendas', a: formatInt(c.currentMetrics.salesCount), b: formatInt(c.previousMetrics.salesCount), v: c.salesCountVariationPercent },
                    { m: 'Ticket medio', a: formatBRL(c.currentMetrics.averageTicket), b: formatBRL(c.previousMetrics.averageTicket), v: c.averageTicketVariationPercent },
                    { m: 'Margem estimada', a: c.currentMetrics.marginPercent == null ? '—' : formatPercent(c.currentMetrics.marginPercent), b: c.previousMetrics.marginPercent == null ? '—' : formatPercent(c.previousMetrics.marginPercent), v: null },
                  ]}
                  rowKey={(r) => r.m}
                  columns={[
                    { header: 'Indicador', cell: (r) => <span className="font-medium">{r.m}</span> },
                    { header: 'Atual', align: 'right', cell: (r) => r.a },
                    { header: 'Anterior', align: 'right', cell: (r) => r.b },
                    { header: 'Variacao', align: 'right', cell: (r) => (r.v == null ? <span className="text-ink-3">—</span> : <span className={r.v >= 0 ? 'text-ok' : 'text-bad'}>{formatPercent(r.v, true)}</span>) },
                  ]}
                />
              </div>
            )}
          </QueryState>
        </Card>
        <Card>
          <CardHeader title="Receita por produto" subtitle="Top 8 do periodo" />
          <div className="p-3">
            <QueryState query={top} isEmpty={(d) => d.length === 0} empty={{ title: 'Sem vendas no periodo' }}>
              {(d) => <TopProductsChart products={d} metric="revenue" />}
            </QueryState>
          </div>
        </Card>
        <Card>
          <CardHeader title="Cobertura de estoque" subtitle="Saldo / media diaria dos ultimos 28 dias" />
          <QueryState query={cov} isEmpty={(d) => d.length === 0} empty={{ title: 'Sem dados de estoque' }}>
            {(d) => (
              <DataTable
                caption="Cobertura de estoque em dias"
                rows={d}
                rowKey={(r) => r.storeId + r.productId}
                columns={[
                  { header: 'Produto', cell: (r) => <div><p className="font-medium">{r.productName}</p><p className="text-xs text-ink-3">{r.storeName}</p></div> },
                  { header: 'Saldo', align: 'right', cell: (r) => formatInt(r.quantity) },
                  { header: 'Media/dia', align: 'right', cell: (r) => formatDecimal(r.averageDailySales) },
                  { header: 'Cobertura', align: 'right', cell: (r) => (r.coverageDays == null ? <span className="text-ink-3">sem vendas</span> : <Badge tone={r.coverageDays <= r.leadTimeDays ? 'bad' : r.coverageDays <= r.targetCoverageDays / 2 ? 'warn' : 'ok'}>{formatDecimal(r.coverageDays)} d</Badge>) },
                ]}
              />
            )}
          </QueryState>
        </Card>
      </div>
    </>
  )
}
