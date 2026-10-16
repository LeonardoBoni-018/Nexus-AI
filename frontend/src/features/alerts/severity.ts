import type { AlertSeverity } from '@/lib/api/types'

export const SEVERITY_VAR: Record<AlertSeverity, string> = {
  CRITICAL: 'var(--bad)',
  WARNING: 'var(--warn)',
  INFO: 'var(--ink-3)',
}
