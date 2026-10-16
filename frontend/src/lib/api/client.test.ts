import { afterEach, describe, expect, it, vi } from 'vitest'
import { mockFetch } from '@/test/utils'
import { ApiError, api, onUnauthorized, setActiveOrg, toQuery } from './client'

afterEach(() => {
  vi.unstubAllGlobals()
  onUnauthorized(null)
  setActiveOrg(null)
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT'
})

describe('api client', () => {
  it('monta query string ignorando valores vazios', () => {
    expect(toQuery({ a: 1, b: '', c: undefined, d: null, e: false })).toBe('?a=1&e=false')
  })

  it('envia credenciais e o header da organizacao ativa em GET', async () => {
    setActiveOrg('org-1')
    const { calls } = mockFetch(() => ({ body: { ok: true } }))
    await api('/sales/summary', { params: { storeId: 's1' } })
    expect(calls[0].url).toBe('/api/v1/sales/summary?storeId=s1')
    expect(calls[0].init.credentials).toBe('include')
    expect((calls[0].init.headers as Record<string, string>)['X-Organization-Id']).toBe('org-1')
  })

  it('anexa o token CSRF em requisicoes mutaveis', async () => {
    document.cookie = 'XSRF-TOKEN=abc123'
    const { calls } = mockFetch(() => ({ body: {} }))
    await api('/actions/1/submit', { method: 'POST', body: {} })
    expect((calls[0].init.headers as Record<string, string>)['X-XSRF-TOKEN']).toBe('abc123')
  })

  it('busca o token CSRF quando ainda nao existe', async () => {
    const { calls } = mockFetch((url) => {
      if (url.endsWith('/auth/csrf')) {
        document.cookie = 'XSRF-TOKEN=fresh'
        return { body: {} }
      }
      return { body: {} }
    })
    await api('/auth/logout', { method: 'POST' })
    expect(calls[0].url).toBe('/api/v1/auth/csrf')
    expect((calls[1].init.headers as Record<string, string>)['X-XSRF-TOKEN']).toBe('fresh')
  })

  it('converte Problem Details em ApiError tipado', async () => {
    mockFetch(() => ({ status: 409, body: { code: 'INVALID_TRANSITION', detail: 'Transicao invalida' } }))
    const err = await api('/x').catch((e: unknown) => e)
    expect(err).toBeInstanceOf(ApiError)
    expect((err as ApiError).status).toBe(409)
    expect((err as ApiError).code).toBe('INVALID_TRANSITION')
    expect((err as ApiError).message).toBe('Transicao invalida')
  })

  it('notifica 401 fora das rotas de autenticacao', async () => {
    const handler = vi.fn()
    onUnauthorized(handler)
    mockFetch(() => ({ status: 401, body: { code: 'UNAUTHENTICATED', detail: 'x' } }))
    await api('/sales').catch(() => undefined)
    await api('/auth/login', { method: 'POST', body: {} }).catch(() => undefined)
    expect(handler).toHaveBeenCalledTimes(1)
  })

  it('traduz falha de rede em erro amigavel', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => { throw new TypeError('Failed to fetch') }))
    const err = (await api('/x').catch((e: unknown) => e)) as ApiError
    expect(err.code).toBe('NETWORK')
  })
})
