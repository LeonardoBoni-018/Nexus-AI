import { Link } from 'react-router-dom'
import { RevenueChart, TopProductsChart } from '@/components/charts/Charts'
import { Shelf } from '@/components/charts/Shelf'
import { FilterBar } from '@/components/layout/FilterBar'
import { DataTable } from '@/components/ui/DataTable'
import { QueryState } from '@/components/ui/QueryState'
import { Badge, Card, CardHeader } from '@/components/ui/primitives'
import { StatCard } from '@/components/ui/StatCard'
import { useApiQuery } from '@/hooks/useApi'
import { queryParams, useFilters } from '@/hooks/useFilters'
import { StatusBadge } from '@/features/actions/StatusBadge'
import { SeverityBadge } from '@/features/alerts/SeverityBadge'
import { SEVERITY_VAR } from '@/features/alerts/severity'
import type {
  ActionSummary, AlertDto, CoverageItem, DashboardSummary, SeriesPoint, StaleProduct, TopProduct,
} from '@/lib/api/types'
import { addDays, formatBRL, formatDate, formatInt, formatPercent } from '@/lib/formatting'

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
  const coverage = useApiQuery<CoverageItem[]>(['dash'], '/analytics/coverage', { storeId: filters.storeId || undefined, windowDays: 28, limit: 10 })
  const stale = useApiQuery<StaleProduct[]>(['dash'], '/dashboard/stale-products', { storeId: filters.storeId || undefined, days: 30, limit: 5 })
  const alerts = useApiQuery<AlertDto[]>(['dash'], '/dashboard/alerts')
  const actions = useApiQuery<ActionSummary[]>(['dash'], '/dashboard/recent-actions')

  return (
    <>
      <header className="mb-5">
        <h1 className="sr-only">Visao geral</h1>
        <QueryState query={summary} skeleton={<div className="h-16" />}>
          {(s) => {
            const c = s.comparison
            return (
              <p className="font-display text-[1.65rem] font-semibold leading-snug sm:text-4xl" style={{ maxWidth: '24ch' }}>
                {formatBRL(c.currentMetrics.revenue)} vendidos
                {' '}entre {formatDate(filters.from)} e {formatDate(filters.to)}.
                {s.lowStockCount > 0 ? (
                  <>
                    {' '}<span className="marker font-extrabold">{s.lowStockCount} {s.lowStockCount === 1 ? 'item pede' : 'itens pedem'}</span> reposicao.
                  </>
                ) : (
                  ' Nenhum item abaixo do minimo.'
                )}
              </p>
            )
          }}
        </QueryState>
      </header>
      <FilterBar />

      <QueryState query={summary} skeleton={<div className="h-24" />}>
        {(s) => {
          const c = s.comparison
          return (
            <>
              {c.note && <p className="mb-3 rounded-[4px] bg-warn-bg px-3 py-2 text-sm text-warn">{c.note}</p>}
              <div className="mb-6 grid grid-cols-1 gap-5 sm:grid-cols-2 xl:grid-cols-4">
                <StatCard label="Faturamento" value={formatBRL(c.currentMetrics.revenue)} variation={c.revenueVariationPercent} />
                <StatCard label="Vendas" value={formatInt(c.currentMetrics.salesCount)} variation={c.salesCountVariationPercent} />
                <StatCard label="Ticket medio" value={formatBRL(c.currentMetrics.averageTicket)} variation={c.averageTicketVariationPercent} />
                <StatCard
                  label="Margem estimada"
                  value={c.currentMetrics.estimatedMargin == null ? '—' : formatPercent(c.currentMetrics.marginPercent)}
                  hint={c.currentMetrics.estimatedMargin == null ? 'Dados de custo insuficientes' : `${formatBRL(c.currentMetrics.estimatedMargin)} sobre o custo cadastrado`}
                />
              </div>
            </>
          )
        }}
      </QueryState>

      <div className="grid gap-6 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader title="Faturamento por dia" subtitle="Linha cheia: periodo atual. Tracejada: periodo anterior." />
          <div className="p-4">
            <QueryState query={series} isEmpty={(d) => d.every((x) => x.salesCount === 0)} empty={{ title: 'Sem vendas no periodo', hint: 'Ajuste o periodo ou a loja.' }}>
              {(d) => <RevenueChart current={d} previous={prevSeries.data} />}
            </QueryState>
          </div>
        </Card>

        <section aria-labelledby="alertas-h">
          <div className="mb-2 flex items-baseline justify-between">
            <h2 id="alertas-h" className="font-display text-lg font-bold">Alertas</h2>
            <Link to="/alertas" className="text-sm font-semibold text-brand-700 underline-offset-4 hover:underline">Ver todos</Link>
          </div>
          <QueryState query={alerts} isEmpty={(d) => d.length === 0} empty={{ title: 'Nenhum alerta ativo' }}>
            {(d) => (
              <ul className="space-y-2">
                {d.map((a) => (
                  <li key={a.id} className="tag py-2 pr-7" style={{ ['--tag' as string]: SEVERITY_VAR[a.severity] }}>
                    <SeverityBadge severity={a.severity} />
                    <p className="text-sm font-semibold leading-snug">{a.title}</p>
                    <p className="line-clamp-2 text-xs text-ink-2">{a.message}</p>
                  </li>
                ))}
              </ul>
            )}
          </QueryState>
        </section>
      </div>

      <section aria-labelledby="prateleira-h" className="mt-10">
        <div className="mb-1 flex flex-wrap items-baseline justify-between gap-2 border-t-[3px] border-ink pt-3">
          <h2 id="prateleira-h" className="font-display text-2xl font-extrabold">Quanto dura cada prateleira</h2>
          <p className="max-w-[48ch] text-sm text-ink-2">Cada caixa e um dia de estoque. Vermelho: acaba antes da reposicao chegar.</p>
        </div>
        <QueryState query={coverage} isEmpty={(d) => d.length === 0} empty={{ title: 'Sem dados de estoque' }}>
          {(d) => <Shelf items={d} />}
        </QueryState>
      </section>

      <div className="mt-10 grid gap-8 lg:grid-cols-3">
        <section aria-labelledby="top-h">
          <h2 id="top-h" className="mb-2 border-t-[3px] border-ink pt-3 font-display text-lg font-bold">Mais vendidos</h2>
          <QueryState query={top} isEmpty={(d) => d.length === 0} empty={{ title: 'Sem vendas no periodo' }}>
            {(d) => <TopProductsChart products={d} metric="unitsSold" />}
          </QueryState>
        </section>
        <section aria-labelledby="stale-h">
          <h2 id="stale-h" className="mb-2 border-t-[3px] border-ink pt-3 font-display text-lg font-bold">Parados ha 30 dias</h2>
          <QueryState query={stale} isEmpty={(d) => d.length === 0} empty={{ title: 'Todos os produtos tiveram vendas' }}>
            {(d) => (
              <DataTable
                caption="Produtos sem movimentacao recente"
                rows={d}
                rowKey={(r) => r.productId}
                columns={[
                  { header: 'Produto', cell: (r) => <span className="font-semibold">{r.name}</span> },
                  { header: 'Estoque', align: 'right', cell: (r) => formatInt(r.totalStock) },
                  { header: 'Ultima venda', align: 'right', cell: (r) => (r.lastSaleDate ? formatDate(r.lastSaleDate) : 'nunca') },
                ]}
              />
            )}
          </QueryState>
        </section>
        <section aria-labelledby="acoes-h">
          <div className="mb-2 flex items-baseline justify-between border-t-[3px] border-ink pt-3">
            <h2 id="acoes-h" className="font-display text-lg font-bold">Propostas recentes</h2>
            <Link to="/acoes" className="text-sm font-semibold text-brand-700 underline-offset-4 hover:underline">Ver todas</Link>
          </div>
          <QueryState query={actions} isEmpty={(d) => d.length === 0} empty={{ title: 'Nenhuma proposta ainda', hint: 'Peca ao assistente para analisar o estoque e preparar uma reposicao.' }}>
            {(d) => (
              <ul className="divide-y divide-line">
                {d.map((r) => (
                  <li key={r.id} className="flex items-center justify-between gap-2 py-2.5">
                    <div className="min-w-0">
                      <Link className="block truncate text-sm font-semibold text-brand-700 hover:underline" to={`/acoes/${r.id}`}>{r.storeName}</Link>
                      <p className="text-xs text-ink-3">{r.itemCount} itens, {formatInt(r.totalUnits)} unidades {r.createdViaAi && <Badge tone="brand">via IA</Badge>}</p>
                    </div>
                    <StatusBadge status={r.status} />
                  </li>
                ))}
              </ul>
            )}
          </QueryState>
        </section>
      </div>
    </>
  )
}
