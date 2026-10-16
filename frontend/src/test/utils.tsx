import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import type { ReactElement } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { vi } from 'vitest'
import { AuthProvider } from '@/lib/auth/AuthProvider'

export type Handler = (url: string, init: RequestInit) => { status?: number; body?: unknown } | undefined

/** Substitui fetch por um roteador de respostas simuladas e registra as chamadas. */
export function mockFetch(handler: Handler) {
  const calls: { url: string; init: RequestInit }[] = []
  const fn = vi.fn(async (input: RequestInfo | URL, init: RequestInit = {}) => {
    const url = String(input)
    calls.push({ url, init })
    const r = handler(url, init) ?? { status: 404, body: { code: 'NOT_FOUND', detail: 'nao mapeado: ' + url } }
    const status = r.status ?? 200
    return new Response(r.body === undefined ? null : JSON.stringify(r.body), { status, headers: { 'Content-Type': 'application/json' } })
  })
  vi.stubGlobal('fetch', fn)
  return { calls, fn }
}

export function renderApp(ui: ReactElement, route = '/') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <AuthProvider>
        <MemoryRouter initialEntries={[route]}>{ui}</MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>,
  )
}

export const managerMe = {
  userId: 'u1',
  name: 'Gabriel Gerente',
  email: 'gerente@aurora.demo',
  organizations: [{ organizationId: 'o1', organizationName: 'Mercado Aurora (DEMO)', demo: true, role: 'MANAGER' }],
}
export const analystMe = { ...managerMe, organizations: [{ ...managerMe.organizations[0], role: 'ANALYST' }] }
