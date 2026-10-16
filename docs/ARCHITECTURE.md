# Arquitetura

Monolito modular (um deployavel) + SPA. Sem Redis/Kafka/microsservicos: nao ha necessidade concreta no MVP.

```
Browser (React SPA) --/api--> nginx (ou proxy Vite) --> Spring Boot --> PostgreSQL
                                                      \--> Provedor de LLM (HTTPS, somente do backend)
```

## Backend (`com.nexusai`)

| Modulo | Responsabilidade |
|---|---|
| `security` | Sessao HttpOnly + CSRF, `OrgContext` (usuario + organizacao ativa validada + papel) injetado nos controllers |
| `identity` | Usuarios, organizacoes, memberships, login/logout/me/register |
| `catalog` | Lojas, produtos, saldos, movimentos. `InventoryService` e o **unico** ponto que altera saldo |
| `sales` | Consultas de vendas (leitura) |
| `analytics` | Metricas, comparacao de periodos, series, top produtos, cobertura, baixo estoque, parados; dashboard |
| `alerts` | Avaliacao deterministica + agendamento (15 min) |
| `actions` | Propostas, maquina de estados, aprovacao, execucao, planejador de reposicao |
| `ai` | Orquestrador, ferramentas, adaptadores de provedor, conversas |
| `audit` | Eventos de auditoria |
| `demo` | Seed demonstrativo (somente com `DEMO_SEED=true` e banco vazio) |

Padrao: controllers finos → services → repositorios. **Escrita** usa JPA (agregados, locks, `@Version`); **leitura analitica** usa `JdbcClient` com SQL parametrizado (agregacoes eficientes, sem N+1). DTOs separados das entidades.

### Regras de negocio chave
- **Periodos**: `[from, to]` inclusivos em datas locais (`sales.sale_date`); periodo anterior = mesma quantidade de dias terminando em `from-1`. Maximo 400 dias.
- **Variacao %**: `null` quando o periodo anterior nao tem base (nunca um numero inventado). **Margem**: so quando todos os itens tem custo > 0.
- **Cobertura** = saldo / (unidades vendidas nos ultimos 28 dias / 28); `null` sem vendas.
- **Reposicao** (deterministica, `ReplenishmentPlanner`): `alvo = max(minimo, ceil(media_diaria * (cobertura_alvo + lead_time)))`, `sugerido = max(0, alvo - saldo)`.
- **Alertas**: `LOW_STOCK` (saldo < minimo; CRITICAL se 0); `STOCKOUT_RISK` (cobertura ≤ lead time e saldo ≥ minimo; CRITICAL ≤ 3 dias); `SALES_DROP` (receita 7d vs 7d anteriores ≤ -20%, CRITICAL ≤ -35%, amostra minima de 20 vendas anteriores); `NO_MOVEMENT` (estoque > 0 e sem vendas ha 30 dias). Cada alerta tem `dedupe_key`; condicoes que deixam de valer viram `resolved`; reavaliar nao duplica.
- **Estado das propostas**: `DRAFT → PENDING_APPROVAL → APPROVED → EXECUTED`; `PENDING_APPROVAL → REJECTED`; `DRAFT/PENDING/APPROVED → CANCELLED`; `APPROVED → FAILED` (falha na execucao). Estados terminais nao transitam.
- **Execucao**: transacao `REQUIRES_NEW` com lock pessimista na proposta; cada item aplica movimento com chave de idempotencia `action:{id}:product:{id}`; reexecucao devolve o estado sem reaplicar; falha marca `FAILED` em transacao separada.
- **Estoque**: saldo nunca negativo (constraint `CHECK` + validacao); saldo = soma dos movimentos (verificado por testes).

## Frontend
`features/` por dominio, `components/ui` (primitivos acessiveis), `components/layout`, `lib/api/client.ts` (cliente HTTP unico: cookies, CSRF, organizacao ativa, Problem Details → `ApiError`), `lib/auth`, `hooks`. Filtros globais na URL. Code splitting por rota; graficos (Recharts) em chunk proprio. Protecao de rota e apenas UX; a autorizacao real e do backend.

## Observabilidade
Logs com `requestId` (header `X-Request-Id` propagado e devolvido), metricas Micrometer (`nexus.ai.requests`, `nexus.ai.tool.calls{tool,status}`, `nexus.ai.request.duration`, `nexus.ai.provider.errors`) mais metricas HTTP/JVM padrao, health `liveness/readiness`. Logs tecnicos, auditoria (`audit_events`) e conversas (`chat_messages`) sao armazenamentos separados.
