# Seguranca

## Autenticacao e sessao
- Sessao server-side com cookie **HttpOnly**, `SameSite=Lax`, `Secure` (configuravel por `COOKIE_SECURE`; obrigatorio `true` em producao com HTTPS). Nenhum token de longa duracao no JavaScript; o frontend so guarda a organizacao ativa (preferencia, nao segredo) em `localStorage`.
- **CSRF**: `CookieCsrfTokenRepository` (`XSRF-TOKEN` → header `X-XSRF-TOKEN`) em todas as mutacoes, inclusive login. Fixacao de sessao mitigada (`changeSessionId`).
- Senhas: BCrypt (custo 10); minimo de 10 caracteres no cadastro. Mensagem de login nao distingue usuario inexistente de senha errada. Limite de 10 tentativas/5 min por e-mail+IP (em memoria; ver riscos).
- Logout invalida a sessao no servidor.
- Riscos conhecidos: sessoes em memoria (perdidas em reinicio; sem escala horizontal sem sessao compartilhada); sem recuperacao de senha, MFA ou bloqueio de conta persistente.

## Autorizacao e isolamento
- Papeis `VIEWER < ANALYST < MANAGER < ADMIN` verificados **no servidor** (`OrgContext.require`).
- A organizacao ativa e validada contra as memberships a cada requisicao; recursos de outra organizacao respondem `404`, header de organizacao alheia `403` (testes automatizados).
- Toda consulta e escopada por `organization_id`; filtros de loja/produto sao validados contra a organizacao antes do uso.
- O frontend esconde acoes por perfil apenas por UX.

## IA
Ver `docs/AI_AGENT.md`: catalogo fechado de ferramentas, sem SQL livre, sem ferramentas de aprovacao/execucao, argumentos validados, identidade da sessao, limites de chamadas/tamanho/taxa, resultados tratados como dados, chave somente no backend e nunca em logs/respostas, erros de provedor sem corpo bruto.

## Aplicacao
- SQL parametrizado (`JdbcClient`/JPA); ordenacao dinamica apenas por **allowlist**; termos de busca com curingas `LIKE` escapados.
- Validacao de entrada (Bean Validation, Zod no formulario), limites de paginacao e de periodo, Problem Details sem stack trace (`server.error.include-*` desabilitado).
- Cabecalhos: `X-Content-Type-Options`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`; nginx adiciona CSP restritiva. CORS explicito (`CORS_ALLOWED_ORIGINS`), credenciais permitidas somente para essas origens.
- Actuator: apenas `health` e `info` publicos; demais endpoints bloqueados (`denyAll`). Swagger desligado por padrao fora do perfil `local`.
- Auditoria de operacoes sensiveis (criacao/edicao/envio/aprovacao/rejeicao/cancelamento/execucao/falha de acoes, interacoes de IA, login, falha de login com e-mail mascarado) na mesma transacao da operacao.
- Segredos somente por variaveis de ambiente; `.env` ignorado pelo Git; `.env.example` sem valores reais. As credenciais demo existem apenas com `DEMO_SEED=true`.
- Container do backend roda como usuario nao root.

## Pendencias de seguranca (nao implementadas)
Rate limit distribuido, MFA, recuperacao de senha, retencao/anonimizacao (LGPD), varredura automatica de dependencias (ex.: Dependabot/OWASP) no CI, headers HSTS (configurar no proxy TLS), exposicao autenticada de metricas Prometheus.
