import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { DataTable } from '@/components/ui/DataTable'
import { Dialog } from '@/components/ui/Dialog'
import { QueryState } from '@/components/ui/QueryState'
import { Badge, Button, Card, CardHeader, PageHeader } from '@/components/ui/primitives'
import { useApiQuery } from '@/hooks/useApi'
import { api, errorMessage } from '@/lib/api/client'
import { useAuth } from '@/lib/auth/AuthContext'
import type { ActionDetail, ActionItem } from '@/lib/api/types'
import { formatDateTime, formatDecimal, formatInt } from '@/lib/formatting'
import { StatusBadge } from './StatusBadge'

export function ActionDetailPage() {
  const { id = '' } = useParams()
  const q = useApiQuery<ActionDetail>(['action', id], `/actions/${id}`)
  return (
    <>
      <p className="mb-2 text-sm"><Link to="/acoes" className="text-brand-700 hover:underline">← Acoes</Link></p>
      <QueryState query={q}>{(a) => <Detail action={a} />}</QueryState>
    </>
  )
}

function Detail({ action }: { action: ActionDetail }) {
  const { can } = useAuth()
  const qc = useQueryClient()
  const [rejecting, setRejecting] = useState(false)
  const [reason, setReason] = useState('')
  const [edits, setEdits] = useState<Record<string, number>>({})
  const editable = (action.status === 'DRAFT' || action.status === 'PENDING_APPROVAL') && can('ANALYST')

  const refresh = () => {
    void qc.invalidateQueries({ predicate: (x) => String(x.queryKey[0]).startsWith('/actions') || String(x.queryKey[0]).startsWith('/dashboard') })
    setEdits({})
  }
  const run = useMutation({
    mutationFn: (v: { verb: string; body?: unknown }) => api<ActionDetail>(`/actions/${action.id}/${v.verb}`, { method: 'POST', body: v.body ?? {} }),
    onSuccess: () => { refresh(); setRejecting(false); setReason('') },
  })
  const save = useMutation({
    mutationFn: () => api<ActionDetail>(`/actions/${action.id}`, { method: 'PATCH', body: { quantities: edits } }),
    onSuccess: () => refresh(),
  })
  const next = new Set(action.nextStatuses)
  const error = run.error ?? save.error

  return (
    <>
      <PageHeader
        title={`Reposicao · ${action.storeName}`}
        description={`Criada por ${action.createdByName}${action.createdViaAi ? ' (rascunho gerado pela IA)' : ''} em ${formatDateTime(action.createdAt)}`}
        actions={<StatusBadge status={action.status} />}
      />
      {error && <p role="alert" className="mb-3 rounded-lg bg-bad-bg px-3 py-2 text-sm text-bad">{errorMessage(error)}</p>}
      {action.failureReason && <p role="alert" className="mb-3 rounded-lg bg-bad-bg px-3 py-2 text-sm text-bad">Falha na execucao: {action.failureReason}</p>}

      <div className="mb-4 flex flex-wrap gap-2">
        {next.has('PENDING_APPROVAL') && can('ANALYST') && <Button loading={run.isPending} onClick={() => run.mutate({ verb: 'submit' })}>Enviar para aprovacao</Button>}
        {next.has('APPROVED') && can('MANAGER') && <Button loading={run.isPending} onClick={() => run.mutate({ verb: 'approve' })}>Aprovar</Button>}
        {next.has('REJECTED') && can('MANAGER') && <Button variant="danger" onClick={() => setRejecting(true)}>Rejeitar</Button>}
        {next.has('EXECUTED') && can('MANAGER') && <Button loading={run.isPending} onClick={() => run.mutate({ verb: 'execute' })}>Executar reposicao</Button>}
        {next.has('CANCELLED') && can('ANALYST') && <Button variant="secondary" loading={run.isPending} onClick={() => run.mutate({ verb: 'cancel' })}>Cancelar proposta</Button>}
        {editable && Object.keys(edits).length > 0 && <Button variant="secondary" loading={save.isPending} onClick={() => save.mutate()}>Salvar quantidades</Button>}
        {!can('MANAGER') && (action.status === 'PENDING_APPROVAL' || action.status === 'APPROVED') && <Badge tone="warn">Aguardando um gerente</Badge>}
      </div>

      <div className="grid gap-4 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader title="Itens da reposicao" subtitle="Quantidade sugerida pelo calculo deterministico; ajustes ficam registrados em auditoria" />
          <DataTable<ActionItem>
            caption="Itens da proposta"
            rows={action.items}
            rowKey={(i) => i.productId}
            columns={[
              { header: 'Produto', cell: (i) => <div><p className="font-medium">{i.productName}</p><p className="text-xs text-ink-3">{i.sku}</p></div> },
              { header: 'Estoque', align: 'right', cell: (i) => formatInt(i.currentStock) },
              { header: 'Media/dia', align: 'right', cell: (i) => formatDecimal(i.averageDailySales) },
              { header: 'Cobertura', align: 'right', cell: (i) => (i.coverageDays == null ? '—' : `${formatDecimal(i.coverageDays)} d`) },
              { header: 'Sugerido', align: 'right', cell: (i) => formatInt(i.suggestedQuantity) },
              {
                header: 'Quantidade',
                align: 'right',
                cell: (i) =>
                  editable ? (
                    <input
                      type="number"
                      min={0}
                      max={100000}
                      aria-label={`Quantidade de ${i.productName}`}
                      value={edits[i.productId] ?? i.approvedQuantity}
                      onChange={(e) => setEdits((s) => ({ ...s, [i.productId]: Math.max(0, Math.floor(Number(e.target.value) || 0)) }))}
                      className="w-20 rounded border border-line px-2 py-1 text-right"
                    />
                  ) : (
                    <span className="font-semibold">{formatInt(i.approvedQuantity)}</span>
                  ),
              },
            ]}
          />
          {editable && <p className="px-4 pb-3 text-xs text-ink-3">Use 0 para remover um item.</p>}
        </Card>
        <div className="space-y-4">
          <Card>
            <CardHeader title="Justificativa" />
            <p className="whitespace-pre-wrap p-4 text-sm text-ink-2">{action.justification ?? '—'}</p>
          </Card>
          <Card>
            <CardHeader title="Historico de decisoes" />
            {action.approvals.length === 0 ? (
              <p className="p-4 text-sm text-ink-3">Nenhuma decisao registrada.</p>
            ) : (
              <ul className="divide-y divide-line">
                {action.approvals.map((a, idx) => (
                  <li key={idx} className="px-4 py-3 text-sm">
                    <p><Badge tone={a.decision === 'APPROVED' ? 'ok' : 'bad'}>{a.decision === 'APPROVED' ? 'Aprovada' : 'Rejeitada'}</Badge> por {a.userName}</p>
                    {a.reason && <p className="mt-1 text-ink-2">{a.reason}</p>}
                    <p className="text-xs text-ink-3">{formatDateTime(a.createdAt)}</p>
                  </li>
                ))}
              </ul>
            )}
            {action.executedAt && <p className="border-t border-line px-4 py-3 text-sm text-ok">Executada em {formatDateTime(action.executedAt)} (movimentos registrados no estoque)</p>}
          </Card>
        </div>
      </div>

      <Dialog open={rejecting} onClose={() => setRejecting(false)} title="Rejeitar proposta">
        <form
          className="space-y-3"
          onSubmit={(e) => {
            e.preventDefault()
            if (reason.trim()) run.mutate({ verb: 'reject', body: { reason: reason.trim() } })
          }}
        >
          <label htmlFor="reject-reason" className="text-xs font-medium text-ink-2">Motivo (obrigatorio)</label>
          <textarea id="reject-reason" required maxLength={900} value={reason} onChange={(e) => setReason(e.target.value)} className="min-h-24 w-full rounded-lg border border-line p-2 text-sm" />
          <div className="flex justify-end gap-2">
            <Button type="button" variant="secondary" onClick={() => setRejecting(false)}>Voltar</Button>
            <Button type="submit" variant="danger" loading={run.isPending} disabled={!reason.trim()}>Confirmar rejeicao</Button>
          </div>
        </form>
      </Dialog>
    </>
  )
}
