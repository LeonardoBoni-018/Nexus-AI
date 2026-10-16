# API REST (`/api/v1`)

Documentacao interativa (OpenAPI/Swagger UI) disponivel em `/swagger-ui.html` quando `OPENAPI_ENABLED=true` (padrao no perfil `local`; desabilitada por padrao em producao).

## Convencoes
- **Autenticacao**: cookie de sessao `NEXUSSESSION` (HttpOnly, SameSite=Lax, `Secure` configuravel). **CSRF**: chame `GET /auth/csrf` uma vez; reenvie o cookie `XSRF-TOKEN` no header `X-XSRF-TOKEN` em POST/PATCH/PUT/DELETE.
- **Organizacao ativa**: header opcional `X-Organization-Id` (padrao: primeira organizacao do usuario). Organizacao fora das memberships → `403`.
- **Periodos**: `from`/`to` (`AAAA-MM-DD`, inclusivos, fuso America/Sao_Paulo). Padrao: ultimos 30 dias; maximo 400 dias; `to < from` → `400`.
- **Paginacao**: `page` (0-based), `size` (1–100) → `{items, page, size, totalItems, totalPages}`.
- **Erros**: Problem Details (`application/problem+json`) com `status`, `title`, `detail`, `code` (e `fields` em validacao). Codigos: `UNAUTHENTICATED`, `BAD_CREDENTIALS`, `FORBIDDEN`, `NOT_FOUND`, `VALIDATION_ERROR`, `BAD_REQUEST`, `INVALID_TRANSITION`, `NOT_EDITABLE`, `NO_REPLENISHMENT_NEEDED`, `INSUFFICIENT_STOCK`, `EXECUTION_FAILED`, `CONCURRENT_UPDATE`, `RATE_LIMITED`, `AI_NOT_CONFIGURED` (503), `AI_PROVIDER_ERROR` (502), `AI_TIMEOUT` (504). Sem stack traces.

## Endpoints

| Metodo e caminho | Perfil minimo | Descricao |
|---|---|---|
| `GET /auth/csrf` · `POST /auth/login` · `POST /auth/logout` · `GET /auth/me` · `POST /auth/register` | publico/autenticado | Login limitado a 10 tentativas/5 min por e-mail+IP; `register` so se `REGISTRATION_ENABLED` (cria organizacao e ADMIN) |
| `GET /stores` · `GET /categories` | VIEWER | Dados de filtro |
| `GET /dashboard/summary` · `/sales-series` · `/top-products` · `/stale-products` · `/alerts` · `/recent-actions` | VIEWER | Dashboard (filtros `from,to,storeId,category`) |
| `GET /sales` · `/sales/{id}` · `/sales/summary` · `/sales/comparison` | VIEWER | Vendas |
| `GET /products` · `/products/{id}` · `/inventory` · `/inventory/movements` · `/inventory/low-stock` | VIEWER | Estoque (busca, categoria, `lowOnly`, tipo de movimento) |
| `GET /analytics/coverage` | VIEWER | Cobertura de estoque (dias) |
| `GET /alerts` · `/alerts/{id}` · `/alerts/unread-count` · `PATCH /alerts/{id}/read` | VIEWER | Alertas |
| `POST /alerts/evaluate` | ANALYST | Reavalia regras agora |
| `GET /actions` · `/actions/{id}` | VIEWER | Propostas (filtro `status`, `storeId`) com itens, aprovacoes e `nextStatuses` |
| `POST /actions/replenishment` | ANALYST | Cria rascunho (`storeId`, `productIds?`, `justification?`); `422` se nada a repor |
| `PATCH /actions/{id}` | ANALYST | Edita justificativa/quantidades (0 remove item) em DRAFT/PENDING |
| `POST /actions/{id}/submit` · `/cancel` | ANALYST (cancelar de terceiros: MANAGER) | Transicoes |
| `POST /actions/{id}/approve` · `/reject` (motivo obrigatorio) · `/execute` | MANAGER | Decisao humana e execucao idempotente |
| `GET /audit-events` | MANAGER | Auditoria (filtro `eventType`) |
| `GET /assistant/status` · `POST/GET /assistant/conversations` · `GET /assistant/conversations/{id}/messages` · `POST .../messages` | VIEWER | Assistente; mensagem ≤ 4000 caracteres; resposta traz `content` e `toolCalls` (evidencias) |

Exemplo de erro:
```json
{"status":409,"title":"Conflict","detail":"Transicao invalida: DRAFT -> APPROVED","code":"INVALID_TRANSITION"}
```
