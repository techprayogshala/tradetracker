import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { format, parseISO } from 'date-fns'
import { Plus, Search, Download, TrendingUp, TrendingDown,
         ArrowLeftRight, DollarSign, X, Loader2 } from 'lucide-react'
import clsx from 'clsx'
import api from '../lib/apiClient'

// ── Types ─────────────────────────────────────────────────────────────────────

interface Trade {
  id: string
  ticker: string
  exchange: string
  tradeType: 'BUY' | 'SELL' | 'DIVIDEND' | 'RETURN_OF_CAPITAL' | 'TRANSFER_IN' | 'TRANSFER_OUT'
  quantity: number
  price: number
  fees: number
  totalCost: number
  currency: string
  tradeDate: string
  source: string
  notes?: string
}

interface TradesPage { content: Trade[]; totalElements: number; totalPages: number }

interface TradeFormData {
  ticker: string; exchange: string; tradeType: string
  quantity: string; price: string; fees: string
  currency: string; tradeDate: string; notes: string
}

const TRADE_TYPES = ['BUY', 'SELL', 'DIVIDEND', 'RETURN_OF_CAPITAL', 'TRANSFER_IN', 'TRANSFER_OUT']
const EXCHANGES   = ['ASX', 'NYSE', 'NASDAQ', 'LSE', 'TSX', 'HKEX', 'OTHER']
const CURRENCIES  = ['AUD', 'USD', 'GBP', 'EUR', 'HKD', 'CAD', 'JPY']

// ── Page ──────────────────────────────────────────────────────────────────────

export default function TradesPage() {
  const { portfolioId } = useParams<{ portfolioId: string }>()
  const [showForm, setShowForm]     = useState(false)
  const [search, setSearch]         = useState('')
  const [typeFilter, setTypeFilter] = useState('')
  const [page, setPage]             = useState(0)

  const { data, isLoading } = useQuery({
    queryKey: ['trades', portfolioId, search, typeFilter, page],
    queryFn: async () => {
      const res = await api.get<TradesPage>(`/v1/portfolios/${portfolioId}/trades`, {
        params: { ticker: search || undefined, tradeType: typeFilter || undefined,
                  page, size: 25, sort: 'tradeDate,desc' },
      })
      return res.data
    },
    enabled: !!portfolioId,
  })

  return (
    <div className="p-8 space-y-5">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-gray-900">Trades</h1>
          <p className="text-sm text-gray-500 mt-0.5">{data?.totalElements ?? 0} transactions</p>
        </div>
        <div className="flex items-center gap-2">
          <button className="flex items-center gap-1.5 px-3 py-2 text-sm text-gray-600
                             border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors">
            <Download size={14} /> Export
          </button>
          <button
            onClick={() => setShowForm(true)}
            className="flex items-center gap-1.5 px-3 py-2 text-sm text-white
                       bg-blue-600 rounded-lg hover:bg-blue-700 transition-colors"
          >
            <Plus size={14} /> Add trade
          </button>
        </div>
      </div>

      {/* Filters */}
      <div className="flex items-center gap-3">
        <div className="relative flex-1 max-w-xs">
          <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
          <input
            type="text" placeholder="Search ticker…" value={search}
            onChange={e => { setSearch(e.target.value); setPage(0) }}
            className="w-full pl-8 pr-3 py-2 text-sm border border-gray-300 rounded-lg
                       focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>
        <select
          value={typeFilter} onChange={e => { setTypeFilter(e.target.value); setPage(0) }}
          className="px-3 py-2 text-sm border border-gray-300 rounded-lg focus:outline-none
                     focus:ring-2 focus:ring-blue-500 bg-white text-gray-700"
        >
          <option value="">All types</option>
          {TRADE_TYPES.map(t => <option key={t} value={t}>{fmtType(t)}</option>)}
        </select>
      </div>

      {/* Table */}
      <div className="bg-white rounded-xl border border-gray-200">
        {isLoading ? (
          <div className="flex items-center justify-center py-16 text-gray-400">
            <Loader2 size={20} className="animate-spin mr-2" /> Loading trades…
          </div>
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-xs text-gray-400 border-b border-gray-100">
                    {['Date','Security','Type','Quantity','Price','Fees','Total','Source'].map(h => (
                      <th key={h} className="px-5 py-3 text-right first:text-left font-medium whitespace-nowrap">{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {data?.content.map(trade => <TradeRow key={trade.id} trade={trade} />)}
                  {data?.content.length === 0 && (
                    <tr><td colSpan={8} className="px-5 py-12 text-center text-gray-400">
                      No trades found. Click "Add trade" to record your first transaction.
                    </td></tr>
                  )}
                </tbody>
              </table>
            </div>
            {(data?.totalPages ?? 0) > 1 && (
              <div className="px-5 py-3 border-t border-gray-100 flex items-center justify-between">
                <span className="text-xs text-gray-400">Page {page + 1} of {data?.totalPages}</span>
                <div className="flex gap-1">
                  <PagerBtn onClick={() => setPage(p => p - 1)} disabled={page === 0}>Prev</PagerBtn>
                  <PagerBtn onClick={() => setPage(p => p + 1)} disabled={page >= (data?.totalPages ?? 1) - 1}>Next</PagerBtn>
                </div>
              </div>
            )}
          </>
        )}
      </div>

      {showForm && <AddTradeModal portfolioId={portfolioId!} onClose={() => setShowForm(false)} />}
    </div>
  )
}

// ── Trade row ─────────────────────────────────────────────────────────────────

function TradeRow({ trade }: { trade: Trade }) {
  const isBuy = trade.tradeType === 'BUY'
  const isSell = trade.tradeType === 'SELL'
  const isDividend = trade.tradeType === 'DIVIDEND'
  return (
    <tr className="border-b border-gray-50 last:border-0 hover:bg-gray-50/50 transition-colors">
      <td className="px-5 py-3 text-gray-500 whitespace-nowrap">{format(parseISO(trade.tradeDate), 'dd MMM yyyy')}</td>
      <td className="px-5 py-3">
        <span className="font-semibold text-gray-900">{trade.ticker}</span>
        <span className="text-xs text-gray-400 ml-1">{trade.exchange}</span>
      </td>
      <td className="px-5 py-3">
        <span className={clsx('inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium',
          isBuy && 'bg-emerald-50 text-emerald-700',
          isSell && 'bg-red-50 text-red-600',
          isDividend && 'bg-blue-50 text-blue-700',
          !isBuy && !isSell && !isDividend && 'bg-gray-100 text-gray-600'
        )}>
          {isBuy && <TrendingUp size={10} />}
          {isSell && <TrendingDown size={10} />}
          {isDividend && <DollarSign size={10} />}
          {!isBuy && !isSell && !isDividend && <ArrowLeftRight size={10} />}
          {fmtType(trade.tradeType)}
        </span>
      </td>
      <td className="px-5 py-3 text-right tabular-nums text-gray-700">{trade.quantity.toLocaleString()}</td>
      <td className="px-5 py-3 text-right tabular-nums text-gray-600">{fmtCcy(trade.price, trade.currency)}</td>
      <td className="px-5 py-3 text-right tabular-nums text-gray-500">{fmtCcy(trade.fees, trade.currency)}</td>
      <td className="px-5 py-3 text-right tabular-nums font-medium text-gray-900">{fmtCcy(trade.totalCost, trade.currency)}</td>
      <td className="px-5 py-3 text-right">
        <span className="text-xs text-gray-400 capitalize">{trade.source.toLowerCase().replace('_', ' ')}</span>
      </td>
    </tr>
  )
}

// ── Add trade modal ───────────────────────────────────────────────────────────

function AddTradeModal({ portfolioId, onClose }: { portfolioId: string; onClose: () => void }) {
  const qc = useQueryClient()
  const [form, setForm] = useState<TradeFormData>({
    ticker: '', exchange: 'ASX', tradeType: 'BUY',
    quantity: '', price: '', fees: '9.95',
    currency: 'AUD', tradeDate: format(new Date(), 'yyyy-MM-dd'), notes: '',
  })
  const [errors, setErrors] = useState<Partial<TradeFormData>>({})

  const mutation = useMutation({
    mutationFn: (d: TradeFormData) => api.post(`/v1/portfolios/${portfolioId}/trades`, {
      ticker: d.ticker.toUpperCase(), exchange: d.exchange, tradeType: d.tradeType,
      quantity: parseFloat(d.quantity), price: parseFloat(d.price),
      fees: parseFloat(d.fees || '0'), currency: d.currency,
      tradeDate: d.tradeDate, notes: d.notes || undefined,
    }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['trades', portfolioId] })
      qc.invalidateQueries({ queryKey: ['holdings', portfolioId] })
      qc.invalidateQueries({ queryKey: ['portfolios'] })
      onClose()
    },
  })

  const set = (field: keyof TradeFormData) => (
    e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>
  ) => { setForm(f => ({ ...f, [field]: e.target.value })); setErrors(e => ({ ...e, [field]: undefined })) }

  const validate = () => {
    const errs: Partial<TradeFormData> = {}
    if (!form.ticker.trim()) errs.ticker = 'Required'
    if (!form.quantity || Number(form.quantity) <= 0) errs.quantity = 'Must be > 0'
    if (form.price === '' || Number(form.price) < 0) errs.price = 'Must be ≥ 0'
    if (!form.tradeDate) errs.tradeDate = 'Required'
    setErrors(errs)
    return Object.keys(errs).length === 0
  }

  const totalCost = (parseFloat(form.quantity) || 0) * (parseFloat(form.price) || 0) + (parseFloat(form.fees) || 0)

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30 backdrop-blur-sm">
      <div className="bg-white rounded-2xl shadow-xl w-full max-w-lg mx-4 max-h-[90vh] overflow-y-auto">
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100 sticky top-0 bg-white">
          <h2 className="text-base font-semibold text-gray-900">Add trade</h2>
          <button onClick={onClose} className="p-1 rounded-lg hover:bg-gray-100 transition-colors">
            <X size={16} className="text-gray-500" />
          </button>
        </div>

        <div className="px-6 py-5 space-y-4">
          {/* Trade type */}
          <div>
            <label className="block text-xs font-medium text-gray-500 mb-1.5">Type</label>
            <div className="flex gap-1 flex-wrap">
              {TRADE_TYPES.map(t => (
                <button key={t} onClick={() => setForm(f => ({ ...f, tradeType: t }))}
                  className={clsx('px-3 py-1.5 text-xs rounded-lg font-medium transition-colors',
                    form.tradeType === t ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                  )}>
                  {fmtType(t)}
                </button>
              ))}
            </div>
          </div>

          {/* Ticker + Exchange */}
          <div className="grid grid-cols-3 gap-3">
            <div className="col-span-2">
              <Field label="Ticker" error={errors.ticker}>
                <input type="text" placeholder="e.g. CBA" value={form.ticker} onChange={set('ticker')}
                  className={inputCls(!!errors.ticker)} style={{ textTransform: 'uppercase' }} />
              </Field>
            </div>
            <Field label="Exchange">
              <select value={form.exchange} onChange={set('exchange')} className={inputCls()}>
                {EXCHANGES.map(e => <option key={e}>{e}</option>)}
              </select>
            </Field>
          </div>

          {/* Quantity + Price */}
          <div className="grid grid-cols-2 gap-3">
            <Field label="Quantity" error={errors.quantity}>
              <input type="number" placeholder="100" min="0" step="any"
                value={form.quantity} onChange={set('quantity')} className={inputCls(!!errors.quantity)} />
            </Field>
            <Field label="Price per unit" error={errors.price}>
              <input type="number" placeholder="0.00" min="0" step="0.01"
                value={form.price} onChange={set('price')} className={inputCls(!!errors.price)} />
            </Field>
          </div>

          {/* Fees + Currency */}
          <div className="grid grid-cols-2 gap-3">
            <Field label="Brokerage fees">
              <input type="number" placeholder="9.95" min="0" step="0.01"
                value={form.fees} onChange={set('fees')} className={inputCls()} />
            </Field>
            <Field label="Currency">
              <select value={form.currency} onChange={set('currency')} className={inputCls()}>
                {CURRENCIES.map(c => <option key={c}>{c}</option>)}
              </select>
            </Field>
          </div>

          {/* Trade date */}
          <Field label="Trade date" error={errors.tradeDate}>
            <input type="date" value={form.tradeDate} onChange={set('tradeDate')}
              className={inputCls(!!errors.tradeDate)} />
          </Field>

          {/* Notes */}
          <Field label="Notes (optional)">
            <textarea placeholder="Optional notes…" value={form.notes} onChange={set('notes')}
              rows={2} className={inputCls() + ' resize-none'} />
          </Field>

          {/* Total preview */}
          {totalCost > 0 && (
            <div className="flex items-center justify-between py-3 px-4 bg-gray-50 rounded-xl">
              <span className="text-sm text-gray-500">
                Total {form.tradeType === 'SELL' ? 'proceeds' : 'cost'}
              </span>
              <span className="text-base font-semibold text-gray-900">{fmtCcy(totalCost, form.currency)}</span>
            </div>
          )}

          {mutation.isError && (
            <div className="px-4 py-3 bg-red-50 text-red-700 text-sm rounded-xl">
              {(mutation.error as any)?.response?.data?.detail ?? 'Something went wrong. Please try again.'}
            </div>
          )}
        </div>

        <div className="flex items-center justify-end gap-2 px-6 py-4 border-t border-gray-100">
          <button onClick={onClose} className="px-4 py-2 text-sm text-gray-600 hover:text-gray-900">Cancel</button>
          <button onClick={() => validate() && mutation.mutate(form)} disabled={mutation.isPending}
            className="flex items-center gap-2 px-4 py-2 text-sm text-white bg-blue-600 rounded-lg
                       hover:bg-blue-700 disabled:opacity-60 transition-colors">
            {mutation.isPending && <Loader2 size={14} className="animate-spin" />}
            {mutation.isPending ? 'Saving…' : 'Save trade'}
          </button>
        </div>
      </div>
    </div>
  )
}

// ── Helpers ───────────────────────────────────────────────────────────────────

function Field({ label, error, children }: { label: string; error?: string; children: React.ReactNode }) {
  return (
    <div>
      <label className="block text-xs font-medium text-gray-500 mb-1.5">{label}</label>
      {children}
      {error && <p className="mt-1 text-xs text-red-500">{error}</p>}
    </div>
  )
}

function PagerBtn({ children, onClick, disabled }: { children: React.ReactNode; onClick: () => void; disabled: boolean }) {
  return (
    <button onClick={onClick} disabled={disabled}
      className="px-3 py-1 text-xs border border-gray-200 rounded-md disabled:opacity-40 hover:bg-gray-50 transition-colors">
      {children}
    </button>
  )
}

function inputCls(hasError = false) {
  return clsx('w-full px-3 py-2 text-sm border rounded-lg transition-colors',
    'focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent',
    hasError ? 'border-red-300 bg-red-50' : 'border-gray-300 bg-white text-gray-900')
}

function fmtType(t: string) {
  return ({ BUY:'Buy', SELL:'Sell', DIVIDEND:'Dividend', RETURN_OF_CAPITAL:'Return of Capital',
            TRANSFER_IN:'Transfer In', TRANSFER_OUT:'Transfer Out' } as Record<string,string>)[t] ?? t
}

function fmtCcy(value: number, currency = 'AUD') {
  return new Intl.NumberFormat('en-AU', { style:'currency', currency,
    minimumFractionDigits:2, maximumFractionDigits:2 }).format(value)
}
