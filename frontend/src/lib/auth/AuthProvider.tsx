import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { ApiError, api, getActiveOrg, onUnauthorized, setActiveOrg } from '@/lib/api/client'
import type { Me } from '@/lib/api/types'
import { AuthContext, roleAtLeast } from './auth-context'
import type { AuthState } from './auth-context'


export function AuthProvider({ children }: { children: ReactNode }) {
  const qc = useQueryClient()
  const [orgId, setOrgId] = useState<string | null>(getActiveOrg())

  const meQuery = useQuery({
    queryKey: ['me'],
    queryFn: async () => {
      try {
        return await api<Me>('/auth/me')
      } catch (e) {
        if (e instanceof ApiError && e.status === 401) return null
        throw e
      }
    },
    staleTime: 60_000,
    retry: false,
  })
  const me = meQuery.data ?? null

  useEffect(() => {
    onUnauthorized(() => qc.setQueryData(['me'], null))
    return () => onUnauthorized(null)
  }, [qc])

  const activeOrg = useMemo(() => {
    if (!me) return null
    return me.organizations.find((o) => o.organizationId === orgId) ?? me.organizations[0] ?? null
  }, [me, orgId])

  // Mantem o header X-Organization-Id coerente com a organizacao efetivamente ativa.
  useEffect(() => {
    if (activeOrg) setActiveOrg(activeOrg.organizationId)
  }, [activeOrg])

  const loginMutation = useMutation({
    mutationFn: (v: { email: string; password: string }) => api<Me>('/auth/login', { method: 'POST', body: v }),
    onSuccess: (data) => {
      qc.removeQueries({ predicate: (q) => q.queryKey[0] !== 'me' })
      qc.setQueryData(['me'], data)
    },
  })

  const login = useCallback(async (email: string, password: string) => {
    await loginMutation.mutateAsync({ email, password })
  }, [loginMutation])

  const logout = useCallback(async () => {
    try {
      await api<void>('/auth/logout', { method: 'POST' })
    } finally {
      setActiveOrg(null)
      setOrgId(null)
      qc.clear()
      qc.setQueryData(['me'], null)
    }
  }, [qc])

  const switchOrg = useCallback((id: string) => {
    setActiveOrg(id)
    setOrgId(id)
    qc.removeQueries({ predicate: (q) => q.queryKey[0] !== 'me' })
  }, [qc])

  const value: AuthState = {
    me,
    loading: meQuery.isPending,
    activeOrg,
    login,
    logout,
    switchOrg,
    can: (min) => roleAtLeast(activeOrg?.role, min),
  }
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

