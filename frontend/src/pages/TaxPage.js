import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { format, parseISO } from 'date-fns';
import { Download, TrendingUp, TrendingDown, Shield, AlertCircle, Loader2, ChevronDown } from 'lucide-react';
import clsx from 'clsx';
import api from '../lib/apiClient';
// Current ATO financial year: runs 1 Jul – 30 Jun
function currentFY() {
    const now = new Date();
    return now.getMonth() >= 6 ? now.getFullYear() + 1 : now.getFullYear();
}
const FY_OPTIONS = Array.from({ length: 5 }, (_, i) => currentFY() - i);
// ── Page ──────────────────────────────────────────────────────────────────────
export default function TaxPage() {
    const { portfolioId } = useParams();
    const [fy, setFy] = useState(currentFY());
    const [tab, setTab] = useState('summary');
    const summaryQ = useQuery({
        queryKey: ['cgt-summary', portfolioId, fy],
        queryFn: async () => {
            const r = await api.get(`/v1/portfolios/${portfolioId}/tax/cgt-summary`, { params: { financialYear: fy } });
            return r.data;
        },
        enabled: !!portfolioId,
    });
    const eventsQ = useQuery({
        queryKey: ['cgt-events', portfolioId, fy],
        queryFn: async () => {
            const r = await api.get(`/v1/portfolios/${portfolioId}/tax/cgt-events`, { params: { financialYear: fy } });
            return r.data;
        },
        enabled: !!portfolioId && tab === 'events',
    });
    const parcelsQ = useQuery({
        queryKey: ['open-parcels', portfolioId],
        queryFn: async () => {
            const r = await api.get(`/v1/portfolios/${portfolioId}/tax/open-parcels`);
            return r.data;
        },
        enabled: !!portfolioId && tab === 'parcels',
    });
    const handleCsvDownload = async () => {
        const r = await api.get(`/v1/portfolios/${portfolioId}/tax/report/csv`, { params: { financialYear: fy }, responseType: 'blob' });
        triggerDownload(r.data, `cgt-events-FY${fy}.csv`, 'text/csv');
    };
    const handlePdfDownload = async () => {
        const r = await api.get(`/v1/portfolios/${portfolioId}/tax/report/pdf`, { params: { financialYear: fy }, responseType: 'blob' });
        triggerDownload(r.data, `cgt-report-FY${fy}.pdf`, 'application/pdf');
    };
    const triggerDownload = (data, filename, type) => {
        const url = window.URL.createObjectURL(new Blob([data], { type }));
        const link = document.createElement('a');
        link.href = url;
        link.setAttribute('download', filename);
        document.body.appendChild(link);
        link.click();
        link.remove();
        window.URL.revokeObjectURL(url);
    };
    return (_jsxs("div", { className: "p-8 space-y-6", children: [_jsxs("div", { className: "flex items-center justify-between", children: [_jsxs("div", { children: [_jsx("h1", { className: "text-2xl font-semibold text-gray-900", children: "Tax & CGT" }), _jsxs("p", { className: "text-sm text-gray-500 mt-0.5", children: ["Australian Capital Gains Tax \u2014 FY ", fy - 1, "\u2013", fy] })] }), _jsxs("div", { className: "flex items-center gap-3", children: [_jsxs("div", { className: "relative", children: [_jsx("select", { value: fy, onChange: e => setFy(Number(e.target.value)), className: "appearance-none pl-3 pr-8 py-2 text-sm border border-gray-300 rounded-lg\n                         focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white text-gray-700", children: FY_OPTIONS.map(y => (_jsxs("option", { value: y, children: ["FY ", y - 1, "\u2013", y] }, y))) }), _jsx(ChevronDown, { size: 14, className: "absolute right-2.5 top-1/2 -translate-y-1/2 text-gray-400 pointer-events-none" })] }), _jsxs("button", { onClick: handlePdfDownload, className: "flex items-center gap-1.5 px-3 py-2 text-sm text-white\n                       bg-blue-600 rounded-lg hover:bg-blue-700 transition-colors", children: [_jsx(Download, { size: 14 }), " PDF Report"] }), _jsxs("button", { onClick: handleCsvDownload, className: "flex items-center gap-1.5 px-3 py-2 text-sm text-gray-600\n                       border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors", children: [_jsx(Download, { size: 14 }), " Export CSV"] })] })] }), summaryQ.isLoading ? (_jsxs("div", { className: "flex items-center gap-2 text-gray-400 py-4", children: [_jsx(Loader2, { size: 16, className: "animate-spin" }), " Loading CGT summary\u2026"] })) : summaryQ.data ? (_jsx(CgtSummaryCards, { summary: summaryQ.data })) : null, _jsxs("div", { className: "flex items-start gap-3 px-4 py-3 bg-amber-50 border border-amber-200 rounded-xl text-sm text-amber-800", children: [_jsx(AlertCircle, { size: 16, className: "flex-shrink-0 mt-0.5" }), _jsx("span", { children: "These calculations are indicative only. Verify all amounts with a registered tax agent before lodging your return. Return of capital adjustments and corporate actions may affect your actual cost base." })] }), _jsx("div", { className: "border-b border-gray-200", children: _jsx("nav", { className: "flex gap-6 -mb-px", children: ['summary', 'events', 'parcels'].map(t => (_jsx("button", { onClick: () => setTab(t), className: clsx('py-3 text-sm font-medium border-b-2 transition-colors', tab === t
                            ? 'border-blue-600 text-blue-600'
                            : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'), children: t === 'summary' ? 'CGT Breakdown'
                            : t === 'events' ? `Disposal Events FY${fy}`
                                : 'Open Parcels' }, t))) }) }), tab === 'summary' && summaryQ.data && (_jsx(CgtBreakdownTable, { summary: summaryQ.data })), tab === 'events' && (_jsx(CgtEventsTable, { events: eventsQ.data ?? [], isLoading: eventsQ.isLoading })), tab === 'parcels' && (_jsx(OpenParcelsTable, { parcels: parcelsQ.data ?? [], isLoading: parcelsQ.isLoading }))] }));
}
// ── CGT Summary Cards ─────────────────────────────────────────────────────────
function CgtSummaryCards({ summary }) {
    const net = summary.netAssessableCgt;
    const netPositive = net >= 0;
    const discountAmount = summary.totalDiscountableGains * 0.5;
    return (_jsxs("div", { className: "grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-3", children: [_jsx(TaxCard, { label: "Short Term", value: fmtCcy(summary.shortTermGains || 0), icon: _jsx(TrendingUp, { size: 15 }), positive: true }), _jsx(TaxCard, { label: "Long Term", value: fmtCcy(summary.longTermGains || 0), icon: _jsx(TrendingUp, { size: 15 }), positive: true }), _jsx(TaxCard, { label: "Losses", value: fmtCcy(summary.totalCurrentYearLosses || 0), icon: _jsx(TrendingDown, { size: 15 }), positive: false }), _jsx(TaxCard, { label: "CGT Disc.", value: `− ${fmtCcy(discountAmount)}`, icon: _jsx(Shield, { size: 15 }), neutral: true }), _jsx(TaxCard, { label: "Net CGT", value: fmtCcy(net), positive: netPositive, icon: netPositive ? _jsx(TrendingUp, { size: 15 }) : _jsx(TrendingDown, { size: 15 }), highlight: true })] }));
}
// ── CGT Breakdown Table ───────────────────────────────────────────────────────
function CgtBreakdownTable({ summary }) {
    const discountAmount = (summary.totalDiscountableGains || 0) * 0.5;
    const currentYrLosses = summary.totalCurrentYearLosses || 0;
    const priorLosses = summary.priorYearLossesApplied ?? 0;
    const rows = [
        { label: 'Short term capital gains', value: summary.shortTermGains || 0, positive: true },
        { label: 'Long term capital gains', value: summary.longTermGains || 0, positive: true },
        { label: 'of which: discountable gains', value: summary.totalDiscountableGains, positive: true, indent: true },
        { label: 'Less: current year losses', value: -currentYrLosses, positive: false },
        { label: 'Less: prior year losses applied', value: -priorLosses, positive: false },
        { label: 'Less: 50% CGT discount', value: -discountAmount, positive: false },
        null,
        { label: 'Net assessable capital gain', value: summary.netAssessableCgt, positive: summary.netAssessableCgt >= 0, bold: true },
        { label: 'Losses carried to next year', value: summary.lossesCarriedForward || 0, positive: false },
    ];
    return (_jsxs("div", { className: "bg-white rounded-xl border border-gray-200 overflow-hidden max-w-2xl", children: [_jsx("div", { className: "px-6 py-4 border-b border-gray-100", children: _jsxs("h2", { className: "text-sm font-semibold text-gray-700", children: ["CGT Calculation \u2014 FY ", summary.financialYear - 1, "\u2013", summary.financialYear] }) }), _jsx("table", { className: "w-full text-sm", children: _jsx("tbody", { children: rows.map((row, i) => row === null ? (_jsx("tr", { children: _jsx("td", { colSpan: 2, className: "border-t border-gray-200" }) }, i)) : (_jsxs("tr", { className: clsx('border-b border-gray-50 last:border-0', row.bold && 'bg-gray-50'), children: [_jsx("td", { className: clsx('px-6 py-3 text-gray-600', row.indent && 'pl-10 text-gray-400'), children: row.label }), _jsx("td", { className: clsx('px-6 py-3 text-right tabular-nums', row.bold ? 'font-semibold text-gray-900' : 'text-gray-700', row.value > 0 && !row.bold && 'text-emerald-600', row.value < 0 && 'text-red-500'), children: row.value >= 0 ? fmtCcy(row.value) : `(${fmtCcy(Math.abs(row.value))})` })] }, i))) }) })] }));
}
// ── CGT Events Table ──────────────────────────────────────────────────────────
function CgtEventsTable({ events, isLoading }) {
    if (isLoading)
        return _jsx(TableLoader, { text: "Loading disposal events\u2026" });
    return (_jsx("div", { className: "bg-white rounded-xl border border-gray-200", children: _jsx("div", { className: "overflow-x-auto", children: _jsxs("table", { className: "w-full text-sm", children: [_jsx("thead", { children: _jsx("tr", { className: "text-xs text-gray-400 border-b border-gray-100", children: ['Ticker', 'Disposal', 'Acquisition', 'Days', 'Quantity', 'Proceeds', 'Cost Base', 'Gain / Loss', 'Discount', 'Assessable'].map(h => (_jsx("th", { className: "px-4 py-3 text-right first:text-left font-medium whitespace-nowrap", children: h }, h))) }) }), _jsxs("tbody", { children: [events.map((e, i) => {
                                const isGain = e.capitalGain >= 0;
                                return (_jsxs("tr", { className: "border-b border-gray-50 last:border-0 hover:bg-gray-50/40", children: [_jsx("td", { className: "px-4 py-3 font-semibold text-gray-900", children: e.ticker }), _jsx("td", { className: "px-4 py-3 text-right text-gray-500 whitespace-nowrap", children: format(parseISO(e.disposalDate), 'dd MMM yyyy') }), _jsx("td", { className: "px-4 py-3 text-right text-gray-400 whitespace-nowrap", children: format(parseISO(e.acquisitionDate), 'dd MMM yyyy') }), _jsx("td", { className: "px-4 py-3 text-right text-gray-400", children: e.holdingDays }), _jsx("td", { className: "px-4 py-3 text-right tabular-nums text-gray-600", children: e.quantity.toLocaleString() }), _jsx("td", { className: "px-4 py-3 text-right tabular-nums text-gray-700", children: fmtCcy(e.proceeds) }), _jsx("td", { className: "px-4 py-3 text-right tabular-nums text-gray-600", children: fmtCcy(e.costBase) }), _jsxs("td", { className: clsx('px-4 py-3 text-right tabular-nums font-medium', isGain ? 'text-emerald-600' : 'text-red-500'), children: [isGain ? '+' : '', fmtCcy(e.capitalGain)] }), _jsx("td", { className: "px-4 py-3 text-right", children: e.discountApplied
                                                ? _jsx("span", { className: "inline-block px-1.5 py-0.5 text-xs bg-emerald-50 text-emerald-700 rounded", children: "50%" })
                                                : _jsx("span", { className: "text-gray-300", children: "\u2014" }) }), _jsx("td", { className: clsx('px-4 py-3 text-right tabular-nums font-semibold', e.assessableGain >= 0 ? 'text-gray-900' : 'text-red-500'), children: fmtCcy(e.assessableGain) })] }, i));
                            }), events.length === 0 && (_jsx("tr", { children: _jsx("td", { colSpan: 10, className: "px-4 py-10 text-center text-gray-400", children: "No disposal events for this financial year." }) }))] })] }) }) }));
}
// ── Open Parcels Table ────────────────────────────────────────────────────────
function OpenParcelsTable({ parcels, isLoading }) {
    if (isLoading)
        return _jsx(TableLoader, { text: "Loading open parcels\u2026" });
    return (_jsxs("div", { className: "bg-white rounded-xl border border-gray-200", children: [_jsxs("div", { className: "px-6 py-3 border-b border-gray-100 flex items-center justify-between", children: [_jsx("span", { className: "text-sm font-semibold text-gray-700", children: "Open Tax Parcels" }), _jsxs("span", { className: "text-xs text-gray-400", children: [parcels.length, " parcels"] })] }), _jsx("div", { className: "overflow-x-auto", children: _jsxs("table", { className: "w-full text-sm", children: [_jsx("thead", { children: _jsx("tr", { className: "text-xs text-gray-400 border-b border-gray-100", children: ['Ticker', 'Quantity', 'Cost/Unit', 'Cost Base', 'Acquired', 'Days', 'Discount', 'Current Price', 'Unrealised'].map(h => (_jsx("th", { className: "px-4 py-3 text-right first:text-left font-medium whitespace-nowrap", children: h }, h))) }) }), _jsxs("tbody", { children: [parcels.map(p => {
                                    const pos = p.unrealisedGain >= 0;
                                    return (_jsxs("tr", { className: "border-b border-gray-50 last:border-0 hover:bg-gray-50/40", children: [_jsx("td", { className: "px-4 py-3 font-semibold text-gray-900", children: p.ticker }), _jsx("td", { className: "px-4 py-3 text-right tabular-nums text-gray-700", children: p.quantity.toLocaleString() }), _jsx("td", { className: "px-4 py-3 text-right tabular-nums text-gray-600", children: fmtCcy(p.costPerUnit) }), _jsx("td", { className: "px-4 py-3 text-right tabular-nums text-gray-700", children: fmtCcy(p.totalCostBase) }), _jsx("td", { className: "px-4 py-3 text-right text-gray-500 whitespace-nowrap", children: format(parseISO(p.acquisitionDate), 'dd MMM yyyy') }), _jsx("td", { className: "px-4 py-3 text-right text-gray-400", children: p.holdingDays }), _jsx("td", { className: "px-4 py-3 text-right", children: p.cgtDiscountEligible
                                                    ? _jsxs("span", { className: "inline-flex items-center gap-1 px-1.5 py-0.5 text-xs bg-emerald-50 text-emerald-700 rounded", children: [_jsx(Shield, { size: 9 }), " Eligible"] })
                                                    : _jsxs("span", { className: "text-xs text-gray-400", children: [365 - p.holdingDays, "d to go"] }) }), _jsx("td", { className: "px-4 py-3 text-right tabular-nums text-gray-700", children: fmtCcy(p.currentPrice) }), _jsxs("td", { className: clsx('px-4 py-3 text-right tabular-nums font-medium', pos ? 'text-emerald-600' : 'text-red-500'), children: [pos ? '+' : '', fmtCcy(p.unrealisedGain), _jsxs("span", { className: "text-xs ml-1 opacity-70", children: ["(", pos ? '+' : '', ((p.unrealisedGainPct ?? 0) * 100).toFixed(1), "%)"] })] })] }, p.parcelId));
                                }), parcels.length === 0 && (_jsx("tr", { children: _jsx("td", { colSpan: 9, className: "px-4 py-10 text-center text-gray-400", children: "No open parcels. Add a BUY trade to get started." }) }))] })] }) })] }));
}
// ── Small components ──────────────────────────────────────────────────────────
function TaxCard({ label, value, icon, positive, neutral, highlight, sub }) {
    return (_jsxs("div", { className: clsx('rounded-xl border p-5', highlight ? 'bg-blue-50 border-blue-200' : 'bg-white border-gray-200'), children: [_jsxs("div", { className: "flex items-center gap-2 text-gray-400 mb-3", children: [icon, _jsx("span", { className: "text-xs font-medium uppercase tracking-wide", children: label })] }), _jsx("div", { className: clsx('text-xl font-semibold', neutral ? 'text-gray-700'
                    : positive ? 'text-emerald-600'
                        : 'text-red-500'), children: value }), sub && _jsx("div", { className: "text-xs text-gray-400 mt-1", children: sub })] }));
}
function TableLoader({ text }) {
    return (_jsxs("div", { className: "flex items-center justify-center py-16 text-gray-400", children: [_jsx(Loader2, { size: 18, className: "animate-spin mr-2" }), " ", text] }));
}
function fmtCcy(value, currency = 'AUD') {
    const num = value ?? 0;
    return new Intl.NumberFormat('en-AU', {
        style: 'currency', currency,
        minimumFractionDigits: 2, maximumFractionDigits: 2,
    }).format(Number.isNaN(num) ? 0 : num);
}
