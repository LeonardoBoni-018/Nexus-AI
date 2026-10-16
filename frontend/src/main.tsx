import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from '@/app/App'
import { applyTheme } from '@/lib/theme'
import './index.css'

try {
  const saved = localStorage.getItem('nexus.theme')
  if (saved === 'light' || saved === 'dark') applyTheme(saved)
} catch {
  /* sem armazenamento: segue o tema do sistema */
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </StrictMode>,
)
