import clsx from 'clsx'
import type { ActionStatus } from '@/lib/api/types'
import { STATUS_LABEL } from './statusLabels'

const COLOR: Record<ActionStatus, string> = {
  DRAFT: 'text-ink-2',
  PENDING_APPROVAL: 'text-warn',
  APPROVED: 'text-brand-700',
  EXECUTED: 'text-ok',
  REJECTED: 'text-bad',
  CANCELLED: 'text-ink-3',
  FAILED: 'text-bad',
}

/** Carimbo de status; `land` anima a "batida do carimbo" quando o estado acabou de mudar por acao da pessoa. */
export function StatusBadge({ status, land }: { status: ActionStatus; land?: boolean }) {
  return <span className={clsx('stamp', COLOR[status], land && 'stamp-land')}>{STATUS_LABEL[status]}</span>
}
