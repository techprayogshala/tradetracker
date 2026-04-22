import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from "react/jsx-runtime";
import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { format, parseISO } from 'date-fns';
import { Plus, Search, Download, TrendingUp, TrendingDown, ArrowLeftRight, DollarSign, X, Loader2 } from 'lucide-react';
import clsx from 'clsx';
import api from '../lib/apiClient';
const TRADE_TYPES = ['BUY', 'SELL', 'DIVIDEND', 'RETURN_OF_CAPITAL', 'TRANSFER_IN', 'TRANSFER_OUT'];
const EXCHANGES = ['ASX', 'NYSE', 'NASDAQ', 'LSE', 'TSX', 'HKEX', 'OTHER'];
const CURRENCIES = ['AUD', 'USD', 'GBP', 'EUR', 'HKD', 'CAD', 'JPY'];
// ── Page ──────────────────────────────────────────────────────────────────────
export default function TradesPage() {
    const { portfolioId } = useParams();
    const [showForm, setShowForm] = useState(false);
    const [search, setSearch] = useState('');
    const [typeFilter, setTypeFilter] = useState('');
    const [page, setPage] = useState(0);
    const { data, isLoading } = useQuery({
        queryKey: ['trades', portfolioId, search, typeFilter, page],
        queryFn: async () => {
            const res = await api.get(`/v1/portfolios/${portfolioId}/trades`, {
                params: { ticker: search || undefined, tradeType: typeFilter || undefined,
                    page, size: 25, sort: 'tradeDate,desc' },
            });
            return res.data;
        },
        enabled: !!portfolioId,
    });
    return (_jsxs("div", { className: "p-8 space-y-5", children: [_jsxs("div", { className: "flex items-center justify-between", children: [_jsxs("div", { children: [_jsx("h1", { className: "text-2xl font-semibold text-gray-900", children: "Trades" }), _jsxs("p", { className: "text-sm text-gray-500 mt-0.5", children: [data?.totalElements ?? 0, " transactions"] })] }), _jsxs("div", { className: "flex items-center gap-2", children: [_jsxs("button", { className: "flex items-center gap-1.5 px-3 py-2 text-sm text-gray-600\n                             border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors", children: [_jsx(Download, { size: 14 }), " Export"] }), _jsxs("button", { onClick: () => setShowForm(true), className: "flex items-center gap-1.5 px-3 py-2 text-sm text-white\n                       bg-blue-600 rounded-lg hover:bg-blue-700 transition-colors", children: [_jsx(Plus, { size: 14 }), " Add trade"] })] })] }), _jsxs("div", { className: "flex items-center gap-3", children: [_jsxs("div", { className: "relative flex-1 max-w-xs", children: [_jsx(Search, { size: 14, className: "absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" }), _jsx("input", { type: "text", placeholder: "Search ticker\u2026", value: search, onChange: e => { setSearch(e.target.value); setPage(0); }, className: "w-full pl-8 pr-3 py-2 text-sm border border-gray-300 rounded-lg\n                       focus:outline-none focus:ring-2 focus:ring-blue-500" })] }), _jsxs("select", { value: typeFilter, onChange: e => { setTypeFilter(e.target.value); setPage(0); }, className: "px-3 py-2 text-sm border border-gray-300 rounded-lg focus:outline-none\n                     focus:ring-2 focus:ring-blue-500 bg-white text-gray-700", children: [_jsx("option", { value: "", children: "All types" }), TRADE_TYPES.map(t => _jsx("option", { value: t, children: fmtType(t) }, t))] })] }), _jsx("div", { className: "bg-white rounded-xl border border-gray-200", children: isLoading ? (_jsxs("div", { className: "flex items-center justify-center py-16 text-gray-400", children: [_jsx(Loader2, { size: 20, className: "animate-spin mr-2" }), " Loading trades\u2026"] })) : (_jsxs(_Fragment, { children: [_jsx("div", { className: "overflow-x-auto", children: _jsxs("table", { className: "w-full text-sm", children: [_jsx("thead", { children: _jsx("tr", { className: "text-xs text-gray-400 border-b border-gray-100", children: ['Date', 'Security', 'Type', 'Quantity', 'Price', 'Fees', 'Total', 'Source'].map(h => (_jsx("th", { className: "px-5 py-3 text-right first:text-left font-medium whitespace-nowrap", children: h }, h))) }) }), _jsxs("tbody", { children: [data?.content.map(trade => _jsx(TradeRow, { trade: trade }, trade.id)), data?.content.length === 0 && (_jsx("tr", { children: _jsx("td", { colSpan: 8, className: "px-5 py-12 text-center text-gray-400", children: "No trades found. Click \"Add trade\" to record your first transaction." }) }))] })] }) }), (data?.totalPages ?? 0) > 1 && (_jsxs("div", { className: "px-5 py-3 border-t border-gray-100 flex items-center justify-between", children: [_jsxs("span", { className: "text-xs text-gray-400", children: ["Page ", page + 1, " of ", data?.totalPages] }), _jsxs("div", { className: "flex gap-1", children: [_jsx(PagerBtn, { onClick: () => setPage(p => p - 1), disabled: page === 0, children: "Prev" }), _jsx(PagerBtn, { onClick: () => setPage(p => p + 1), disabled: page >= (data?.totalPages ?? 1) - 1, children: "Next" })] })] }))] })) }), showForm && _jsx(AddTradeModal, { portfolioId: portfolioId, onClose: () => setShowForm(false) })] }));
}
// ── Trade row ─────────────────────────────────────────────────────────────────
function TradeRow({ trade }) {
    const isBuy = trade.tradeType === 'BUY';
    const isSell = trade.tradeType === 'SELL';
    const isDividend = trade.tradeType === 'DIVIDEND';
    return (_jsxs("tr", { className: "border-b border-gray-50 last:border-0 hover:bg-gray-50/50 transition-colors", children: [_jsx("td", { className: "px-5 py-3 text-gray-500 whitespace-nowrap", children: format(parseISO(trade.tradeDate), 'dd MMM yyyy') }), _jsxs("td", { className: "px-5 py-3", children: [_jsx("span", { className: "font-semibold text-gray-900", children: trade.ticker }), _jsx("span", { className: "text-xs text-gray-400 ml-1", children: trade.exchange })] }), _jsx("td", { className: "px-5 py-3", children: _jsxs("span", { className: clsx('inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium', isBuy && 'bg-emerald-50 text-emerald-700', isSell && 'bg-red-50 text-red-600', isDividend && 'bg-blue-50 text-blue-700', !isBuy && !isSell && !isDividend && 'bg-gray-100 text-gray-600'), children: [isBuy && _jsx(TrendingUp, { size: 10 }), isSell && _jsx(TrendingDown, { size: 10 }), isDividend && _jsx(DollarSign, { size: 10 }), !isBuy && !isSell && !isDividend && _jsx(ArrowLeftRight, { size: 10 }), fmtType(trade.tradeType)] }) }), _jsx("td", { className: "px-5 py-3 text-right tabular-nums text-gray-700", children: trade.quantity.toLocaleString() }), _jsx("td", { className: "px-5 py-3 text-right tabular-nums text-gray-600", children: fmtCcy(trade.price, trade.currency) }), _jsx("td", { className: "px-5 py-3 text-right tabular-nums text-gray-500", children: fmtCcy(trade.fees, trade.currency) }), _jsx("td", { className: "px-5 py-3 text-right tabular-nums font-medium text-gray-900", children: fmtCcy(trade.totalCost, trade.currency) }), _jsx("td", { className: "px-5 py-3 text-right", children: _jsx("span", { className: "text-xs text-gray-400 capitalize", children: trade.source.toLowerCase().replace('_', ' ') }) })] }));
}
// ── Add trade modal ───────────────────────────────────────────────────────────
function AddTradeModal({ portfolioId, onClose }) {
    const qc = useQueryClient();
    const [form, setForm] = useState({
        ticker: '', exchange: 'ASX', tradeType: 'BUY',
        quantity: '', price: '', fees: '9.95',
        currency: 'AUD', tradeDate: format(new Date(), 'yyyy-MM-dd'), notes: '',
    });
    const [errors, setErrors] = useState({});
    const mutation = useMutation({
        mutationFn: (d) => api.post(`/v1/portfolios/${portfolioId}/trades`, {
            ticker: d.ticker.toUpperCase(), exchange: d.exchange, tradeType: d.tradeType,
            quantity: parseFloat(d.quantity), price: parseFloat(d.price),
            fees: parseFloat(d.fees || '0'), currency: d.currency,
            tradeDate: d.tradeDate, notes: d.notes || undefined,
        }),
        onSuccess: () => {
            qc.invalidateQueries({ queryKey: ['trades', portfolioId] });
            qc.invalidateQueries({ queryKey: ['holdings', portfolioId] });
            qc.invalidateQueries({ queryKey: ['portfolios'] });
            onClose();
        },
    });
    const set = (field) => (e) => { setForm(f => ({ ...f, [field]: e.target.value })); setErrors(e => ({ ...e, [field]: undefined })); };
    const validate = () => {
        const errs = {};
        if (!form.ticker.trim())
            errs.ticker = 'Required';
        if (!form.quantity || Number(form.quantity) <= 0)
            errs.quantity = 'Must be > 0';
        if (form.price === '' || Number(form.price) < 0)
            errs.price = 'Must be ≥ 0';
        if (!form.tradeDate)
            errs.tradeDate = 'Required';
        setErrors(errs);
        return Object.keys(errs).length === 0;
    };
    const totalCost = (parseFloat(form.quantity) || 0) * (parseFloat(form.price) || 0) + (parseFloat(form.fees) || 0);
    return (_jsx("div", { className: "fixed inset-0 z-50 flex items-center justify-center bg-black/30 backdrop-blur-sm", children: _jsxs("div", { className: "bg-white rounded-2xl shadow-xl w-full max-w-lg mx-4 max-h-[90vh] overflow-y-auto", children: [_jsxs("div", { className: "flex items-center justify-between px-6 py-4 border-b border-gray-100 sticky top-0 bg-white", children: [_jsx("h2", { className: "text-base font-semibold text-gray-900", children: "Add trade" }), _jsx("button", { onClick: onClose, className: "p-1 rounded-lg hover:bg-gray-100 transition-colors", children: _jsx(X, { size: 16, className: "text-gray-500" }) })] }), _jsxs("div", { className: "px-6 py-5 space-y-4", children: [_jsxs("div", { children: [_jsx("label", { className: "block text-xs font-medium text-gray-500 mb-1.5", children: "Type" }), _jsx("div", { className: "flex gap-1 flex-wrap", children: TRADE_TYPES.map(t => (_jsx("button", { onClick: () => setForm(f => ({ ...f, tradeType: t })), className: clsx('px-3 py-1.5 text-xs rounded-lg font-medium transition-colors', form.tradeType === t ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'), children: fmtType(t) }, t))) })] }), _jsxs("div", { className: "grid grid-cols-3 gap-3", children: [_jsx("div", { className: "col-span-2", children: _jsx(Field, { label: "Ticker", error: errors.ticker, children: _jsx("input", { type: "text", placeholder: "e.g. CBA", value: form.ticker, onChange: set('ticker'), className: inputCls(!!errors.ticker), style: { textTransform: 'uppercase' } }) }) }), _jsx(Field, { label: "Exchange", children: _jsx("select", { value: form.exchange, onChange: set('exchange'), className: inputCls(), children: EXCHANGES.map(e => _jsx("option", { children: e }, e)) }) })] }), _jsxs("div", { className: "grid grid-cols-2 gap-3", children: [_jsx(Field, { label: "Quantity", error: errors.quantity, children: _jsx("input", { type: "number", placeholder: "100", min: "0", step: "any", value: form.quantity, onChange: set('quantity'), className: inputCls(!!errors.quantity) }) }), _jsx(Field, { label: "Price per unit", error: errors.price, children: _jsx("input", { type: "number", placeholder: "0.00", min: "0", step: "0.01", value: form.price, onChange: set('price'), className: inputCls(!!errors.price) }) })] }), _jsxs("div", { className: "grid grid-cols-2 gap-3", children: [_jsx(Field, { label: "Brokerage fees", children: _jsx("input", { type: "number", placeholder: "9.95", min: "0", step: "0.01", value: form.fees, onChange: set('fees'), className: inputCls() }) }), _jsx(Field, { label: "Currency", children: _jsx("select", { value: form.currency, onChange: set('currency'), className: inputCls(), children: CURRENCIES.map(c => _jsx("option", { children: c }, c)) }) })] }), _jsx(Field, { label: "Trade date", error: errors.tradeDate, children: _jsx("input", { type: "date", value: form.tradeDate, onChange: set('tradeDate'), className: inputCls(!!errors.tradeDate) }) }), _jsx(Field, { label: "Notes (optional)", children: _jsx("textarea", { placeholder: "Optional notes\u2026", value: form.notes, onChange: set('notes'), rows: 2, className: inputCls() + ' resize-none' }) }), totalCost > 0 && (_jsxs("div", { className: "flex items-center justify-between py-3 px-4 bg-gray-50 rounded-xl", children: [_jsxs("span", { className: "text-sm text-gray-500", children: ["Total ", form.tradeType === 'SELL' ? 'proceeds' : 'cost'] }), _jsx("span", { className: "text-base font-semibold text-gray-900", children: fmtCcy(totalCost, form.currency) })] })), mutation.isError && (_jsx("div", { className: "px-4 py-3 bg-red-50 text-red-700 text-sm rounded-xl", children: mutation.error?.response?.data?.detail ?? 'Something went wrong. Please try again.' }))] }), _jsxs("div", { className: "flex items-center justify-end gap-2 px-6 py-4 border-t border-gray-100", children: [_jsx("button", { onClick: onClose, className: "px-4 py-2 text-sm text-gray-600 hover:text-gray-900", children: "Cancel" }), _jsxs("button", { onClick: () => validate() && mutation.mutate(form), disabled: mutation.isPending, className: "flex items-center gap-2 px-4 py-2 text-sm text-white bg-blue-600 rounded-lg\n                       hover:bg-blue-700 disabled:opacity-60 transition-colors", children: [mutation.isPending && _jsx(Loader2, { size: 14, className: "animate-spin" }), mutation.isPending ? 'Saving…' : 'Save trade'] })] })] }) }));
}
// ── Helpers ───────────────────────────────────────────────────────────────────
function Field({ label, error, children }) {
    return (_jsxs("div", { children: [_jsx("label", { className: "block text-xs font-medium text-gray-500 mb-1.5", children: label }), children, error && _jsx("p", { className: "mt-1 text-xs text-red-500", children: error })] }));
}
function PagerBtn({ children, onClick, disabled }) {
    return (_jsx("button", { onClick: onClick, disabled: disabled, className: "px-3 py-1 text-xs border border-gray-200 rounded-md disabled:opacity-40 hover:bg-gray-50 transition-colors", children: children }));
}
function inputCls(hasError = false) {
    return clsx('w-full px-3 py-2 text-sm border rounded-lg transition-colors', 'focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent', hasError ? 'border-red-300 bg-red-50' : 'border-gray-300 bg-white text-gray-900');
}
function fmtType(t) {
    return { BUY: 'Buy', SELL: 'Sell', DIVIDEND: 'Dividend', RETURN_OF_CAPITAL: 'Return of Capital',
        TRANSFER_IN: 'Transfer In', TRANSFER_OUT: 'Transfer Out' }[t] ?? t;
}
function fmtCcy(value, currency = 'AUD') {
    return new Intl.NumberFormat('en-AU', { style: 'currency', currency,
        minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(value);
}
