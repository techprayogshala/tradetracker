import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { useKeycloak } from '@react-keycloak/web'
import { useEffect } from 'react'
import api from './lib/apiClient'
import AppLayout from './components/layout/AppLayout'
import DashboardPage from './pages/DashboardPage'
import PortfolioPage from './pages/PortfolioPage'
import TradesPage from './pages/TradesPage'
import TaxPage from './pages/TaxPage'
import SettingsPage from './pages/SettingsPage'

export default function App() {
  const { keycloak, initialized } = useKeycloak()

  useEffect(() => {
    if (!initialized || !keycloak.authenticated) return

    const provision = async () => {
      try {
        await api.post('/v1/portfolios/provision')
      } catch {}
    }
    const timer = setTimeout(provision, 100)
    return () => clearTimeout(timer)
  }, [initialized, keycloak.authenticated])

  if (!initialized) {
    return (
      <div className="flex h-screen items-center justify-center bg-gray-50">
        <div className="text-gray-400 text-sm">Loading…</div>
      </div>
    )
  }

  return (
    <BrowserRouter>
      <Routes>
        <Route element={<AppLayout />}>
          <Route index element={<Navigate to="/dashboard" replace />} />
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/portfolio/:portfolioId" element={<PortfolioPage />} />
          <Route path="/portfolio/:portfolioId/trades" element={<TradesPage />} />
          <Route path="/portfolio/:portfolioId/tax" element={<TaxPage />} />
          <Route path="/settings" element={<SettingsPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}
