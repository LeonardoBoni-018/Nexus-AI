# Decisoes arquiteturais

| # | Decisao | Motivo / consequencia |
|---|---|---|
| 1 | **Java 17 + Spring Boot 4.0.8** (nao Java 21) | Unico JDK instalado era 17 (LTS, suportado pelo Boot 4). Initializr atual so oferece Boot ≥ 4.0. Boot 4 usa **Jackson 3** (`tools.jackson`). Migrar para 21 e trocar `java.version`. |
| 2 | Monolito modular | Sem necessidade de distribuicao; menor custo operacional |
| 3 | **JPA para escrita + JdbcClient para leitura analitica** | Agregados com lock/versionamento via JPA; consultas de dashboard em SQL parametrizado (eficientes, sem N+1, portaveis H2/PG) |
| 4 | Lombok (`@Getter`/`@NoArgsConstructor`) nas entidades | Reduz boilerplate; entidades nao expõem setters, mutacoes por metodos de dominio |
| 5 | Sessao + cookie HttpOnly + CSRF em vez de JWT; sessoes persistidas via Spring Session JDBC | SPA same-origin; sem token acessivel a JS; logout real; varias instancias e reinicios sem perder login. Custo: leitura/escrita da sessao no banco |
| 6 | Organizacao ativa por header validado | Suporta usuarios em varias organizacoes; nunca confiado sem checar memberships |
| 7 | `sales.sale_date` (DATE local) | Limites de periodo consistentes e indices simples, sem depender de conversao de fuso em SQL |
| 8 | IDs `UUID` gerados na aplicacao; SQL compativel H2/PG | Perfil `local` sem Docker; testes rapidos; validado em PG real via PostgreSQL embarcado |
| 9 | **Sem Spring AI**; adaptador proprio (`LlmClient`) para Anthropic e OpenAI-compativel | Evita acoplar o dominio a uma API ainda em evolucao e a compatibilidade com Boot 4; superficie pequena (2 adaptadores, JDK `HttpClient`). Trade-off: sem streaming no MVP |
| 10 | Quantidades de reposicao sempre deterministicas | A IA escolhe o que consultar e explica; nao calcula |
| 11 | Aprovacao/execucao fora do alcance da IA | Requisito de seguranca; catalogo fechado de ferramentas |
| 12 | Execucao em `REQUIRES_NEW` + lock pessimista + chave de idempotencia por item | Impede dupla execucao concorrente; falha vira `FAILED` persistido |
| 13 | Rejeicao exige motivo; aprovador pode ser o criador | Politica simples para o MVP; separacao de funcoes (4 olhos) e evolucao |
| 14 | Componentes de UI proprios (Tailwind) em vez de shadcn/ui | shadcn depende de CLI/Radix e adiciona peso; primitivos acessiveis pequenos atendem o MVP (`<dialog>` nativo, labels, foco) |
| 15 | Tema claro/escuro por variaveis CSS (preferencia do sistema + alternancia) | Tokens semanticos; paletas validadas por axe (WCAG AA) nos dois temas |
| 20 | Identidade visual "Expedicao" (skill `frontend-design`) | Etiquetas de alerta, carimbos de status, prateleira de cobertura e marca-texto; evita o kit padrao de cards roxos de SaaS. Ver `docs/DESIGN.md` |
| 16 | PostgreSQL embarcado (zonky) em teste em vez de Testcontainers | Docker indisponivel no ambiente; equivalente para validar SQL real |
| 17 | Alertas por regras explicitas, avaliados a cada 15 min e no seed | Sem afirmar "anomalia" sem regra/amostra minima |
| 18 | Rate limit em memoria (sessoes ja sao persistidas) | Suficiente por instancia; limite documentado |
| 19 | Skills externas: busca no catalogo do usuario nao retornou skills de Spring/React/seguranca; foi usada a skill `dataviz` (paleta de graficos validada) | Registro de transparencia |
