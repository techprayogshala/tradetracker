import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useState, useEffect } from 'react';
import api from '../lib/apiClient';
// ── Portfolios ────────────────────────────────────────────────────────────────
export function usePortfolios() {
    const [activePortfolio, setActivePortfolio] = useState(null);
    const { data: portfolios = [], isLoading, error } = useQuery({
        queryKey: ['portfolios'],
        queryFn: async () => {
            const res = await api.get('/v1/portfolios');
            return res.data;
        },
    });
    useEffect(() => {
        if (!activePortfolio && portfolios.length > 0) {
            setActivePortfolio(portfolios.find(p => p.isDefault) ?? portfolios[0]);
        }
    }, [portfolios.length]);
    return { portfolios, activePortfolio, setActivePortfolio, isLoading, error };
}
export function usePortfolio(portfolioId) {
    return useQuery({
        queryKey: ['portfolio', portfolioId],
        queryFn: async () => {
            const res = await api.get(`/v1/portfolios/${portfolioId}`);
            return res.data;
        },
        enabled: !!portfolioId,
    });
}
// ── Holdings ──────────────────────────────────────────────────────────────────
export function useHoldings(portfolioId, sort = 'ticker', sortDir = 'asc') {
    return useQuery({
        queryKey: ['holdings', portfolioId, sort, sortDir],
        queryFn: async () => {
            const res = await api.get(`/v1/portfolios/${portfolioId}/holdings`, {
                params: { sort, sortDir }
            });
            return res.data;
        },
        enabled: !!portfolioId,
    });
}
// ── Performance ───────────────────────────────────────────────────────────────
export function usePerformance(portfolioId, period = '1Y') {
    return useQuery({
        queryKey: ['performance', portfolioId, period],
        queryFn: async () => {
            const res = await api.get(`/v1/portfolios/${portfolioId}/performance`, { params: { period } });
            return res.data;
        },
        enabled: !!portfolioId,
        staleTime: 60000,
        refetchOnWindowFocus: false,
    });
}
// ── Trades ────────────────────────────────────────────────────────────────────
export function useTrades(portfolioId, filters) {
    return useQuery({
        queryKey: ['trades', portfolioId, filters],
        queryFn: async () => {
            const res = await api.get(`/v1/portfolios/${portfolioId}/trades`, { params: filters });
            return res.data;
        },
        enabled: !!portfolioId,
    });
}
export function useCreateTrade(portfolioId) {
    const qc = useQueryClient();
    return useMutation({
        mutationFn: (payload) => api.post(`/v1/portfolios/${portfolioId}/trades`, payload),
        onSuccess: () => {
            qc.invalidateQueries({ queryKey: ['trades', portfolioId] });
            qc.invalidateQueries({ queryKey: ['holdings', portfolioId] });
            qc.invalidateQueries({ queryKey: ['portfolios'] });
        },
    });
}
// ── CGT ───────────────────────────────────────────────────────────────────────
export function useCgtSummary(portfolioId, financialYear) {
    return useQuery({
        queryKey: ['cgt-summary', portfolioId, financialYear],
        queryFn: async () => {
            const res = await api.get(`/v1/portfolios/${portfolioId}/tax/cgt-summary`, { params: { financialYear } });
            return res.data;
        },
        enabled: !!portfolioId,
    });
}
// ── Broker connections ────────────────────────────────────────────────────────
export function useBrokerConnections() {
    return useQuery({
        queryKey: ['broker-connections'],
        queryFn: async () => {
            const res = await api.get('/v1/broker/connections');
            return res.data;
        },
    });
}
// ── Notification preferences ──────────────────────────────────────────────────
export function useNotificationPrefs() {
    const qc = useQueryClient();
    const query = useQuery({
        queryKey: ['notification-prefs'],
        queryFn: async () => {
            const res = await api.get('/v1/settings/notifications');
            return res.data;
        },
    });
    const toggle = useMutation({
        mutationFn: ({ eventType, enabled }) => api.put(`/v1/settings/notifications/${eventType}`, null, {
            params: { channel: 'EMAIL', enabled }
        }),
        onSuccess: () => qc.invalidateQueries({ queryKey: ['notification-prefs'] }),
    });
    return { ...query, toggle };
}
export function useOpenParcels(portfolioId, sort = 'acquisitionDate') {
    return useQuery({
        queryKey: ['open-parcels', portfolioId, sort],
        queryFn: async () => {
            const res = await api.get(`/v1/portfolios/${portfolioId}/tax/open-parcels`, { params: { sort } });
            return res.data;
        },
        enabled: !!portfolioId,
    });
}
export function useDividends(portfolioId, financialYear) {
    return useQuery({
        queryKey: ['dividends', portfolioId, financialYear],
        queryFn: async () => {
            const res = await api.get(`/v1/portfolios/${portfolioId}/tax/dividends`, { params: { financialYear } });
            return res.data;
        },
        enabled: !!portfolioId,
    });
}
