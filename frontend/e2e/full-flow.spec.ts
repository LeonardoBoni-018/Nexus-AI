import { expect, test } from '@playwright/test'
import type { Browser, Page } from '@playwright/test'

// Senha dos usuarios demo gerados pelo seed local (DEMO_SEED), documentada no README.
const DEMO_PASSWORD = 'Demo@12345!'

async function login(browser: Browser, email: string): Promise<Page> {
  const page = await (await browser.newContext()).newPage()
  await page.goto('/login')
  await page.getByLabel('E-mail').fill(email)
  await page.getByLabel('Senha').fill(DEMO_PASSWORD)
  await page.getByRole('button', { name: 'Entrar' }).click()
  await expect(page.getByRole('heading', { name: 'Visao geral' })).toBeVisible()
  return page
}

test('credenciais invalidas exibem erro e rota protegida redireciona ao login', async ({ page }) => {
  await page.goto('/vendas')
  await expect(page.getByRole('heading', { name: 'Entrar' })).toBeVisible()
  await page.getByLabel('E-mail').fill('analista@aurora.demo')
  await page.getByLabel('Senha').fill('senha-errada')
  await page.getByRole('button', { name: 'Entrar' }).click()
  await expect(page.getByRole('alert')).toContainText('E-mail ou senha invalidos')
})

test('fluxo completo: dashboard, assistente, proposta, aprovacao, execucao e auditoria', async ({ browser }) => {
  // 1) Analista: dashboard com dados reais
  const analyst = await login(browser, 'analista@aurora.demo')
  await expect(analyst.getByText('Dados de demonstracao')).toBeVisible()
  await expect(analyst.getByText('Faturamento', { exact: true }).first()).toBeVisible()
  await expect(analyst.getByText(/R\$\s?[\d.]+,\d{2}/).first()).toBeVisible()
  await expect(analyst.getByRole('heading', { name: 'Alertas operacionais' })).toBeVisible()
  await expect(analyst.getByRole('link', { name: 'Auditoria', exact: true })).toHaveCount(0) // papel sem acesso

  // 2) Consulta de produtos
  await analyst.getByRole('link', { name: 'Estoque', exact: true }).click()
  await analyst.getByRole('tab', { name: 'Catalogo' }).click()
  await analyst.getByLabel('Buscar').fill('agua')
  await expect(analyst.getByRole('cell', { name: /Agua Mineral 500ml/ })).toBeVisible()

  // 3) Assistente (provedor simulado) cria rascunho via ferramentas reais
  await analyst.getByRole('link', { name: 'Assistente de IA' }).click()
  await expect(analyst.getByText('nao esta configurada')).toHaveCount(0)
  await analyst.getByLabel('Mensagem').fill('Analise o estoque da Loja Centro e prepare uma proposta de reposicao.')
  await analyst.getByRole('button', { name: 'Enviar' }).click()
  await expect(analyst.getByText(/Criei o rascunho/)).toBeVisible({ timeout: 30_000 })
  await expect(analyst.getByText('Evidencias: 2 consulta(s) a ferramentas')).toBeVisible()
  await analyst.getByText('Evidencias: 2 consulta(s) a ferramentas').click()
  await expect(analyst.getByText('Rascunho de reposicao')).toBeVisible()
  await analyst.getByRole('link', { name: /Abrir proposta criada/ }).click()

  // 4) Proposta e rascunho da IA; analista envia para aprovacao mas nao aprova
  await expect(analyst.getByText('Rascunho', { exact: true }).first()).toBeVisible()
  await expect(analyst.getByText(/rascunho gerado pela IA/)).toBeVisible()
  const actionUrl = analyst.url()
  await analyst.getByRole('button', { name: 'Enviar para aprovacao' }).click()
  await expect(analyst.getByText('Aguardando aprovacao').first()).toBeVisible()
  await expect(analyst.getByRole('button', { name: 'Aprovar' })).toHaveCount(0)
  await expect(analyst.getByText('Aguardando um gerente')).toBeVisible()

  // 5) Gerente aprova e executa
  const manager = await login(browser, 'gerente@aurora.demo')
  await manager.goto(actionUrl)
  await manager.getByRole('button', { name: 'Aprovar' }).click()
  await expect(manager.getByText('Aprovada', { exact: true }).first()).toBeVisible()
  await manager.getByRole('button', { name: 'Executar reposicao' }).click()
  await expect(manager.getByText('Executada', { exact: true }).first()).toBeVisible()
  await expect(manager.getByText(/movimentos registrados no estoque/)).toBeVisible()
  await expect(manager.getByRole('button', { name: 'Executar reposicao' })).toHaveCount(0)

  // 6) Auditoria registra o ciclo completo
  await manager.getByRole('link', { name: 'Auditoria', exact: true }).click()
  for (const ev of ['ACTION_CREATED', 'ACTION_SUBMITTED', 'ACTION_APPROVED', 'ACTION_EXECUTED', 'AI_INTERACTION']) {
    await expect(manager.getByRole('table').getByText(ev, { exact: true }).first()).toBeVisible()
  }

  // 7) Movimentacao de reposicao aparece no estoque
  await manager.getByRole('link', { name: 'Estoque', exact: true }).click()
  await manager.getByRole('tab', { name: 'Movimentacoes' }).click()
  await manager.getByLabel('Tipo').selectOption('REPLENISHMENT')
  await expect(manager.getByRole('cell', { name: 'Reposicao' }).first()).toBeVisible()
})

test('isolamento: usuario de outra organizacao nao ve a proposta', async ({ browser }) => {
  const other = await login(browser, 'admin@casaecia.demo')
  await expect(other.getByText('Casa & Cia (DEMO)').first()).toBeVisible()
  await other.getByRole('link', { name: 'Acoes e aprovacoes' }).click()
  await expect(other.getByText('Nenhuma proposta')).toBeVisible()
})
