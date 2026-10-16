import clsx from 'clsx'
import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { DataTable, Pagination } from '@/components/ui/DataTable'
import { Dialog } from '@/components/ui/Dialog'
import { QueryState } from '@/components/ui/QueryState'
import { Badge, Card, Input, PageHeader, Select } from '@/components/ui/primitives'
import { useApiQuery, useCategories, useStores } from '@/hooks/useApi'
import type { InventoryRow, MovementRow, Page, ProductRow } from '@/lib/api/types'
import { formatBRL, formatDateTime, formatInt } from '@/lib/formatting'
import type { ReactNode } from 'react'

type Tab = 'saldos' | 'produtos' | 'movimentos'
const TABS: { id: Tab; label: string }[] = [
  { id: 'saldos', label: 'Saldos por loja' },
  { id: 'produtos', label: 'Catalogo' },
  { id: 'movimentos', label: 'Movimentacoes' },
]

export function InventoryPage() {
  const [params, setParams] = useSearchParams()
  const tab = (params.get('tab') as Tab | null) ?? 'saldos'
  const setTab = (t: Tab) => setParams({ tab: t }, { replace: true })
  return (
    <>
      <PageHeader title="Estoque" description="Saldos, catalogo de produtos e historico de movimentacoes." />
      <div role="tablist" aria-label="Secoes de estoque" className="mb-4 flex gap-1 border-b border-line">
        {TABS.map((t) => (
          <button
            key={t.id}
            role="tab"
            aria-selected={tab === t.id}
            onClick={() => setTab(t.id)}
            className={clsx('-mb-px border-b-2 px-3 py-2 text-sm', tab === t.id ? 'border-brand-600 font-medium text-brand-700' : 'border-transparent text-ink-3 hover:text-ink')}
          >
            {t.label}
          </button>
        ))}
      </div>
      {tab === 'saldos' && <Balances lowDefault={params.get('low') === '1'} />}
      {tab === 'produtos' && <Products />}
      {tab === 'movimentos' && <Movements />}
    </>
  )
}

function Toolbar({ children }: { children: ReactNode }) {
  return <div className="mb-3 flex flex-wrap items-end gap-3 rounded-xl border border-line bg-surface p-3">{children}</div>
}

function Balances({ lowDefault }: { lowDefault: boolean }) {
  const [search, setSearch] = useState('')
  const [store, setStore] = useState('')
  const [low, setLow] = useState(lowDefault)
  const [page, setPage] = useState(0)
  const stores = useStores()
  const q = useApiQuery<Page<InventoryRow>>(['inv', page], '/inventory', { search: search || undefined, storeId: store || undefined, lowOnly: low, page, size: 15 })
  return (
    <>
      <Toolbar>
        <Input label="Buscar produto ou SKU" value={search} onChange={(e) => { setSearch(e.target.value); setPage(0) }} />
        <Select label="Loja" value={store} onChange={(e) => { setStore(e.target.value); setPage(0) }} options={[{ value: '', label: 'Todas' }, ...(stores.data ?? []).map((s) => ({ value: s.id, label: s.name }))]} />
        <label className="flex min-h-9 items-center gap-2 text-sm"><input type="checkbox" checked={low} onChange={(e) => { setLow(e.target.checked); setPage(0) }} /> Somente abaixo do minimo</label>
      </Toolbar>
      <Card>
        <QueryState query={q} isEmpty={(d) => d.items.length === 0} empty={{ title: 'Nenhum saldo encontrado', hint: 'Limpe a busca ou desmarque o filtro.' }}>
          {(d) => (
            <>
              <DataTable
                caption="Saldos de estoque"
                rows={d.items}
                rowKey={(r) => r.storeId + r.productId}
                columns={[
                  { header: 'Produto', cell: (r) => <div><p className="font-medium">{r.productName}</p><p className="text-xs text-ink-3">{r.sku} · {r.category}</p></div> },
                  { header: 'Loja', cell: (r) => r.storeName },
                  { header: 'Minimo', align: 'right', cell: (r) => formatInt(r.minStock) },
                  { header: 'Saldo', align: 'right', cell: (r) => <Badge tone={r.quantity === 0 ? 'bad' : r.belowMinimum ? 'warn' : 'ok'}>{formatInt(r.quantity)}</Badge> },
                ]}
              />
              <Pagination page={d.page} totalPages={d.totalPages} totalItems={d.totalItems} onPage={setPage} />
            </>
          )}
        </QueryState>
      </Card>
    </>
  )
}

function Products() {
  const [search, setSearch] = useState('')
  const [category, setCategory] = useState('')
  const [page, setPage] = useState(0)
  const [selected, setSelected] = useState<string | null>(null)
  const categories = useCategories()
  const q = useApiQuery<Page<ProductRow>>(['prod', page], '/products', { search: search || undefined, category: category || undefined, page, size: 15 })
  return (
    <>
      <Toolbar>
        <Input label="Buscar" value={search} onChange={(e) => { setSearch(e.target.value); setPage(0) }} />
        <Select label="Categoria" value={category} onChange={(e) => { setCategory(e.target.value); setPage(0) }} options={[{ value: '', label: 'Todas' }, ...(categories.data ?? []).map((c) => ({ value: c, label: c }))]} />
      </Toolbar>
      <Card>
        <QueryState query={q} isEmpty={(d) => d.items.length === 0} empty={{ title: 'Nenhum produto encontrado' }}>
          {(d) => (
            <>
              <DataTable
                caption="Catalogo de produtos"
                rows={d.items}
                rowKey={(r) => r.id}
                onRowClick={(r) => setSelected(r.id)}
                columns={[
                  { header: 'Produto', cell: (r) => <div><p className="font-medium">{r.name}</p><p className="text-xs text-ink-3">{r.sku}</p></div> },
                  { header: 'Categoria', cell: (r) => r.category },
                  { header: 'Custo', align: 'right', cell: (r) => formatBRL(r.cost) },
                  { header: 'Preco', align: 'right', cell: (r) => formatBRL(r.price) },
                  { header: 'Estoque total', align: 'right', cell: (r) => formatInt(r.totalStock) },
                ]}
              />
              <Pagination page={d.page} totalPages={d.totalPages} totalItems={d.totalItems} onPage={setPage} />
            </>
          )}
        </QueryState>
      </Card>
      <ProductDialog id={selected} onClose={() => setSelected(null)} />
    </>
  )
}

function ProductDialog({ id, onClose }: { id: string | null; onClose: () => void }) {
  const q = useApiQuery<{ product: ProductRow; balances: { storeId: string; storeName: string; quantity: number }[] }>(['prodd', id], `/products/${id}`, undefined, id !== null)
  return (
    <Dialog open={id !== null} onClose={onClose} title="Produto">
      {id && (
        <QueryState query={q}>
          {(d) => (
            <div className="space-y-3 text-sm">
              <p className="font-medium">{d.product.name} <span className="text-ink-3">({d.product.sku})</span></p>
              <p className="text-ink-2">Minimo {d.product.minStock} · cobertura alvo {d.product.targetCoverageDays} dias · lead time {d.product.leadTimeDays} dias</p>
              <DataTable caption="Saldo por loja" rows={d.balances} rowKey={(b) => b.storeId} columns={[
                { header: 'Loja', cell: (b) => b.storeName },
                { header: 'Saldo', align: 'right', cell: (b) => formatInt(b.quantity) },
              ]} />
            </div>
          )}
        </QueryState>
      )}
    </Dialog>
  )
}

const TYPE_LABEL: Record<string, string> = { OPENING: 'Abertura', SALE: 'Venda', REPLENISHMENT: 'Reposicao', ADJUSTMENT: 'Ajuste' }

function Movements() {
  const [type, setType] = useState('')
  const [store, setStore] = useState('')
  const [page, setPage] = useState(0)
  const stores = useStores()
  const q = useApiQuery<Page<MovementRow>>(['mov', page], '/inventory/movements', { type: type || undefined, storeId: store || undefined, page, size: 15 })
  return (
    <>
      <Toolbar>
        <Select label="Tipo" value={type} onChange={(e) => { setType(e.target.value); setPage(0) }} options={[{ value: '', label: 'Todos' }, ...Object.entries(TYPE_LABEL).map(([v, l]) => ({ value: v, label: l }))]} />
        <Select label="Loja" value={store} onChange={(e) => { setStore(e.target.value); setPage(0) }} options={[{ value: '', label: 'Todas' }, ...(stores.data ?? []).map((s) => ({ value: s.id, label: s.name }))]} />
      </Toolbar>
      <Card>
        <QueryState query={q} isEmpty={(d) => d.items.length === 0} empty={{ title: 'Nenhuma movimentacao' }}>
          {(d) => (
            <>
              <DataTable
                caption="Movimentacoes de estoque"
                rows={d.items}
                rowKey={(r) => r.id}
                columns={[
                  { header: 'Data', cell: (r) => formatDateTime(r.occurredAt) },
                  { header: 'Produto', cell: (r) => <div><p className="font-medium">{r.productName}</p><p className="text-xs text-ink-3">{r.storeName}</p></div> },
                  { header: 'Tipo', cell: (r) => <Badge tone={r.type === 'REPLENISHMENT' ? 'ok' : 'neutral'}>{TYPE_LABEL[r.type] ?? r.type}</Badge> },
                  { header: 'Variacao', align: 'right', cell: (r) => <span className={r.quantityDelta > 0 ? 'text-ok' : 'text-ink'}>{r.quantityDelta > 0 ? '+' : ''}{r.quantityDelta}</span> },
                  { header: 'Saldo apos', align: 'right', cell: (r) => formatInt(r.balanceAfter) },
                ]}
              />
              <Pagination page={d.page} totalPages={d.totalPages} totalItems={d.totalItems} onPage={setPage} />
            </>
          )}
        </QueryState>
      </Card>
    </>
  )
}
