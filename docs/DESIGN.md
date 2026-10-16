# Direcao de design

Skill aplicada: [`frontend-design`](../.claude/skills/frontend-design/SKILL.md) (oficial da Anthropic, copiada de `anthropics/skills`). Metodo: plano de tokens → revisao contra o briefing → implementacao → critica com capturas de tela (Playwright) em desktop, tablet e celular, nos dois temas.

## Assunto
Operacao de varejo: quem abre a loja, confere a prateleira e decide o que repor. O vocabulario visual vem do chao de loja e de expedicao, nao de um painel de BI generico.

## Plano
- **Cores** (tokens por tema): tinta `#0C1230`, papel `#EEF1F8`, ultramarino `#2638D6`, marca-texto `#FFD23F`, alerta `#A3180D`, ok `#0B6B45`. Escuro: `#080C20` / `#101733` com ultramarino claro `#6F86FF`. Amarelo so aparece onde a pessoa precisa olhar (numero de itens a repor, item ativo da navegacao).
- **Tipografia** (auto-hospedada, sem requisicoes externas): *Bricolage Grotesque* (titulos, pesos 600 a 800, numeros grandes) e *Schibsted Grotesk* (texto e tabelas). Sem caixa alta em rotulos, sem eyebrows, sem setas decorativas.
- **Estrutura**: dock lateral de icones com rotulo (barra inferior no celular) em vez de menu de lista; a pagina inicial abre com uma frase em tipografia grande que diz o essencial; indicadores como entradas de livro-razao (regra lateral), sem grade de cartoes iguais; secoes separadas por regras, nao por caixas.
- **Elemento memoravel**: a **prateleira de cobertura** — cada caixa e um dia de estoque; vermelho quando o estoque acaba antes da reposicao chegar; tracejado quando a prateleira esta vazia. Tambem esta escrito em texto (nao depende de cor).
- **Linguagem visual com funcao**: *etiquetas* de palete para alertas (fita lateral na cor da gravidade + icone + rotulo), *carimbos* para status de propostas (o carimbo "bate" quando uma acao da pessoa muda o estado), *marca-texto* para o numero que pede acao.
- **Movimento**: um unico momento orquestrado (caixas entrando na prateleira ao abrir a tela) e respostas a acoes (icones da dock reagem a hover/foco; carimbo ao aprovar/executar). `prefers-reduced-motion` desliga tudo.

## Revisao contra o padrao "gerado por IA"
Removidos: gradiente roxo sobre branco, cartoes arredondados identicos com sombra cinza, rotulos em caixa alta com pontos medios, fundo creme com acento terracota, fundo preto com verde-acido. Raios variam por papel (controles 5px, paineis 4px, etiquetas assimetricas).

## Qualidade
- Axe (WCAG 2 A/AA) sem violacoes em 10 telas nos temas claro e escuro (`frontend/e2e/a11y-responsive.spec.ts`).
- Sem rolagem horizontal em 375 px e 768 px; alvos de toque da barra inferior ≥ 44 px.
- Foco visivel (contorno de 3 px), navegacao por teclado, link "Ir para o conteudo".
- Tema: preferencia do sistema por padrao; alternancia no cabecalho persiste em `localStorage` (opcional).
