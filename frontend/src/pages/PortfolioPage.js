import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { format, parseISO } from 'date-fns';
import { ChevronRight, Shield, Layers, Loader2 } from 'lucide-react';
import clsx from 'clsx';
import { useHoldings, } from '../hooks/usePortfolios';
import { useQuery } from '@tanstack/react-query';
import api from '../lib/apiClient';
// ── Hook ──────────────────────────────────────────────────────────────────────
function useOpenParcels(portfolioId) {
    return useQuery({
        queryKey: ['open-parcels', portfolioId],
        queryFn: async () => {
            const r = await api.get(`/v1/portfolios/${portfolioId}/tax/open-parcels`);
            return r.data;
        },
        enabled: !!portfolioId,
    });
}
// =============================================================================
// Page
// =============================================================================
export default function PortfolioPage() {
    const { portfolioId } = useParams();
    const [selected, setSelected] = useState(null);
    const [sort, setSort] = useState('ticker');
    const [sortDir, setSortDir] = useState('asc');
    const [showSold, setShowSold] = useState(false);
    const { data: holdings = [], isLoading } = useHoldings(portfolioId, sort, sortDir, showSold);
    const { data: allParcels = [] } = useOpenParcels(portfolioId);
    const HOLDING_COLS = [
        { key: 'ticker', label: 'Security' },
        { key: 'quantity', label: 'Qty' },
        { key: 'currentPrice', label: 'Price' },
        { key: 'marketValue', label: 'Value' },
        { key: 'unrealisedGain', label: 'Gain' },
    ];
    const toggleSort = (key) => {
        if (sort === key) {
            setSortDir(sortDir === 'asc' ? 'desc' : 'asc');
        }
        else {
            setSort(key);
            setSortDir('asc');
        }
    };
    // Group parcels by ticker for the detail panel
    const parcelsByTicker = allParcels.reduce((acc, p) => {
        acc[p.ticker] = [...(acc[p.ticker] ?? []), p];
        return acc;
    }, {});
    const selectedHolding = holdings.find(h => h.securityId === selected) ?? null;
    const selectedParcels = selectedHolding
        ? (parcelsByTicker[selectedHolding.ticker] ?? [])
        : [];
    if (isLoading) {
        return (_jsxs("div", { className: "flex items-center justify-center h-64 text-gray-400", children: [_jsx(Loader2, { size: 20, className: "animate-spin mr-2" }), " Loading holdings\u2026"] }));
    }
    return (_jsxs("div", { className: "p-8 space-y-5", children: [_jsxs("div", { className: "flex items-center gap-2 text-sm text-gray-400", children: [_jsx(Link, { to: "/dashboard", className: "hover:text-gray-600 transition-colors", children: "Dashboard" }), _jsx(ChevronRight, { size: 14 }), _jsx("span", { className: "text-gray-700 font-medium", children: "Holdings" })] }), _jsx("h1", { className: "text-2xl font-semibold text-gray-900", children: "Holdings" }), holdings.length > 0 && _jsx(SummaryBar, { holdings: holdings }), _jsxs("div", { className: "flex gap-5", children: [_jsx("div", { className: "flex-1", children: _jsxs("div", { className: "bg-white rounded-xl border border-gray-200", children: [_jsxs("div", { className: "px-5 py-3 border-b border-gray-100 flex items-center justify-between", children: [_jsxs("div", { className: "flex items-center gap-3", children: [_jsxs("span", { className: "text-sm font-semibold text-gray-700", children: [holdings.length, " position", holdings.length !== 1 ? 's' : ''] }), _jsxs("label", { className: "flex items-center gap-2 text-xs cursor-pointer", children: [_jsx("input", { type: "checkbox", checked: showSold, onChange: e => setShowSold(e.target.checked), className: "rounded border-gray-300 text-blue-600 focus:ring-blue-500" }), _jsx("span", { className: "text-gray-500", children: "Show sold" })] })] }), _jsxs("div", { className: "flex items-center gap-2 text-xs", children: [_jsx("span", { className: "text-gray-400", children: "Sort:" }), HOLDING_COLS.map(col => (_jsxs("button", { onClick: () => toggleSort(col.key), className: clsx('px-2 py-1 rounded transition-colors flex items-center gap-1', sort === col.key ? 'bg-blue-100 text-blue-700' : 'hover:bg-gray-100 text-gray-500'), children: [col.label, sort === col.key && (sortDir === 'asc' ? '↑' : '↓')] }, col.key)))] })] }), _jsxs("div", { className: "divide-y divide-gray-50", children: [holdings.map(h => (_jsx(HoldingRow, { holding: h, isSelected: h.securityId === selected, onSelect: () => setSelected(h.securityId === selected ? null : h.securityId), portfolioId: portfolioId }, h.securityId))), holdings.length === 0 && (_jsxs("div", { className: "py-16 text-center text-sm text-gray-400", children: ["No holdings yet.", ' ', _jsx(Link, { to: `/portfolio/${portfolioId}/trades`, className: "text-blue-500 hover:underline", children: "Add a trade" }), ' ', "to get started."] }))] })] }) }), selectedHolding && (_jsx("div", { className: "w-80 flex-shrink-0", children: _jsx(ParcelPanel, { holding: selectedHolding, parcels: selectedParcels }) }))] })] }));
}
// =============================================================================
// Portfolio summary bar
// =============================================================================
function SummaryBar({ holdings }) {
    const totalValue = holdings.reduce((s, h) => s + h.marketValue, 0);
    const totalCost = holdings.reduce((s, h) => s + h.costBase, 0);
    const totalGain = totalValue - totalCost;
    const gainPct = totalCost > 0 ? totalGain / totalCost : 0;
    const pos = totalGain >= 0;
    return (_jsxs("div", { className: "grid grid-cols-3 gap-4", children: [_jsx(SummaryCard, { label: "Total Value", value: fmtCcy(totalValue) }), _jsx(SummaryCard, { label: "Total Cost Base", value: fmtCcy(totalCost), muted: true }), _jsx(SummaryCard, { label: "Unrealised Gain / Loss", value: `${pos ? '+' : ''}${fmtCcy(totalGain)}`, sub: `${pos ? '+' : ''}${(gainPct * 100).toFixed(1)}%`, positive: pos })] }));
}
function SummaryCard({ label, value, sub, positive, muted, }) {
    return (_jsxs("div", { className: clsx('rounded-xl border px-5 py-4', positive === true && 'bg-emerald-50 border-emerald-200', positive === false && 'bg-red-50 border-red-200', positive === undefined && 'bg-white border-gray-200'), children: [_jsx("div", { className: "text-xs text-gray-400 uppercase tracking-wide mb-1", children: label }), _jsx("div", { className: clsx('text-xl font-semibold', muted ? 'text-gray-600'
                    : positive === true ? 'text-emerald-700'
                        : positive === false ? 'text-red-600'
                            : 'text-gray-900'), children: value }), sub && _jsx("div", { className: "text-xs text-gray-400 mt-0.5", children: sub })] }));
}
// =============================================================================
// Holding row
// =============================================================================
function HoldingRow({ holding: h, isSelected, onSelect, portfolioId, }) {
    const pos = h.unrealisedGain >= 0;
    return (_jsxs("button", { onClick: onSelect, className: clsx('w-full flex items-center gap-4 px-5 py-4 text-left transition-colors', isSelected ? 'bg-blue-50' : 'hover:bg-gray-50/60'), children: [_jsx("div", { className: "w-10 h-10 rounded-xl bg-gray-100 flex items-center justify-center\n                      text-xs font-bold text-gray-600 flex-shrink-0 select-none", children: h.ticker.slice(0, 3) }), _jsxs("div", { className: "flex-1 min-w-0", children: [_jsxs("div", { className: "font-semibold text-gray-900 text-sm", children: [h.ticker, _jsx("span", { className: "text-xs text-gray-400 font-normal ml-1.5", children: h.exchange })] }), h.securityName && (_jsx("div", { className: "text-xs text-gray-400 truncate", children: h.securityName }))] }), _jsxs("div", { className: "text-right hidden sm:block flex-shrink-0", children: [_jsxs("div", { className: "text-sm text-gray-600 tabular-nums", children: [h.quantity.toLocaleString(), " units"] }), _jsxs("div", { className: "text-xs text-gray-400", children: ["avg ", fmtCcy(h.averageCostPerUnit)] })] }), _jsxs("div", { className: "text-right w-28 flex-shrink-0", children: [_jsx("div", { className: "font-semibold text-gray-900 tabular-nums text-sm", children: fmtCcy(h.marketValue) }), _jsxs("div", { className: "text-xs text-gray-400 tabular-nums", children: [fmtCcy(h.currentPrice), " / unit"] })] }), _jsxs("div", { className: clsx('text-right w-24 flex-shrink-0', pos ? 'text-emerald-600' : 'text-red-500'), children: [_jsxs("div", { className: "text-sm font-medium tabular-nums", children: [pos ? '+' : '', fmtCcy(h.unrealisedGain)] }), _jsxs("div", { className: "text-xs tabular-nums", children: [pos ? '+' : '', (h.unrealisedGainPct * 100).toFixed(1), "%"] })] }), _jsx(ChevronRight, { size: 14, className: clsx('text-gray-300 flex-shrink-0 transition-transform duration-150', isSelected && 'rotate-90 text-blue-400') })] }));
}
// =============================================================================
// Parcel detail panel
// =============================================================================
function ParcelPanel({ holding, parcels, }) {
    return (_jsxs("div", { className: "bg-white rounded-xl border border-gray-200 sticky top-8", children: [_jsxs("div", { className: "px-5 py-4 border-b border-gray-100", children: [_jsxs("div", { className: "flex items-center gap-2 mb-1", children: [_jsx(Layers, { size: 15, className: "text-gray-400" }), _jsx("span", { className: "text-sm font-semibold text-gray-700", children: "Tax Parcels" })] }), _jsxs("div", { className: "text-xs text-gray-400", children: [holding.ticker, " \u00B7 ", parcels.length, " open parcel", parcels.length !== 1 ? 's' : ''] })] }), _jsxs("div", { className: "divide-y divide-gray-50 max-h-[420px] overflow-y-auto", children: [parcels.map(p => {
                        const pos = p.unrealisedGain >= 0;
                        const pct = Math.min(100, Math.abs(p.unrealisedGainPct) * 100);
                        return (_jsxs("div", { className: "px-5 py-3", children: [_jsxs("div", { className: "flex items-start justify-between mb-1.5", children: [_jsxs("div", { children: [_jsxs("div", { className: "text-sm font-medium text-gray-900 tabular-nums", children: [p.quantity.toLocaleString(), " units"] }), _jsxs("div", { className: "text-xs text-gray-400 mt-0.5", children: [format(parseISO(p.acquisitionDate), 'dd MMM yyyy'), ' · ', _jsxs("span", { className: "tabular-nums", children: [p.holdingDays, "d"] })] })] }), _jsxs("div", { className: "text-right", children: [_jsxs("div", { className: clsx('text-sm font-medium tabular-nums', pos ? 'text-emerald-600' : 'text-red-500'), children: [pos ? '+' : '', fmtCcy(p.unrealisedGain)] }), _jsxs("div", { className: "text-xs text-gray-400 tabular-nums", children: ["cost ", fmtCcy(p.costPerUnit)] })] })] }), _jsxs("div", { className: "flex items-center gap-2", children: [_jsx("div", { className: "flex-1 bg-gray-100 rounded-full h-1", children: _jsx("div", { className: clsx('h-1 rounded-full transition-all', pos ? 'bg-emerald-400' : 'bg-red-400'), style: { width: `${pct}%` } }) }), p.cgtDiscountEligible ? (_jsxs("span", { className: "inline-flex items-center gap-0.5 text-xs text-emerald-600 flex-shrink-0", children: [_jsx(Shield, { size: 10 }), " 50%"] })) : (_jsxs("span", { className: "text-xs text-gray-400 flex-shrink-0 tabular-nums", children: [Math.max(0, 366 - p.holdingDays), "d left"] }))] })] }, p.parcelId));
                    }), parcels.length === 0 && (_jsx("div", { className: "px-5 py-8 text-center text-xs text-gray-400", children: "No open parcels for this holding." }))] }), _jsxs("div", { className: "px-5 py-4 border-t border-gray-100 bg-gray-50 rounded-b-xl space-y-1.5", children: [_jsxs("div", { className: "flex justify-between text-sm", children: [_jsx("span", { className: "text-gray-500", children: "Total cost base" }), _jsx("span", { className: "font-medium text-gray-700 tabular-nums", children: fmtCcy(holding.costBase) })] }), _jsxs("div", { className: "flex justify-between text-sm", children: [_jsx("span", { className: "text-gray-500", children: "Market value" }), _jsx("span", { className: "font-semibold text-gray-900 tabular-nums", children: fmtCcy(holding.marketValue) })] }), _jsxs("div", { className: "flex justify-between text-sm", children: [_jsx("span", { className: "text-gray-500", children: "Unrealised" }), _jsxs("span", { className: clsx('font-medium tabular-nums', holding.unrealisedGain >= 0 ? 'text-emerald-600' : 'text-red-500'), children: [holding.unrealisedGain >= 0 ? '+' : '', fmtCcy(holding.unrealisedGain)] })] })] })] }));
}
// ── Formatters ────────────────────────────────────────────────────────────────
function fmtCcy(v, currency = 'AUD') {
    return new Intl.NumberFormat('en-AU', {
        style: 'currency', currency,
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
    }).format(v);
}
