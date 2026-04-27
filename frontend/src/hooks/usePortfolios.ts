import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useState, useEffect } from 'react'
import api from '../lib/apiClient'

// ── Types ─────────────────────────────────────────────────────────────────────

export interface Portfolio {
  id: string
  name: string
  baseCurrency: string
  parcelMatchingStrategy: string
  isDefault: boolean
  totalValue: number
  totalCostBase: number
  unrealisedGainLoss: number
  unrealisedGainLossPct: number
}

export interface Holding {
  securityId: string
  ticker: string
  exchange: string
  securityName: string
  quantity: number
  currentPrice: number
  marketValue: number
  costBase: number
  unrealisedGain: number
  unrealisedGainPct: number
  averageCostPerUnit: number
  oldestParcelDate: string
}

export interface PerformancePoint { date: string; value: number; twr: number }

export interface Performance {
  period: string
  twr: number
  mwr: number
  openingValue: number
  closingValue: number
  netCashFlow: number
  timeSeries: PerformancePoint[]
}

export interface Trade {
  id: string
  ticker: string
  exchange: string
  tradeType: string
  quantity: number
  price: number
  fees: number
  totalCost: number
  currency: string
  tradeDate: string
  source: string
  notes?: string
}

export interface CreateTradePayload {
  ticker: string
  exchange: string
  tradeType: string
  quantity: number
  price: number
  fees: number
  currency: string
  tradeDate: string
  notes?: string
}

export interface CgtSummary {
  financialYear: number
  totalCapitalGains: number
  totalDiscountableGains: number
  totalCapitalLosses: number
  currentYearLossesApplied: number
  priorYearLossesApplied: number
  cgDiscountAmount: number
  netAssessableCgt: number
  lossesCarriedForward: number
}

export interface BrokerConnection {
  id: string
  broker: string
  displayName: string
  status: string
  lastSyncAt: string | null
  lastSyncStatus: string | null
}

export interface NotificationPref {
  channel: string
  eventType: string
  enabled: boolean
}

// ── Portfolios ────────────────────────────────────────────────────────────────

export function usePortfolios() {
  const [activePortfolio, setActivePortfolio] = useState<Portfolio | null>(null)

  const { data: portfolios = [], isLoading, error } = useQuery({
    queryKey: ['portfolios'],
    queryFn: async () => {
      const res = await api.get<Portfolio[]>('/v1/portfolios')
      return res.data
    },
  })

  useEffect(() => {
    if (!activePortfolio && portfolios.length > 0) {
      setActivePortfolio(portfolios.find(p => p.isDefault) ?? portfolios[0])
    }
  }, [portfolios.length])

  return { portfolios, activePortfolio, setActivePortfolio, isLoading, error }
}

export function usePortfolio(portfolioId: string | undefined) {
  return useQuery({
    queryKey: ['portfolio', portfolioId],
    queryFn: async () => {
      const res = await api.get<Portfolio>(`/v1/portfolios/${portfolioId}`)
      return res.data
    },
    enabled: !!portfolioId,
  })
}

// ── Holdings ──────────────────────────────────────────────────────────────────

export function useHoldings(portfolioId: string | undefined, sort = 'ticker', sortDir = 'asc', includeDisposed = false) {
  return useQuery({
    queryKey: ['holdings', portfolioId, sort, sortDir, includeDisposed],
    queryFn: async () => {
      const res = await api.get<Holding[]>(`/v1/portfolios/${portfolioId}/holdings`, {
        params: { sort, sortDir, includeDisposed }
      })
      return res.data
    },
    enabled: !!portfolioId,
  })
}

// ── Performance ───────────────────────────────────────────────────────────────

export function usePerformance(portfolioId: string | undefined, period = '1Y') {
  return useQuery({
    queryKey: ['performance', portfolioId, period],
    queryFn: async () => {
      const res = await api.get<Performance>(
        `/v1/portfolios/${portfolioId}/performance`,
        { params: { period } }
      )
      return res.data
    },
    enabled: !!portfolioId,
    staleTime: 60000,
    refetchOnWindowFocus: false,
  })
}

// ── Trades ────────────────────────────────────────────────────────────────────

export function useTrades(
  portfolioId: string | undefined,
  filters?: Record<string, string | number | undefined>
) {
  return useQuery({
    queryKey: ['trades', portfolioId, filters],
    queryFn: async () => {
      const res = await api.get(
        `/v1/portfolios/${portfolioId}/trades`,
        { params: filters }
      )
      return res.data as { content: Trade[]; totalElements: number; totalPages: number }
    },
    enabled: !!portfolioId,
  })
}

export function useCreateTrade(portfolioId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (payload: CreateTradePayload) =>
      api.post(`/v1/portfolios/${portfolioId}/trades`, payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['trades', portfolioId] })
      qc.invalidateQueries({ queryKey: ['holdings', portfolioId] })
      qc.invalidateQueries({ queryKey: ['portfolios'] })
    },
  })
}

// ── CGT ───────────────────────────────────────────────────────────────────────

export function useCgtSummary(portfolioId: string | undefined, financialYear: number) {
  return useQuery({
    queryKey: ['cgt-summary', portfolioId, financialYear],
    queryFn: async () => {
      const res = await api.get<CgtSummary>(
        `/v1/portfolios/${portfolioId}/tax/cgt-summary`,
        { params: { financialYear } }
      )
      return res.data
    },
    enabled: !!portfolioId,
  })
}

// ── Broker connections ────────────────────────────────────────────────────────

export function useBrokerConnections() {
  return useQuery({
    queryKey: ['broker-connections'],
    queryFn: async () => {
      const res = await api.get<BrokerConnection[]>('/v1/broker/connections')
      return res.data
    },
  })
}

// ── Notification preferences ──────────────────────────────────────────────────

export function useNotificationPrefs() {
  const qc = useQueryClient()

  const query = useQuery({
    queryKey: ['notification-prefs'],
    queryFn: async () => {
      const res = await api.get<NotificationPref[]>('/v1/settings/notifications')
      return res.data
    },
  })

  const toggle = useMutation({
    mutationFn: ({ eventType, enabled }: { eventType: string; enabled: boolean }) =>
      api.put(`/v1/settings/notifications/${eventType}`, null, {
        params: { channel: 'EMAIL', enabled }
      }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['notification-prefs'] }),
  })

  return { ...query, toggle }
}

// ── Open Tax Parcels ─────────────────────────────────────────────────────

export interface OpenParcel {
  parcelId: string
  ticker: string
  quantity: number
  costPerUnit: number
  costBase: number
  acquisitionDate: string
  holdingDays: number
  cgtDiscountEligible: boolean
  currentPrice: number
  unrealisedGain: number
  unrealisedGainPct: number
}

export function useOpenParcels(portfolioId: string | undefined, sort = 'acquisitionDate') {
  return useQuery({
    queryKey: ['open-parcels', portfolioId, sort],
    queryFn: async () => {
      const res = await api.get<OpenParcel[]>(
        `/v1/portfolios/${portfolioId}/tax/open-parcels`,
        { params: { sort } }
      )
      return res.data
    },
    enabled: !!portfolioId,
  })
}

// ── CGT Events ───────────────────────────────────────────────────────

export interface CgtEvent {
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

// ── Dividends ────────────────────────────────────────────────────────

export interface DividendDetail {
  ticker: string
  cashDividends: number
  frankingCredits: number
  grossedUpAmount: number
  frankingPercentage: number
}

export interface DividendSummary {
  financialYear: number
  totalCashDividends: number
  totalFrankingCredits: number
  totalGrossedUpIncome: number
  totalTaxWithheld: number
  byHolding: DividendDetail[]
}

export function useDividends(portfolioId: string | undefined, financialYear: number) {
  return useQuery({
    queryKey: ['dividends', portfolioId, financialYear],
    queryFn: async () => {
      const res = await api.get<DividendSummary>(
        `/v1/portfolios/${portfolioId}/tax/dividends`,
        { params: { financialYear } }
      )
      return res.data
    },
    enabled: !!portfolioId,
  })
}

