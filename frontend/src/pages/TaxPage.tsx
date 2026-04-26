import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { format, parseISO } from 'date-fns'
import { Download, TrendingUp, TrendingDown, Shield, AlertCircle, Loader2, ChevronDown, ChevronUp } from 'lucide-react'
import clsx from 'clsx'
import api from '../lib/apiClient'

// ── Types ─────────────────────────────────────────────────────────────────────

interface CgtSummary {
  financialYear: number
  shortTermGains: number
  longTermGains: number
  totalDiscountableGains: number
  totalCurrentYearLosses: number
  priorYearLossesApplied: number
  netAssessableCgt: number
  lossesCarriedForward: number
}

interface CgtEvent {
  disposalDate: string
  ticker: string
  quantity: number
  proceeds: number
  costBase: number
  capitalGain: number
  discountApplied: boolean
  assessableGain: number
  acquisitionDate: string
  holdingDays: number
}

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

// Current ATO financial year: runs 1 Jul – 30 Jun
function currentFY(): number {
  const now = new Date()
  return now.getMonth() >= 6 ? now.getFullYear() + 1 : now.getFullYear()
}

const FY_OPTIONS = Array.from({ length: 5 }, (_, i) => currentFY() - i)

// ── Page ──────────────────────────────────────────────────────────────────────

export default function TaxPage() {
  const { portfolioId } = useParams<{ portfolioId: string }>()
  const [fy, setFy]     = useState(currentFY())
  const [tab, setTab]   = useState<'summary' | 'events' | 'parcels'>('summary')
  const [eventSort, setEventSort] = useState('disposalDate')
  const [eventSortDir, setEventSortDir] = useState<'asc' | 'desc'>('desc')
  const [parcelSort, setParcelSort] = useState('acquisitionDate')
  const [parcelSortDir, setParcelSortDir] = useState<'asc' | 'desc'>('desc')

  const toggleEventSort = (field: string) => {
    if (eventSort === field) {
      setEventSortDir(eventSortDir === 'asc' ? 'desc' : 'asc')
    } else {
      setEventSort(field)
      setEventSortDir('desc')
    }
  }

  const toggleParcelSort = (field: string) => {
    if (parcelSort === field) {
      setParcelSortDir(parcelSortDir === 'asc' ? 'desc' : 'asc')
    } else {
      setParcelSort(field)
      setParcelSortDir('desc')
    }
  }

  const summaryQ = useQuery({
    queryKey: ['cgt-summary', portfolioId, fy],
    queryFn: async () => {
      const r = await api.get<CgtSummary>(`/v1/portfolios/${portfolioId}/tax/cgt-summary`, { params: { financialYear: fy } })
      return r.data
    },
    enabled: !!portfolioId,
  })

  const eventsQ = useQuery({
    queryKey: ['cgt-events', portfolioId, fy, eventSort, eventSortDir],
    queryFn: async () => {
      const r = await api.get<CgtEvent[]>(`/v1/portfolios/${portfolioId}/tax/cgt-events`, { params: { financialYear: fy, sort: eventSort, sortDir: eventSortDir } })
      return r.data
    },
    enabled: !!portfolioId && tab === 'events',
  })

  const parcelsQ = useQuery({
    queryKey: ['open-parcels', portfolioId, parcelSort, parcelSortDir],
    queryFn: async () => {
      const r = await api.get<OpenParcel[]>(`/v1/portfolios/${portfolioId}/tax/open-parcels`, { params: { sort: parcelSort, sortDir: parcelSortDir } })
      return r.data
    },
    enabled: !!portfolioId && tab === 'parcels',
  })

  const handleCsvDownload = async () => {
    const r = await api.get(`/v1/portfolios/${portfolioId}/tax/report/csv`,
      { params: { financialYear: fy }, responseType: 'blob' })
    triggerDownload(r.data, `cgt-events-FY${fy}.csv`, 'text/csv')
  }

  const handlePdfDownload = async () => {
    const r = await api.get(`/v1/portfolios/${portfolioId}/tax/report/pdf`,
      { params: { financialYear: fy }, responseType: 'blob' })
    triggerDownload(r.data, `cgt-report-FY${fy}.pdf`, 'application/pdf')
  }

  const triggerDownload = (data: Blob, filename: string, type: string) => {
    const url  = window.URL.createObjectURL(new Blob([data], { type }))
    const link = document.createElement('a')
    link.href = url
    link.setAttribute('download', filename)
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.URL.revokeObjectURL(url)
  }

  return (
    <div className="p-8 space-y-6">
      {/* ── Header ────────────────────────────────────────────────────── */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-gray-900">Tax & CGT</h1>
          <p className="text-sm text-gray-500 mt-0.5">Australian Capital Gains Tax — FY {fy - 1}–{fy}</p>
        </div>
        <div className="flex items-center gap-3">
          {/* FY selector */}
          <div className="relative">
            <select
              value={fy}
              onChange={e => setFy(Number(e.target.value))}
              className="appearance-none pl-3 pr-8 py-2 text-sm border border-gray-300 rounded-lg
                         focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white text-gray-700"
            >
              {FY_OPTIONS.map(y => (
                <option key={y} value={y}>FY {y - 1}–{y}</option>
              ))}
            </select>
            <ChevronDown size={14} className="absolute right-2.5 top-1/2 -translate-y-1/2 text-gray-400 pointer-events-none" />
          </div>
          <button
            onClick={handlePdfDownload}
            className="flex items-center gap-1.5 px-3 py-2 text-sm text-white
                       bg-blue-600 rounded-lg hover:bg-blue-700 transition-colors"
          >
            <Download size={14} /> PDF Report
          </button>
          <button
            onClick={handleCsvDownload}
            className="flex items-center gap-1.5 px-3 py-2 text-sm text-gray-600
                       border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors"
          >
            <Download size={14} /> Export CSV
          </button>
        </div>
      </div>

      {/* ── CGT Summary Cards ─────────────────────────────────────────── */}
      {summaryQ.isLoading ? (
        <div className="flex items-center gap-2 text-gray-400 py-4">
          <Loader2 size={16} className="animate-spin" /> Loading CGT summary…
        </div>
      ) : summaryQ.data ? (
        <CgtSummaryCards summary={summaryQ.data} />
      ) : null}

      {/* ── Disclaimer ────────────────────────────────────────────────── */}
      <div className="flex items-start gap-3 px-4 py-3 bg-amber-50 border border-amber-200 rounded-xl text-sm text-amber-800">
        <AlertCircle size={16} className="flex-shrink-0 mt-0.5" />
        <span>
          These calculations are indicative only. Verify all amounts with a registered tax agent
          before lodging your return. Return of capital adjustments and corporate actions may affect
          your actual cost base.
        </span>
      </div>

      {/* ── Tabs ──────────────────────────────────────────────────────── */}
      <div className="border-b border-gray-200">
        <nav className="flex gap-6 -mb-px">
          {(['summary', 'events', 'parcels'] as const).map(t => (
            <button
              key={t}
              onClick={() => setTab(t)}
              className={clsx(
                'py-3 text-sm font-medium border-b-2 transition-colors',
                tab === t
                  ? 'border-blue-600 text-blue-600'
                  : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
              )}
            >
              {t === 'summary' ? 'CGT Breakdown'
               : t === 'events' ? `Disposal Events FY${fy}`
               : 'Open Parcels'}
            </button>
          ))}
        </nav>
      </div>

      {/* ── Tab content ───────────────────────────────────────────────── */}
      {tab === 'summary' && summaryQ.data && (
        <CgtBreakdownTable summary={summaryQ.data} />
      )}
      {tab === 'events' && (
        <CgtEventsTable 
          events={eventsQ.data ?? []} 
          isLoading={eventsQ.isLoading}
          sort={eventSort}
          sortDir={eventSortDir}
          onSort={toggleEventSort}
        />
      )}
      {tab === 'parcels' && (
        <OpenParcelsTable 
          parcels={parcelsQ.data ?? []} 
          isLoading={parcelsQ.isLoading}
          sort={parcelSort}
          sortDir={parcelSortDir}
          onSort={toggleParcelSort}
        />
      )}
    </div>
  )
}

// ── CGT Summary Cards ─────────────────────────────────────────────────────────

function CgtSummaryCards({ summary }: { summary: CgtSummary }) {
  const net = summary.netAssessableCgt
  const netPositive = net >= 0
  const discountAmount = summary.totalDiscountableGains * 0.5
  return (
    <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-3">
      <TaxCard
        label="Short Term"
        value={fmtCcy(summary.shortTermGains || 0)}
        icon={<TrendingUp size={15} />}
        positive
      />
      <TaxCard
        label="Long Term"
        value={fmtCcy(summary.longTermGains || 0)}
        icon={<TrendingUp size={15} />}
        positive
      />
      <TaxCard
        label="Losses"
        value={fmtCcy(summary.totalCurrentYearLosses || 0)}
        icon={<TrendingDown size={15} />}
        positive={false}
      />
      <TaxCard
        label="CGT Disc."
        value={`− ${fmtCcy(discountAmount)}`}
        icon={<Shield size={15} />}
        neutral
      />
      <TaxCard
        label="Net CGT"
        value={fmtCcy(net)}
        positive={netPositive}
        icon={netPositive ? <TrendingUp size={15} /> : <TrendingDown size={15} />}
        highlight
      />
    </div>
  )
}

// ── CGT Breakdown Table ───────────────────────────────────────────────────────

function CgtBreakdownTable({ summary }: { summary: CgtSummary }) {
  const discountAmount = (summary.totalDiscountableGains || 0) * 0.5
  const currentYrLosses = summary.totalCurrentYearLosses || 0
  const priorLosses = summary.priorYearLossesApplied ?? 0
  const rows: ({ label: string; value: number; positive?: boolean; indent?: boolean; bold?: boolean } | null)[] = [
    { label: 'Short term capital gains',    value: summary.shortTermGains || 0,            positive: true },
    { label: 'Long term capital gains',    value: summary.longTermGains || 0,            positive: true },
    { label: 'of which: discountable gains',   value: summary.totalDiscountableGains,    positive: true, indent: true },
    { label: 'Less: current year losses',      value: -currentYrLosses,     positive: false },
    { label: 'Less: prior year losses applied',value: -priorLosses,   positive: false },
    { label: 'Less: 50% CGT discount',         value: -discountAmount,                  positive: false },
    null,
    { label: 'Net assessable capital gain',    value: summary.netAssessableCgt,           positive: summary.netAssessableCgt >= 0, bold: true },
    { label: 'Losses carried to next year',    value: summary.lossesCarriedForward || 0,       positive: false },
  ]

  return (
    <div className="bg-white rounded-xl border border-gray-200 overflow-hidden max-w-2xl">
      <div className="px-6 py-4 border-b border-gray-100">
        <h2 className="text-sm font-semibold text-gray-700">
          CGT Calculation — FY {summary.financialYear - 1}–{summary.financialYear}
        </h2>
      </div>
      <table className="w-full text-sm">
        <tbody>
          {rows.map((row, i) =>
            row === null ? (
              <tr key={i}><td colSpan={2} className="border-t border-gray-200" /></tr>
            ) : (
              <tr key={i} className={clsx('border-b border-gray-50 last:border-0', row.bold && 'bg-gray-50')}>
                <td className={clsx('px-6 py-3 text-gray-600', row.indent && 'pl-10 text-gray-400')}>
                  {row.label}
                </td>
                <td className={clsx(
                  'px-6 py-3 text-right tabular-nums',
                  row.bold ? 'font-semibold text-gray-900' : 'text-gray-700',
                  row.value > 0 && !row.bold && 'text-emerald-600',
                  row.value < 0 && 'text-red-500'
                )}>
                  {row.value >= 0 ? fmtCcy(row.value) : `(${fmtCcy(Math.abs(row.value))})`}
                </td>
              </tr>
            )
          )}
        </tbody>
      </table>
    </div>
  )
}

// ── CGT Events Table ──────────────────────────────────────────────────────────

function CgtEventsTable({ events, isLoading, sort, sortDir, onSort }: { 
  events: CgtEvent[]; 
  isLoading: boolean;
  sort: string;
  sortDir: 'asc' | 'desc';
  onSort: (field: string) => void;
}) {
  if (isLoading) return <TableLoader text="Loading disposal events…" />

  const EVENT_COLS = [
    { key: 'ticker', label: 'Ticker', align: 'left' },
    { key: 'disposalDate', label: 'Disposal', align: 'right' },
    { key: 'acquisitionDate', label: 'Acquisition', align: 'right' },
    { key: 'holdingDays', label: 'Days', align: 'right' },
    { key: 'quantity', label: 'Quantity', align: 'right' },
    { key: 'proceeds', label: 'Proceeds', align: 'right' },
    { key: 'costBase', label: 'Cost Base', align: 'right' },
    { key: 'capitalGain', label: 'Gain / Loss', align: 'right' },
    { key: 'discountApplied', label: 'Discount', align: 'right' },
    { key: 'assessableGain', label: 'Assessable', align: 'right' },
  ]

  return (
    <div className="bg-white rounded-xl border border-gray-200">
      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="text-xs text-gray-400 border-b border-gray-100">
              {EVENT_COLS.map(col => (
                <th key={col.key}
                  className={`px-4 py-3 text-${col.align} font-medium whitespace-nowrap cursor-pointer hover:text-blue-600 select-none`}
                  onClick={() => onSort(col.key)}>
                  <span className="flex items-center gap-1 justify-between">
                    {col.label}
                    {sort === col.key && (
                      sortDir === 'asc' ? <ChevronUp size={14} /> : <ChevronDown size={14} />
                    )}
                  </span>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {events.map((e, i) => {
              const isGain = e.capitalGain >= 0
              return (
                <tr key={i} className="border-b border-gray-50 last:border-0 hover:bg-gray-50/40">
                  <td className="px-4 py-3 font-semibold text-gray-900">{e.ticker}</td>
                  <td className="px-4 py-3 text-right text-gray-500 whitespace-nowrap">
                    {format(parseISO(e.disposalDate), 'dd MMM yyyy')}
                  </td>
                  <td className="px-4 py-3 text-right text-gray-400 whitespace-nowrap">
                    {format(parseISO(e.acquisitionDate), 'dd MMM yyyy')}
                  </td>
                  <td className="px-4 py-3 text-right text-gray-400">{e.holdingDays}</td>
                  <td className="px-4 py-3 text-right tabular-nums text-gray-600">{e.quantity.toLocaleString()}</td>
                  <td className="px-4 py-3 text-right tabular-nums text-gray-700">{fmtCcy(e.proceeds)}</td>
                  <td className="px-4 py-3 text-right tabular-nums text-gray-600">{fmtCcy(e.costBase)}</td>
                  <td className={clsx('px-4 py-3 text-right tabular-nums font-medium',
                    isGain ? 'text-emerald-600' : 'text-red-500')}>
                    {isGain ? '+' : ''}{fmtCcy(e.capitalGain)}
                  </td>
                  <td className="px-4 py-3 text-right">
                    {e.discountApplied
                      ? <span className="inline-block px-1.5 py-0.5 text-xs bg-emerald-50 text-emerald-700 rounded">50%</span>
                      : <span className="text-gray-300">—</span>}
                  </td>
                  <td className={clsx('px-4 py-3 text-right tabular-nums font-semibold',
                    e.assessableGain >= 0 ? 'text-gray-900' : 'text-red-500')}>
                    {fmtCcy(e.assessableGain)}
                  </td>
                </tr>
              )
            })}
            {events.length === 0 && (
              <tr><td colSpan={10} className="px-4 py-10 text-center text-gray-400">
                No disposal events for this financial year.
              </td></tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  )
}

// ── Open Parcels Table ────────────────────────────────────────────────────────

function OpenParcelsTable({ parcels, isLoading, sort, sortDir, onSort }: { 
  parcels: OpenParcel[]; 
  isLoading: boolean;
  sort: string;
  sortDir: 'asc' | 'desc';
  onSort: (field: string) => void;
}) {
  if (isLoading) return <TableLoader text="Loading open parcels…" />

  const PARCEL_COLS = [
    { key: 'ticker', label: 'Ticker', align: 'left' },
    { key: 'quantity', label: 'Quantity', align: 'right' },
    { key: 'costPerUnit', label: 'Cost/Unit', align: 'right' },
    { key: 'totalCostBase', label: 'Cost Base', align: 'right' },
    { key: 'acquisitionDate', label: 'Acquired', align: 'right' },
    { key: 'holdingDays', label: 'Days', align: 'right' },
    { key: 'cgtDiscountEligible', label: 'Discount', align: 'right' },
    { key: 'currentPrice', label: 'Current Price', align: 'right' },
    { key: 'unrealisedGain', label: 'Unrealised', align: 'right' },
  ]

  return (
    <div className="bg-white rounded-xl border border-gray-200">
      <div className="px-6 py-3 border-b border-gray-100 flex items-center justify-between">
        <span className="text-sm font-semibold text-gray-700">Open Tax Parcels</span>
        <span className="text-xs text-gray-400">{parcels.length} parcels</span>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="text-xs text-gray-400 border-b border-gray-100">
              {PARCEL_COLS.map(col => (
                <th key={col.key}
                  className={`px-4 py-3 text-${col.align} font-medium whitespace-nowrap cursor-pointer hover:text-blue-600 select-none`}
                  onClick={() => onSort(col.key)}>
                  <span className="flex items-center gap-1 justify-between">
                    {col.label}
                    {sort === col.key && (
                      sortDir === 'asc' ? <ChevronUp size={14} /> : <ChevronDown size={14} />
                    )}
                  </span>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {parcels.map(p => {
              const pos = p.unrealisedGain >= 0
              return (
                <tr key={p.parcelId} className="border-b border-gray-50 last:border-0 hover:bg-gray-50/40">
                  <td className="px-4 py-3 font-semibold text-gray-900">{p.ticker}</td>
                  <td className="px-4 py-3 text-right tabular-nums text-gray-700">{p.quantity.toLocaleString()}</td>
                  <td className="px-4 py-3 text-right tabular-nums text-gray-600">{fmtCcy(p.costPerUnit)}</td>
                  <td className="px-4 py-3 text-right tabular-nums text-gray-700">{fmtCcy(p.totalCostBase)}</td>
                  <td className="px-4 py-3 text-right text-gray-500 whitespace-nowrap">
                    {format(parseISO(p.acquisitionDate), 'dd MMM yyyy')}
                  </td>
                  <td className="px-4 py-3 text-right text-gray-400">{p.holdingDays}</td>
                  <td className="px-4 py-3 text-right">
                    {p.cgtDiscountEligible
                      ? <span className="inline-flex items-center gap-1 px-1.5 py-0.5 text-xs bg-emerald-50 text-emerald-700 rounded">
                          <Shield size={9} /> Eligible
                        </span>
                      : <span className="text-xs text-gray-400">{365 - p.holdingDays}d to go</span>}
                  </td>
                  <td className="px-4 py-3 text-right tabular-nums text-gray-700">{fmtCcy(p.currentPrice)}</td>
                  <td className={clsx('px-4 py-3 text-right tabular-nums font-medium',
                    pos ? 'text-emerald-600' : 'text-red-500')}>
                    {pos ? '+' : ''}{fmtCcy(p.unrealisedGain)}
                    <span className="text-xs ml-1 opacity-70">
                      ({pos ? '+' : ''}{((p.unrealisedGainPct ?? 0) * 100).toFixed(1)}%)
                    </span>
                  </td>
                </tr>
              )
            })}
            {parcels.length === 0 && (
              <tr><td colSpan={9} className="px-4 py-10 text-center text-gray-400">
                No open parcels. Add a BUY trade to get started.
              </td></tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  )
}

// ── Small components ──────────────────────────────────────────────────────────

function TaxCard({ label, value, icon, positive, neutral, highlight, sub }:
  { label: string; value: string; icon?: React.ReactNode; positive?: boolean;
    neutral?: boolean; highlight?: boolean; sub?: string }) {
  return (
    <div className={clsx('rounded-xl border p-5',
      highlight ? 'bg-blue-50 border-blue-200' : 'bg-white border-gray-200')}>
      <div className="flex items-center gap-2 text-gray-400 mb-3">
        {icon}
        <span className="text-xs font-medium uppercase tracking-wide">{label}</span>
      </div>
      <div className={clsx('text-xl font-semibold',
        neutral   ? 'text-gray-700'
        : positive ? 'text-emerald-600'
        :            'text-red-500')}>
        {value}
      </div>
      {sub && <div className="text-xs text-gray-400 mt-1">{sub}</div>}
    </div>
  )
}

function TableLoader({ text }: { text: string }) {
  return (
    <div className="flex items-center justify-center py-16 text-gray-400">
      <Loader2 size={18} className="animate-spin mr-2" /> {text}
    </div>
  )
}

function fmtCcy(value: number | undefined | null, currency = 'AUD') {
  const num = value ?? 0
  return new Intl.NumberFormat('en-AU', {
    style: 'currency', currency,
    minimumFractionDigits: 2, maximumFractionDigits: 2,
  }).format(Number.isNaN(num) ? 0 : num)
}
