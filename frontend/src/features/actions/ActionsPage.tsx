import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { DataTable, Pagination } from '@/components/ui/DataTable'
import { Dialog } from '@/components/ui/Dialog'
import { QueryState } from '@/components/ui/QueryState'
import { Badge, Button, Card, PageHeader, Select } from '@/components/ui/primitives'
import { useApiQuery, useStores } from '@/hooks/useApi'
import { api, errorMessage } from '@/lib/api/client'
import { useAuth } from '@/lib/auth/auth-context'
import type { ActionDetail, ActionSummary, Page } from '@/lib/api/types'
import { formatDateTime, formatInt } from '@/lib/formatting'
import { StatusBadge } from './StatusBadge'
import { statusLabel } from './statusLabels'

const STATUSES = ['DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'EXECUTED', 'REJECTED', 'CANCELLED', 'FAILED'] as const

export function ActionsPage() {
  const { can } = useAuth()
  const navigate = useNavigate()
  const [status, setStatus] = useState('')
  const [page, setPage] = useState(0)
  const [creating, setCreating] = useState(false)
  const q = useApiQuery<Page<ActionSummary>>(['actions', page], '/actions', { status: status || undefined, page, size: 15 })
  return (
    <>
      <PageHeader
        title="Acoes e aprovacoes"
        description="Propostas operacionais. A IA so cria rascunhos; aprovacao e execucao sao decisoes humanas."
        actions={can('ANALYST') ? <Button onClick={() => setCreating(true)}>Nova proposta de reposicao</Button> : undefined}
      />
      <div className="mb-3 max-w-xs">
        <Select label="Status" value={status} onChange={(e) => { setStatus(e.target.value); setPage(0) }}
          options={[{ value: '', label: 'Todos' }, ...STATUSES.map((s) => ({ value: s, label: statusLabel(s) }))]} />
      </div>
      <Card>
        <QueryState query={q} isEmpty={(d) => d.items.length === 0} empty={{ title: 'Nenhuma proposta', hint: 'Crie uma proposta ou peca ao assistente de IA para analisar o estoque.' }}>
          {(d) => (
            <>
              <DataTable
                caption="Propostas de acao"
                rows={d.items}
                rowKey={(r) => r.id}
                onRowClick={(r) => navigate(`/acoes/${r.id}`)}
                columns={[
                  { header: 'Loja', cell: (r) => <Link to={`/acoes/${r.id}`} className="font-medium text-brand-700 hover:underline" onClick={(e) => e.stopPropagation()}>{r.storeName}</Link> },
                  { header: 'Status', cell: (r) => <StatusBadge status={r.status} /> },
                  { header: 'Itens', align: 'right', cell: (r) => r.itemCount },
                  { header: 'Unidades', align: 'right', cell: (r) => formatInt(r.totalUnits) },
                  { header: 'Criada por', cell: (r) => <span>{r.createdByName} {r.createdViaAi && <Badge tone="brand">via IA</Badge>}</span> },
                  { header: 'Data', cell: (r) => formatDateTime(r.createdAt) },
                ]}
              />
              <Pagination page={d.page} totalPages={d.totalPages} totalItems={d.totalItems} onPage={setPage} />
            </>
          )}
        </QueryState>
      </Card>
      <CreateDialog open={creating} onClose={() => setCreating(false)} />
    </>
  )
}

function CreateDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const stores = useStores()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const [storeId, setStoreId] = useState('')
  const create = useMutation({
    mutationFn: (id: string) => api<ActionDetail>('/actions/replenishment', { method: 'POST', body: { storeId: id } }),
    onSuccess: (d) => {
      void qc.invalidateQueries({ queryKey: ['/actions'] })
      onClose()
      navigate(`/acoes/${d.id}`)
    },
  })
  const selected = storeId || stores.data?.[0]?.id || ''
  return (
    <Dialog open={open} onClose={onClose} title="Nova proposta de reposicao">
      <div className="space-y-4">
        <p className="text-sm text-ink-2">As quantidades sao calculadas pelo sistema (cobertura alvo + lead time sobre as vendas de 28 dias). Voce podera ajustar antes de enviar para aprovacao.</p>
        <Select label="Loja de destino" value={selected} onChange={(e) => setStoreId(e.target.value)} options={(stores.data ?? []).map((s) => ({ value: s.id, label: s.name }))} />
        {create.isError && <p role="alert" className="rounded-lg bg-bad-bg px-3 py-2 text-sm text-bad">{errorMessage(create.error)}</p>}
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose}>Cancelar</Button>
          <Button disabled={!selected} loading={create.isPending} onClick={() => create.mutate(selected)}>Calcular e criar rascunho</Button>
        </div>
      </div>
    </Dialog>
  )
}
