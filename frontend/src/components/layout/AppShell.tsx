import { useQuery } from '@tanstack/react-query'
import clsx from 'clsx'
import {
  BarChart3, Bell, Bot, ClipboardCheck, History, LayoutDashboard, LogOut, Moon, Package, Receipt, Settings, Sun,
} from 'lucide-react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { api } from '@/lib/api/client'
import { useAuth } from '@/lib/auth/auth-context'
import type { Role } from '@/lib/api/types'
import { useTheme } from '@/lib/theme'
import { Badge } from '@/components/ui/primitives'

interface NavItem { to: string; label: string; icon: typeof Bell; min?: Role }

const NAV: NavItem[] = [
  { to: '/', label: 'Visao geral', icon: LayoutDashboard },
  { to: '/vendas', label: 'Vendas', icon: Receipt },
  { to: '/estoque', label: 'Estoque', icon: Package },
  { to: '/analises', label: 'Analises', icon: BarChart3 },
  { to: '/alertas', label: 'Alertas', icon: Bell },
  { to: '/assistente', label: 'Assistente', icon: Bot },
  { to: '/acoes', label: 'Acoes', icon: ClipboardCheck },
  { to: '/auditoria', label: 'Auditoria', icon: History, min: 'MANAGER' },
  { to: '/configuracoes', label: 'Ajustes', icon: Settings },
]

export function AppShell() {
  const { me, activeOrg, switchOrg, logout, can } = useAuth()
  const { pref, set } = useTheme()
  const location = useLocation()
  const unread = useQuery({
    queryKey: ['/alerts/unread-count'],
    queryFn: () => api<{ count: number }>('/alerts/unread-count'),
    refetchInterval: 60_000,
  })
  const count = unread.data?.count ?? 0
  const dark = pref === 'dark' || (pref === 'system' && typeof matchMedia === 'function' && matchMedia('(prefers-color-scheme: dark)').matches)

  const items = NAV.filter((n) => !n.min || can(n.min))
  const link = (n: NavItem, mobile: boolean) => (
    <NavLink
      key={n.to}
      to={n.to}
      end={n.to === '/'}
      aria-label={n.to === '/alertas' && count > 0 ? `${n.label}, ${count} nao lidos` : n.label}
      className={({ isActive }) =>
        clsx(
          'group relative flex shrink-0 flex-col items-center justify-center gap-1 rounded-[6px] text-xs font-semibold transition-colors',
          mobile ? 'min-h-14 min-w-[4.5rem] px-2' : 'min-h-[4.25rem] w-full px-1',
          isActive ? 'text-rail-ink-strong' : 'text-[var(--rail-ink)] hover:text-rail-ink-strong',
        )
      }
    >
      {({ isActive }) => (
        <>
          <span
            className={clsx(
              'relative flex size-9 items-center justify-center rounded-[6px] transition-colors',
              isActive ? 'bg-mark text-on-mark' : 'group-hover:bg-white/10',
            )}
          >
            <n.icon className="nav-icon size-5" aria-hidden />
            {n.to === '/alertas' && count > 0 && (
              <span className="absolute -right-1.5 -top-1.5 min-w-4 rounded-[4px] bg-bad px-1 text-[10px] font-bold leading-4 text-canvas" aria-hidden>{count}</span>
            )}
          </span>
          <span>{n.label}</span>
        </>
      )}
    </NavLink>
  )

  return (
    <div className="flex min-h-full">
      <a href="#conteudo" className="sr-only focus:not-sr-only focus:fixed focus:left-2 focus:top-2 focus:z-50 focus:rounded focus:bg-surface focus:p-2">Ir para o conteudo</a>

      <aside className="sticky top-0 hidden h-screen w-[84px] shrink-0 flex-col items-stretch gap-1 bg-rail px-2 py-4 lg:flex">
        <img src="/favicon.svg" alt="Nexus AI" className="mx-auto mb-3 size-10" />
        <nav aria-label="Principal" className="flex flex-1 flex-col gap-1 overflow-y-auto">{items.map((n) => link(n, false))}</nav>
        <button
          onClick={() => void logout()}
          className="flex min-h-14 flex-col items-center justify-center gap-1 rounded-[6px] text-xs font-semibold text-[var(--rail-ink)] hover:bg-white/10 hover:text-rail-ink-strong"
        >
          <LogOut className="nav-icon size-5" aria-hidden /> Sair
        </button>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col pb-20 lg:pb-0">
        <header className="sticky top-0 z-30 flex min-h-14 items-center gap-3 border-b border-line bg-surface/95 px-4 backdrop-blur sm:px-6">
          <img src="/favicon.svg" alt="" className="size-7 lg:hidden" />
          <div className="min-w-0 flex-1">
            {me && me.organizations.length > 1 ? (
              <select
                aria-label="Organizacao"
                value={activeOrg?.organizationId}
                onChange={(e) => switchOrg(e.target.value)}
                className="max-w-full rounded-[5px] border border-ink-3/50 bg-surface px-2 py-1.5 font-display text-base font-bold"
              >
                {me.organizations.map((o) => <option key={o.organizationId} value={o.organizationId}>{o.organizationName}</option>)}
              </select>
            ) : (
              <p className="truncate font-display text-base font-bold">{activeOrg?.organizationName}</p>
            )}
          </div>
          {activeOrg?.demo && <Badge tone="warn">Dados de demonstracao</Badge>}
          <button
            onClick={() => set(dark ? 'light' : 'dark')}
            aria-label={dark ? 'Ativar tema claro' : 'Ativar tema escuro'}
            className="rounded-[6px] p-2 text-ink-2 hover:bg-brand-50"
          >
            {dark ? <Sun className="size-5" aria-hidden /> : <Moon className="size-5" aria-hidden />}
          </button>
          <div className="hidden text-right sm:block">
            <p className="text-sm font-semibold leading-tight">{me?.name}</p>
            <p className="text-xs text-ink-3">{activeOrg?.role}</p>
          </div>
          <button className="rounded-[6px] p-2 text-ink-2 hover:bg-brand-50 lg:hidden" aria-label="Sair" onClick={() => void logout()}>
            <LogOut className="size-5" aria-hidden />
          </button>
        </header>
        <main id="conteudo" key={location.pathname} className="mx-auto w-full max-w-[1280px] flex-1 px-4 py-6 sm:px-8">
          <Outlet />
        </main>
      </div>

      <nav aria-label="Principal (celular)" className="fixed inset-x-0 bottom-0 z-40 flex gap-1 overflow-x-auto bg-rail px-2 py-1 lg:hidden">
        {items.map((n) => link(n, true))}
      </nav>
    </div>
  )
}
