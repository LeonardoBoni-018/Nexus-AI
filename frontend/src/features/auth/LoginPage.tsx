import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Navigate, useLocation } from 'react-router-dom'
import { z } from 'zod'
import { Button, Input } from '@/components/ui/primitives'
import { errorMessage } from '@/lib/api/client'
import { useAuth } from '@/lib/auth/auth-context'

const schema = z.object({
  email: z.string().min(1, 'Informe o e-mail').email('E-mail invalido'),
  password: z.string().min(1, 'Informe a senha'),
})
type FormValues = z.infer<typeof schema>

export function LoginPage() {
  const { me, login } = useAuth()
  const location = useLocation()
  const [error, setError] = useState<string | null>(null)
  const { register, handleSubmit, formState } = useForm<FormValues>({ resolver: zodResolver(schema) })

  if (me) {
    const from = (location.state as { from?: string } | null)?.from ?? '/'
    return <Navigate to={from} replace />
  }

  const onSubmit = handleSubmit(async (v) => {
    setError(null)
    try {
      await login(v.email, v.password)
    } catch (e) {
      setError(errorMessage(e))
    }
  })

  return (
    <div className="grid min-h-full lg:grid-cols-[1.1fr_1fr]">
      <div className="relative hidden flex-col justify-between overflow-hidden bg-rail p-12 text-rail-ink-strong lg:flex">
        <div className="flex items-center gap-3">
          <img src="/favicon.svg" alt="" className="size-9" />
          <span className="font-display text-xl font-extrabold">Nexus AI</span>
        </div>
        <div>
          <p className="font-display text-6xl font-extrabold leading-[1.02]">
            Saiba o que vai <span className="marker">faltar</span> antes de faltar.
          </p>
          <p className="mt-5 max-w-[44ch] text-lg text-[var(--rail-ink)]">
            O assistente le as vendas e o estoque de verdade, mostra as contas e prepara a reposicao. Quem aprova e voce.
          </p>
        </div>
        <div className="shelf-in" aria-hidden>
          <div className="shelf" style={{ height: 64, borderColor: 'var(--rail-ink)' }}>
            {Array.from({ length: 34 }, (_, i) => (
              <span key={i} className="crate" data-low={i > 27} style={{ ['--i' as string]: i, height: `${40 + ((i * 53) % 60)}%` }} />
            ))}
          </div>
        </div>
      </div>
      <div className="flex items-center justify-center p-6">
        <form onSubmit={onSubmit} noValidate className="w-full max-w-sm space-y-4" aria-label="Entrar">
          <div>
            <h1 className="font-display text-4xl font-extrabold">Entrar</h1>
            <p className="mt-1 text-sm text-ink-2">Acesse a operacao da sua loja.</p>
          </div>
          {error && <p role="alert" className="rounded-[4px] bg-bad-bg px-3 py-2 text-sm text-bad">{error}</p>}
          <Input label="E-mail" type="email" autoComplete="username" error={formState.errors.email?.message} {...register('email')} />
          <Input label="Senha" type="password" autoComplete="current-password" error={formState.errors.password?.message} {...register('password')} />
          <Button type="submit" className="w-full" loading={formState.isSubmitting}>Entrar</Button>
          {import.meta.env.DEV && (
            <p className="rounded-[4px] bg-brand-50 px-3 py-2 text-xs text-ink-2">
              Ambiente local de demonstracao: gerente@aurora.demo (senha no README).
            </p>
          )}
        </form>
      </div>
    </div>
  )
}
