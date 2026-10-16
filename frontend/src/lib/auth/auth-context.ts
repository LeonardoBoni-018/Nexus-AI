import { createContext, useContext } from 'react'
import type { Me, Membership, Role } from '@/lib/api/types'

const ORDER: Role[] = ['VIEWER', 'ANALYST', 'MANAGER', 'ADMIN']

export function roleAtLeast(role: Role | undefined, min: Role): boolean {
  return role !== undefined && ORDER.indexOf(role) >= ORDER.indexOf(min)
}

export interface AuthState {
  me: Me | null
  loading: boolean
  activeOrg: Membership | null
  login: (email: string, password: string) => Promise<void>
  logout: () => Promise<void>
  switchOrg: (organizationId: string) => void
  can: (min: Role) => boolean
}

export const AuthContext = createContext<AuthState | null>(null)

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth fora do AuthProvider')
  return ctx
}
