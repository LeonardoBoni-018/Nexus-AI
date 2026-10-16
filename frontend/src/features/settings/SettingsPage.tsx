import { useQuery } from '@tanstack/react-query'
import { Badge, Card, CardHeader, PageHeader } from '@/components/ui/primitives'
import { api } from '@/lib/api/client'
import { useAuth } from '@/lib/auth/auth-context'
import type { AiStatus } from '@/lib/api/types'

const ROLE_HELP: Record<string, string> = {
  VIEWER: 'Consulta dados e conversa com o assistente (somente leitura).',
  ANALYST: 'Tudo do leitor + criar/editar/enviar propostas e reavaliar alertas.',
  MANAGER: 'Tudo do analista + aprovar, rejeitar e executar propostas; ver auditoria.',
  ADMIN: 'Todos os privilegios da organizacao.',
}

export function SettingsPage() {
  const { me, activeOrg } = useAuth()
  const ai = useQuery({ queryKey: ['/assistant/status'], queryFn: () => api<AiStatus>('/assistant/status') })
  return (
    <>
      <PageHeader title="Configuracoes" description="Perfil, organizacao e integracoes." />
      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader title="Perfil" />
          <dl className="space-y-2 p-4 text-sm">
            <div><dt className="text-xs text-ink-3">Nome</dt><dd>{me?.name}</dd></div>
            <div><dt className="text-xs text-ink-3">E-mail</dt><dd>{me?.email}</dd></div>
          </dl>
        </Card>
        <Card>
          <CardHeader title="Organizacao ativa" />
          <div className="space-y-2 p-4 text-sm">
            <p className="font-medium">{activeOrg?.organizationName} {activeOrg?.demo && <Badge tone="warn">demo</Badge>}</p>
            <p>Seu perfil: <Badge tone="brand">{activeOrg?.role}</Badge></p>
            <p className="text-ink-2">{activeOrg ? ROLE_HELP[activeOrg.role] : ''}</p>
            <p className="text-xs text-ink-3">Fuso horario: America/Sao_Paulo · Moeda: BRL</p>
          </div>
        </Card>
        <Card className="lg:col-span-2">
          <CardHeader title="Integracao de IA" />
          <div className="space-y-1 p-4 text-sm">
            {ai.isPending ? <p className="text-ink-3">Verificando...</p> : ai.data?.configured ? (
              <p><Badge tone="ok">Configurada</Badge> provedor <strong>{ai.data.provider}</strong>, modelo <strong>{ai.data.model}</strong></p>
            ) : (
              <p><Badge tone="warn">Nao configurada</Badge> {ai.data?.message}</p>
            )}
            <p className="text-xs text-ink-3">As credenciais ficam apenas no backend (variaveis de ambiente) e nunca sao exibidas.</p>
          </div>
        </Card>
      </div>
    </>
  )
}
