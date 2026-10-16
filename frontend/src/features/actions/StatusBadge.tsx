import { Badge } from '@/components/ui/primitives'
import type { ActionStatus } from '@/lib/api/types'

const LABEL: Record<ActionStatus, string> = {
  DRAFT: 'Rascunho',
  PENDING_APPROVAL: 'Aguardando aprovacao',
  APPROVED: 'Aprovada',
  EXECUTED: 'Executada',
  REJECTED: 'Rejeitada',
  CANCELLED: 'Cancelada',
  FAILED: 'Falhou',
}
const TONE: Record<ActionStatus, 'neutral' | 'ok' | 'warn' | 'bad' | 'brand'> = {
  DRAFT: 'neutral',
  PENDING_APPROVAL: 'warn',
  APPROVED: 'brand',
  EXECUTED: 'ok',
  REJECTED: 'bad',
  CANCELLED: 'neutral',
  FAILED: 'bad',
}
export const statusLabel = (s: ActionStatus): string => LABEL[s]
export function StatusBadge({ status }: { status: ActionStatus }) {
  return <Badge tone={TONE[status]}>{LABEL[status]}</Badge>
}
