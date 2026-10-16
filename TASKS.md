# TASKS

Legenda: ✅ concluido e verificado · 🟡 parcial · ⏳ pendente · ⛔ bloqueado (depende de acao externa)

## Fases
| Fase | Estado |
|---|---|
| A. Inspecao e planejamento | ✅ |
| B. Estrutura e ambiente local | ✅ (perfil local sem Docker verificado) · 🟡 Docker Compose escrito, **nao executado** |
| C. Modelo de dados e backend | ✅ |
| D. Frontend e experiencia analitica | ✅ |
| E. IA e ferramentas | ✅ com provedor simulado · ⛔ validacao com provedor real (precisa de `AI_API_KEY`) |
| F. Acoes, aprovacao e auditoria | ✅ |
| G. Seguranca, testes, robustez | ✅ (backend 39, frontend 21, E2E 3) |
| H. Deploy, docs, validacao final | 🟡 docs ✅ · deploy em nuvem ⏳ · Docker/CI nao executados |

## Criterios de aceitacao (secao 19 do prompt)
1. Backend e frontend compilam — ✅ (`mvnw verify`, `tsc -b`, `vite build`)
2. Banco e migrations inicializam — ✅ (H2 e PostgreSQL 16 embarcado; **nao** no container `postgres:16-alpine`)
3. Ambiente demo executavel seguindo o README — 🟡 opcao A verificada; opcao B (Compose) nao executada
4. Login e autorizacao — ✅
5. Isolamento entre organizacoes — ✅ (testes)
6. Dashboard/consultas com dados persistidos — ✅
7. Filtros e intervalos consistentes — ✅ (testes de periodos/series)
8. Assistente chama ferramentas reais autorizadas — ✅ (provedor simulado)
9. Assistente analisa e apresenta resultados verificaveis — 🟡 evidencias exibidas; qualidade das respostas com LLM real nao avaliada
10. Proposta de reposicao persistida — ✅
11. Fluxo de aprovacao controlado — ✅
12. Execucao registra movimentos consistentes — ✅
13. Duplicidade impedida/idempotente — ✅ (teste de concorrencia)
14. Auditoria das operacoes criticas — ✅
15. Falha do provedor de IA nao derruba o resto — ✅
16. Testes criticos automatizados e executados — ✅ incluindo E2E Playwright (3)
17. Sem erros de compilacao ignorados — ✅
18. Documentacao corresponde ao comportamento — ✅ (revisada ao final)
19. Interface em desktop/tablet/celular — 🟡 layout responsivo implementado (menu off-canvas, tabelas com rolagem); verificado visualmente apenas em viewport ~800 px
20. Sem credenciais reais no repositorio — ✅ (somente senha demo documentada)

## Pendentes
- ⛔ Configurar `AI_PROVIDER/AI_MODEL/AI_API_KEY` e fazer teste de fumaca real do assistente.
- ⏳ Executar `docker compose up --build` e o workflow de CI (Docker Desktop precisa estar ativo; CI precisa de remoto GitHub).
- ⏳ Verificacao visual em mobile (375 px) e tablet; auditoria de acessibilidade com leitor de tela.
- ⏳ Tema escuro; avisos `react-refresh/only-export-components` (DX).
- ⏳ P1: streaming SSE, notificacoes em tempo real, exportacao de relatorios, RAG, deteccao proativa de anomalias, recuperacao de senha.
- ⏳ Seguranca: sessoes/rate limit distribuidos, MFA, varredura de dependencias, HSTS no proxy, metricas autenticadas.
- ⏳ Separacao de funcoes na aprovacao (4 olhos) como politica configuravel.

## Concluido (resumo)
Backend modular (identity, security, catalog, sales, analytics, alerts, actions, ai, audit, demo), migrations Flyway, seed demo consistente, API REST com Problem Details, agente com 10 ferramentas, adaptadores Anthropic/OpenAI, frontend completo (10 telas), testes automatizados, documentacao, Dockerfiles/Compose/CI.
