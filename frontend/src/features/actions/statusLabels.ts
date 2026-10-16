import type { ActionStatus } from '@/lib/api/types'

export const STATUS_LABEL: Record<ActionStatus, string> = {
  DRAFT: 'Rascunho',
  PENDING_APPROVAL: 'Aguardando aprovacao',
  APPROVED: 'Aprovada',
  EXECUTED: 'Executada',
  REJECTED: 'Rejeitada',
  CANCELLED: 'Cancelada',
  FAILED: 'Falhou',
}
export const statusLabel = (s: ActionStatus): string => STATUS_LABEL[s]
