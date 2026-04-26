import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useState } from 'react';
import { AreaChart, Area, XAxis, YAxis, Tooltip, ResponsiveContainer } from 'recharts';
import { TrendingUp, TrendingDown, DollarSign, BarChart2 } from 'lucide-react';
import { format, parseISO } from 'date-fns';
import clsx from 'clsx';
import { usePortfolios, useHoldings, usePerformance } from '../hooks/usePortfolios';
const PERIODS = ['1M', '3M', '6M', '1Y', '3Y', 'ALL'];
export default function DashboardPage() {
    const { activePortfolio } = usePortfolios();
    const [sort, setSort] = useState('ticker');
    const [sortDir, setSortDir] = useState('asc');
    const { data: holdings = [] } = useHoldings(activePortfolio?.id ?? '', sort, sortDir);
    const [period, setPeriod] = useState('1Y');
    const { data: perf } = usePerformance(activePortfolio?.id ?? '', period);
    const COLS = [
        { key: 'ticker', label: 'Security' },
        { key: 'quantity', label: 'Quantity' },
        { key: 'averageCostPerUnit', label: 'Avg Cost' },
        { key: 'currentPrice', label: 'Current Price' },
        { key: 'marketValue', label: 'Market Value' },
        { key: 'unrealisedGain', label: 'Gain / Loss' },
        { key: 'unrealisedGainPct', label: '%' },
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
    if (!activePortfolio) {
        return (_jsx("div", { className: "p-8 text-gray-400 text-sm", children: "No portfolio selected. Create one in Settings." }));
    }
    const gainPositive = (activePortfolio.unrealisedGainLoss ?? 0) >= 0;
    return (_jsxs("div", { className: "p-8 space-y-6", children: [_jsxs("div", { children: [_jsx("h1", { className: "text-2xl font-semibold text-gray-900", children: activePortfolio.name }), _jsxs("p", { className: "text-sm text-gray-500 mt-0.5", children: [activePortfolio.baseCurrency, " \u00B7 ", activePortfolio.parcelMatchingStrategy] })] }), _jsxs("div", { className: "grid grid-cols-2 lg:grid-cols-4 gap-4", children: [_jsx(StatCard, { label: "Portfolio Value", value: fmt(activePortfolio.totalValue, activePortfolio.baseCurrency), icon: _jsx(DollarSign, { size: 16 }) }), _jsx(StatCard, { label: "Cost Base", value: fmt(activePortfolio.totalCostBase, activePortfolio.baseCurrency), icon: _jsx(BarChart2, { size: 16 }) }), _jsx(StatCard, { label: "Unrealised Gain / Loss", value: fmt(activePortfolio.unrealisedGainLoss, activePortfolio.baseCurrency), sub: `${pct(activePortfolio.unrealisedGainLossPct)}`, positive: gainPositive, icon: gainPositive ? _jsx(TrendingUp, { size: 16 }) : _jsx(TrendingDown, { size: 16 }) }), _jsx(StatCard, { label: "TWR (1Y)", value: perf ? pct(perf.twr) : '—', sub: perf ? `MWR ${pct(perf.mwr)}` : undefined, positive: (perf?.twr ?? 0) >= 0, icon: _jsx(TrendingUp, { size: 16 }) })] }), perf && perf.timeSeries.length > 0 && (_jsxs("div", { className: "bg-white rounded-xl border border-gray-200 p-6", children: [_jsxs("div", { className: "flex items-center justify-between mb-4", children: [_jsx("h2", { className: "text-sm font-semibold text-gray-700", children: "Portfolio Value" }), _jsx("div", { className: "flex gap-1", children: PERIODS.map(p => (_jsx("button", { onClick: () => setPeriod(p), className: clsx('px-2.5 py-1 text-xs rounded-md transition-colors', period === p
                                        ? 'bg-blue-600 text-white'
                                        : 'text-gray-500 hover:bg-gray-100'), children: p }, p))) })] }), _jsx(ResponsiveContainer, { width: "100%", height: 240, children: _jsxs(AreaChart, { data: perf.timeSeries, children: [_jsx("defs", { children: _jsxs("linearGradient", { id: "valueGrad", x1: "0", y1: "0", x2: "0", y2: "1", children: [_jsx("stop", { offset: "5%", stopColor: "#2563eb", stopOpacity: 0.15 }), _jsx("stop", { offset: "95%", stopColor: "#2563eb", stopOpacity: 0 })] }) }), _jsx(XAxis, { dataKey: "date", tickFormatter: d => format(parseISO(d), 'MMM yy'), tick: { fontSize: 11, fill: '#9ca3af' }, axisLine: false, tickLine: false }), _jsx(YAxis, { tickFormatter: v => `$${(v / 1000).toFixed(0)}k`, tick: { fontSize: 11, fill: '#9ca3af' }, axisLine: false, tickLine: false, width: 52 }), _jsx(Tooltip, { formatter: (v) => [fmt(v, activePortfolio.baseCurrency), 'Value'], labelFormatter: d => format(parseISO(d), 'dd MMM yyyy'), contentStyle: { fontSize: 12, borderRadius: 8, border: '1px solid #e5e7eb' } }), _jsx(Area, { type: "monotone", dataKey: "value", stroke: "#2563eb", strokeWidth: 2, fill: "url(#valueGrad)", dot: false })] }) })] })), _jsxs("div", { className: "bg-white rounded-xl border border-gray-200", children: [_jsx("div", { className: "px-6 py-4 border-b border-gray-100", children: _jsx("h2", { className: "text-sm font-semibold text-gray-700", children: "Holdings" }) }), _jsx("div", { className: "overflow-x-auto", children: _jsxs("table", { className: "w-full text-sm", children: [_jsx("thead", { children: _jsx("tr", { className: "text-xs text-gray-400 border-b border-gray-100", children: COLS.map(col => (_jsx("th", { className: "px-6 py-3 text-right first:text-left font-medium cursor-pointer hover:text-blue-600 select-none", onClick: () => toggleSort(col.key), children: _jsxs("span", { className: "flex items-center gap-1 justify-end first:justify-start", children: [col.label, sort === col.key && (sortDir === 'asc' ? '↑' : '↓')] }) }, col.key))) }) }), _jsxs("tbody", { children: [holdings.map(h => {
                                            const pos = h.unrealisedGain >= 0;
                                            return (_jsxs("tr", { className: "border-b border-gray-50 last:border-0 hover:bg-gray-50/50", children: [_jsxs("td", { className: "px-6 py-3", children: [_jsx("div", { className: "font-semibold text-gray-900", children: h.ticker }), _jsx("div", { className: "text-xs text-gray-400", children: h.exchange })] }), _jsx("td", { className: "px-6 py-3 text-right tabular-nums text-gray-700", children: h.quantity.toLocaleString() }), _jsx("td", { className: "px-6 py-3 text-right tabular-nums text-gray-600", children: fmt(h.averageCostPerUnit, activePortfolio.baseCurrency) }), _jsx("td", { className: "px-6 py-3 text-right tabular-nums text-gray-700", children: fmt(h.currentPrice, activePortfolio.baseCurrency) }), _jsx("td", { className: "px-6 py-3 text-right tabular-nums font-medium text-gray-900", children: fmt(h.marketValue, activePortfolio.baseCurrency) }), _jsxs("td", { className: clsx('px-6 py-3 text-right tabular-nums font-medium', pos ? 'text-emerald-600' : 'text-red-500'), children: [pos ? '+' : '', fmt(h.unrealisedGain, activePortfolio.baseCurrency)] }), _jsxs("td", { className: clsx('px-6 py-3 text-right tabular-nums', pos ? 'text-emerald-600' : 'text-red-500'), children: [pos ? '+' : '', pct(h.unrealisedGainPct)] })] }, h.securityId));
                                        }), holdings.length === 0 && (_jsx("tr", { children: _jsx("td", { colSpan: 7, className: "px-6 py-10 text-center text-gray-400 text-sm", children: "No holdings yet \u2014 add a trade to get started." }) }))] })] }) })] })] }));
}
// ── Helpers ───────────────────────────────────────────────────────────────────
function fmt(value, currency = 'AUD') {
    if (value === undefined || value === null)
        return '—';
    return new Intl.NumberFormat('en-AU', {
        style: 'currency',
        currency,
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
    }).format(value);
}
function pct(value) {
    if (value === undefined || value === null)
        return '—';
    return `${(value * 100).toFixed(2)}%`;
}
function StatCard({ label, value, sub, positive, icon }) {
    return (_jsxs("div", { className: "bg-white rounded-xl border border-gray-200 p-5", children: [_jsxs("div", { className: "flex items-center gap-2 text-gray-400 mb-3", children: [icon, _jsx("span", { className: "text-xs font-medium uppercase tracking-wide", children: label })] }), _jsx("div", { className: clsx('text-2xl font-semibold', positive === undefined ? 'text-gray-900'
                    : positive ? 'text-emerald-600' : 'text-red-500'), children: value }), sub && _jsx("div", { className: "text-xs text-gray-400 mt-1", children: sub })] }));
}
