import clsx from 'clsx'
import type { ButtonHTMLAttributes, HTMLAttributes, InputHTMLAttributes, ReactNode, SelectHTMLAttributes } from 'react'
import { forwardRef, useId } from 'react'

type Variant = 'primary' | 'secondary' | 'danger' | 'ghost'

export function Button({
  variant = 'primary',
  loading,
  className,
  children,
  disabled,
  ...rest
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant; loading?: boolean }) {
  return (
    <button
      {...rest}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      className={clsx(
        'inline-flex min-h-10 items-center justify-center gap-2 rounded-[5px] px-4 text-sm font-semibold transition-[transform,background-color] active:translate-y-px disabled:cursor-not-allowed disabled:opacity-50',
        variant === 'primary' && 'bg-brand-600 text-on-primary hover:brightness-110',
        variant === 'secondary' && 'border border-ink-3/50 bg-surface text-ink hover:bg-brand-50',
        variant === 'danger' && 'bg-bad text-canvas hover:brightness-110',
        variant === 'ghost' && 'text-ink-2 hover:bg-brand-50',
        className,
      )}
    >
      {loading && <span className="size-3.5 animate-spin rounded-full border-2 border-current border-t-transparent" aria-hidden />}
      {children}
    </button>
  )
}

export function Card({ className, ...rest }: HTMLAttributes<HTMLDivElement>) {
  return <div {...rest} className={clsx('min-w-0 rounded-[4px] border border-line border-t-[3px] border-t-ink bg-surface', className)} />
}

export function CardHeader({ title, action, subtitle }: { title: string; subtitle?: string; action?: ReactNode }) {
  return (
    <div className="flex flex-wrap items-start justify-between gap-2 border-b border-line px-4 py-3">
      <div>
        <h2 className="font-display text-base font-bold text-ink">{title}</h2>
        {subtitle && <p className="text-xs text-ink-3">{subtitle}</p>}
      </div>
      {action}
    </div>
  )
}

type Tone = 'neutral' | 'ok' | 'warn' | 'bad' | 'brand'
const tones: Record<Tone, string> = {
  neutral: 'bg-canvas text-ink-2 border-line',
  ok: 'bg-ok-bg text-ok border-transparent',
  warn: 'bg-warn-bg text-warn border-transparent',
  bad: 'bg-bad-bg text-bad border-transparent',
  brand: 'bg-brand-100 text-brand-700 border-transparent',
}
export function Badge({ tone = 'neutral', children }: { tone?: Tone; children: ReactNode }) {
  return <span className={clsx('inline-flex items-center rounded-[4px] border px-2 py-0.5 text-xs font-semibold', tones[tone])}>{children}</span>
}

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement> & { label: string; error?: string }>(
  function Input({ label, error, className, id, ...rest }, ref) {
    const auto = useId()
    const inputId = id ?? auto
    return (
      <div className="flex flex-col gap-1">
        <label htmlFor={inputId} className="text-xs font-medium text-ink-2">{label}</label>
        <input
          ref={ref}
          id={inputId}
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? `${inputId}-err` : undefined}
          {...rest}
          className={clsx('min-h-10 rounded-[5px] border bg-surface px-3 text-sm', error ? 'border-bad' : 'border-ink-3/50', className)}
        />
        {error && <p id={`${inputId}-err`} role="alert" className="text-xs text-bad">{error}</p>}
      </div>
    )
  },
)

export function Select({
  label,
  options,
  className,
  ...rest
}: SelectHTMLAttributes<HTMLSelectElement> & { label: string; options: { value: string; label: string }[] }) {
  const id = useId()
  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={id} className="text-xs font-medium text-ink-2">{label}</label>
      <select id={id} {...rest} className={clsx('min-h-10 rounded-[5px] border border-ink-3/50 bg-surface px-2 text-sm', className)}>
        {options.map((o) => (
          <option key={o.value} value={o.value}>{o.label}</option>
        ))}
      </select>
    </div>
  )
}

export function Skeleton({ className }: { className?: string }) {
  return <div aria-hidden className={clsx('animate-pulse rounded-md bg-line/70', className)} />
}

export function PageHeader({ title, description, actions }: { title: string; description?: string; actions?: ReactNode }) {
  return (
    <div className="mb-4 flex flex-wrap items-end justify-between gap-3">
      <div>
        <h1 className="font-display text-[1.75rem] font-extrabold leading-tight text-ink">{title}</h1>
        {description && <p className="mt-1 max-w-[62ch] text-sm text-ink-2">{description}</p>}
      </div>
      {actions}
    </div>
  )
}
