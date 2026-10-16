import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Navigate, useLocation } from 'react-router-dom'
import { z } from 'zod'
import { Button, Input } from '@/components/ui/primitives'
import { errorMessage } from '@/lib/api/client'
import { useAuth } from '@/lib/auth/AuthContext'

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
    <div className="grid min-h-full lg:grid-cols-2">
      <div className="hidden flex-col justify-between bg-navy-900 p-10 text-white lg:flex">
        <div className="flex items-center gap-2">
          <img src="/favicon.svg" alt="" className="size-8" />
          <span className="text-lg font-semibold">Nexus AI</span>
        </div>
        <div>
          <h2 className="max-w-md text-3xl font-semibold leading-tight">Decisoes operacionais com evidencias, nao palpites.</h2>
          <p className="mt-3 max-w-md text-indigo-200">
            Agentes de IA consultam seus dados reais, mostram as contas e preparam acoes que so acontecem com a sua aprovacao.
          </p>
        </div>
        <p className="text-xs text-indigo-300">Inteligencia operacional para varejo</p>
      </div>
      <div className="flex items-center justify-center p-6">
        <form onSubmit={onSubmit} noValidate className="w-full max-w-sm space-y-4" aria-label="Entrar">
          <div>
            <h1 className="text-2xl font-semibold tracking-tight">Entrar</h1>
            <p className="mt-1 text-sm text-ink-3">Acesse sua organizacao no Nexus AI.</p>
          </div>
          {error && <p role="alert" className="rounded-lg bg-bad-bg px-3 py-2 text-sm text-bad">{error}</p>}
          <Input label="E-mail" type="email" autoComplete="username" error={formState.errors.email?.message} {...register('email')} />
          <Input label="Senha" type="password" autoComplete="current-password" error={formState.errors.password?.message} {...register('password')} />
          <Button type="submit" className="w-full" loading={formState.isSubmitting}>Entrar</Button>
          {import.meta.env.DEV && (
            <p className="rounded-lg bg-brand-50 px-3 py-2 text-xs text-ink-2">
              Ambiente local de demonstracao: <strong>gerente@aurora.demo</strong> (senha no README).
            </p>
          )}
        </form>
      </div>
    </div>
  )
}
