import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from "react/jsx-runtime";
import { useState, useRef } from 'react';
import { useParams } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { format, parseISO } from 'date-fns';
import { Plus, Search, Download, Upload, TrendingUp, TrendingDown, ArrowLeftRight, DollarSign, X, Loader2, Pencil, Trash2, CheckSquare, Square } from 'lucide-react';
import clsx from 'clsx';
import api from '../lib/apiClient';
const TRADE_TYPES = ['BUY', 'SELL', 'DIVIDEND', 'RETURN_OF_CAPITAL', 'TRANSFER_IN', 'TRANSFER_OUT'];
const EXCHANGES = ['ASX', 'NYSE', 'NASDAQ', 'LSE', 'TSX', 'HKEX', 'OTHER'];
const CURRENCIES = ['AUD', 'USD', 'GBP', 'EUR', 'HKD', 'CAD', 'JPY'];
// ── Page ──────────────────────────────────────────────────────────────────────
export default function TradesPage() {
    const { portfolioId } = useParams();
    const qc = useQueryClient();
    const [showForm, setShowForm] = useState(false);
    const [showUpload, setShowUpload] = useState(false);
    const [editTrade, setEditTrade] = useState(null);
    const [deleteTrade, setDeleteTrade] = useState(null);
    const [selectedTrades, setSelectedTrades] = useState(new Set());
    const [showDeleteMultiple, setShowDeleteMultiple] = useState(false);
    const [search, setSearch] = useState('');
    const [typeFilter, setTypeFilter] = useState('');
    const [page, setPage] = useState(0);
    const fileRef = useRef(null);
    const deleteMutation = useMutation({
        mutationFn: (tradeId) => api.delete(`/v1/portfolios/${portfolioId}/trades/${tradeId}`),
        onSuccess: () => {
            qc.invalidateQueries({ queryKey: ['trades', portfolioId] });
            qc.invalidateQueries({ queryKey: ['holdings', portfolioId] });
            qc.invalidateQueries({ queryKey: ['portfolios'] });
            setDeleteTrade(null);
        },
    });
    const deleteMultipleMutation = useMutation({
        mutationFn: async (tradeIds) => {
            await api.delete(`/v1/portfolios/${portfolioId}/trades`, { params: { ids: tradeIds.join(',') } });
        },
        onSuccess: () => {
            qc.invalidateQueries({ queryKey: ['trades', portfolioId] });
            qc.invalidateQueries({ queryKey: ['holdings', portfolioId] });
            qc.invalidateQueries({ queryKey: ['portfolios'] });
            setSelectedTrades(new Set());
            setShowDeleteMultiple(false);
        },
    });
    const updateMutation = useMutation({
        mutationFn: ({ tradeId, data }) => api.put(`/v1/portfolios/${portfolioId}/trades/${tradeId}`, {
            ticker: data.ticker.toUpperCase(), exchange: data.exchange, tradeType: data.tradeType,
            quantity: parseFloat(data.quantity), price: parseFloat(data.price),
            fees: parseFloat(data.fees || '0'), currency: data.currency,
            tradeDate: data.tradeDate, notes: data.notes || undefined,
        }),
        onSuccess: () => {
            qc.invalidateQueries({ queryKey: ['trades', portfolioId] });
            qc.invalidateQueries({ queryKey: ['holdings', portfolioId] });
            qc.invalidateQueries({ queryKey: ['portfolios'] });
            setEditTrade(null);
        },
    });
    const { data, isLoading } = useQuery({
        queryKey: ['trades', portfolioId, search, typeFilter, page],
        queryFn: async () => {
            const params = { page: String(page), size: '25', sort: 'tradeDate,desc' };
            if (search)
                params.ticker = search;
            if (typeFilter)
                params.tradeType = typeFilter;
            const res = await api.get(`/v1/portfolios/${portfolioId}/trades`, { params });
            return res.data;
        },
        enabled: !!portfolioId,
    });
    const toggleSelectAll = () => {
        if (!data?.content)
            return;
        if (selectedTrades.size === data.content.length) {
            setSelectedTrades(new Set());
        }
        else {
            setSelectedTrades(new Set(data.content.map(t => t.id)));
        }
    };
    const toggleSelect = (tradeId) => {
        const newSet = new Set(selectedTrades);
        if (newSet.has(tradeId)) {
            newSet.delete(tradeId);
        }
        else {
            newSet.add(tradeId);
        }
        setSelectedTrades(newSet);
    };
    return (_jsxs("div", { className: "p-8 space-y-5", children: [_jsxs("div", { className: "flex items-center justify-between", children: [_jsxs("div", { children: [_jsx("h1", { className: "text-2xl font-semibold text-gray-900", children: "Trades" }), _jsxs("p", { className: "text-sm text-gray-500 mt-0.5", children: [data?.totalElements ?? 0, " transactions"] })] }), _jsxs("div", { className: "flex items-center gap-2", children: [selectedTrades.size > 0 && (_jsxs("button", { onClick: () => setShowDeleteMultiple(true), className: "flex items-center gap-1.5 px-3 py-2 text-sm text-red-600\n                         border border-red-200 rounded-lg hover:bg-red-50 transition-colors", children: [_jsx(Trash2, { size: 14 }), " Delete (", selectedTrades.size, ")"] })), _jsxs("button", { onClick: () => setShowUpload(true), className: "flex items-center gap-1.5 px-3 py-2 text-sm text-gray-600\n                       border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors", children: [_jsx(Upload, { size: 14 }), " Import CSV"] }), _jsxs("button", { className: "flex items-center gap-1.5 px-3 py-2 text-sm text-gray-600\n                             border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors", children: [_jsx(Download, { size: 14 }), " Export"] }), _jsxs("button", { onClick: () => setShowForm(true), className: "flex items-center gap-1.5 px-3 py-2 text-sm text-white\n                       bg-blue-600 rounded-lg hover:bg-blue-700 transition-colors", children: [_jsx(Plus, { size: 14 }), " Add trade"] })] })] }), _jsxs("div", { className: "flex items-center gap-3", children: [_jsxs("div", { className: "relative flex-1 max-w-xs", children: [_jsx(Search, { size: 14, className: "absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" }), _jsx("input", { type: "text", placeholder: "Search ticker\u2026", value: search, onChange: e => { setSearch(e.target.value); setPage(0); }, className: "w-full pl-8 pr-3 py-2 text-sm border border-gray-300 rounded-lg\n                       focus:outline-none focus:ring-2 focus:ring-blue-500" })] }), _jsxs("select", { value: typeFilter, onChange: e => { setTypeFilter(e.target.value); setPage(0); }, className: "px-3 py-2 text-sm border border-gray-300 rounded-lg focus:outline-none\n                     focus:ring-2 focus:ring-blue-500 bg-white text-gray-700", children: [_jsx("option", { value: "", children: "All types" }), TRADE_TYPES.map(t => _jsx("option", { value: t, children: fmtType(t) }, t))] })] }), _jsx("div", { className: "bg-white rounded-xl border border-gray-200", children: isLoading ? (_jsxs("div", { className: "flex items-center justify-center py-16 text-gray-400", children: [_jsx(Loader2, { size: 20, className: "animate-spin mr-2" }), " Loading trades\u2026"] })) : (_jsxs(_Fragment, { children: [_jsx("div", { className: "overflow-x-auto", children: _jsxs("table", { className: "w-full text-sm", children: [_jsx("thead", { children: _jsxs("tr", { className: "text-xs text-gray-400 border-b border-gray-100", children: [_jsx("th", { className: "px-5 py-3 w-8", children: _jsx("button", { onClick: toggleSelectAll, className: "text-blue-600 hover:text-blue-800", children: data?.content && selectedTrades.size === data.content.length ? _jsx(CheckSquare, { size: 16 }) : _jsx(Square, { size: 16 }) }) }), ['Date', 'Security', 'Type', 'Quantity', 'Price', 'Fees', 'Total', 'Source', ''].map(h => (_jsx("th", { className: "px-5 py-3 text-right first:text-left font-medium whitespace-nowrap", children: h }, h)))] }) }), _jsxs("tbody", { children: [data?.content.map(trade => (_jsx(TradeRow, { trade: trade, isSelected: selectedTrades.has(trade.id), onSelect: () => toggleSelect(trade.id), onEdit: () => setEditTrade(trade), onDelete: () => setDeleteTrade(trade) }, trade.id))), data?.content.length === 0 && (_jsx("tr", { children: _jsx("td", { colSpan: 9, className: "px-5 py-12 text-center text-gray-400", children: "No trades found. Click \"Add trade\" to record your first transaction." }) }))] })] }) }), (data?.totalPages ?? 0) > 1 && (_jsxs("div", { className: "px-5 py-3 border-t border-gray-100 flex items-center justify-between", children: [_jsxs("span", { className: "text-xs text-gray-400", children: ["Page ", page + 1, " of ", data?.totalPages] }), _jsxs("div", { className: "flex gap-1", children: [_jsx(PagerBtn, { onClick: () => setPage(p => p - 1), disabled: page === 0, children: "Prev" }), _jsx(PagerBtn, { onClick: () => setPage(p => p + 1), disabled: page >= (data?.totalPages ?? 1) - 1, children: "Next" })] })] }))] })) }), showForm && _jsx(AddTradeModal, { portfolioId: portfolioId, onClose: () => setShowForm(false) }), showUpload && _jsx(BulkUploadModal, { portfolioId: portfolioId, onClose: () => setShowUpload(false) }), editTrade && (_jsx(EditTradeModal, { trade: editTrade, portfolioId: portfolioId, onClose: () => setEditTrade(null) })), deleteTrade && (_jsx(DeleteTradeModal, { trade: deleteTrade, onClose: () => setDeleteTrade(null), onConfirm: () => deleteMutation.mutate(deleteTrade.id), isPending: deleteMutation.isPending, isError: deleteMutation.isError, error: deleteMutation.error })), showDeleteMultiple && (_jsx(DeleteMultipleModal, { count: selectedTrades.size, onClose: () => setShowDeleteMultiple(false), onConfirm: () => deleteMultipleMutation.mutate(Array.from(selectedTrades)), isPending: deleteMultipleMutation.isPending, isError: deleteMultipleMutation.isError, error: deleteMultipleMutation.error }))] }));
}
// ── Trade row ─────────────────────────────────────────────────────────────────
function TradeRow({ trade, isSelected, onSelect, onEdit, onDelete }) {
    const isBuy = trade.tradeType === 'BUY';
    const isSell = trade.tradeType === 'SELL';
    const isDividend = trade.tradeType === 'DIVIDEND';
    return (_jsxs("tr", { className: "border-b border-gray-50 last:border-0 hover:bg-gray-50/50 transition-colors", children: [_jsx("td", { className: "px-5 py-3 w-8", children: _jsx("button", { onClick: onSelect, className: "text-blue-600 hover:text-blue-800", children: isSelected ? _jsx(CheckSquare, { size: 16 }) : _jsx(Square, { size: 16 }) }) }), _jsx("td", { className: "px-5 py-3 text-gray-500 whitespace-nowrap", children: format(parseISO(trade.tradeDate), 'dd MMM yyyy') }), _jsxs("td", { className: "px-5 py-3", children: [_jsx("span", { className: "font-semibold text-gray-900", children: trade.ticker }), _jsx("span", { className: "text-xs text-gray-400 ml-1", children: trade.exchange })] }), _jsx("td", { className: "px-5 py-3", children: _jsxs("span", { className: clsx('inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium', isBuy && 'bg-emerald-50 text-emerald-700', isSell && 'bg-red-50 text-red-600', isDividend && 'bg-blue-50 text-blue-700', !isBuy && !isSell && !isDividend && 'bg-gray-100 text-gray-600'), children: [isBuy && _jsx(TrendingUp, { size: 10 }), isSell && _jsx(TrendingDown, { size: 10 }), isDividend && _jsx(DollarSign, { size: 10 }), !isBuy && !isSell && !isDividend && _jsx(ArrowLeftRight, { size: 10 }), fmtType(trade.tradeType)] }) }), _jsx("td", { className: "px-5 py-3 text-right tabular-nums text-gray-700", children: trade.quantity.toLocaleString() }), _jsx("td", { className: "px-5 py-3 text-right tabular-nums text-gray-600", children: fmtCcy(trade.price, trade.currency) }), _jsx("td", { className: "px-5 py-3 text-right tabular-nums text-gray-500", children: fmtCcy(trade.fees, trade.currency) }), _jsx("td", { className: "px-5 py-3 text-right tabular-nums font-medium text-gray-900", children: fmtCcy(trade.totalCost, trade.currency) }), _jsx("td", { className: "px-5 py-3 text-right", children: _jsx("span", { className: "text-xs text-gray-400 capitalize", children: trade.source.toLowerCase().replace('_', ' ') }) }), _jsx("td", { className: "px-5 py-3 text-right", children: _jsxs("div", { className: "flex items-center justify-end gap-1", children: [_jsx("button", { onClick: onEdit, className: "p-1 text-gray-400 hover:text-blue-600 rounded hover:bg-blue-50", children: _jsx(Pencil, { size: 14 }) }), _jsx("button", { onClick: onDelete, className: "p-1 text-gray-400 hover:text-red-600 rounded hover:bg-red-50", children: _jsx(Trash2, { size: 14 }) })] }) })] }));
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
    const bulkMutation = useMutation({
        mutationFn: async (trades) => {
            const res = await api.post(`/v1/portfolios/${portfolioId}/trades/bulk`, trades);
            return res.data;
        },
        onSuccess: () => {
            qc.invalidateQueries({ queryKey: ['trades', portfolioId] });
            qc.invalidateQueries({ queryKey: ['holdings', portfolioId] });
            qc.invalidateQueries({ queryKey: ['portfolios'] });
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
function BulkUploadModal({ portfolioId, onClose }) {
    const qc = useQueryClient();
    const fileRef = useRef(null);
    const [file, setFile] = useState(null);
    const [preview, setPreview] = useState([]);
    const [error, setError] = useState(null);
    const mutation = useMutation({
        mutationFn: async (trades) => {
            const res = await api.post(`/v1/portfolios/${portfolioId}/trades/bulk`, trades);
            return res.data;
        },
        onSuccess: () => {
            qc.invalidateQueries({ queryKey: ['trades', portfolioId] });
            qc.invalidateQueries({ queryKey: ['holdings', portfolioId] });
            qc.invalidateQueries({ queryKey: ['portfolios'] });
            onClose();
        },
    });
    const parseCsv = (text) => {
        alert('CSV PARSE STARTED!');
        console.log('=== CSV PARSE STARTED ===');
        const rows = [];
        let currentRow = [];
        let currentCell = '';
        let inQuotes = false;
        for (let i = 0; i < text.length; i++) {
            const char = text[i];
            const nextChar = text[i + 1];
            if (inQuotes) {
                if (char === '"' && nextChar === '"') {
                    currentCell += '"';
                    i++;
                }
                else if (char === '"') {
                    inQuotes = false;
                }
                else {
                    currentCell += char;
                }
            }
            else {
                if (char === '"') {
                    inQuotes = true;
                }
                else if (char === ',') {
                    currentRow.push(currentCell.trim());
                    currentCell = '';
                }
                else if (char === '\n' || (char === '\r' && nextChar === '\n')) {
                    currentRow.push(currentCell.trim());
                    if (currentRow.some(c => c))
                        rows.push(currentRow);
                    currentRow = [];
                    currentCell = '';
                    if (char === '\r')
                        i++;
                }
                else if (char !== '\r') {
                    currentCell += char;
                }
            }
        }
        currentRow.push(currentCell.trim());
        if (currentRow.some(c => c))
            rows.push(currentRow);
        if (rows.length < 2) {
            alert('parseCsv: Less than 2 rows found');
            return [];
        }
        // Find header row
        // Find header row - look for columns with meaningful names
        const headerRowIdx = rows.findIndex(r => r.some(c => /^(code|ticker|qty|quantity|price|date|type|action)$/i.test(c.trim())));
        const startIdx = headerRowIdx >= 0 ? headerRowIdx + 1 : 1;
        if (startIdx >= rows.length)
            return [];
        // Normalize headers for matching
        const headers = rows[startIdx - 1].map(h => h.toLowerCase().replace(/[^a-z0-9]/g, ''));
        console.log('CSV Headers:', headers);
        console.log('Header row (raw):', rows[startIdx - 1]);
        const col = (name) => headers.indexOf(name.toLowerCase().replace(/[^a-z0-9]/g, ''));
        const findCol = (names) => {
            for (const n of names) {
                const idx = col(n);
                if (idx >= 0)
                    return idx;
            }
            return -1;
        };
        const tickerCol = findCol(['code', 'marketcode', 'ticker', 'symbol', 'instrument']);
        const typeCol = findCol(['type', 'trdtype', 'action', 'side', 'buysell', 'tradetype', 'transactiontype', 'buyorsell']);
        console.log('typeCol index:', typeCol, 'headers:', headers);
        const qtyCol = findCol(['qty', 'quantity', 'units', 'shares', 'volume', 'number']);
        const priceCol = findCol(['price', 'rate', 'priceaud', 'unitprice', 'amount']);
        const dateCol = findCol(['date', 'tradedate', 'transactiondate', 'trxdate']);
        const feesCol = findCol(['brokerage', 'fees', 'commission', 'cost', 'fee']);
        const currencyCol = findCol(['instrumentcurrency', 'currency', 'audcurrency', 'tradecurrency', 'ccy']);
        const nameCol = findCol(['name', 'instrumentname', 'description', 'company']);
        const trades = [];
        for (let i = startIdx; i < rows.length; i++) {
            const values = rows[i];
            console.log('Row', i, 'raw values:', values);
            const tickerRaw = tickerCol >= 0 ? values[tickerCol] : values[0] || '';
            const ticker = tickerRaw.toUpperCase().trim();
            // Skip empty rows or rows with only whitespace
            if (!ticker || ticker === '')
                continue;
            // Parse quantity - allow negative for sells
            const qtyRaw = qtyCol >= 0 ? values[qtyCol] : values[2] || '0';
            const quantity = parseFloat(qtyRaw.replace(/[^0-9.-]/g, ''));
            if (isNaN(quantity) || quantity === 0)
                continue;
            // Parse price - allow negative for sells
            const priceRaw = priceCol >= 0 ? values[priceCol] : values[3] || '0';
            const price = parseFloat(priceRaw.replace(/[^0-9.-]/g, ''));
            if (isNaN(price) || price === 0)
                continue;
            // Parse trade type
            let tradeType = 'BUY';
            console.log('typeCol:', typeCol, 'value:', typeCol >= 0 ? values[typeCol] : 'N/A');
            // Check type column first
            if (typeCol >= 0 && values[typeCol] && values[typeCol].toString().trim()) {
                const typeRaw = values[typeCol].toString().toUpperCase().trim();
                console.log('Row', i, 'typeRaw:', typeRaw);
                if (typeRaw.includes('SELL') || typeRaw.includes('SOLD') || typeRaw === 'S') {
                    tradeType = 'SELL';
                }
                else if (typeRaw.includes('DIV') || typeRaw.includes('DRIP')) {
                    tradeType = 'DIVIDEND';
                }
                else if (typeRaw.includes('TRANSFER IN') || typeRaw === 'TI' || typeRaw.includes(' IN')) {
                    tradeType = 'TRANSFER_IN';
                }
                else if (typeRaw.includes('TRANSFER OUT') || typeRaw === 'TO' || typeRaw.includes('OUT')) {
                    tradeType = 'TRANSFER_OUT';
                }
                else if (typeRaw.includes('RETURN') || typeRaw.includes('ROC')) {
                    tradeType = 'RETURN_OF_CAPITAL';
                }
                else if (typeRaw === 'BUY' || typeRaw === 'B') {
                    tradeType = 'BUY';
                }
                console.log('Row', i, 'tradeType:', tradeType);
            }
            else if (quantity < 0) {
                // Negative quantity = SELL
                tradeType = 'SELL';
            }
            else if (price < 0) {
                // Negative price = SELL
                tradeType = 'SELL';
            }
            // Use absolute values for quantity and price
            const absQuantity = Math.abs(quantity);
            const absPrice = Math.abs(price);
            // Parse date - try various formats
            let tradeDate = format(new Date(), 'yyyy-MM-dd');
            if (dateCol >= 0 && values[dateCol]) {
                const dateRaw = values[dateCol].trim();
                try {
                    const parsed = parseCSVDate(dateRaw);
                    if (parsed)
                        tradeDate = format(parsed, 'yyyy-MM-dd');
                }
                catch {
                    // Use default
                }
            }
            // Parse currency
            const currency = currencyCol >= 0 ? values[currencyCol].toUpperCase().trim().substring(0, 3) : 'AUD';
            // Parse fees
            const fees = feesCol >= 0 ? parseFloat((values[feesCol] || '0').replace(/[^0-9.-]/g, '')) : 0;
            trades.push({
                ticker,
                exchange: 'ASX',
                tradeType,
                quantity: absQuantity,
                price: absPrice,
                fees: isNaN(fees) ? 0 : Math.abs(fees),
                currency: /^[A-Z]{3}$/.test(currency) ? currency : 'AUD',
                tradeDate,
                notes: nameCol >= 0 ? values[nameCol].trim() : '',
            });
        }
        return trades;
    };
    const parseCSVDate = (dateStr) => {
        // Handle DD/MM/YYYY format (Australian)
        if (/\d{1,2}\/\d{1,2}\/\d{4}/.test(dateStr)) {
            const parts = dateStr.split('/');
            return new Date(parseInt(parts[2]), parseInt(parts[1]) - 1, parseInt(parts[0]));
        }
        // Handle YYYY-MM-DD format
        if (/\d{4}-\d{2}-\d{2}/.test(dateStr)) {
            return new Date(dateStr);
        }
        // Handle DD-MM-YYYY format
        if (/\d{1,2}-\d{1,2}-\d{4}/.test(dateStr)) {
            const parts = dateStr.split('-');
            return new Date(parseInt(parts[2]), parseInt(parts[1]) - 1, parseInt(parts[0]));
        }
        return null;
    };
    const handleFile = (e) => {
        const f = e.target.files?.[0];
        if (!f)
            return;
        setFile(f);
        setError(null);
        const reader = new FileReader();
        reader.onload = (event) => {
            const text = event.target?.result;
            console.log('File loaded, length:', text.length, 'first 200 chars:', text.substring(0, 200));
            const trades = parseCsv(text);
            console.log('Parsed trades:', trades.length, trades[0]);
            if (trades.length === 0) {
                setError('No valid trades found. Check CSV format.');
                setPreview([]);
            }
            else {
                const invalid = trades.filter((t) => !t.ticker || t.quantity <= 0 || t.price <= 0);
                if (invalid.length > 0) {
                    setError(`Found ${invalid.length} invalid rows`);
                    setPreview([]);
                }
                else {
                    setPreview(trades);
                }
            }
        };
        reader.onerror = () => setError('Failed to read file');
        reader.readAsText(f);
    };
    const handleUpload = () => {
        if (!preview.length)
            return;
        mutation.mutate(preview);
    };
    const isPending = mutation.isPending;
    const isError = mutation.isError;
    const isSuccess = mutation.isSuccess;
    const errorData = mutation.error;
    const successData = mutation.data;
    return (_jsxs("div", { className: "fixed inset-0 z-50 flex items-center justify-center", children: [_jsx("div", { className: "absolute inset-0 bg-black/40", onClick: onClose }), _jsxs("div", { className: "relative bg-white rounded-2xl shadow-xl w-full max-w-2xl max-h-[90vh] overflow-auto", children: [_jsxs("div", { className: "flex items-center justify-between px-6 py-4 border-b border-gray-100", children: [_jsx("h2", { className: "text-lg font-semibold", children: "Bulk Import Trades" }), _jsx("button", { onClick: onClose, children: _jsx(X, { size: 20, className: "text-gray-400 hover:text-gray-600" }) })] }), _jsxs("div", { className: "p-6 space-y-4", children: [!file && (_jsxs("div", { onClick: () => fileRef.current?.click(), onDragOver: (e) => { e.preventDefault(); e.stopPropagation(); }, onDrop: (e) => {
                                    e.preventDefault();
                                    e.stopPropagation();
                                    const f = e.dataTransfer.files[0];
                                    if (f) {
                                        setFile(f);
                                        setError(null);
                                        const reader = new FileReader();
                                        reader.onload = (ev) => {
                                            const text = ev.target?.result;
                                            const trades = parseCsv(text);
                                            if (trades.length === 0) {
                                                setError('No valid trades found');
                                                setPreview([]);
                                            }
                                            else {
                                                const invalid = trades.filter((t) => !t.ticker || t.quantity <= 0 || t.price <= 0);
                                                if (invalid.length > 0)
                                                    setError(`Found ${invalid.length} invalid rows`);
                                                setPreview(trades);
                                            }
                                        };
                                        reader.onerror = () => setError('Failed to read file');
                                        reader.readAsText(f);
                                    }
                                }, className: "border-2 border-dashed border-gray-200 rounded-xl p-8 text-center cursor-pointer hover:border-blue-400 transition-colors", children: [_jsx(Upload, { size: 32, className: "mx-auto text-gray-300 mb-2" }), _jsx("p", { className: "text-sm text-gray-500", children: "Click or drag CSV file here" }), _jsx("p", { className: "text-xs text-gray-400 mt-1", children: "Required columns: ticker, quantity, price, date" })] })), _jsx("input", { ref: fileRef, type: "file", accept: ".csv", className: "hidden", onChange: handleFile }), preview.length > 0 && (_jsxs(_Fragment, { children: [_jsxs("div", { className: "flex items-center justify-between px-3 py-2 bg-green-50 text-green-700 rounded-lg", children: [_jsxs("span", { className: "text-sm", children: [preview.length, " trades ready to import"] }), _jsx("button", { onClick: () => { setFile(null); setPreview([]); }, className: "text-sm underline", children: "Clear" })] }), _jsx("div", { className: "max-h-60 overflow-auto border rounded-lg", children: _jsxs("table", { className: "w-full text-xs", children: [_jsx("thead", { className: "sticky top-0 bg-gray-50", children: _jsxs("tr", { children: [_jsx("th", { className: "px-3 py-2 text-left", children: "Ticker" }), _jsx("th", { className: "px-3 py-2 text-left", children: "Type" }), _jsx("th", { className: "px-3 py-2 text-right", children: "Qty" }), _jsx("th", { className: "px-3 py-2 text-right", children: "Price" }), _jsx("th", { className: "px-3 py-2 text-left", children: "Date" })] }) }), _jsxs("tbody", { children: [preview.slice(0, 20).map((t, i) => (_jsxs("tr", { className: "border-t", children: [_jsx("td", { className: "px-3 py-1.5", children: t.ticker }), _jsx("td", { className: "px-3 py-1.5", children: t.tradeType }), _jsx("td", { className: "px-3 py-1.5 text-right", children: t.quantity }), _jsx("td", { className: "px-3 py-1.5 text-right", children: t.price }), _jsx("td", { className: "px-3 py-1.5", children: t.tradeDate })] }, i))), preview.length > 20 && (_jsx("tr", { children: _jsxs("td", { colSpan: 5, className: "px-3 py-2 text-center text-gray-400", children: ["... and ", preview.length - 20, " more"] }) }))] })] }) })] })), error && (_jsx("div", { className: "px-4 py-3 bg-red-50 text-red-700 text-sm rounded-xl", children: error })), isError && (_jsx("div", { className: "px-4 py-3 bg-red-50 text-red-700 text-sm rounded-xl", children: errorData?.response?.data?.detail ?? 'Import failed' })), isSuccess && (_jsxs("div", { className: "px-4 py-3 bg-green-50 text-green-700 text-sm rounded-xl", children: ["Successfully imported ", successData?.length ?? 0, " trades"] }))] }), _jsxs("div", { className: "flex items-center justify-end gap-2 px-6 py-4 border-t border-gray-100", children: [_jsx("button", { onClick: onClose, className: "px-4 py-2 text-sm text-gray-600 hover:text-gray-900", children: "Cancel" }), _jsxs("button", { onClick: handleUpload, disabled: !preview.length || isPending, className: "flex items-center gap-2 px-4 py-2 text-sm text-white bg-blue-600 rounded-lg\n                       hover:bg-blue-700 disabled:opacity-60 transition-colors", children: [isPending && _jsx(Loader2, { size: 14, className: "animate-spin" }), isPending ? 'Importing…' : `Import ${preview.length} trades`] })] })] })] }));
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
// ── Edit trade modal ──────────────────────────────────────────────────────────
function EditTradeModal({ trade, portfolioId, onClose }) {
    const qc = useQueryClient();
    const [form, setForm] = useState({
        ticker: trade.ticker, exchange: trade.exchange, tradeType: trade.tradeType,
        quantity: String(trade.quantity), price: String(trade.price), fees: String(trade.fees),
        currency: trade.currency, tradeDate: trade.tradeDate, notes: trade.notes || '',
    });
    const [errors, setErrors] = useState({});
    const mutation = useMutation({
        mutationFn: (d) => api.put(`/v1/portfolios/${portfolioId}/trades/${trade.id}`, {
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
    return (_jsxs("div", { className: "fixed inset-0 z-50 flex items-center justify-center", children: [_jsx("div", { className: "absolute inset-0 bg-black/40", onClick: onClose }), _jsxs("div", { className: "relative bg-white rounded-2xl shadow-xl w-full max-w-lg max-h-[90vh] overflow-auto", children: [_jsxs("div", { className: "flex items-center justify-between px-6 py-4 border-b border-gray-100", children: [_jsx("h2", { className: "text-lg font-semibold", children: "Edit Trade" }), _jsx("button", { onClick: onClose, children: _jsx(X, { size: 20, className: "text-gray-400 hover:text-gray-600" }) })] }), _jsxs("div", { className: "p-6 space-y-4", children: [_jsxs("div", { className: "grid grid-cols-2 gap-4", children: [_jsx(Field, { label: "Ticker", error: errors.ticker, children: _jsx("input", { type: "text", value: form.ticker, onChange: set('ticker'), className: inputCls(!!errors.ticker) }) }), _jsx(Field, { label: "Exchange", children: _jsx("select", { value: form.exchange, onChange: set('exchange'), className: inputCls(), children: EXCHANGES.map(e => _jsx("option", { value: e, children: e }, e)) }) })] }), _jsxs("div", { className: "grid grid-cols-2 gap-4", children: [_jsx(Field, { label: "Type", children: _jsx("select", { value: form.tradeType, onChange: set('tradeType'), className: inputCls(), children: TRADE_TYPES.map(t => _jsx("option", { value: t, children: fmtType(t) }, t)) }) }), _jsx(Field, { label: "Currency", children: _jsx("select", { value: form.currency, onChange: set('currency'), className: inputCls(), children: CURRENCIES.map(c => _jsx("option", { value: c, children: c }, c)) }) })] }), _jsxs("div", { className: "grid grid-cols-3 gap-4", children: [_jsx(Field, { label: "Quantity", error: errors.quantity, children: _jsx("input", { type: "number", value: form.quantity, onChange: set('quantity'), className: inputCls(!!errors.quantity) }) }), _jsx(Field, { label: "Price", error: errors.price, children: _jsx("input", { type: "number", step: "0.01", value: form.price, onChange: set('price'), className: inputCls(!!errors.price) }) }), _jsx(Field, { label: "Fees", children: _jsx("input", { type: "number", step: "0.01", value: form.fees, onChange: set('fees'), className: inputCls() }) })] }), _jsx(Field, { label: "Trade date", error: errors.tradeDate, children: _jsx("input", { type: "date", value: form.tradeDate, onChange: set('tradeDate'), className: inputCls(!!errors.tradeDate) }) }), _jsx(Field, { label: "Notes (optional)", children: _jsx("textarea", { value: form.notes, onChange: set('notes'), rows: 2, className: inputCls() + ' resize-none' }) }), totalCost > 0 && (_jsxs("div", { className: "flex items-center justify-between py-3 px-4 bg-gray-50 rounded-xl", children: [_jsxs("span", { className: "text-sm text-gray-500", children: ["Total ", form.tradeType === 'SELL' ? 'proceeds' : 'cost'] }), _jsx("span", { className: "text-base font-semibold text-gray-900", children: fmtCcy(totalCost, form.currency) })] })), mutation.isError && (_jsx("div", { className: "px-4 py-3 bg-red-50 text-red-700 text-sm rounded-xl", children: mutation.error?.response?.data?.detail ?? 'Something went wrong' }))] }), _jsxs("div", { className: "flex items-center justify-end gap-2 px-6 py-4 border-t border-gray-100", children: [_jsx("button", { onClick: onClose, className: "px-4 py-2 text-sm text-gray-600 hover:text-gray-900", children: "Cancel" }), _jsxs("button", { onClick: () => validate() && mutation.mutate(form), disabled: mutation.isPending, className: "flex items-center gap-2 px-4 py-2 text-sm text-white bg-blue-600 rounded-lg hover:bg-blue-700 disabled:opacity-60", children: [mutation.isPending && _jsx(Loader2, { size: 14, className: "animate-spin" }), mutation.isPending ? 'Saving…' : 'Save changes'] })] })] })] }));
}
// ── Delete trade modal ─────────────────────────────────────────────────────────
function DeleteTradeModal({ trade, onClose, onConfirm, isPending, isError, error }) {
    return (_jsxs("div", { className: "fixed inset-0 z-50 flex items-center justify-center", children: [_jsx("div", { className: "absolute inset-0 bg-black/40", onClick: onClose }), _jsxs("div", { className: "relative bg-white rounded-2xl shadow-xl w-full max-w-md", children: [_jsxs("div", { className: "flex items-center justify-between px-6 py-4 border-b border-gray-100", children: [_jsx("h2", { className: "text-lg font-semibold text-red-600", children: "Delete Trade" }), _jsx("button", { onClick: onClose, children: _jsx(X, { size: 20, className: "text-gray-400 hover:text-gray-600" }) })] }), _jsxs("div", { className: "p-6 space-y-4", children: [_jsx("p", { className: "text-gray-600", children: "Are you sure you want to delete this trade?" }), _jsxs("div", { className: "p-4 bg-gray-50 rounded-xl space-y-1", children: [_jsxs("div", { className: "flex justify-between", children: [_jsx("span", { className: "text-gray-500", children: "Ticker" }), _jsx("span", { className: "font-medium", children: trade.ticker })] }), _jsxs("div", { className: "flex justify-between", children: [_jsx("span", { className: "text-gray-500", children: "Type" }), _jsx("span", { className: "font-medium", children: trade.tradeType })] }), _jsxs("div", { className: "flex justify-between", children: [_jsx("span", { className: "text-gray-500", children: "Quantity" }), _jsx("span", { className: "font-medium", children: trade.quantity })] }), _jsxs("div", { className: "flex justify-between", children: [_jsx("span", { className: "text-gray-500", children: "Price" }), _jsx("span", { className: "font-medium", children: fmtCcy(trade.price, trade.currency) })] })] }), _jsx("p", { className: "text-sm text-red-500", children: "This will also delete any associated tax parcels." }), isError && (_jsx("div", { className: "px-4 py-3 bg-red-50 text-red-700 text-sm rounded-xl", children: error?.response?.data?.detail ?? 'Delete failed' }))] }), _jsxs("div", { className: "flex items-center justify-end gap-2 px-6 py-4 border-t border-gray-100", children: [_jsx("button", { onClick: onClose, className: "px-4 py-2 text-sm text-gray-600 hover:text-gray-900", children: "Cancel" }), _jsxs("button", { onClick: onConfirm, disabled: isPending, className: "flex items-center gap-2 px-4 py-2 text-sm text-white bg-red-600 rounded-lg hover:bg-red-700 disabled:opacity-60", children: [isPending && _jsx(Loader2, { size: 14, className: "animate-spin" }), isPending ? 'Deleting…' : 'Delete trade'] })] })] })] }));
}
// ── Delete multiple trades modal ──────────────────────────────────────────────
function DeleteMultipleModal({ count, onClose, onConfirm, isPending, isError, error }) {
    return (_jsxs("div", { className: "fixed inset-0 z-50 flex items-center justify-center", children: [_jsx("div", { className: "absolute inset-0 bg-black/40", onClick: onClose }), _jsxs("div", { className: "relative bg-white rounded-2xl shadow-xl w-full max-w-md", children: [_jsxs("div", { className: "flex items-center justify-between px-6 py-4 border-b border-gray-100", children: [_jsxs("h2", { className: "text-lg font-semibold text-red-600", children: ["Delete ", count, " Trades"] }), _jsx("button", { onClick: onClose, children: _jsx(X, { size: 20, className: "text-gray-400 hover:text-gray-600" }) })] }), _jsxs("div", { className: "p-6 space-y-4", children: [_jsxs("p", { className: "text-gray-600", children: ["Are you sure you want to delete ", count, " trades? This action cannot be undone."] }), _jsx("p", { className: "text-sm text-red-500", children: "This will also delete any associated tax parcels." }), isError && (_jsx("div", { className: "px-4 py-3 bg-red-50 text-red-700 text-sm rounded-xl", children: error?.response?.data?.detail ?? 'Delete failed' }))] }), _jsxs("div", { className: "flex items-center justify-end gap-2 px-6 py-4 border-t border-gray-100", children: [_jsx("button", { onClick: onClose, className: "px-4 py-2 text-sm text-gray-600 hover:text-gray-900", children: "Cancel" }), _jsxs("button", { onClick: onConfirm, disabled: isPending, className: "flex items-center gap-2 px-4 py-2 text-sm text-white bg-red-600 rounded-lg hover:bg-red-700 disabled:opacity-60", children: [isPending && _jsx(Loader2, { size: 14, className: "animate-spin" }), isPending ? 'Deleting…' : `Delete ${count} trades`] })] })] })] }));
}
