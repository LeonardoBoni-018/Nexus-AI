# Especificacao do produto

**Nexus AI** — AI-powered Business Intelligence and Operations Platform. Publico: PMEs de varejo, gestores e operadores. Idioma da interface: pt-BR; moeda BRL; fuso `America/Sao_Paulo`.

## Proposta de valor
Transformar dados operacionais em decisoes: indicadores confiaveis, alertas por regras explicitas e um agente que consulta ferramentas reais, **mostra de onde vem cada numero** e prepara acoes que so ocorrem com autorizacao humana.

## Jornada principal
1. Consultar vendas/estoque (dashboard, vendas, estoque, analises).
2. Perguntar ao assistente em linguagem natural; a resposta traz periodo, valores, comparacao, limitacoes e as "evidencias" (ferramentas chamadas, argumentos, duracao).
3. O assistente pode criar um **rascunho** de reposicao (quantidades calculadas pelo sistema).
4. Analista envia para aprovacao; gerente aprova/rejeita (motivo obrigatorio) e executa; o estoque recebe movimentos `REPLENISHMENT`.
5. Todo evento sensivel fica em **Auditoria**.

## Perfis

| Perfil | Pode |
|---|---|
| VIEWER | Ler dados, conversar com o assistente (ferramentas somente leitura) |
| ANALYST | + criar/editar/enviar/cancelar propostas, reavaliar alertas, rascunhos via IA |
| MANAGER | + aprovar, rejeitar, executar propostas; ver auditoria |
| ADMIN | todos os privilegios da organizacao |

## Funcionalidades (MVP / P0)
- Dashboard: faturamento, vendas, ticket medio, margem estimada (so com custo em todos os itens), variacao vs. periodo anterior de mesma duracao, serie diaria atual x anterior, top produtos, baixo estoque, produtos parados, alertas, acoes recentes. Filtros: periodo, loja, categoria (na URL).
- Vendas: listagem paginada, detalhe com itens, resumo e comparacao.
- Estoque: saldos por loja, catalogo (SKU, categoria, custo, preco, minimo), movimentacoes com filtros.
- Alertas deterministicos: `LOW_STOCK`, `STOCKOUT_RISK`, `SALES_DROP`, `NO_MOVEMENT` (regras em `docs/ARCHITECTURE.md`), marcar como lido.
- Propostas de reposicao com maquina de estados, historico de decisoes e execucao idempotente.
- Assistente de IA com 10 ferramentas tipadas e historico persistido.
- Auditoria e multi-organizacao com isolamento.

## Fora do MVP (P1/P2) — nao implementado
Streaming SSE, notificacoes em tempo real, agendamento de analises, deteccao proativa de anomalias estatisticas, relatorios exportaveis, RAG, ERP/CSV, multiplos provedores simultaneos, filas, planos/cobranca, recuperacao de senha (exige envio de e-mail).

## Criterios de aceitacao
Rastreados em [`TASKS.md`](../TASKS.md) (secao "Criterios de aceitacao") com o estado real de cada um.
