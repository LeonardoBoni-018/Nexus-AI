# Deploy

> Estado: artefatos de deploy (Dockerfiles, Compose, CI) **escritos mas nao executados** — o daemon Docker nao estava ativo no ambiente de desenvolvimento. Nenhum deploy em nuvem foi realizado. O jar do backend (`./mvnw package`) e o build do frontend (`npm run build`) foram gerados com sucesso localmente.

## Docker Compose (referencia)
`docker-compose.yml`: `db` (postgres:16-alpine, healthcheck, volume), `backend` (build `./backend`, depende do banco saudavel), `frontend` (nginx com SPA + proxy `/api` → backend, porta 8081). Variaveis: ver `.env.example`.

```bash
cp .env.example .env && docker compose up --build
```

## Producao — checklist
1. Banco PostgreSQL gerenciado; `DB_URL`, `DB_USER`, `DB_PASSWORD` por secret manager.
2. `DEMO_SEED=false`, `REGISTRATION_ENABLED` conforme politica, `OPENAPI_ENABLED=false`.
3. **HTTPS obrigatorio** e `COOKIE_SECURE=true`; frontend e API na mesma origem (proxy reverso) ou `CORS_ALLOWED_ORIGINS` explicito. Adicionar HSTS no proxy.
4. Sessoes sao em memoria: use 1 instancia ou configure sessao compartilhada (Spring Session) antes de escalar horizontalmente; o rate limit tambem e por instancia.
5. Provisionar o primeiro usuario/organizacao (ex.: habilitar `REGISTRATION_ENABLED` temporariamente) — nao ha usuario padrao fora do seed demo.
6. IA: `AI_PROVIDER`, `AI_MODEL`, `AI_API_KEY` (secret). Defina orcamento/alertas de custo no provedor.
7. Migrations rodam no start (Flyway). Faca backup antes de atualizar versoes.
8. Observabilidade: `/actuator/health/{liveness,readiness}` publicos; metricas Micrometer existem mas os endpoints `metrics/prometheus` estao bloqueados — exponha-os apenas em rede interna/autenticados conforme sua infra.

## Opcoes de hospedagem
Qualquer plataforma de containers (Render, Fly.io, ECS, Cloud Run) + Postgres gerenciado; o frontend pode ser servido pelo nginx da imagem fornecida. Nenhuma conta/conexao externa foi criada nesta entrega.
