import { QueryClient } from '@tanstack/react-query'
import { ApiError } from '@/lib/api/client'

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
