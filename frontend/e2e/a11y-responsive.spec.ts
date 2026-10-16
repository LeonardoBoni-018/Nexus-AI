import AxeBuilder from '@axe-core/playwright'
import { expect, test } from '@playwright/test'
import type { Page } from '@playwright/test'

const DEMO_PASSWORD = 'Demo@12345!'
const ROUTES = ['/', '/vendas', '/estoque', '/analises', '/alertas', '/assistente', '/acoes', '/auditoria', '/configuracoes']

async function signIn(page: Page) {
  await page.goto('/login')
  await page.getByLabel('E-mail').fill('gerente@aurora.demo')
  await page.getByLabel('Senha').fill(DEMO_PASSWORD)
  await page.getByRole('button', { name: 'Entrar' }).click()
  await expect(page.getByText('Dados de demonstracao')).toBeVisible()
}

for (const scheme of ['light', 'dark'] as const) {
  test(`acessibilidade (axe, WCAG 2 A/AA) sem violacoes no tema ${scheme}`, async ({ browser }) => {
    const ctx = await browser.newContext({ colorScheme: scheme, viewport: { width: 1280, height: 900 } })
    const page = await ctx.newPage()
    await page.goto('/login')
    expect((await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa']).analyze()).violations).toEqual([])
    await signIn(page)
    for (const route of ROUTES) {
      await page.goto(route)
      await page.waitForLoadState('networkidle')
      await page.waitForTimeout(700) // fim das animacoes de entrada
      const { violations } = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa']).analyze()
      expect(violations.map((v) => `${route}: ${v.id} (${v.nodes.length}) ${v.nodes[0]?.html.slice(0, 120)}`)).toEqual([])
    }
    await ctx.close()
  })
}

for (const [name, width, height] of [['celular', 375, 812], ['tablet', 768, 1024]] as const) {
  test(`layout ${name} (${width}px) sem rolagem horizontal e com navegacao acessivel`, async ({ browser }) => {
    const ctx = await browser.newContext({ viewport: { width, height } })
    const page = await ctx.newPage()
    await signIn(page)
    for (const route of ROUTES) {
      await page.goto(route)
      await page.waitForLoadState('networkidle')
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
      expect(overflow, `${route} estoura a largura em ${overflow}px`).toBeLessThanOrEqual(1)
    }
    // Barra inferior visivel e navegavel por toque
    const nav = page.getByRole('navigation', { name: 'Principal (celular)' })
    await expect(nav).toBeVisible()
    await nav.getByRole('link', { name: 'Estoque', exact: true }).click()
    await expect(page).toHaveURL(/\/estoque/)
    for (const link of await nav.getByRole('link').all()) {
      const box = await link.boundingBox()
      expect(box && box.height >= 44, 'alvo de toque >= 44px').toBeTruthy()
    }
    await ctx.close()
  })
}

test('alternar tema persiste e aplica o atributo data-theme', async ({ page }) => {
  await signIn(page)
  await page.getByRole('button', { name: /Ativar tema escuro|Ativar tema claro/ }).click()
  const theme = await page.evaluate(() => document.documentElement.getAttribute('data-theme'))
  expect(['dark', 'light']).toContain(theme)
  await page.reload()
  expect(await page.evaluate(() => document.documentElement.getAttribute('data-theme'))).toBe(theme)
})
