import { useCallback, useEffect, useState } from 'react'

export type ThemePref = 'system' | 'light' | 'dark'
const KEY = 'nexus.theme'

function read(): ThemePref {
  try {
    const v = localStorage.getItem(KEY)
    return v === 'light' || v === 'dark' ? v : 'system'
  } catch {
    return 'system'
  }
}

export function applyTheme(pref: ThemePref): void {
  const root = document.documentElement
  if (pref === 'system') root.removeAttribute('data-theme')
  else root.setAttribute('data-theme', pref)
}

/** Preferencia de tema por pessoa (convenience em localStorage; sem localStorage segue o sistema). */
export function useTheme() {
  const [pref, setPref] = useState<ThemePref>(read)
  useEffect(() => applyTheme(pref), [pref])
  const set = useCallback((p: ThemePref) => {
    try {
      if (p === 'system') localStorage.removeItem(KEY)
      else localStorage.setItem(KEY, p)
    } catch {
      /* sem armazenamento: vale so nesta sessao */
    }
    setPref(p)
  }, [])
  return { pref, set }
}
