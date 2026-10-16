import type { UseQueryResult } from '@tanstack/react-query'
import { AlertTriangle, Inbox } from 'lucide-react'
import type { ReactNode } from 'react'
import { errorMessage } from '@/lib/api/client'
import { Button, Skeleton } from './primitives'

export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  return (
    <div role="alert" className="flex flex-col items-center gap-2 px-4 py-10 text-center">
      <AlertTriangle className="size-6 text-bad" aria-hidden />
      <p className="text-sm font-medium text-ink">Nao foi possivel carregar</p>
      <p className="max-w-md text-sm text-ink-3">{errorMessage(error)}</p>
      {onRetry && <Button variant="secondary" onClick={onRetry}>Tentar novamente</Button>}
    </div>
  )
}

export function EmptyState({ title, hint }: { title: string; hint?: string }) {
  return (
    <div className="flex flex-col items-center gap-1 px-4 py-10 text-center">
      <Inbox className="size-6 text-ink-3" aria-hidden />
      <p className="text-sm font-medium text-ink">{title}</p>
      {hint && <p className="max-w-md text-sm text-ink-3">{hint}</p>}
    </div>
  )
}

/** Trata loading / erro / vazio / sucesso de forma uniforme para qualquer consulta. */
export function QueryState<T>({
  query,
  isEmpty,
  empty,
  skeleton,
  children,
}: {
  query: UseQueryResult<T>
  isEmpty?: (data: T) => boolean
  empty?: { title: string; hint?: string }
  skeleton?: ReactNode
  children: (data: T) => ReactNode
}) {
  if (query.isPending) {
    return (
      <div aria-busy="true" aria-live="polite" className="space-y-2 p-4">
        {skeleton ?? (
          <>
            <Skeleton className="h-5 w-1/3" />
            <Skeleton className="h-24 w-full" />
          </>
        )}
        <span className="sr-only">Carregando...</span>
      </div>
    )
  }
  if (query.isError) return <ErrorState error={query.error} onRetry={() => void query.refetch()} />
  if (isEmpty?.(query.data)) return <EmptyState title={empty?.title ?? 'Nada por aqui'} hint={empty?.hint} />
  return <>{children(query.data)}</>
}
