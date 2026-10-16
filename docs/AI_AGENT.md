# Agente de IA

## Visao geral
`AiAgentService` orquestra: valida contexto → carrega historico autorizado (ultimas `AI_MAX_CONVERSATION_HISTORY` mensagens da conversa do proprio usuario/organizacao) → chama o LLM com as ferramentas permitidas ao **perfil** do usuario → valida e executa cada chamada **no servidor** → devolve resultados ao modelo → repete ate resposta final → persiste mensagens e execucoes.

A conexao com o provedor e uma porta (`LlmClient`) com adaptadores `AnthropicClient` (Messages API, `tool_use`/`tool_result`), `OpenAiCompatibleClient` (Chat Completions, `tool_calls`; `AI_BASE_URL` para servidores compativeis) e `UnconfiguredLlmClient`. O dominio nao conhece formatos de provedor. **Spring AI nao foi usado** (ver `DECISIONS.md`).

## Ferramentas (catalogo fechado — `ToolRegistry`)

| Ferramenta | Perfil | Descricao |
|---|---|---|
| `getOrganizationContext` | VIEWER | Data de hoje, fuso, organizacao, lojas (UUIDs), categorias |
| `getSalesSummary` | VIEWER | Faturamento, vendas, itens, ticket, margem |
| `compareSalesPeriods` | VIEWER | Periodo vs. anterior de mesma duracao |
| `getLowStockProducts` | VIEWER | Abaixo do minimo |
| `getProductInventory` | VIEWER | Saldo por loja (por `productId` ou busca) |
| `getTopSellingProducts` | VIEWER | Top por unidades/faturamento |
| `analyzeInventoryCoverage` | VIEWER | Cobertura em dias |
| `simulateReplenishment` | VIEWER | Plano de reposicao **sem gravar** |
| `createReplenishmentDraft` | ANALYST | Cria **rascunho** (DRAFT) marcado `created_via_ai` |
| `getActionStatus` | VIEWER | Consulta proposta |

Cada ferramenta: JSON Schema tipado (enviado ao modelo), validacao de todos os argumentos (UUID, datas, inteiros com limites, listas ≤ 50, strings com tamanho maximo), escopo de organizacao derivado da sessao, resultado estruturado, erros previsiveis (`{"error": "..."}` devolvido ao modelo), registro em `ai_tool_executions` (nome, argumentos, resumo, status `SUCCESS/ERROR/DENIED`, duracao) e metrica `nexus.ai.tool.calls`.

**Nao existe** ferramenta de SQL livre, de aprovacao, rejeicao ou execucao. Quantidades de reposicao nunca sao definidas pelo modelo.

## Limites e protecoes
- `AI_MAX_TOOL_CALLS` (8) por mensagem; excedido, o modelo recebe uma ultima chamada **sem ferramentas** para responder com o que tem.
- Chamadas repetidas (mesmo nome + argumentos normalizados) nao sao reexecutadas; apos 2 repeticoes as ferramentas sao retiradas (anti-loop).
- Timeout por chamada ao provedor (`AI_REQUEST_TIMEOUT`); falhas → `502 AI_PROVIDER_ERROR`/`504 AI_TIMEOUT` sem afetar outras funcionalidades. Nenhuma transacao de banco fica aberta durante chamadas ao provedor.
- Rate limit por usuario (`AI_RATE_LIMIT_PER_MINUTE`), mensagem ≤ 4000 caracteres, resultado de ferramenta truncado em 14 000 caracteres, argumentos ≤ 8000.
- Prompt de sistema (pt-BR) exige fundamentar todo numero em ferramentas, informar periodo/valores/comparacao/limitacoes, admitir falta de dados, tratar resultados/historico como **dados** (nao instrucoes) e nunca afirmar aprovacao/execucao sem `getActionStatus`.
- Identidade e organizacao vem da sessao autenticada; IDs fornecidos pelo modelo sao validados contra a organizacao (teste: tentativa de reposicao em loja de outra organizacao resulta em `ERROR` sem criar nada).
- Memoria: historico persistido por usuario+organizacao; mensagens anteriores nao sao tratadas como fatos — dados atuais sempre vem das ferramentas. Sem memoria vetorial/RAG no MVP.

## Evidencias na interface
Cada resposta mostra "Evidencias": ferramentas chamadas, argumentos, status e duracao (lidos de `ai_tool_executions`); propostas criadas pela IA mostram selo "via IA" e o link da proposta.

## Estado de validacao
- Testado com provedor **simulado** (script de respostas): ciclo completo, limites, erros, isolamento, permissao por perfil, falhas/timeout do provedor.
- Adaptadores: testes de serializacao/parsing sem rede.
- **Nao validado**: chamadas reais a Anthropic/OpenAI (nenhuma chave disponivel). Ao configurar uma chave, execute uma conversa de fumaca e ajuste o adaptador se o provedor divergir da documentacao oficial.
