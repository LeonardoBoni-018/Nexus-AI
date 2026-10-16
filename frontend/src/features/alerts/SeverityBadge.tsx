import { AlertOctagon, AlertTriangle, Info } from 'lucide-react'
import type { AlertSeverity } from '@/lib/api/types'

// Status nunca depende so de cor: icone + rotulo.
export function SeverityBadge({ severity }: { severity: AlertSeverity }) {
  const base = 'inline-flex items-center gap-1 text-xs font-bold'
  if (severity === 'CRITICAL') return <span className={`${base} text-bad`}><AlertOctagon className="size-3.5" aria-hidden />Critico</span>
  if (severity === 'WARNING') return <span className={`${base} text-warn`}><AlertTriangle className="size-3.5" aria-hidden />Atencao</span>
  return <span className={`${base} text-ink-2`}><Info className="size-3.5" aria-hidden />Informativo</span>
}
