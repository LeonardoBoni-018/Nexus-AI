import clsx from 'clsx'
import type { ReactNode } from 'react'
import { Button } from './primitives'

export interface Column<T> {
  header: string
  cell: (row: T) => ReactNode
  align?: 'left' | 'right'
  className?: string
}

export function DataTable<T>({
  columns,
  rows,
  rowKey,
  onRowClick,
  caption,
}: {
  columns: Column<T>[]
  rows: T[]
  rowKey: (row: T) => string
  onRowClick?: (row: T) => void
  caption: string
}) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[560px] text-sm">
        <caption className="sr-only">{caption}</caption>
        <thead>
          <tr className="border-b border-line text-xs text-ink-3">
            {columns.map((c) => (
              <th key={c.header} scope="col" className={clsx('px-4 py-2 font-medium', c.align === 'right' ? 'text-right' : 'text-left')}>
                {c.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr
              key={rowKey(r)}
              className={clsx('border-b border-line/60 last:border-0', onRowClick && 'cursor-pointer hover:bg-brand-50/60')}
              onClick={onRowClick ? () => onRowClick(r) : undefined}
              onKeyDown={onRowClick ? (e) => e.key === 'Enter' && onRowClick(r) : undefined}
              tabIndex={onRowClick ? 0 : undefined}
            >
              {columns.map((c) => (
                <td key={c.header} className={clsx('px-4 py-2.5 align-middle', c.align === 'right' && 'num text-right', c.className)}>
                  {c.cell(r)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

export function Pagination({
  page,
  totalPages,
  totalItems,
  onPage,
}: {
  page: number
  totalPages: number
  totalItems: number
  onPage: (p: number) => void
}) {
  if (totalPages <= 1) return <p className="px-4 py-2 text-xs text-ink-3">{totalItems} registro(s)</p>
  return (
    <nav aria-label="Paginacao" className="flex items-center justify-between gap-2 border-t border-line px-4 py-2">
      <span className="text-xs text-ink-3">
        Pagina {page + 1} de {totalPages} · {totalItems} registros
      </span>
      <div className="flex gap-2">
        <Button variant="secondary" disabled={page <= 0} onClick={() => onPage(page - 1)}>Anterior</Button>
        <Button variant="secondary" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}>Proxima</Button>
      </div>
    </nav>
  )
}
