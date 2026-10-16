import { useCategories, useStores } from '@/hooks/useApi'
import { useFilters } from '@/hooks/useFilters'
import { Button, Input, Select } from '@/components/ui/primitives'

/** Filtros de periodo/loja/categoria na URL. Presets rapidos + datas explicitas. */
export function FilterBar({ showCategory = true }: { showCategory?: boolean }) {
  const { filters, update, setPreset } = useFilters()
  const stores = useStores()
  const categories = useCategories()
  return (
    <form
      aria-label="Filtros"
      onSubmit={(e) => e.preventDefault()}
      className="mb-4 flex flex-wrap items-end gap-3 rounded-xl border border-line bg-surface p-3"
    >
      <div className="flex gap-1.5" role="group" aria-label="Periodos rapidos">
        {[7, 30, 90].map((d) => (
          <Button key={d} type="button" variant="secondary" onClick={() => setPreset(d)}>{d} dias</Button>
        ))}
      </div>
      <Input label="De" type="date" value={filters.from} max={filters.to} onChange={(e) => e.target.value && update({ from: e.target.value })} />
      <Input label="Ate" type="date" value={filters.to} min={filters.from} onChange={(e) => e.target.value && update({ to: e.target.value })} />
      <Select
        label="Loja"
        value={filters.storeId}
        onChange={(e) => update({ storeId: e.target.value })}
        options={[{ value: '', label: 'Todas as lojas' }, ...(stores.data ?? []).map((s) => ({ value: s.id, label: s.name }))]}
      />
      {showCategory && (
        <Select
          label="Categoria"
          value={filters.category}
          onChange={(e) => update({ category: e.target.value })}
          options={[{ value: '', label: 'Todas' }, ...(categories.data ?? []).map((c) => ({ value: c, label: c }))]}
        />
      )}
    </form>
  )
}
