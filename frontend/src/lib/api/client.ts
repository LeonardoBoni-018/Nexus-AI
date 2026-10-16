export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fields?: Record<string, string>
  constructor(status: number, code: string, message: string, fields?: Record<string, string>) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.fields = fields
  }
}

const ORG_KEY = 'nexus.activeOrg'

// Preferencia de conveniencia (nao e segredo): organizacao ativa escolhida pelo usuario.
export function getActiveOrg(): string | null {
  try {
    return localStorage.getItem(ORG_KEY)
  } catch {
    return null
  }
}
export function setActiveOrg(id: string | null): void {
  try {
    if (id) localStorage.setItem(ORG_KEY, id)
    else localStorage.removeItem(ORG_KEY)
  } catch {
    /* armazenamento indisponivel: segue sem persistir */
  }
}

function readCookie(name: string): string | null {
  const m = document.cookie.split('; ').find((c) => c.startsWith(name + '='))
  return m ? decodeURIComponent(m.slice(name.length + 1)) : null
}

let unauthorizedHandler: (() => void) | null = null
export function onUnauthorized(handler: (() => void) | null): void {
  unauthorizedHandler = handler
}

async function ensureCsrf(): Promise<string | null> {
  const existing = readCookie('XSRF-TOKEN')
  if (existing) return existing
  await fetch('/api/v1/auth/csrf', { credentials: 'include' })
  return readCookie('XSRF-TOKEN')
}

type Query = Record<string, string | number | boolean | null | undefined>

export function toQuery(params?: Query): string {
  if (!params) return ''
  const sp = new URLSearchParams()
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== null && v !== '') sp.set(k, String(v))
  }
  const s = sp.toString()
  return s ? `?${s}` : ''
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PATCH' | 'PUT' | 'DELETE'
  body?: unknown
  params?: Query
  signal?: AbortSignal
}

/** Cliente HTTP unico: cookies de sessao, CSRF, organizacao ativa e erros Problem Details tipados. */
export async function api<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  const method = opts.method ?? 'GET'
  const headers: Record<string, string> = { Accept: 'application/json' }
  const org = getActiveOrg()
  if (org) headers['X-Organization-Id'] = org
  if (opts.body !== undefined) headers['Content-Type'] = 'application/json'
  if (method !== 'GET') {
    const token = await ensureCsrf()
    if (token) headers['X-XSRF-TOKEN'] = token
  }
  let res: Response
  try {
    res = await fetch(`/api/v1${path}${toQuery(opts.params)}`, {
      method,
      headers,
      credentials: 'include',
      body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined,
      signal: opts.signal,
    })
  } catch (e) {
    if (e instanceof DOMException && e.name === 'AbortError') throw e
    throw new ApiError(0, 'NETWORK', 'Nao foi possivel conectar ao servidor. Verifique sua conexao.')
  }
  if (res.status === 204) return undefined as T
  const text = await res.text()
  const data: unknown = text ? safeJson(text) : undefined
  if (!res.ok) {
    const p = (data ?? {}) as { code?: string; detail?: string; fields?: Record<string, string> }
    if (res.status === 401 && !path.startsWith('/auth/')) unauthorizedHandler?.()
    throw new ApiError(res.status, p.code ?? 'ERROR', p.detail ?? `Erro ${res.status}`, p.fields)
  }
  return data as T
}

function safeJson(text: string): unknown {
  try {
    return JSON.parse(text)
  } catch {
    return undefined
  }
}

export function errorMessage(e: unknown): string {
  if (e instanceof ApiError) return e.message
  if (e instanceof Error) return e.message
  return 'Erro inesperado'
}
