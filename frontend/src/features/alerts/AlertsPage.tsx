import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Pagination } from '@/components/ui/DataTable'
import { QueryState } from '@/components/ui/QueryState'
import { Badge, Button, Card, PageHeader } from '@/components/ui/primitives'
import { useApiQuery } from '@/hooks/useApi'
import { api, errorMessage } from '@/lib/api/client'
import { useAuth } from '@/lib/auth/AuthContext'
import type { AlertDto, Page } from '@/lib/api/types'
import { formatDateTime } from '@/lib/formatting'
import { SeverityBadge } from './SeverityBadge'

const TYPE: Record<string, string> = { LOW_STOCK: 'Baixo estoque', STOCKOUT_RISK: 'Risco de ruptura', SALES_DROP: 'Queda de vendas', NO_MOVEMENT: 'Sem movimentacao' }

export function AlertsPage() {
  const { can } = useAuth()
  const qc = useQueryClient()
  const [unreadOnly, setUnreadOnly] = useState(false)
  const [page, setPage] = useState(0)
  const q = useApiQuery<Page<AlertDto>>(['alerts', page], '/alerts', { unreadOnly, page, size: 15 })
  const invalidate = () => qc.invalidateQueries({ predicate: (x) => String(x.queryKey[0]).startsWith('/alerts') || x.queryKey[0] === '/dashboard/alerts' })
  const read = useMutation({ mutationFn: (id: string) => api(`/alerts/${id}/read`, { method: 'PATCH' }), onSuccess: invalidate })
  const evaluate = useMutation({ mutationFn: () => api('/alerts/evaluate', { method: 'POST' }), onSuccess: invalidate })

  return (
    <>
      <PageHeader
        title="Alertas"
        description="Regras deterministicas: estoque abaixo do minimo, risco de ruptura, queda de vendas (>= 20% com amostra minima) e produtos parados (30 dias)."
        actions={can('ANALYST') ? <Button variant="secondary" loading={evaluate.isPending} onClick={() => evaluate.mutate()}>Reavaliar agora</Button> : undefined}
      />
      {(read.isError || evaluate.isError) && <p role="alert" className="mb-3 rounded-lg bg-bad-bg px-3 py-2 text-sm text-bad">{errorMessage(read.error ?? evaluate.error)}</p>}
      <label className="mb-3 flex items-center gap-2 text-sm"><input type="checkbox" checked={unreadOnly} onChange={(e) => { setUnreadOnly(e.target.checked); setPage(0) }} /> Somente nao lidos</label>
      <Card>
        <QueryState query={q} isEmpty={(d) => d.items.length === 0} empty={{ title: 'Nenhum alerta ativo', hint: 'Tudo certo por aqui. Os alertas sao reavaliados a cada 15 minutos.' }}>
          {(d) => (
            <>
              <ul className="divide-y divide-line">
                {d.items.map((a) => (
                  <li key={a.id} className="flex flex-wrap items-start gap-3 px-4 py-3">
                    <SeverityBadge severity={a.severity} />
                    <div className="min-w-0 flex-1">
                      <p className={a.read ? 'text-sm' : 'text-sm font-semibold'}>{a.title}</p>
                      <p className="text-sm text-ink-2">{a.message}</p>
                      <p className="mt-0.5 flex items-center gap-2 text-xs text-ink-3"><Badge>{TYPE[a.type] ?? a.type}</Badge>{formatDateTime(a.createdAt)}</p>
                    </div>
                    {!a.read && <Button variant="ghost" loading={read.isPending && read.variables === a.id} onClick={() => read.mutate(a.id)}>Marcar como lido</Button>}
                  </li>
                ))}
              </ul>
              <Pagination page={d.page} totalPages={d.totalPages} totalItems={d.totalItems} onPage={setPage} />
            </>
          )}
        </QueryState>
      </Card>
    </>
  )
}
