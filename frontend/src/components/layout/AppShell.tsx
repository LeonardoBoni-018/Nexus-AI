import { useQuery } from '@tanstack/react-query'
import clsx from 'clsx'
import {
  BarChart3, Bell, Bot, ClipboardCheck, History, LayoutDashboard, LogOut, Menu, Package, Receipt, Settings, X,
} from 'lucide-react'
import { useState } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { api } from '@/lib/api/client'
import { useAuth } from '@/lib/auth/AuthContext'
import type { Role } from '@/lib/api/types'
import { Badge } from '@/components/ui/primitives'

interface NavItem { to: string; label: string; icon: typeof Bell; min?: Role }

const NAV: NavItem[] = [
  { to: '/', label: 'Visao geral', icon: LayoutDashboard },
  { to: '/vendas', label: 'Vendas', icon: Receipt },
  { to: '/estoque', label: 'Estoque', icon: Package },
  { to: '/analises', label: 'Analises', icon: BarChart3 },
  { to: '/alertas', label: 'Alertas', icon: Bell },
  { to: '/assistente', label: 'Assistente de IA', icon: Bot },
  { to: '/acoes', label: 'Acoes e aprovacoes', icon: ClipboardCheck },
  { to: '/auditoria', label: 'Auditoria', icon: History, min: 'MANAGER' },
  { to: '/configuracoes', label: 'Configuracoes', icon: Settings },
]

export function AppShell() {
  const { me, activeOrg, switchOrg, logout, can } = useAuth()
  const [open, setOpen] = useState(false)
  const location = useLocation()
  const unread = useQuery({
    queryKey: ['/alerts/unread-count'],
    queryFn: () => api<{ count: number }>('/alerts/unread-count'),
    refetchInterval: 60_000,
  })

  const nav = (
    <nav aria-label="Principal" className="flex flex-col gap-0.5 px-3">
      {NAV.filter((n) => !n.min || can(n.min)).map(({ to, label, icon: Icon }) => (
        <NavLink
          key={to}
          to={to}
          end={to === '/'}
          onClick={() => setOpen(false)}
          className={({ isActive }) =>
            clsx(
              'flex min-h-10 items-center gap-3 rounded-lg px-3 text-sm transition-colors',
              isActive ? 'bg-white/12 font-medium text-white' : 'text-indigo-200 hover:bg-white/8 hover:text-white',
            )
          }
        >
          <Icon className="size-4 shrink-0" aria-hidden />
          <span className="flex-1">{label}</span>
          {to === '/alertas' && (unread.data?.count ?? 0) > 0 && (
            <span className="rounded-full bg-violet-accent px-1.5 text-xs text-white" aria-label={`${unread.data?.count} nao lidos`}>
              {unread.data?.count}
            </span>
          )}
        </NavLink>
      ))}
    </nav>
  )

  return (
    <div className="flex min-h-full">
      <a href="#conteudo" className="sr-only focus:not-sr-only focus:absolute focus:z-50 focus:bg-white focus:p-2">Ir para o conteudo</a>
      <aside className="hidden w-60 shrink-0 flex-col bg-navy-900 py-4 lg:flex">
        <Brand />
        <div className="mt-4 flex-1 overflow-y-auto">{nav}</div>
      </aside>

      {open && (
        <div className="fixed inset-0 z-40 lg:hidden" role="dialog" aria-modal="true" aria-label="Menu">
          <button className="absolute inset-0 bg-navy-950/60" aria-label="Fechar menu" onClick={() => setOpen(false)} />
          <aside className="relative flex h-full w-64 flex-col bg-navy-900 py-4">
            <button className="absolute right-3 top-3 text-indigo-200" aria-label="Fechar" onClick={() => setOpen(false)}><X className="size-5" /></button>
            <Brand />
            <div className="mt-4 flex-1 overflow-y-auto">{nav}</div>
          </aside>
        </div>
      )}

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-30 flex min-h-14 items-center gap-3 border-b border-line bg-surface/95 px-4 backdrop-blur sm:px-6">
          <button className="rounded p-1.5 text-ink-2 hover:bg-brand-50 lg:hidden" aria-label="Abrir menu" onClick={() => setOpen(true)}>
            <Menu className="size-5" />
          </button>
          <div className="min-w-0 flex-1">
            {me && me.organizations.length > 1 ? (
              <select
                aria-label="Organizacao"
                value={activeOrg?.organizationId}
                onChange={(e) => switchOrg(e.target.value)}
                className="max-w-full rounded-lg border border-line bg-surface px-2 py-1.5 text-sm font-medium"
              >
                {me.organizations.map((o) => <option key={o.organizationId} value={o.organizationId}>{o.organizationName}</option>)}
              </select>
            ) : (
              <p className="truncate text-sm font-medium">{activeOrg?.organizationName}</p>
            )}
          </div>
          {activeOrg?.demo && <Badge tone="warn">Dados de demonstracao</Badge>}
          <NavLink to="/alertas" aria-label="Alertas" className="relative rounded p-1.5 text-ink-2 hover:bg-brand-50">
            <Bell className="size-5" />
            {(unread.data?.count ?? 0) > 0 && <span className="absolute right-0.5 top-0.5 size-2 rounded-full bg-violet-accent" />}
          </NavLink>
          <div className="hidden text-right sm:block">
            <p className="text-sm font-medium leading-tight">{me?.name}</p>
            <p className="text-xs text-ink-3">{activeOrg?.role}</p>
          </div>
          <button className="rounded p-1.5 text-ink-2 hover:bg-brand-50" aria-label="Sair" onClick={() => void logout()}>
            <LogOut className="size-5" />
          </button>
        </header>
        <main id="conteudo" key={location.pathname} className="mx-auto w-full max-w-[1280px] flex-1 px-4 py-5 sm:px-6">
          <Outlet />
        </main>
      </div>
    </div>
  )
}

function Brand() {
  return (
    <div className="flex items-center gap-2 px-6">
      <img src="/favicon.svg" alt="" className="size-7" />
      <span className="text-base font-semibold tracking-tight text-white">Nexus AI</span>
    </div>
  )
}
