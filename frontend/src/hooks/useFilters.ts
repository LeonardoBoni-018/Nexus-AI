import { useCallback, useMemo } from 'react'
import { useSearchParams } from 'react-router-dom'
import { addDays, todayYmd } from '@/lib/formatting'

export interface Filters {
  from: string
  to: string
  storeId: string
  category: string
}

/** Filtros globais (periodo/loja/categoria) sincronizados com a URL: links compartilhaveis e voltar/avancar. */
export function useFilters() {
  const [params, setParams] = useSearchParams()
  const today = todayYmd()
  const filters: Filters = useMemo(
    () => ({
      from: params.get('from') ?? addDays(today, -29),
      to: params.get('to') ?? today,
      storeId: params.get('store') ?? '',
      category: params.get('category') ?? '',
    }),
    [params, today],
  )
  const update = useCallback(
    (patch: Partial<Filters>) => {
      setParams(
        (prev) => {
          const next = new URLSearchParams(prev)
          const map: Record<keyof Filters, string> = { from: 'from', to: 'to', storeId: 'store', category: 'category' }
          for (const [k, v] of Object.entries(patch) as [keyof Filters, string][]) {
            if (v) next.set(map[k], v)
            else next.delete(map[k])
          }
          return next
        },
        { replace: true },
      )
    },
    [setParams],
  )
  const setPreset = useCallback((days: number) => update({ from: addDays(todayYmd(), -(days - 1)), to: todayYmd() }), [update])
  return { filters, update, setPreset }
}

export function queryParams(f: Filters, extra?: Record<string, string | number | boolean>) {
  return { from: f.from, to: f.to, storeId: f.storeId || undefined, category: f.category || undefined, ...extra }
}
