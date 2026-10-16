import { X } from 'lucide-react'
import { useEffect, useId, useRef } from 'react'
import type { ReactNode } from 'react'

/** Dialogo modal acessivel usando o elemento nativo <dialog> (foco preso, Esc fecha). */
export function Dialog({ open, onClose, title, children }: { open: boolean; onClose: () => void; title: string; children: ReactNode }) {
  const ref = useRef<HTMLDialogElement>(null)
  const titleId = useId()
  useEffect(() => {
    const d = ref.current
    if (!d) return
    if (open && !d.open) d.showModal()
    if (!open && d.open) d.close()
  }, [open])
  return (
    <dialog
      ref={ref}
      aria-labelledby={titleId}
      onClose={onClose}
      onClick={(e) => e.target === ref.current && onClose()}
      className="m-auto w-[min(92vw,560px)] rounded-xl border border-line bg-surface p-0 text-ink backdrop:bg-navy-950/50"
    >
      {open && (
        <div>
          <div className="flex items-center justify-between border-b border-line px-4 py-3">
            <h2 id={titleId} className="text-sm font-semibold">{title}</h2>
            <button type="button" aria-label="Fechar" onClick={onClose} className="rounded p-1 text-ink-3 hover:bg-brand-50">
              <X className="size-4" />
            </button>
          </div>
          <div className="p-4">{children}</div>
        </div>
      )}
    </dialog>
  )
}
