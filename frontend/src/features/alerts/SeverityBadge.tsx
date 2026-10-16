import { AlertOctagon, AlertTriangle, Info } from 'lucide-react'
import { Badge } from '@/components/ui/primitives'
import type { AlertSeverity } from '@/lib/api/types'

// Status nunca depende so de cor: icone + rotulo.
export function SeverityBadge({ severity }: { severity: AlertSeverity }) {
  if (severity === 'CRITICAL') return <Badge tone="bad"><AlertOctagon className="mr-1 size-3" aria-hidden />Critico</Badge>
  if (severity === 'WARNING') return <Badge tone="warn"><AlertTriangle className="mr-1 size-3" aria-hidden />Atencao</Badge>
  return <Badge tone="neutral"><Info className="mr-1 size-3" aria-hidden />Info</Badge>
}
