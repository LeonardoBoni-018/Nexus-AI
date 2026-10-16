import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AppRoutes } from '@/app/App'
import { QueryState } from '@/components/ui/QueryState'
import { analystMe, managerMe, mockFetch, renderApp } from '@/test/utils'
import { useQuery } from '@tanstack/react-query'

afterEach(() => vi.unstubAllGlobals())

const unauth = { status: 401, body: { code: 'UNAUTHENTICATED', detail: 'Autenticacao necessaria' } }

describe('autenticacao e navegacao', () => {
  it('redireciona usuario nao autenticado para o login', async () => {
    mockFetch((url) => (url.includes('/auth/me') ? unauth : undefined))
    renderApp(<AppRoutes />, '/vendas')
    expect(await screen.findByRole('heading', { name: 'Entrar' })).toBeInTheDocument()
  })

  it('valida os campos do formulario de login antes de enviar', async () => {
    const { calls } = mockFetch((url) => (url.includes('/auth/me') ? unauth : undefined))
    renderApp(<AppRoutes />, '/login')
    await userEvent.click(await screen.findByRole('button', { name: 'Entrar' }))
    expect(await screen.findByText('Informe o e-mail')).toBeInTheDocument()
    expect(screen.getByText('Informe a senha')).toBeInTheDocument()
    expect(calls.some((c) => c.url.includes('/auth/login'))).toBe(false)
  })

  it('exibe a mensagem do servidor quando as credenciais sao invalidas', async () => {
    document.cookie = 'XSRF-TOKEN=t'
    mockFetch((url) => {
      if (url.includes('/auth/me')) return unauth
      if (url.includes('/auth/login')) return { status: 401, body: { code: 'BAD_CREDENTIALS', detail: 'E-mail ou senha invalidos' } }
      return undefined
    })
    renderApp(<AppRoutes />, '/login')
    await userEvent.type(await screen.findByLabelText('E-mail'), 'a@b.com')
    await userEvent.type(screen.getByLabelText('Senha'), 'errada')
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('E-mail ou senha invalidos')
  })
})

const detail = (status: string, next: string[]) => ({
  id: 'a1', type: 'REPLENISHMENT', status, storeId: 's1', storeName: 'Loja Centro', justification: 'teste',
  createdBy: 'u9', createdByName: 'Alice', createdViaAi: true, conversationId: null,
  createdAt: '2026-03-01T12:00:00Z', updatedAt: '2026-03-01T12:00:00Z', executedAt: null, failureReason: null,
  items: [{ productId: 'p1', sku: 'BEB-001', productName: 'Agua', currentStock: 3, averageDailySales: 5, coverageDays: 0.6, suggestedQuantity: 100, approvedQuantity: 100 }],
  approvals: [], nextStatuses: next,
})

describe('fluxo de aprovacao', () => {
  it('gerente ve aprovar/rejeitar e rejeicao exige motivo', async () => {
    document.cookie = 'XSRF-TOKEN=t'
    const { calls } = mockFetch((url, init) => {
      if (url.includes('/auth/me')) return { body: managerMe }
      if (url.includes('/actions/a1/reject') && init.method === 'POST') return { body: detail('REJECTED', []) }
      if (url.includes('/actions/a1')) return { body: detail('PENDING_APPROVAL', ['APPROVED', 'REJECTED', 'CANCELLED']) }
      if (url.includes('/alerts/unread-count')) return { body: { count: 0 } }
      return undefined
    })
    renderApp(<AppRoutes />, '/acoes/a1')
    expect(await screen.findByRole('button', { name: 'Aprovar' })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Rejeitar' }))
    const confirm = await screen.findByRole('button', { name: 'Confirmar rejeicao' })
    expect(confirm).toBeDisabled()
    await userEvent.type(screen.getByLabelText('Motivo (obrigatorio)'), 'Sem orcamento')
    await userEvent.click(confirm)
    await waitFor(() => expect(calls.some((c) => c.url.includes('/actions/a1/reject'))).toBe(true))
    const call = calls.find((c) => c.url.includes('/actions/a1/reject'))!
    expect(JSON.parse(String(call.init.body))).toEqual({ reason: 'Sem orcamento' })
  })

  it('analista nao ve controles de aprovacao e indica que aguarda um gerente', async () => {
    mockFetch((url) => {
      if (url.includes('/auth/me')) return { body: analystMe }
      if (url.includes('/actions/a1')) return { body: detail('PENDING_APPROVAL', ['APPROVED', 'REJECTED', 'CANCELLED']) }
      if (url.includes('/alerts/unread-count')) return { body: { count: 0 } }
      return undefined
    })
    renderApp(<AppRoutes />, '/acoes/a1')
    expect(await screen.findByText('Aguardando um gerente')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Aprovar' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Executar reposicao' })).not.toBeInTheDocument()
  })

  it('mostra a mensagem de erro do servidor quando a transicao falha', async () => {
    document.cookie = 'XSRF-TOKEN=t'
    mockFetch((url, init) => {
      if (url.includes('/auth/me')) return { body: managerMe }
      if (url.includes('/actions/a1/approve') && init.method === 'POST') return { status: 409, body: { code: 'INVALID_TRANSITION', detail: 'Transicao invalida: DRAFT -> APPROVED' } }
      if (url.includes('/actions/a1')) return { body: detail('PENDING_APPROVAL', ['APPROVED', 'REJECTED']) }
      if (url.includes('/alerts/unread-count')) return { body: { count: 0 } }
      return undefined
    })
    renderApp(<AppRoutes />, '/acoes/a1')
    await userEvent.click(await screen.findByRole('button', { name: 'Aprovar' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Transicao invalida')
  })
})

function Probe({ path }: { path: string }) {
  const q = useQuery({ queryKey: [path], queryFn: async () => (await fetch(path).then((r) => (r.ok ? r.json() : Promise.reject(new Error('Falhou feio'))))) as string[] })
  return <QueryState query={q} isEmpty={(d) => d.length === 0} empty={{ title: 'Vazio!' }}>{(d) => <p>{d.join(',')}</p>}</QueryState>
}

describe('QueryState', () => {
  it.each([
    ['/ok', 200, ['x', 'y'], 'x,y'],
    ['/empty', 200, [], 'Vazio!'],
    ['/err', 500, null, 'Falhou feio'],
  ])('renderiza loading e depois %s', async (path, status, body, text) => {
    mockFetch((url) => (url === path ? { status: status as number, body: body ?? {} } : undefined))
    renderApp(<Probe path={path} />)
    expect(screen.getByText('Carregando...')).toBeInTheDocument()
    expect(await screen.findByText(text as string, { exact: false })).toBeInTheDocument()
  })
})
