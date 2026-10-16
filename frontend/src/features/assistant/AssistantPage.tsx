import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Bot, ChevronDown, Send, User } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { QueryState } from '@/components/ui/QueryState'
import { Badge, Button, Card, PageHeader } from '@/components/ui/primitives'
import { api, errorMessage } from '@/lib/api/client'
import type { AiStatus, AssistantReply, ChatMessage, Conversation, ToolTrace } from '@/lib/api/types'

const SUGGESTIONS = [
  'Analise as vendas dos ultimos sete dias, compare com os sete dias anteriores, identifique os produtos com risco de ruptura e prepare uma proposta de reposicao.',
  'Quais produtos estao abaixo do estoque minimo e em qual loja?',
  'Quais foram os 5 produtos mais vendidos no ultimo mes por faturamento?',
]

const TOOL_LABEL: Record<string, string> = {
  getOrganizationContext: 'Contexto da organizacao',
  getSalesSummary: 'Resumo de vendas',
  compareSalesPeriods: 'Comparacao de periodos',
  getLowStockProducts: 'Baixo estoque',
  getProductInventory: 'Estoque do produto',
  getTopSellingProducts: 'Mais vendidos',
  analyzeInventoryCoverage: 'Cobertura de estoque',
  simulateReplenishment: 'Simulacao de reposicao',
  createReplenishmentDraft: 'Rascunho de reposicao',
  getActionStatus: 'Status da proposta',
}

export function AssistantPage() {
  const qc = useQueryClient()
  const [conversationId, setConversationId] = useState<string | null>(null)
  const [draft, setDraft] = useState('')
  const [pending, setPending] = useState<string | null>(null)
  const bottom = useRef<HTMLDivElement>(null)

  const status = useQuery({ queryKey: ['/assistant/status'], queryFn: () => api<AiStatus>('/assistant/status') })
  const conversations = useQuery({ queryKey: ['/assistant/conversations'], queryFn: () => api<Conversation[]>('/assistant/conversations') })
  const messages = useQuery({
    queryKey: ['/assistant/messages', conversationId],
    queryFn: () => api<ChatMessage[]>(`/assistant/conversations/${conversationId}/messages`),
    enabled: conversationId !== null,
  })

  const send = useMutation({
    mutationFn: async (text: string) => {
      let id = conversationId
      if (!id) {
        const c = await api<Conversation>('/assistant/conversations', { method: 'POST', body: {} })
        id = c.id
        setConversationId(id)
      }
      return { id, reply: await api<AssistantReply>(`/assistant/conversations/${id}/messages`, { method: 'POST', body: { content: text } }) }
    },
    onMutate: (text) => setPending(text),
    onSettled: async (data) => {
      await qc.invalidateQueries({ queryKey: ['/assistant/conversations'] })
      if (data) await qc.invalidateQueries({ queryKey: ['/assistant/messages', data.id] })
      await qc.invalidateQueries({ predicate: (x) => String(x.queryKey[0]).startsWith('/actions') })
      setPending(null)
    },
    onSuccess: () => setDraft(''),
  })

  useEffect(() => { bottom.current?.scrollIntoView({ behavior: 'smooth' }) }, [messages.data, pending])
  const notConfigured = status.data && !status.data.configured

  const submit = (text: string) => {
    const t = text.trim()
    if (t && !send.isPending) send.mutate(t)
  }

  return (
    <>
      <PageHeader title="Assistente de IA" description="Consulta ferramentas reais, mostra as evidencias e prepara rascunhos. Nunca aprova nem executa." />
      {notConfigured && (
        <div role="status" className="mb-4 rounded-lg bg-warn-bg px-4 py-3 text-sm text-warn">
          A integracao de IA nao esta configurada neste ambiente. Defina <code>AI_PROVIDER</code>, <code>AI_MODEL</code> e <code>AI_API_KEY</code> no backend (veja o README). O restante da plataforma continua funcionando.
        </div>
      )}
      <div className="grid gap-4 lg:grid-cols-[260px_1fr]">
        <Card className="h-fit">
          <div className="flex items-center justify-between border-b border-line px-3 py-2">
            <h2 className="text-sm font-semibold">Conversas</h2>
            <Button variant="ghost" onClick={() => setConversationId(null)}>Nova</Button>
          </div>
          <QueryState query={conversations} isEmpty={(d) => d.length === 0} empty={{ title: 'Sem conversas' }}>
            {(d) => (
              <ul className="max-h-80 overflow-auto p-1">
                {d.map((c) => (
                  <li key={c.id}>
                    <button onClick={() => setConversationId(c.id)} aria-current={c.id === conversationId} className={`w-full truncate rounded-lg px-3 py-2 text-left text-sm ${c.id === conversationId ? 'bg-brand-50 font-medium text-brand-700' : 'hover:bg-canvas'}`}>
                      {c.title}
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </QueryState>
        </Card>

        <Card className="flex min-h-[28rem] flex-col">
          <div className="flex-1 space-y-4 overflow-auto p-4" aria-live="polite">
            {conversationId === null && !pending && (
              <div className="space-y-2">
                <p className="text-sm text-ink-2">Experimente perguntar:</p>
                {SUGGESTIONS.map((s) => (
                  <button key={s} disabled={!!notConfigured || send.isPending} onClick={() => submit(s)} className="block w-full rounded-lg border border-line px-3 py-2 text-left text-sm hover:bg-brand-50 disabled:opacity-50">
                    {s}
                  </button>
                ))}
              </div>
            )}
            {messages.data?.map((m) => <Message key={m.id} role={m.role} content={m.content} traces={m.toolCalls} />)}
            {pending && !messages.data?.some((m) => m.content === pending && m.role === 'USER') && <Message role="USER" content={pending} traces={[]} />}
            {send.isPending && <p className="flex items-center gap-2 text-sm text-ink-3"><span className="size-3.5 animate-spin rounded-full border-2 border-brand-600 border-t-transparent" aria-hidden />Consultando os dados...</p>}
            {send.isError && <p role="alert" className="rounded-lg bg-bad-bg px-3 py-2 text-sm text-bad">{errorMessage(send.error)}</p>}
            <div ref={bottom} />
          </div>
          <form
            className="flex gap-2 border-t border-line p-3"
            onSubmit={(e) => { e.preventDefault(); submit(draft) }}
          >
            <label htmlFor="chat-input" className="sr-only">Mensagem</label>
            <textarea
              id="chat-input"
              rows={2}
              maxLength={4000}
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              onKeyDown={(e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); submit(draft) } }}
              placeholder="Pergunte sobre vendas, estoque ou reposicao..."
              className="flex-1 resize-none rounded-lg border border-line p-2 text-sm"
            />
            <Button type="submit" disabled={!draft.trim() || !!notConfigured} loading={send.isPending} aria-label="Enviar"><Send className="size-4" /></Button>
          </form>
        </Card>
      </div>
    </>
  )
}

function Message({ role, content, traces }: { role: 'USER' | 'ASSISTANT'; content: string; traces: ToolTrace[] }) {
  const isUser = role === 'USER'
  const actionIds = [...content.matchAll(/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/g)].map((m) => m[0])
  const created = traces.some((t) => t.tool === 'createReplenishmentDraft' && t.status === 'SUCCESS')
  return (
    <div className={`flex gap-3 ${isUser ? 'flex-row-reverse' : ''}`}>
      <span className={`flex size-8 shrink-0 items-center justify-center rounded-full ${isUser ? 'bg-brand-100 text-brand-700' : 'bg-navy-900 text-white'}`}>
        {isUser ? <User className="size-4" aria-hidden /> : <Bot className="size-4" aria-hidden />}
        <span className="sr-only">{isUser ? 'Voce' : 'Assistente'}</span>
      </span>
      <div className={`max-w-[85%] space-y-2 ${isUser ? 'text-right' : ''}`}>
        <p className={`whitespace-pre-wrap rounded-xl px-3 py-2 text-left text-sm ${isUser ? 'bg-brand-600 text-white' : 'bg-canvas'}`}>{content}</p>
        {created && actionIds.length > 0 && (
          <Link to={`/acoes/${actionIds[actionIds.length - 1]}`} className="inline-block text-sm text-brand-700 hover:underline">Abrir proposta criada →</Link>
        )}
        {traces.length > 0 && (
          <details className="rounded-lg border border-line text-left text-xs">
            <summary className="flex cursor-pointer items-center gap-1 px-2 py-1.5 text-ink-2"><ChevronDown className="size-3" aria-hidden />Evidencias: {traces.length} consulta(s) a ferramentas</summary>
            <ul className="divide-y divide-line">
              {traces.map((t, i) => (
                <li key={i} className="px-2 py-1.5">
                  <div className="flex items-center gap-2"><span className="font-medium">{TOOL_LABEL[t.tool] ?? t.tool}</span><Badge tone={t.status === 'SUCCESS' ? 'ok' : 'bad'}>{t.status === 'SUCCESS' ? 'ok' : t.status === 'DENIED' ? 'negado' : 'erro'}</Badge><span className="text-ink-3">{t.durationMs} ms</span></div>
                  <code className="mt-0.5 block break-all text-ink-3">{t.arguments}</code>
                </li>
              ))}
            </ul>
          </details>
        )}
      </div>
    </div>
  )
}
