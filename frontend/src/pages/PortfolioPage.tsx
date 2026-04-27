import { useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { format, parseISO } from 'date-fns'
import { ChevronRight, TrendingUp, TrendingDown, Shield, Layers, Loader2, ChevronUp, ChevronDown } from 'lucide-react'
import clsx from 'clsx'
import {
  useHoldings,
  type Holding,
} from '../hooks/usePortfolios'
import { useQuery } from '@tanstack/react-query'
import api from '../lib/apiClient'

// ── Types specific to this page (not in shared hooks) ─────────────────────────

interface OpenParcel {
  parcelId: string
  ticker: string
  quantity: number
  costPerUnit: number
  totalCostBase: number
  acquisitionDate: string
  holdingDays: number
  cgtDiscountEligible: boolean
  currentPrice: number
  unrealisedGain: number
  unrealisedGainPct: number
}

// ── Hook ──────────────────────────────────────────────────────────────────────

function useOpenParcels(portfolioId: string | undefined) {
  return useQuery({
    queryKey: ['open-parcels', portfolioId],
    queryFn: async () => {
      const r = await api.get<OpenParcel[]>(
        `/v1/portfolios/${portfolioId}/tax/open-parcels`
      )
      return r.data
    },
    enabled: !!portfolioId,
  })
}

// =============================================================================
// Page
// =============================================================================

export default function PortfolioPage() {
  const { portfolioId } = useParams<{ portfolioId: string }>()
  const [selected, setSelected] = useState<string | null>(null)
  const [sort, setSort] = useState('ticker')
  const [sortDir, setSortDir] = useState<'asc' | 'desc'>('asc')
  const [showSold, setShowSold] = useState(false)

  const { data: holdings = [], isLoading } = useHoldings(portfolioId, sort, sortDir, showSold)
  const { data: allParcels = [] }          = useOpenParcels(portfolioId)

  const HOLDING_COLS = [
    { key: 'ticker', label: 'Security' },
    { key: 'quantity', label: 'Qty' },
    { key: 'currentPrice', label: 'Price' },
    { key: 'marketValue', label: 'Value' },
    { key: 'unrealisedGain', label: 'Gain' },
  ]

  const toggleSort = (key: string) => {
    if (sort === key) {
      setSortDir(sortDir === 'asc' ? 'desc' : 'asc')
    } else {
      setSort(key)
      setSortDir('asc')
    }
  }

  // Group parcels by ticker for the detail panel
  const parcelsByTicker = allParcels.reduce<Record<string, OpenParcel[]>>((acc, p) => {
    acc[p.ticker] = [...(acc[p.ticker] ?? []), p]
    return acc
  }, {})

  const selectedHolding = holdings.find(h => h.securityId === selected) ?? null
  const selectedParcels = selectedHolding
    ? (parcelsByTicker[selectedHolding.ticker] ?? [])
    : []

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-64 text-gray-400">
        <Loader2 size={20} className="animate-spin mr-2" /> Loading holdings…
      </div>
    )
  }

  return (
    <div className="p-8 space-y-5">
      {/* Breadcrumb */}
      <div className="flex items-center gap-2 text-sm text-gray-400">
        <Link to="/dashboard" className="hover:text-gray-600 transition-colors">
          Dashboard
        </Link>
        <ChevronRight size={14} />
        <span className="text-gray-700 font-medium">Holdings</span>
      </div>

      <h1 className="text-2xl font-semibold text-gray-900">Holdings</h1>

      {holdings.length > 0 && <SummaryBar holdings={holdings} />}

      <div className="flex gap-5">
        {/* Holdings list */}
        <div className="flex-1">
          <div className="bg-white rounded-xl border border-gray-200">
            <div className="px-5 py-3 border-b border-gray-100 flex items-center justify-between">
              <div className="flex items-center gap-3">
                <span className="text-sm font-semibold text-gray-700">
                  {holdings.length} position{holdings.length !== 1 ? 's' : ''}
                </span>
                <label className="flex items-center gap-2 text-xs cursor-pointer">
                  <input
                    type="checkbox"
                    checked={showSold}
                    onChange={e => setShowSold(e.target.checked)}
                    className="rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                  />
                  <span className="text-gray-500">Show sold</span>
                </label>
              </div>
              <div className="flex items-center gap-2 text-xs">
                <span className="text-gray-400">Sort:</span>
                {HOLDING_COLS.map(col => (
                  <button
                    key={col.key}
                    onClick={() => toggleSort(col.key)}
                    className={clsx(
                      'px-2 py-1 rounded transition-colors flex items-center gap-1',
                      sort === col.key ? 'bg-blue-100 text-blue-700' : 'hover:bg-gray-100 text-gray-500'
                    )}
                  >
                    {col.label}
                    {sort === col.key && (
                      sortDir === 'asc' ? '↑' : '↓'
                    )}
                  </button>
                ))}
              </div>
            </div>

            <div className="divide-y divide-gray-50">
              {holdings.map(h => (
                <HoldingRow
                  key={h.securityId}
                  holding={h}
                  isSelected={h.securityId === selected}
                  onSelect={() => setSelected(h.securityId === selected ? null : h.securityId)}
                  portfolioId={portfolioId!}
                />
              ))}

              {holdings.length === 0 && (
                <div className="py-16 text-center text-sm text-gray-400">
                  No holdings yet.{' '}
                  <Link
                    to={`/portfolio/${portfolioId}/trades`}
                    className="text-blue-500 hover:underline"
                  >
                    Add a trade
                  </Link>{' '}
                  to get started.
                </div>
              )}
            </div>
          </div>
        </div>

        {/* Parcel detail panel */}
        {selectedHolding && (
          <div className="w-80 flex-shrink-0">
            <ParcelPanel holding={selectedHolding} parcels={selectedParcels} />
          </div>
        )}
      </div>
    </div>
  )
}

// =============================================================================
// Portfolio summary bar
// =============================================================================

function SummaryBar({ holdings }: { holdings: Holding[] }) {
  const totalValue = holdings.reduce((s, h) => s + h.marketValue, 0)
  const totalCost  = holdings.reduce((s, h) => s + h.costBase, 0)
  const totalGain  = totalValue - totalCost
  const gainPct    = totalCost > 0 ? totalGain / totalCost : 0
  const pos        = totalGain >= 0

  return (
    <div className="grid grid-cols-3 gap-4">
      <SummaryCard label="Total Value" value={fmtCcy(totalValue)} />
      <SummaryCard label="Total Cost Base" value={fmtCcy(totalCost)} muted />
      <SummaryCard
        label="Unrealised Gain / Loss"
        value={`${pos ? '+' : ''}${fmtCcy(totalGain)}`}
        sub={`${pos ? '+' : ''}${(gainPct * 100).toFixed(1)}%`}
        positive={pos}
      />
    </div>
  )
}

function SummaryCard({
  label, value, sub, positive, muted,
}: {
  label: string; value: string; sub?: string; positive?: boolean; muted?: boolean
}) {
  return (
    <div className={clsx(
      'rounded-xl border px-5 py-4',
      positive === true  && 'bg-emerald-50 border-emerald-200',
      positive === false && 'bg-red-50 border-red-200',
      positive === undefined && 'bg-white border-gray-200'
    )}>
      <div className="text-xs text-gray-400 uppercase tracking-wide mb-1">{label}</div>
      <div className={clsx(
        'text-xl font-semibold',
        muted             ? 'text-gray-600'
        : positive === true  ? 'text-emerald-700'
        : positive === false ? 'text-red-600'
        :                      'text-gray-900'
      )}>
        {value}
      </div>
      {sub && <div className="text-xs text-gray-400 mt-0.5">{sub}</div>}
    </div>
  )
}

// =============================================================================
// Holding row
// =============================================================================

function HoldingRow({
  holding: h, isSelected, onSelect, portfolioId,
}: {
  holding: Holding; isSelected: boolean; onSelect: () => void; portfolioId: string
}) {
  const pos = h.unrealisedGain >= 0
  return (
    <button
      onClick={onSelect}
      className={clsx(
        'w-full flex items-center gap-4 px-5 py-4 text-left transition-colors',
        isSelected ? 'bg-blue-50' : 'hover:bg-gray-50/60'
      )}
    >
      {/* Ticker badge */}
      <div className="w-10 h-10 rounded-xl bg-gray-100 flex items-center justify-center
                      text-xs font-bold text-gray-600 flex-shrink-0 select-none">
        {h.ticker.slice(0, 3)}
      </div>

      {/* Name + exchange */}
      <div className="flex-1 min-w-0">
        <div className="font-semibold text-gray-900 text-sm">
          {h.ticker}
          <span className="text-xs text-gray-400 font-normal ml-1.5">{h.exchange}</span>
        </div>
        {h.securityName && (
          <div className="text-xs text-gray-400 truncate">{h.securityName}</div>
        )}
      </div>

      {/* Quantity + avg cost */}
      <div className="text-right hidden sm:block flex-shrink-0">
        <div className="text-sm text-gray-600 tabular-nums">
          {h.quantity.toLocaleString()} units
        </div>
        <div className="text-xs text-gray-400">avg {fmtCcy(h.averageCostPerUnit)}</div>
      </div>

      {/* Market value */}
      <div className="text-right w-28 flex-shrink-0">
        <div className="font-semibold text-gray-900 tabular-nums text-sm">
          {fmtCcy(h.marketValue)}
        </div>
        <div className="text-xs text-gray-400 tabular-nums">
          {fmtCcy(h.currentPrice)} / unit
        </div>
      </div>

      {/* Unrealised gain */}
      <div className={clsx('text-right w-24 flex-shrink-0', pos ? 'text-emerald-600' : 'text-red-500')}>
        <div className="text-sm font-medium tabular-nums">
          {pos ? '+' : ''}{fmtCcy(h.unrealisedGain)}
        </div>
        <div className="text-xs tabular-nums">
          {pos ? '+' : ''}{(h.unrealisedGainPct * 100).toFixed(1)}%
        </div>
      </div>

      <ChevronRight size={14} className={clsx(
        'text-gray-300 flex-shrink-0 transition-transform duration-150',
        isSelected && 'rotate-90 text-blue-400'
      )} />
    </button>
  )
}

// =============================================================================
// Parcel detail panel
// =============================================================================

function ParcelPanel({
  holding, parcels,
}: {
  holding: Holding; parcels: OpenParcel[]
}) {
  return (
    <div className="bg-white rounded-xl border border-gray-200 sticky top-8">
      {/* Header */}
      <div className="px-5 py-4 border-b border-gray-100">
        <div className="flex items-center gap-2 mb-1">
          <Layers size={15} className="text-gray-400" />
          <span className="text-sm font-semibold text-gray-700">Tax Parcels</span>
        </div>
        <div className="text-xs text-gray-400">
          {holding.ticker} · {parcels.length} open parcel{parcels.length !== 1 ? 's' : ''}
        </div>
      </div>

      {/* Parcel list */}
      <div className="divide-y divide-gray-50 max-h-[420px] overflow-y-auto">
        {parcels.map(p => {
          const pos = p.unrealisedGain >= 0
          const pct = Math.min(100, Math.abs(p.unrealisedGainPct) * 100)

          return (
            <div key={p.parcelId} className="px-5 py-3">
              <div className="flex items-start justify-between mb-1.5">
                <div>
                  <div className="text-sm font-medium text-gray-900 tabular-nums">
                    {p.quantity.toLocaleString()} units
                  </div>
                  <div className="text-xs text-gray-400 mt-0.5">
                    {format(parseISO(p.acquisitionDate), 'dd MMM yyyy')}
                    {' · '}
                    <span className="tabular-nums">{p.holdingDays}d</span>
                  </div>
                </div>
                <div className="text-right">
                  <div className={clsx(
                    'text-sm font-medium tabular-nums',
                    pos ? 'text-emerald-600' : 'text-red-500'
                  )}>
                    {pos ? '+' : ''}{fmtCcy(p.unrealisedGain)}
                  </div>
                  <div className="text-xs text-gray-400 tabular-nums">
                    cost {fmtCcy(p.costPerUnit)}
                  </div>
                </div>
              </div>

              {/* Progress bar */}
              <div className="flex items-center gap-2">
                <div className="flex-1 bg-gray-100 rounded-full h-1">
                  <div
                    className={clsx('h-1 rounded-full transition-all',
                      pos ? 'bg-emerald-400' : 'bg-red-400'
                    )}
                    style={{ width: `${pct}%` }}
                  />
                </div>
                {p.cgtDiscountEligible ? (
                  <span className="inline-flex items-center gap-0.5 text-xs text-emerald-600 flex-shrink-0">
                    <Shield size={10} /> 50%
                  </span>
                ) : (
                  <span className="text-xs text-gray-400 flex-shrink-0 tabular-nums">
                    {Math.max(0, 366 - p.holdingDays)}d left
                  </span>
                )}
              </div>
            </div>
          )
        })}

        {parcels.length === 0 && (
          <div className="px-5 py-8 text-center text-xs text-gray-400">
            No open parcels for this holding.
          </div>
        )}
      </div>

      {/* Totals footer */}
      <div className="px-5 py-4 border-t border-gray-100 bg-gray-50 rounded-b-xl space-y-1.5">
        <div className="flex justify-between text-sm">
          <span className="text-gray-500">Total cost base</span>
          <span className="font-medium text-gray-700 tabular-nums">
            {fmtCcy(holding.costBase)}
          </span>
        </div>
        <div className="flex justify-between text-sm">
          <span className="text-gray-500">Market value</span>
          <span className="font-semibold text-gray-900 tabular-nums">
            {fmtCcy(holding.marketValue)}
          </span>
        </div>
        <div className="flex justify-between text-sm">
          <span className="text-gray-500">Unrealised</span>
          <span className={clsx(
            'font-medium tabular-nums',
            holding.unrealisedGain >= 0 ? 'text-emerald-600' : 'text-red-500'
          )}>
            {holding.unrealisedGain >= 0 ? '+' : ''}
            {fmtCcy(holding.unrealisedGain)}
          </span>
        </div>
      </div>
    </div>
  )
}

// ── Formatters ────────────────────────────────────────────────────────────────

function fmtCcy(v: number, currency = 'AUD') {
  return new Intl.NumberFormat('en-AU', {
    style: 'currency', currency,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(v)
}
