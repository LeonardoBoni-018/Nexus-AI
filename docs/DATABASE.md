# Banco de dados

PostgreSQL 16 (producao/compose). Migrations Flyway versionadas em `backend/src/main/resources/db/migration` (`V1__core_schema.sql`). `ddl-auto=validate`: o Hibernate nunca altera o esquema. O banco nunca e recriado automaticamente.

O esquema e escrito em SQL compativel com PostgreSQL **e** H2 (modo PostgreSQL, usado no perfil `local` e nos testes rapidos): `UUID`, `TIMESTAMP WITH TIME ZONE` (instantes em UTC), `NUMERIC(14,2)` para dinheiro (`BigDecimal`; nunca float/double), `CHECK`s. Validado tambem contra PostgreSQL 16 real (`PostgresCompatibilityTest`).

## Tabelas

| Tabela | Observacoes |
|---|---|
| `organizations` (`demo` flag), `users`, `memberships(user, org, role)` | `UNIQUE(user, org)`; papel `VIEWER/ANALYST/MANAGER/ADMIN` |
| `stores` | pertence a uma organizacao; `UNIQUE(org, name)` |
| `products` | `UNIQUE(org, sku)`; `cost/price >= 0`; `min_stock`, `target_coverage_days` (21), `lead_time_days` |
| `inventory_balances` | `UNIQUE(store, product)`, `CHECK(quantity >= 0)`, `version` (otimista) |
| `inventory_movements` | tipo `OPENING/SALE/REPLENISHMENT/ADJUSTMENT`, `quantity_delta`, `balance_after >= 0`, `idempotency_key UNIQUE`, referencia (tipo/id) |
| `sales`, `sale_items` | `sale_date` (data local) para filtros/series; `unit_cost` gravado no item para margem historica |
| `alerts` | `UNIQUE(org, dedupe_key)`, `read_at`, `resolved` |
| `conversations`, `chat_messages` | por usuario **e** organizacao |
| `ai_tool_executions` | ferramenta, argumentos (truncados), resumo do resultado (500 chars), status, duracao |
| `proposed_actions`, `action_items`, `action_approvals` | estado, `created_via_ai`, `conversation_id`, `version`; itens com estoque/media/cobertura/sugerido/aprovado |
| `audit_events` | org, usuario, tipo, entidade, detalhes (sem segredos), `request_id` |

## Indices (guiados pelas consultas)
`sales(organization_id, sale_date)` e `sales(store_id, sale_date)` (dashboard/series/comparacao); `sale_items(sale_id)`, `sale_items(product_id)`; `inventory_balances(organization_id, store_id)`; `inventory_movements(organization_id, occurred_at)` e `(product_id, occurred_at)`; `alerts(organization_id, resolved, created_at)`; `proposed_actions(organization_id, status, created_at)`; `audit_events(organization_id, created_at)`; `conversations(organization_id, user_id, created_at)`.

## Integridade de estoque
- Alteracoes de saldo somente via `InventoryService.apply` (transacao obrigatoria, lock pessimista no saldo, validacao de escopo loja/produto/organizacao, gravacao do movimento na mesma transacao).
- Invariante testada: `quantity == SUM(quantity_delta)` por loja/produto, inclusive apos execucoes concorrentes.
- Retries: `idempotency_key` unica impede movimento duplicado.

## Retencao
Sem exclusao fisica no MVP (nao ha endpoints de delete). Politica de retencao/arquivamento de auditoria e conversas fica como evolucao (ver `TASKS.md`).

## Seed demonstrativo
`DemoDataSeeder` (apenas `DEMO_SEED=true` e banco sem organizacoes): 2 organizacoes demo, 5 usuarios, 3+2 lojas, 25/8 produtos, 56 dias de vendas deterministicas (`Random(seed)`), saldo de abertura = saldo final + vendas (nunca negativo), queda de 26% na ultima semana, produto parado e itens abaixo do minimo.
