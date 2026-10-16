import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api/client'
import type { Page, Store } from '@/lib/api/types'

/** Consulta paginada/filtravel com cache por chave e transicao suave entre paginas. */
export function useApiQuery<T>(key: unknown[], path: string, params?: Record<string, string | number | boolean | null | undefined>, enabled = true) {
  return useQuery({
    queryKey: [path, ...key, params],
    queryFn: ({ signal }) => api<T>(path, { params, signal }),
    placeholderData: keepPreviousData,
    enabled,
  })
}

export function useStores() {
  return useQuery({ queryKey: ['/stores'], queryFn: ({ signal }) => api<Store[]>('/stores', { signal }), staleTime: 5 * 60_000 })
}
export function useCategories() {
  return useQuery({ queryKey: ['/categories'], queryFn: ({ signal }) => api<string[]>('/categories', { signal }), staleTime: 5 * 60_000 })
}

export type { Page }
