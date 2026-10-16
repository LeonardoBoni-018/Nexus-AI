# Testes

## Como executar
```bash
cd backend  && ./mvnw verify
cd frontend && npm run lint && npm run typecheck && npm test && npm run build
```
Nenhuma chamada de IA paga ocorre na suite: o provedor e simulado (`ScriptedLlm`) ou ausente (`UnconfiguredLlmClient`).

## Resultados reais da ultima execucao (2026-10-16, Windows 11, Java 17.0.12, Node 24)
- **Backend**: `./mvnw verify` → **39 testes, 0 falhas**, `BUILD SUCCESS`.
- **Frontend**: Vitest → **21 testes, 0 falhas**; `tsc -b` (strict) sem erros; `vite build` ok; `oxlint` com 4 avisos de Fast Refresh (apenas DX em desenvolvimento).
- **Verificacao manual no navegador** (backend local + Vite): login, dashboard com dados reais, criacao → envio → aprovacao → execucao de proposta e consulta de auditoria funcionaram.

## Backend (JUnit 5 / MockMvc / Spring Boot Test)
| Classe | Cobre |
|---|---|
| `AuthAndIsolationTest` (9) | 401/403, CSRF obrigatorio, login invalido, sessao, header de organizacao alheia → 403, recursos de outra organizacao → 404, numeros por organizacao, periodo invalido, VIEWER sem escrita |
| `ActionFlowTest` (4) | Fluxo completo draft→submit→approve→execute; transicoes invalidas (409); analista sem aprovar (403); rejeicao exige motivo e e terminal; isolamento entre organizacoes; **6 execucoes concorrentes aplicam o estoque uma unica vez**; saldo = soma dos movimentos; eventos de auditoria |
| `ReplenishmentAndAnalyticsTest` (6) | Invariantes do seed (saldo×movimentos, total×itens), periodos adjacentes e soma da serie = faturamento, variacao nula sem base, formula de reposicao deterministica, alertas gerados e reavaliacao idempotente |
| `DomainRulesTest` (5) | Maquina de estados, `DateRange`, `RateLimiter` |
| `AiAgentTest` (9) | Ciclo de ferramentas e rascunho (nunca alem de DRAFT), tentativa de acesso a outra organizacao, VIEWER sem ferramenta de escrita (DENIED), argumentos invalidos/ferramenta desconhecida/JSON invalido, anti-loop, orcamento de chamadas, falha (502) e timeout (504) do provedor, conversa de outro usuario → 404, mensagem longa → 400 |
| `AiNotConfiguredTest` (1) | Sem credenciais: 503 `AI_NOT_CONFIGURED`, restante funcionando |
| `ProviderAdaptersTest` (4) | Corpo/parsing Anthropic e OpenAI, ausencia da chave no corpo, respostas malformadas |
| `PostgresCompatibilityTest` (1) | Flyway + seed + consultas + fluxo de acao em **PostgreSQL 16 real** (binarios embarcados `io.zonky`, sem Docker) |

Os demais testes rodam em H2 (modo PostgreSQL) com banco em memoria unico por contexto; `ActionFlowTest` usa `@DirtiesContext` para nao poluir os demais.

## Frontend (Vitest + Testing Library)
Formatacao (BRL, %, datas, fuso), cliente HTTP (cookies, CSRF, organizacao, Problem Details, 401, rede), roteamento protegido, validacao e erro do login, fluxo de aprovacao por perfil (gerente x analista, rejeicao com motivo, erro de transicao), estados loading/vazio/erro/sucesso (`QueryState`).

## E2E (Playwright) — `frontend/e2e`
`npm run e2e` (requer `npx playwright install chromium` uma vez). O Playwright sobe: um **provedor de LLM simulado** (`e2e/mock-llm.mjs`, formato OpenAI), o **backend real** (perfil local, H2 em memoria, seed demo, `AI_PROVIDER=openai` apontando para o mock — exercita o adaptador real) e o Vite. Resultado da ultima execucao: **3 testes passando (~31 s)**:
1. Credenciais invalidas exibem erro; rota protegida redireciona ao login.
2. Fluxo completo: login (analista) → dashboard com dados reais → busca no catalogo → assistente cria rascunho via ferramentas (evidencias visiveis) → analista envia, nao aprova → login (gerente, outro contexto) aprova e executa → auditoria mostra CREATED/SUBMITTED/APPROVED/EXECUTED/AI_INTERACTION → movimentacao de reposicao no estoque.
3. Isolamento: usuario de outra organizacao nao ve a proposta.

## Lacunas conhecidas
- Testcontainers nao usado (sem Docker no ambiente); substituido por PostgreSQL embarcado.
- Sem testes de carga/performance, sem teste visual automatizado, sem testes reais contra provedores de IA.
- Docker/Compose e CI (GitHub Actions) escritos mas **nao executados** neste ambiente.
