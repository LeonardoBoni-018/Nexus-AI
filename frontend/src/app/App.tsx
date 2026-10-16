import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Suspense, lazy } from 'react'
import { Navigate, Outlet, Route, Routes, useLocation } from 'react-router-dom'
import { AppShell } from '@/components/layout/AppShell'
import { Skeleton } from '@/components/ui/primitives'
const ActionDetailPage = lazy(() => import('@/features/actions/ActionDetailPage').then((m) => ({ default: m.ActionDetailPage })))
const ActionsPage = lazy(() => import('@/features/actions/ActionsPage').then((m) => ({ default: m.ActionsPage })))
const AlertsPage = lazy(() => import('@/features/alerts/AlertsPage').then((m) => ({ default: m.AlertsPage })))
const AnalyticsPage = lazy(() => import('@/features/analytics/AnalyticsPage').then((m) => ({ default: m.AnalyticsPage })))
const AssistantPage = lazy(() => import('@/features/assistant/AssistantPage').then((m) => ({ default: m.AssistantPage })))
const AuditPage = lazy(() => import('@/features/audit/AuditPage').then((m) => ({ default: m.AuditPage })))
import { LoginPage } from '@/features/auth/LoginPage'
const DashboardPage = lazy(() => import('@/features/dashboard/DashboardPage').then((m) => ({ default: m.DashboardPage })))
const InventoryPage = lazy(() => import('@/features/inventory/InventoryPage').then((m) => ({ default: m.InventoryPage })))
const SalesPage = lazy(() => import('@/features/sales/SalesPage').then((m) => ({ default: m.SalesPage })))
const SettingsPage = lazy(() => import('@/features/settings/SettingsPage').then((m) => ({ default: m.SettingsPage })))
import { ApiError } from '@/lib/api/client'
import { AuthProvider, useAuth } from '@/lib/auth/AuthContext'

export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        refetchOnWindowFocus: false,
        // Nao repete erros do cliente (4xx); tenta novamente falhas transitorias uma vez.
        retry: (count, e) => !(e instanceof ApiError && e.status >= 400 && e.status < 500) && count < 1,
      },
    },
  })
}

/** Protecao de rota apenas para UX; a autorizacao real e feita no backend. */
export function RequireAuth() {
  const { me, loading } = useAuth()
  const location = useLocation()
  if (loading) return <div className="p-8" aria-busy="true"><Skeleton className="h-8 w-48" /></div>
  if (!me) return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  return <Outlet />
}

export function AppRoutes() {
  return (
    <Suspense fallback={<div className="p-8" aria-busy="true"><Skeleton className="h-8 w-48" /></div>}>
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<RequireAuth />}>
        <Route element={<AppShell />}>
          <Route index element={<DashboardPage />} />
          <Route path="vendas" element={<SalesPage />} />
          <Route path="estoque" element={<InventoryPage />} />
          <Route path="analises" element={<AnalyticsPage />} />
          <Route path="alertas" element={<AlertsPage />} />
          <Route path="assistente" element={<AssistantPage />} />
          <Route path="acoes" element={<ActionsPage />} />
          <Route path="acoes/:id" element={<ActionDetailPage />} />
          <Route path="auditoria" element={<AuditPage />} />
          <Route path="configuracoes" element={<SettingsPage />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
    </Suspense>
  )
}

export default function App({ client = createQueryClient() }: { client?: QueryClient }) {
  return (
    <QueryClientProvider client={client}>
      <AuthProvider>
        <AppRoutes />
      </AuthProvider>
    </QueryClientProvider>
  )
}
