import { useState } from 'react'
import {
  Bar, BarChart, CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts'
import { formatBRL, formatDate, formatInt } from '@/lib/formatting'
import type { SeriesPoint, TopProduct } from '@/lib/api/types'
import { Button } from '@/components/ui/primitives'

// Paleta categorica validada (slots 1 e 2 do conjunto de referencia de dataviz).
export const SERIES_1 = '#2a78d6'
export const SERIES_2 = '#eb6834'
const GRID = '#e4e6ef'
const AXIS = '#6b6f8a'

export function RevenueChart({ current, previous }: { current: SeriesPoint[]; previous?: SeriesPoint[] }) {
  const [table, setTable] = useState(false)
  const data = current.map((p, i) => ({
    date: p.date,
    atual: p.revenue,
    anterior: previous?.[i]?.revenue,
    datePrev: previous?.[i]?.date,
  }))
  return (
    <div>
      <div className="mb-2 flex justify-end">
        <Button variant="ghost" onClick={() => setTable((t) => !t)} aria-pressed={table}>
          {table ? 'Ver grafico' : 'Ver como tabela'}
        </Button>
      </div>
      {table ? (
        <div className="max-h-72 overflow-auto">
          <table className="w-full text-sm">
            <caption className="sr-only">Faturamento diario</caption>
            <thead><tr className="text-left text-xs text-ink-3"><th className="px-2 py-1">Data</th><th className="px-2 py-1 text-right">Faturamento</th><th className="px-2 py-1 text-right">Periodo anterior</th></tr></thead>
            <tbody>
              {data.map((d) => (
                <tr key={d.date} className="border-t border-line/60">
                  <td className="px-2 py-1">{formatDate(d.date)}</td>
                  <td className="num px-2 py-1 text-right">{formatBRL(d.atual)}</td>
                  <td className="num px-2 py-1 text-right">{formatBRL(d.anterior)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <div role="img" aria-label="Grafico de linha do faturamento diario comparado ao periodo anterior" className="h-64 w-full">
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={data} margin={{ top: 8, right: 12, bottom: 0, left: 0 }}>
              <CartesianGrid stroke={GRID} strokeDasharray="3 3" vertical={false} />
              <XAxis dataKey="date" tickFormatter={(v: string) => formatDate(v).slice(0, 5)} stroke={AXIS} fontSize={12} tickLine={false} minTickGap={24} />
              <YAxis tickFormatter={(v: number) => (v >= 1000 ? `${Math.round(v / 1000)}k` : String(v))} stroke={AXIS} fontSize={12} tickLine={false} axisLine={false} width={40} />
              <Tooltip
                formatter={(v) => formatBRL(typeof v === 'number' ? v : Number(v))}
                labelFormatter={(l) => formatDate(String(l))}
                contentStyle={{ borderRadius: 8, borderColor: GRID, fontSize: 12 }}
              />
              <Legend iconType="plainline" wrapperStyle={{ fontSize: 12 }} />
              <Line type="monotone" dataKey="atual" name="Periodo atual" stroke={SERIES_1} strokeWidth={2} dot={false} activeDot={{ r: 4 }} />
              {previous && previous.length > 0 && (
                <Line type="monotone" dataKey="anterior" name="Periodo anterior" stroke={SERIES_2} strokeWidth={2} strokeDasharray="5 4" dot={false} activeDot={{ r: 4 }} />
              )}
            </LineChart>
          </ResponsiveContainer>
        </div>
      )}
    </div>
  )
}

export function TopProductsChart({ products, metric }: { products: TopProduct[]; metric: 'unitsSold' | 'revenue' }) {
  const data = products.map((p) => ({ name: p.name, value: p[metric] }))
  return (
    <div role="img" aria-label="Grafico de barras dos produtos mais vendidos" style={{ height: Math.max(160, data.length * 38) }} className="w-full">
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} layout="vertical" margin={{ top: 0, right: 24, bottom: 0, left: 8 }}>
          <CartesianGrid stroke={GRID} horizontal={false} />
          <XAxis type="number" stroke={AXIS} fontSize={12} tickLine={false} tickFormatter={(v: number) => (metric === 'revenue' ? formatBRL(v) : formatInt(v))} />
          <YAxis type="category" dataKey="name" width={150} stroke={AXIS} fontSize={12} tickLine={false} axisLine={false} />
          <Tooltip formatter={(v) => (metric === 'revenue' ? formatBRL(Number(v)) : `${formatInt(Number(v))} un.`)} contentStyle={{ borderRadius: 8, borderColor: GRID, fontSize: 12 }} />
          <Bar dataKey="value" name={metric === 'revenue' ? 'Faturamento' : 'Unidades'} fill={SERIES_1} radius={[0, 4, 4, 0]} barSize={18} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  )
}
