import { useState } from 'react'
import { DataTable, Pagination } from '@/components/ui/DataTable'
import { QueryState } from '@/components/ui/QueryState'
import { Badge, Card, PageHeader, Select } from '@/components/ui/primitives'
import { useApiQuery } from '@/hooks/useApi'
import type { AuditEventDto, Page } from '@/lib/api/types'
import { formatDateTime } from '@/lib/formatting'

const TYPES = ['ACTION_CREATED', 'ACTION_EDITED', 'ACTION_SUBMITTED', 'ACTION_APPROVED', 'ACTION_REJECTED', 'ACTION_CANCELLED', 'ACTION_EXECUTED', 'ACTION_FAILED', 'AI_INTERACTION', 'USER_REGISTERED']

export function AuditPage() {
  const [type, setType] = useState('')
  const [page, setPage] = useState(0)
  const q = useApiQuery<Page<AuditEventDto>>(['audit', page], '/audit-events', { eventType: type || undefined, page, size: 20 })
  return (
    <>
      <PageHeader title="Historico de auditoria" description="Registro imutavel de operacoes sensiveis: quem fez o que, quando e em qual requisicao." />
      <div className="mb-3 max-w-xs">
        <Select label="Tipo de evento" value={type} onChange={(e) => { setType(e.target.value); setPage(0) }} options={[{ value: '', label: 'Todos' }, ...TYPES.map((t) => ({ value: t, label: t }))]} />
      </div>
      <Card>
        <QueryState query={q} isEmpty={(d) => d.items.length === 0} empty={{ title: 'Nenhum evento registrado' }}>
          {(d) => (
            <>
              <DataTable
                caption="Eventos de auditoria"
                rows={d.items}
                rowKey={(r) => r.id}
                columns={[
                  { header: 'Quando', cell: (r) => formatDateTime(r.createdAt) },
                  { header: 'Evento', cell: (r) => <Badge tone={r.eventType.includes('FAIL') || r.eventType.includes('REJECT') ? 'bad' : r.eventType.includes('EXECUTED') ? 'ok' : 'neutral'}>{r.eventType}</Badge> },
                  { header: 'Entidade', cell: (r) => <span className="text-xs">{r.entityType} <span className="text-ink-3">{r.entityId?.slice(0, 8)}</span></span> },
                  { header: 'Detalhes', cell: (r) => <span className="text-ink-2">{r.details ?? '—'}</span> },
                  { header: 'Request', cell: (r) => <code className="text-xs text-ink-3">{r.requestId?.slice(0, 8)}</code> },
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
