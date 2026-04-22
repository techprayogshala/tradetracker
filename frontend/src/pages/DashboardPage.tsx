import { useState } from 'react'
import { AreaChart, Area, XAxis, YAxis, Tooltip, ResponsiveContainer } from 'recharts'
import { TrendingUp, TrendingDown, DollarSign, BarChart2 } from 'lucide-react'
import { format, parseISO } from 'date-fns'
import clsx from 'clsx'
import { usePortfolios, useHoldings, usePerformance } from '../hooks/usePortfolios'

const PERIODS = ['1M', '3M', '6M', '1Y', '3Y', 'ALL']

export default function DashboardPage() {
  const { activePortfolio } = usePortfolios()
  const { data: holdings = [] } = useHoldings(activePortfolio?.id ?? '')
  const [period, setPeriod] = useState('1Y')
  const { data: perf } = usePerformance(activePortfolio?.id ?? '', period)

  if (!activePortfolio) {
    return (
      <div className="p-8 text-gray-400 text-sm">
        No portfolio selected. Create one in Settings.
      </div>
    )
  }

  const gainPositive = (activePortfolio.unrealisedGainLoss ?? 0) >= 0

  return (
    <div className="p-8 space-y-6">
      {/* ── Header ──────────────────────────────────────────────────────── */}
      <div>
        <h1 className="text-2xl font-semibold text-gray-900">{activePortfolio.name}</h1>
        <p className="text-sm text-gray-500 mt-0.5">
          {activePortfolio.baseCurrency} · {activePortfolio.parcelMatchingStrategy}
        </p>
      </div>

      {/* ── Summary cards ────────────────────────────────────────────────── */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard
          label="Portfolio Value"
          value={fmt(activePortfolio.totalValue, activePortfolio.baseCurrency)}
          icon={<DollarSign size={16} />}
        />
        <StatCard
          label="Cost Base"
          value={fmt(activePortfolio.totalCostBase, activePortfolio.baseCurrency)}
          icon={<BarChart2 size={16} />}
        />
        <StatCard
          label="Unrealised Gain / Loss"
          value={fmt(activePortfolio.unrealisedGainLoss, activePortfolio.baseCurrency)}
          sub={`${pct(activePortfolio.unrealisedGainLossPct)}`}
          positive={gainPositive}
          icon={gainPositive ? <TrendingUp size={16} /> : <TrendingDown size={16} />}
        />
        <StatCard
          label="TWR (1Y)"
          value={perf ? pct(perf.twr) : '—'}
          sub={perf ? `MWR ${pct(perf.mwr)}` : undefined}
          positive={(perf?.twr ?? 0) >= 0}
          icon={<TrendingUp size={16} />}
        />
      </div>

      {/* ── Value chart ──────────────────────────────────────────────────── */}
      {perf && perf.timeSeries.length > 0 && (
        <div className="bg-white rounded-xl border border-gray-200 p-6">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-sm font-semibold text-gray-700">Portfolio Value</h2>
            <div className="flex gap-1">
              {PERIODS.map(p => (
                <button
                  key={p}
                  onClick={() => setPeriod(p)}
                  className={clsx(
                    'px-2.5 py-1 text-xs rounded-md transition-colors',
                    period === p
                      ? 'bg-blue-600 text-white'
                      : 'text-gray-500 hover:bg-gray-100'
                  )}
                >
                  {p}
                </button>
              ))}
            </div>
          </div>
          <ResponsiveContainer width="100%" height={240}>
            <AreaChart data={perf.timeSeries}>
              <defs>
                <linearGradient id="valueGrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%"  stopColor="#2563eb" stopOpacity={0.15} />
                  <stop offset="95%" stopColor="#2563eb" stopOpacity={0}    />
                </linearGradient>
              </defs>
              <XAxis
                dataKey="date"
                tickFormatter={d => format(parseISO(d), 'MMM yy')}
                tick={{ fontSize: 11, fill: '#9ca3af' }}
                axisLine={false}
                tickLine={false}
              />
              <YAxis
                tickFormatter={v => `$${(v / 1000).toFixed(0)}k`}
                tick={{ fontSize: 11, fill: '#9ca3af' }}
                axisLine={false}
                tickLine={false}
                width={52}
              />
              <Tooltip
                formatter={(v: number) => [fmt(v, activePortfolio.baseCurrency), 'Value']}
                labelFormatter={d => format(parseISO(d as string), 'dd MMM yyyy')}
                contentStyle={{ fontSize: 12, borderRadius: 8, border: '1px solid #e5e7eb' }}
              />
              <Area
                type="monotone"
                dataKey="value"
                stroke="#2563eb"
                strokeWidth={2}
                fill="url(#valueGrad)"
                dot={false}
              />
            </AreaChart>
          </ResponsiveContainer>
        </div>
      )}

      {/* ── Holdings table ────────────────────────────────────────────────── */}
      <div className="bg-white rounded-xl border border-gray-200">
        <div className="px-6 py-4 border-b border-gray-100">
          <h2 className="text-sm font-semibold text-gray-700">Holdings</h2>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-xs text-gray-400 border-b border-gray-100">
                {['Security', 'Quantity', 'Avg Cost', 'Current Price',
                  'Market Value', 'Gain / Loss', '%'].map(h => (
                  <th key={h} className="px-6 py-3 text-right first:text-left font-medium">
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {holdings.map(h => {
                const pos = h.unrealisedGain >= 0
                return (
                  <tr key={h.securityId}
                      className="border-b border-gray-50 last:border-0 hover:bg-gray-50/50">
                    <td className="px-6 py-3">
                      <div className="font-semibold text-gray-900">{h.ticker}</div>
                      <div className="text-xs text-gray-400">{h.exchange}</div>
                    </td>
                    <td className="px-6 py-3 text-right tabular-nums text-gray-700">
                      {h.quantity.toLocaleString()}
                    </td>
                    <td className="px-6 py-3 text-right tabular-nums text-gray-600">
                      {fmt(h.averageCostPerUnit, activePortfolio.baseCurrency)}
                    </td>
                    <td className="px-6 py-3 text-right tabular-nums text-gray-700">
                      {fmt(h.currentPrice, activePortfolio.baseCurrency)}
                    </td>
                    <td className="px-6 py-3 text-right tabular-nums font-medium text-gray-900">
                      {fmt(h.marketValue, activePortfolio.baseCurrency)}
                    </td>
                    <td className={clsx('px-6 py-3 text-right tabular-nums font-medium',
                        pos ? 'text-emerald-600' : 'text-red-500')}>
                      {pos ? '+' : ''}{fmt(h.unrealisedGain, activePortfolio.baseCurrency)}
                    </td>
                    <td className={clsx('px-6 py-3 text-right tabular-nums',
                        pos ? 'text-emerald-600' : 'text-red-500')}>
                      {pos ? '+' : ''}{pct(h.unrealisedGainPct)}
                    </td>
                  </tr>
                )
              })}
              {holdings.length === 0 && (
                <tr>
                  <td colSpan={7} className="px-6 py-10 text-center text-gray-400 text-sm">
                    No holdings yet — add a trade to get started.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}

// ── Helpers ───────────────────────────────────────────────────────────────────

function fmt(value: number | undefined, currency = 'AUD') {
  if (value === undefined || value === null) return '—'
  return new Intl.NumberFormat('en-AU', {
    style: 'currency',
    currency,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value)
}

function pct(value: number | undefined) {
  if (value === undefined || value === null) return '—'
  return `${(value * 100).toFixed(2)}%`
}

function StatCard({
  label, value, sub, positive, icon
}: {
  label: string
  value: string
  sub?: string
  positive?: boolean
  icon?: React.ReactNode
}) {
  return (
    <div className="bg-white rounded-xl border border-gray-200 p-5">
      <div className="flex items-center gap-2 text-gray-400 mb-3">
        {icon}
        <span className="text-xs font-medium uppercase tracking-wide">{label}</span>
      </div>
      <div className={clsx('text-2xl font-semibold',
        positive === undefined ? 'text-gray-900'
        : positive ? 'text-emerald-600' : 'text-red-500'
      )}>
        {value}
      </div>
      {sub && <div className="text-xs text-gray-400 mt-1">{sub}</div>}
    </div>
  )
}
