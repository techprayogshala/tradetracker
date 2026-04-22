import { Outlet, NavLink, useNavigate, useLocation } from 'react-router-dom'
import { useKeycloak } from '@react-keycloak/web'
import {
  LayoutDashboard, TrendingUp, ArrowLeftRight,
  Receipt, Settings, LogOut, ChevronDown
} from 'lucide-react'
import { usePortfolios } from '../../hooks/usePortfolios'
import { useState } from 'react'
import clsx from 'clsx'

const NAV = [
  { to: '/dashboard',             icon: LayoutDashboard, label: 'Dashboard'   },
  { to: '/portfolio/:id',       icon: TrendingUp,     label: 'Holdings'    },
  { to: '/portfolio/:id/trades', icon: ArrowLeftRight, label: 'Trades'      },
  { to: '/portfolio/:id/tax',   icon: Receipt,      label: 'Tax & CGT'   },
  { to: '/settings',          icon: Settings,     label: 'Settings'    },
]

export default function AppLayout() {
  const { keycloak } = useKeycloak()
  const { portfolios, activePortfolio, setActivePortfolio, isLoading } = usePortfolios()
  const [portfolioMenuOpen, setPortfolioMenuOpen] = useState(false)
  const navigate = useNavigate()
  const location = useLocation()

  const navTo = (template: string) => {
    if (template === '/dashboard') return '/dashboard'
    if (template === '/settings') return '/settings'
    const id = activePortfolio?.id
    if (!id) {
      console.log('No active portfolio id:', { activePortfolio, portfolios })
      return '#'
    }
    return template.replace(':id', id)
  }

  const isActive = (template: string) => {
    if (!activePortfolio?.id && template.includes(':id')) return false
    const path = location.pathname
    if (template === '/dashboard') return path === '/dashboard'
    if (template === '/settings') return path === '/settings'
    if (template.includes(':id/trades')) return path.endsWith('/trades')
    if (template.includes(':id/tax')) return path.endsWith('/tax')
    if (template.includes(':id')) return path.match(/^\/portfolio\/[^/]+$/)?.length === 1
    return false
  }

  if (isLoading) {
    return (
      <div className="flex h-screen items-center justify-center bg-gray-50">
        <div className="text-gray-400 text-sm">Loading portfolios…</div>
      </div>
    )
  }

  return (
    <div className="flex h-screen bg-gray-50 font-sans">
      {/* ── Sidebar ──────────────────────────────────────────────────────── */}
      <aside className="w-60 flex-shrink-0 bg-white border-r border-gray-200 flex flex-col">

        {/* Logo */}
        <div className="h-16 flex items-center px-5 border-b border-gray-100">
          <span className="text-lg font-semibold tracking-tight text-gray-900">
            Trade<span className="text-blue-600">Tracker</span>
          </span>
        </div>

        {/* Portfolio switcher */}
        <div className="px-3 pt-4 pb-2">
          <button
            onClick={() => setPortfolioMenuOpen(o => !o)}
            className="w-full flex items-center justify-between px-3 py-2 rounded-lg
                       text-sm text-gray-700 hover:bg-gray-50 transition-colors"
          >
            <span className="font-medium truncate">
              {activePortfolio?.name ?? 'Select portfolio'}
            </span>
            <ChevronDown size={14} className={clsx(
              'ml-1 text-gray-400 transition-transform flex-shrink-0',
              portfolioMenuOpen && 'rotate-180'
            )} />
          </button>

          {portfolioMenuOpen && (
            <div className="mt-1 bg-white border border-gray-200 rounded-lg shadow-sm overflow-hidden">
              {(portfolios ?? []).map(p => (
                <button
                  key={p.id}
                  onClick={() => {
                    setActivePortfolio(p)
                    setPortfolioMenuOpen(false)
                    navigate(`/portfolio/${p.id}`)
                  }}
                  className={clsx(
                    'w-full text-left px-3 py-2 text-sm hover:bg-gray-50 transition-colors',
                    activePortfolio?.id === p.id && 'text-blue-600 bg-blue-50'
                  )}
                >
                  {p.name}
                </button>
              ))}
            </div>
          )}
        </div>

        {/* Nav links */}
        <nav className="flex-1 px-3 pt-2 space-y-0.5">
          {NAV.map(({ to, icon: Icon, label }) => (
            <button
              key={to}
              onClick={() => navigate(navTo(to))}
              className={clsx(
                'w-full flex items-center gap-3 px-3 py-2 rounded-lg text-sm transition-colors text-left',
                isActive(to)
                  ? 'bg-blue-50 text-blue-700 font-medium'
                  : 'text-gray-600 hover:bg-gray-50 hover:text-gray-900'
              )}
            >
              <Icon size={16} />
              {label}
            </button>
          ))}
        </nav>

        {/* User / logout */}
        <div className="p-3 border-t border-gray-100">
          <div className="flex items-center gap-2 px-3 py-2">
            <div className="w-7 h-7 rounded-full bg-blue-100 flex items-center justify-center
                            text-xs font-semibold text-blue-600 flex-shrink-0">
              {keycloak.tokenParsed?.given_name?.[0]?.toUpperCase() ?? 'U'}
            </div>
            <div className="flex-1 min-w-0">
              <div className="text-sm font-medium text-gray-900 truncate">
                {keycloak.tokenParsed?.name ?? 'User'}
              </div>
              <div className="text-xs text-gray-400 truncate">
                {keycloak.tokenParsed?.email}
              </div>
            </div>
            <button
              onClick={() => keycloak.logout()}
              className="p-1 rounded text-gray-400 hover:text-gray-600 transition-colors"
              title="Sign out"
            >
              <LogOut size={15} />
            </button>
          </div>
        </div>
      </aside>

      {/* ── Main content ─────────────────────────────────────────────────── */}
      <main className="flex-1 overflow-y-auto">
        <Outlet />
      </main>
    </div>
  )
}
