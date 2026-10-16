# Nexus AI

Plataforma SaaS de **inteligencia operacional** para varejo: dashboards de vendas e estoque, alertas deterministicos e um **agente de IA com tool calling** que consulta dados reais, mostra as evidencias e prepara propostas de reposicao — que so sao executadas apos **aprovacao humana**, com trilha de auditoria completa.

- Backend: Java 17, Spring Boot 4.0.8, Spring Security (sessao + CSRF), JPA + JDBC, Flyway, PostgreSQL 16
- Frontend: React 19, TypeScript (strict), Vite, Tailwind CSS 4, TanStack Query, React Router, React Hook Form + Zod, Recharts
- IA: adaptadores isolados para Anthropic (Messages API) e OpenAI/compativeis, configurados por variaveis de ambiente
- Documentacao: [`docs/`](docs) · Backlog e status: [`TASKS.md`](TASKS.md)

## Executar localmente

### Opcao A — sem Docker (H2 em modo PostgreSQL, dados demo automaticos)

Requisitos: Java 17+, Node 22+.

```bash
# terminal 1 - backend em http://localhost:8080 (perfil local: H2 em ./backend/data, seed demo, Swagger em /swagger-ui.html)
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# terminal 2 - frontend em http://localhost:5173 (proxy /api -> :8080)
cd frontend
npm install
npm run dev
```

### Opcao B — Docker Compose (PostgreSQL + backend + nginx)

```bash
cp .env.example .env      # defina DB_PASSWORD
docker compose up --build # http://localhost:8081
docker compose down       # (-v apaga o volume do banco)
```

> Observacao: os Dockerfiles e o `docker-compose.yml` **ainda nao foram executados** (o daemon Docker nao estava disponivel no ambiente de desenvolvimento). O backend foi validado contra PostgreSQL 16 real via binarios embarcados (ver `docs/TESTING.md`).

### Usuarios de demonstracao (somente quando `DEMO_SEED=true`)

Senha de todos: `Demo@12345!` — apenas para ambientes locais/demo.

| E-mail | Organizacao | Perfil |
|---|---|---|
| `admin@aurora.demo` | Mercado Aurora (DEMO) | ADMIN |
| `gerente@aurora.demo` | Mercado Aurora (DEMO) | MANAGER (aprova/executa) |
| `analista@aurora.demo` | Mercado Aurora (DEMO) | ANALYST (cria/envia propostas) |
| `leitor@aurora.demo` | Mercado Aurora (DEMO) | VIEWER |
| `admin@casaecia.demo` | Casa & Cia (DEMO) | ADMIN (outra organizacao, para testar isolamento) |

Os dados (≈ 8 semanas de vendas, saldos e movimentos consistentes, cenarios de baixo estoque, risco de ruptura, queda de vendas na ultima semana e produto parado) sao sinteticos e identificados como demonstracao na interface.

## Configurar o provedor de IA

Sem credenciais a aplicacao sobe normalmente e o assistente informa que precisa ser configurado. Para habilitar (no ambiente do backend ou no `.env` do compose):

```bash
AI_PROVIDER=anthropic        # ou: openai (tambem serve para servidores compativeis via AI_BASE_URL)
AI_MODEL=<id do modelo>
AI_API_KEY=<sua chave>
```

Demais variaveis: `AI_BASE_URL`, `AI_MAX_TOOL_CALLS` (8), `AI_REQUEST_TIMEOUT` (60s), `AI_MAX_OUTPUT_TOKENS` (2048), `AI_MAX_CONVERSATION_HISTORY` (20), `AI_RATE_LIMIT_PER_MINUTE` (20). A chave nunca e exposta pela API nem registrada em logs. **As chamadas reais aos provedores ainda nao foram validadas** (nao havia chave); os adaptadores sao cobertos por testes de serializacao/parsing. Ver [`docs/AI_AGENT.md`](docs/AI_AGENT.md).

## Testes

```bash
cd backend  && ./mvnw verify          # JUnit 5 (inclui PostgreSQL 16 embarcado; sem Docker; nenhuma chamada paga de IA)
cd frontend && npm run lint && npm run typecheck && npm test && npm run build
```

## Estrutura

```
backend/   Spring Boot (monolito modular: identity, security, catalog, sales, analytics, alerts, actions, ai, audit, demo)
frontend/  React + Vite (features/, components/, lib/, hooks/)
docs/      Especificacao, arquitetura, banco, API, agente de IA, seguranca, testes, deploy, decisoes
.github/   CI (backend, frontend, build das imagens)
```

## Variaveis de ambiente

Veja [`.env.example`](.env.example). Principais: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `COOKIE_SECURE`, `CORS_ALLOWED_ORIGINS`, `DEMO_SEED`, `REGISTRATION_ENABLED`, `OPENAPI_ENABLED`, `AI_*`.

## Diagnostico rapido

- *401 no frontend em dev*: confirme que o backend esta em `:8080` (o Vite faz proxy de `/api`).
- *Login funciona mas sessao cai em HTTP*: `COOKIE_SECURE` deve ser `false` fora de HTTPS.
- *Assistente retorna 503 `AI_NOT_CONFIGURED`*: defina as variaveis `AI_*`.
- *Recomecar com dados demo limpos (perfil local)*: apague `backend/data/`.
