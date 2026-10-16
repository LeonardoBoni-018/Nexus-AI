import { Link } from 'react-router-dom'
import { RevenueChart, TopProductsChart } from '@/components/charts/Charts'
import { FilterBar } from '@/components/layout/FilterBar'
import { DataTable } from '@/components/ui/DataTable'
import { QueryState } from '@/components/ui/QueryState'
import { Badge, Card, CardHeader, PageHeader } from '@/components/ui/primitives'
import { StatCard } from '@/components/ui/StatCard'
import { useApiQuery } from '@/hooks/useApi'
import { queryParams, useFilters } from '@/hooks/useFilters'
import type {
  ActionSummary, AlertDto, DashboardSummary, LowStockItem, SeriesPoint, StaleProduct, TopProduct,
} from '@/lib/api/types'
import { addDays, formatBRL, formatDate, formatInt, formatPercent } from '@/lib/formatting'
import { StatusBadge } from '@/features/actions/StatusBadge'
import { SeverityBadge } from '@/features/alerts/SeverityBadge'

export function DashboardPage() {
  const { filters } = useFilters()
  const p = queryParams(filters)
  const summary = useApiQuery<DashboardSummary>(['dash'], '/dashboard/summary', p)
  const series = useApiQuery<SeriesPoint[]>(['dash'], '/dashboard/sales-series', p)
  const days = Math.round((Date.parse(filters.to) - Date.parse(filters.from)) / 86_400_000) + 1
  const prevTo = addDays(filters.from, -1)
  const prevFrom = addDays(prevTo, -(days - 1))
  const prevSeries = useApiQuery<SeriesPoint[]>(['dash', 'prev'], '/dashboard/sales-series', queryParams({ ...filters, from: prevFrom, to: prevTo }))
  const top = useApiQuery<TopProduct[]>(['dash'], '/dashboard/top-products', { ...p, limit: 5 })
  const low = useApiQuery<LowStockItem[]>(['dash'], '/inventory/low-stock', { storeId: filters.storeId || undefined, limit: 6 })
  const stale = useApiQuery<StaleProduct[]>(['dash'], '/dashboard/stale-products', { storeId: filters.storeId || undefined, days: 30, limit: 5 })
  const alerts = useApiQuery<AlertDto[]>(['dash'], '/dashboard/alerts')
  const actions = useApiQuery<ActionSummary[]>(['dash'], '/dashboard/recent-actions')

  return (
    <>
      <PageHeader title="Visao geral" description={`${formatDate(filters.from)} a ${formatDate(filters.to)} · comparado ao periodo anterior de mesma duracao`} />
      <FilterBar />

      <QueryState query={summary} skeleton={<div className="h-24" />}>
        {(s) => {
          const c = s.comparison
          return (
            <>
              {c.note && <p className="mb-3 rounded-lg bg-warn-bg px-3 py-2 text-sm text-warn">{c.note}</p>}
              <div className="mb-4 grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4">
                <StatCard label="Faturamento" value={formatBRL(c.currentMetrics.revenue)} variation={c.revenueVariationPercent} />
                <StatCard label="Vendas" value={formatInt(c.currentMetrics.salesCount)} variation={c.salesCountVariationPercent} />
                <StatCard label="Ticket medio" value={formatBRL(c.currentMetrics.averageTicket)} variation={c.averageTicketVariationPercent} />
                <StatCard
                  label="Margem estimada"
                  value={c.currentMetrics.estimatedMargin == null ? '—' : `${formatBRL(c.currentMetrics.estimatedMargin)} (${formatPercent(c.currentMetrics.marginPercent)})`}
                  hint={c.currentMetrics.estimatedMargin == null ? 'Dados de custo insuficientes' : 'Sobre o custo cadastrado das vendas'}
                />
              </div>
            </>
          )
        }}
      </QueryState>

      <div className="grid gap-4 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader title="Faturamento diario" subtitle="Periodo atual vs. anterior" />
          <div className="p-4">
            <QueryState query={series} isEmpty={(d) => d.every((x) => x.salesCount === 0)} empty={{ title: 'Sem vendas no periodo', hint: 'Ajuste o periodo ou a loja.' }}>
              {(d) => <RevenueChart current={d} previous={prevSeries.data} />}
            </QueryState>
          </div>
        </Card>
        <Card>
          <CardHeader title="Alertas operacionais" action={<Link to="/alertas" className="text-xs text-brand-600 hover:underline">Ver todos</Link>} />
          <QueryState query={alerts} isEmpty={(d) => d.length === 0} empty={{ title: 'Nenhum alerta ativo' }}>
            {(d) => (
              <ul className="divide-y divide-line">
                {d.map((a) => (
                  <li key={a.id} className="flex items-start gap-2 px-4 py-2.5">
                    <SeverityBadge severity={a.severity} />
                    <div className="min-w-0"><p className="truncate text-sm font-medium">{a.title}</p><p className="line-clamp-2 text-xs text-ink-3">{a.message}</p></div>
                  </li>
                ))}
              </ul>
            )}
          </QueryState>
        </Card>

        <Card>
          <CardHeader title="Mais vendidos" subtitle="Por unidades" />
          <div className="p-3">
            <QueryState query={top} isEmpty={(d) => d.length === 0} empty={{ title: 'Sem vendas no periodo' }}>
              {(d) => <TopProductsChart products={d} metric="unitsSold" />}
            </QueryState>
          </div>
        </Card>
        <Card>
          <CardHeader title="Baixo estoque" action={<Link to="/estoque?tab=saldos&low=1" className="text-xs text-brand-600 hover:underline">Ver estoque</Link>} />
          <QueryState query={low} isEmpty={(d) => d.length === 0} empty={{ title: 'Nenhum produto abaixo do minimo' }}>
            {(d) => (
              <DataTable
                caption="Produtos com baixo estoque"
                rows={d}
                rowKey={(r) => r.storeId + r.productId}
                columns={[
                  { header: 'Produto', cell: (r) => <div><p className="font-medium">{r.productName}</p><p className="text-xs text-ink-3">{r.storeName}</p></div> },
                  { header: 'Saldo / min.', align: 'right', cell: (r) => <Badge tone={r.quantity === 0 ? 'bad' : 'warn'}>{r.quantity} / {r.minStock}</Badge> },
                ]}
              />
            )}
          </QueryState>
        </Card>
        <Card>
          <CardHeader title="Sem movimentacao (30 dias)" />
          <QueryState query={stale} isEmpty={(d) => d.length === 0} empty={{ title: 'Todos os produtos tiveram vendas' }}>
            {(d) => (
              <DataTable
                caption="Produtos sem movimentacao recente"
                rows={d}
                rowKey={(r) => r.productId}
                columns={[
                  { header: 'Produto', cell: (r) => <span className="font-medium">{r.name}</span> },
                  { header: 'Estoque', align: 'right', cell: (r) => formatInt(r.totalStock) },
                  { header: 'Ultima venda', align: 'right', cell: (r) => (r.lastSaleDate ? formatDate(r.lastSaleDate) : 'nunca') },
                ]}
              />
            )}
          </QueryState>
        </Card>

        <Card className="lg:col-span-3">
          <CardHeader title="Acoes recentes" action={<Link to="/acoes" className="text-xs text-brand-600 hover:underline">Ver todas</Link>} />
          <QueryState query={actions} isEmpty={(d) => d.length === 0} empty={{ title: 'Nenhuma proposta ainda', hint: 'Peca ao assistente para analisar o estoque e preparar uma reposicao.' }}>
            {(d) => (
              <DataTable
                caption="Acoes recentes"
                rows={d}
                rowKey={(r) => r.id}
                columns={[
                  { header: 'Loja', cell: (r) => <Link className="font-medium text-brand-700 hover:underline" to={`/acoes/${r.id}`}>{r.storeName}</Link> },
                  { header: 'Status', cell: (r) => <StatusBadge status={r.status} /> },
                  { header: 'Itens', align: 'right', cell: (r) => r.itemCount },
                  { header: 'Unidades', align: 'right', cell: (r) => formatInt(r.totalUnits) },
                  { header: 'Origem', cell: (r) => (r.createdViaAi ? <Badge tone="brand">IA</Badge> : <span className="text-ink-3">Manual</span>) },
                ]}
              />
            )}
          </QueryState>
        </Card>
      </div>
    </>
  )
}
