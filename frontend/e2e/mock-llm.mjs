// Provedor de LLM SIMULADO (formato OpenAI Chat Completions) usado apenas nos testes E2E.
// Exercita o adaptador real do backend sem rede externa nem custo.
import { createServer } from 'node:http'

const PORT = Number(process.env.MOCK_LLM_PORT ?? 4010)

function reply(message) {
  return { choices: [{ index: 0, message, finish_reason: message.tool_calls ? 'tool_calls' : 'stop' }] }
}

createServer((req, res) => {
  if (req.method === 'GET' && req.url === '/health') {
    res.writeHead(200).end('ok')
    return
  }
  if (req.method !== 'POST' || !req.url.endsWith('/chat/completions')) {
    res.writeHead(404).end()
    return
  }
  let raw = ''
  req.on('data', (c) => (raw += c))
  req.on('end', () => {
    const body = JSON.parse(raw)
    const tools = body.messages.filter((m) => m.role === 'tool')
    let out
    if (tools.length === 0) {
      out = reply({ role: 'assistant', content: null, tool_calls: [{ id: 'call_ctx', type: 'function', function: { name: 'getOrganizationContext', arguments: '{}' } }] })
    } else if (tools.length === 1) {
      const ctx = JSON.parse(tools[0].content)
      const store = ctx.stores.find((s) => s.name === 'Loja Centro') ?? ctx.stores[0]
      out = reply({
        role: 'assistant', content: null,
        tool_calls: [{ id: 'call_draft', type: 'function', function: { name: 'createReplenishmentDraft', arguments: JSON.stringify({ storeId: store.id, justification: 'E2E: reposicao baseada na cobertura de estoque dos ultimos 28 dias.' }) } }],
      })
    } else {
      const draft = JSON.parse(tools[1].content)
      out = reply({ role: 'assistant', content: `Analise concluida. Criei o rascunho ${draft.actionId} para aprovacao humana.` })
    }
    res.writeHead(200, { 'Content-Type': 'application/json' }).end(JSON.stringify(out))
  })
}).listen(PORT, '127.0.0.1', () => console.log(`mock-llm em ${PORT}`))
