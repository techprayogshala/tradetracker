import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from "react/jsx-runtime";
import { useState, useRef } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useKeycloak } from '@react-keycloak/web';
import { User, Briefcase, Link2, Upload, Bell, Check, Loader2, X, AlertCircle, RefreshCw } from 'lucide-react';
import clsx from 'clsx';
import api from '../lib/apiClient';
import { usePortfolios, useBrokerConnections, useNotificationPrefs, } from '../hooks/usePortfolios';
const BROKERS = [
    { id: 'COMMSEC', label: 'CommSec', note: 'CSV / PDF import' },
    { id: 'SELFWEALTH', label: 'SelfWealth', note: 'API connected' },
    { id: 'IBKR', label: 'Interactive Brokers', note: 'API connected' },
    { id: 'STAKE', label: 'Stake', note: 'Coming soon', disabled: true },
    { id: 'PEARLER', label: 'Pearler', note: 'Coming soon', disabled: true },
];
const NOTIFICATION_TYPES = [
    { key: 'TRADE_CONFIRMED', label: 'Trade confirmations',
        desc: 'Email when any trade is recorded' },
    { key: 'DAILY_SUMMARY', label: 'Daily summary',
        desc: 'End-of-day portfolio value email' },
    { key: 'CGT_REMINDER', label: 'CGT discount reminders',
        desc: '7-day notice before a parcel reaches 12-month CGT eligibility' },
    { key: 'PRICE_ALERT', label: 'Price alerts',
        desc: 'When a holding moves more than 5% in a day' },
];
// =============================================================================
// Page root
// =============================================================================
export default function SettingsPage() {
    const [tab, setTab] = useState('profile');
    const TABS = [
        { id: 'profile', label: 'Profile', icon: _jsx(User, { size: 15 }) },
        { id: 'portfolios', label: 'Portfolios', icon: _jsx(Briefcase, { size: 15 }) },
        { id: 'brokers', label: 'Broker Accounts', icon: _jsx(Link2, { size: 15 }) },
        { id: 'import', label: 'Import PDF', icon: _jsx(Upload, { size: 15 }) },
        { id: 'notifications', label: 'Notifications', icon: _jsx(Bell, { size: 15 }) },
    ];
    return (_jsxs("div", { className: "p-8 max-w-4xl", children: [_jsx("h1", { className: "text-2xl font-semibold text-gray-900 mb-6", children: "Settings" }), _jsxs("div", { className: "flex gap-6", children: [_jsx("nav", { className: "w-44 flex-shrink-0 space-y-0.5", children: TABS.map(t => (_jsxs("button", { onClick: () => setTab(t.id), className: clsx('w-full flex items-center gap-2.5 px-3 py-2 rounded-lg text-sm transition-colors', tab === t.id ? 'bg-blue-50 text-blue-700 font-medium'
                                : 'text-gray-600 hover:bg-gray-50'), children: [t.icon, t.label] }, t.id))) }), _jsxs("div", { className: "flex-1 min-w-0", children: [tab === 'profile' && _jsx(ProfileTab, {}), tab === 'portfolios' && _jsx(PortfoliosTab, {}), tab === 'brokers' && _jsx(BrokersTab, {}), tab === 'import' && _jsx(ImportTab, {}), tab === 'notifications' && _jsx(NotificationsTab, {})] })] })] }));
}
// =============================================================================
// Profile tab
// =============================================================================
function ProfileTab() {
    const { keycloak } = useKeycloak();
    const qc = useQueryClient();
    const [editing, setEditing] = useState(false);
    const [displayName, setDisplayName] = useState('');
    const { data: profile, isLoading } = useQuery({
        queryKey: ['profile'],
        queryFn: async () => {
            const r = await api.get('/v1/settings/profile');
            return r.data;
        },
    });
    const saveMutation = useMutation({
        mutationFn: () => api.put('/v1/settings/profile', { displayName }),
        onSuccess: () => { qc.invalidateQueries({ queryKey: ['profile'] }); setEditing(false); },
    });
    if (isLoading)
        return _jsx(Card, { title: "Profile", children: _jsx(Spinner, {}) });
    return (_jsx(Card, { title: "Profile", children: _jsxs("div", { className: "space-y-3", children: [_jsx(Row, { label: "Email", children: profile?.email ?? keycloak.tokenParsed?.email ?? '—' }), _jsx(Row, { label: "Display name", children: editing ? (_jsxs("div", { className: "flex items-center gap-2", children: [_jsx("input", { autoFocus: true, value: displayName, onChange: e => setDisplayName(e.target.value), className: "flex-1 px-2 py-1 text-sm border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500" }), _jsx("button", { onClick: () => saveMutation.mutate(), disabled: saveMutation.isPending, className: "p-1 text-emerald-600 hover:text-emerald-700", children: saveMutation.isPending ? _jsx(Loader2, { size: 14, className: "animate-spin" }) : _jsx(Check, { size: 14 }) }), _jsx("button", { onClick: () => setEditing(false), className: "p-1 text-gray-400 hover:text-gray-600", children: _jsx(X, { size: 14 }) })] })) : (_jsxs("span", { className: "flex items-center gap-2", children: [profile?.displayName ?? '—', _jsx("button", { onClick: () => { setDisplayName(profile?.displayName ?? ''); setEditing(true); }, className: "text-xs text-blue-500 hover:underline", children: "Edit" })] })) }), _jsx(Row, { label: "Base currency", children: profile?.baseCurrency ?? 'AUD' }), _jsx(Row, { label: "Tax country", children: profile?.taxCountry ?? 'AU' }), _jsx(Row, { label: "Keycloak ID", children: _jsx("span", { className: "font-mono text-xs text-gray-400 truncate max-w-xs block", children: keycloak.tokenParsed?.sub }) }), _jsx("div", { className: "pt-2 border-t border-gray-100", children: _jsx("button", { onClick: () => keycloak.accountManagement(), className: "text-sm text-blue-600 hover:underline", children: "Manage account in Keycloak \u2192" }) })] }) }));
}
// =============================================================================
// Portfolios tab
// =============================================================================
function PortfoliosTab() {
    const { portfolios } = usePortfolios();
    const qc = useQueryClient();
    const [creating, setCreating] = useState(false);
    const [name, setName] = useState('');
    const [editing, setEditing] = useState(null);
    const [strategy, setStrategy] = useState('');
    const STRATEGIES = [
        { value: 'FIFO', label: 'FIFO (First In, First Out)' },
        { value: 'LIFO', label: 'LIFO (Last In, First Out)' },
        { value: 'MAXIMISE_GAIN', label: 'Maximize Gain' },
        { value: 'MINIMISE_GAIN', label: 'Minimize Gain' },
        { value: 'MINIMISE_CGT', label: 'Minimize CGT (Tax Optimal)' },
    ];
    const createMutation = useMutation({
        mutationFn: () => api.post('/v1/portfolios', {
            name, baseCurrency: 'AUD', parcelMatchingStrategy: 'FIFO'
        }),
        onSuccess: () => {
            qc.invalidateQueries({ queryKey: ['portfolios'] });
            setName('');
            setCreating(false);
        },
    });
    const updateMutation = useMutation({
        mutationFn: ({ id, strategy }) => api.put(`/v1/portfolios/${id}`, { parcelMatchingStrategy: strategy }),
        onSuccess: () => {
            qc.invalidateQueries({ queryKey: ['portfolios'] });
            setEditing(null);
        },
    });
    return (_jsx(Card, { title: "Portfolios", action: _jsx("button", { onClick: () => setCreating(true), className: "text-sm text-blue-600 hover:underline", children: "+ New" }), children: _jsxs("div", { className: "space-y-2", children: [portfolios.map(p => (_jsxs("div", { className: "flex items-center justify-between py-3 px-4 bg-gray-50 rounded-xl", children: [_jsxs("div", { children: [_jsxs("div", { className: "font-medium text-gray-900 text-sm flex items-center gap-2", children: [p.name, p.isDefault && (_jsx("span", { className: "text-xs text-blue-600 bg-blue-50 px-1.5 py-0.5 rounded", children: "Default" }))] }), _jsx("div", { className: "text-xs text-gray-400 flex items-center gap-2", children: editing === p.id ? (_jsx("select", { value: strategy || p.parcelMatchingStrategy, onChange: e => {
                                            setStrategy(e.target.value);
                                            updateMutation.mutate({ id: p.id, strategy: e.target.value });
                                        }, disabled: updateMutation.isPending, className: "mt-1 text-xs border border-gray-300 rounded px-2 py-1 bg-white", children: STRATEGIES.map(s => (_jsx("option", { value: s.value, children: s.label }, s.value))) })) : (_jsxs("button", { onClick: () => { setEditing(p.id); setStrategy(p.parcelMatchingStrategy); }, className: "hover:text-blue-600", children: [p.baseCurrency, " \u00B7 ", p.parcelMatchingStrategy] })) })] }), _jsx("div", { className: "text-sm font-semibold text-gray-700", children: new Intl.NumberFormat('en-AU', { style: 'currency', currency: 'AUD' })
                                .format(p.totalValue ?? 0) })] }, p.id))), portfolios.length === 0 && (_jsx("p", { className: "text-sm text-gray-400 py-4 text-center", children: "No portfolios yet." })), creating && (_jsxs("div", { className: "flex items-center gap-2 pt-2", children: [_jsx("input", { autoFocus: true, type: "text", placeholder: "Portfolio name", value: name, onChange: e => setName(e.target.value), onKeyDown: e => e.key === 'Enter' && name.trim() && createMutation.mutate(), className: "flex-1 px-3 py-2 text-sm border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500" }), _jsx("button", { onClick: () => createMutation.mutate(), disabled: !name.trim() || createMutation.isPending, className: "p-2 bg-blue-600 text-white rounded-lg disabled:opacity-50 hover:bg-blue-700", children: createMutation.isPending ? _jsx(Loader2, { size: 14, className: "animate-spin" }) : _jsx(Check, { size: 14 }) }), _jsx("button", { onClick: () => setCreating(false), className: "p-2 border border-gray-200 rounded-lg hover:bg-gray-50", children: _jsx(X, { size: 14, className: "text-gray-400" }) })] }))] }) }));
}
// =============================================================================
// Brokers tab
// =============================================================================
function BrokersTab() {
    const qc = useQueryClient();
    const { data: connections = [], isLoading } = useBrokerConnections();
    const syncMutation = useMutation({
        mutationFn: (connId) => api.post(`/v1/broker/connections/${connId}/sync`),
        onSuccess: () => qc.invalidateQueries({ queryKey: ['broker-connections'] }),
    });
    const connectedIds = new Set(connections.map(c => c.broker));
    return (_jsx(Card, { title: "Broker Accounts", subtitle: "Connect your brokerage accounts to automatically import trades.", children: isLoading ? _jsx(Spinner, {}) : (_jsx("div", { className: "space-y-3", children: BROKERS.map(broker => {
                const conn = connections.find(c => c.broker === broker.id);
                const connected = connectedIds.has(broker.id);
                return (_jsxs("div", { className: clsx('flex items-center justify-between p-4 rounded-xl border transition-colors', connected ? 'bg-emerald-50 border-emerald-200'
                        : 'bg-gray-50 border-gray-200', broker.disabled && 'opacity-50 pointer-events-none'), children: [_jsxs("div", { children: [_jsx("div", { className: "font-medium text-gray-900 text-sm", children: broker.label }), _jsxs("div", { className: "text-xs text-gray-400 mt-0.5", children: [connected && conn?.lastSyncAt
                                            ? `Last synced ${new Date(conn.lastSyncAt).toLocaleDateString('en-AU')}`
                                            : broker.note, connected && conn?.lastSyncStatus && (_jsxs("span", { className: clsx('ml-2', conn.lastSyncStatus === 'SUCCESS' ? 'text-emerald-600' : 'text-amber-600'), children: ["\u00B7 ", conn.lastSyncStatus.toLowerCase()] }))] })] }), _jsx("div", { className: "flex items-center gap-2", children: connected ? (_jsxs(_Fragment, { children: [_jsx("button", { onClick: () => syncMutation.mutate(conn.id), disabled: syncMutation.isPending, title: "Sync now", className: "p-1.5 rounded-lg text-gray-400 hover:text-gray-600 hover:bg-white transition-colors", children: _jsx(RefreshCw, { size: 13, className: syncMutation.isPending ? 'animate-spin' : '' }) }), _jsxs("span", { className: "inline-flex items-center gap-1 text-xs text-emerald-700 bg-emerald-100 px-2.5 py-1 rounded-full", children: [_jsx(Check, { size: 11 }), " Connected"] })] })) : (_jsx("button", { disabled: broker.disabled, className: "text-xs text-blue-600 hover:underline disabled:text-gray-400", children: broker.disabled ? 'Coming soon' : 'Connect →' })) })] }, broker.id));
            }) })) }));
}
// =============================================================================
// PDF Import tab
// =============================================================================
function ImportTab() {
    const { activePortfolio } = usePortfolios();
    const fileRef = useRef(null);
    const [dragOver, setDragOver] = useState(false);
    const [docId, setDocId] = useState(null);
    const { data: status } = useQuery({
        queryKey: ['doc-status', docId],
        queryFn: async () => {
            const r = await api.get(`/v1/documents/${docId}`);
            return r.data;
        },
        enabled: !!docId,
        refetchInterval: (query) => query.state.data?.status === 'PARSING' || query.state.data?.status === 'PENDING' ? 2000 : false,
    });
    const uploadMutation = useMutation({
        mutationFn: async (file) => {
            if (!activePortfolio)
                throw new Error('No portfolio selected');
            const fd = new FormData();
            fd.append('file', file);
            fd.append('portfolioId', activePortfolio.id);
            const r = await api.post('/v1/documents/upload', fd, { headers: { 'Content-Type': 'multipart/form-data' } });
            return r.data;
        },
        onSuccess: (data) => setDocId(data.documentId),
    });
    const handleFile = (file) => {
        if (!file.name.toLowerCase().endsWith('.pdf'))
            return;
        setDocId(null);
        uploadMutation.mutate(file);
    };
    const statusIcon = () => {
        if (!status)
            return null;
        if (status.status === 'PARSING' || status.status === 'PENDING')
            return _jsx(Loader2, { size: 16, className: "animate-spin text-blue-500" });
        if (status.status === 'PARSED')
            return _jsx(Check, { size: 16, className: "text-emerald-600" });
        return _jsx(AlertCircle, { size: 16, className: "text-red-500" });
    };
    return (_jsxs(Card, { title: "Import Trade Confirmation (PDF)", subtitle: "Upload a broker contract note \u2014 we'll extract the trade details automatically.", children: [_jsxs("div", { onDragOver: e => { e.preventDefault(); setDragOver(true); }, onDragLeave: () => setDragOver(false), onDrop: e => { e.preventDefault(); setDragOver(false); const f = e.dataTransfer.files[0]; if (f)
                    handleFile(f); }, onClick: () => fileRef.current?.click(), className: clsx('border-2 border-dashed rounded-xl p-8 text-center cursor-pointer transition-colors', dragOver ? 'border-blue-400 bg-blue-50'
                    : 'border-gray-300 hover:border-gray-400 hover:bg-gray-50/60'), children: [_jsx(Upload, { size: 24, className: "mx-auto text-gray-400 mb-2" }), _jsx("p", { className: "text-sm font-medium text-gray-600", children: "Drop a PDF here or click to browse" }), _jsx("p", { className: "text-xs text-gray-400 mt-1", children: "CommSec and SelfWealth contract notes supported" }), _jsx("input", { ref: fileRef, type: "file", accept: ".pdf", className: "hidden", onChange: e => { const f = e.target.files?.[0]; if (f)
                            handleFile(f); } })] }), uploadMutation.isPending && (_jsxs("div", { className: "flex items-center gap-2 mt-3 text-sm text-gray-500", children: [_jsx(Loader2, { size: 14, className: "animate-spin" }), " Uploading\u2026"] })), status && (_jsx("div", { className: clsx('mt-3 p-4 rounded-xl border text-sm', status.status === 'PARSED'
                    ? 'bg-emerald-50 border-emerald-200 text-emerald-800'
                    : status.status === 'FAILED'
                        ? 'bg-red-50 border-red-200 text-red-800'
                        : 'bg-blue-50 border-blue-200 text-blue-800'), children: _jsxs("div", { className: "flex items-start gap-2", children: [statusIcon(), _jsxs("div", { className: "flex-1", children: [_jsxs("p", { className: "font-medium", children: [status.status === 'PARSED' && 'Trade extracted successfully', status.status === 'PARSING' && 'Parsing in progress…', status.status === 'PENDING' && 'Queued for parsing…', status.status === 'FAILED' && 'Parse failed'] }), status.brokerDetected && (_jsxs("p", { className: "text-xs mt-0.5", children: ["Detected broker: ", _jsx("strong", { children: status.brokerDetected }), status.confidence != null && (_jsxs("span", { className: "ml-2", children: ["(", Math.round(status.confidence * 100), "% confidence)"] }))] })), status.status === 'FAILED' && status.errorMessage && (_jsx("p", { className: "text-xs mt-1", children: status.errorMessage })), status.status === 'PARSED' && (_jsx("p", { className: "text-xs mt-1", children: "Review and confirm the extracted trade in the Trades page before it's saved." }))] })] }) })), _jsx("div", { className: "mt-4 text-xs text-gray-400 space-y-1", children: _jsx("p", { children: "The extracted trade data is shown for your review before being saved to your portfolio." }) })] }));
}
// =============================================================================
// Notifications tab
// =============================================================================
function NotificationsTab() {
    const { data: prefs = [], isLoading, toggle: toggleMutation } = useNotificationPrefs();
    const prefMap = Object.fromEntries(prefs.filter((p) => p.channel === 'EMAIL').map(p => [p.eventType, p.enabled]));
    if (isLoading)
        return _jsx(Card, { title: "Notification Preferences", children: _jsx(Spinner, {}) });
    return (_jsx(Card, { title: "Notification Preferences", subtitle: "All notifications are sent to your account email address.", children: _jsx("div", { className: "space-y-3", children: NOTIFICATION_TYPES.map(nt => {
                const enabled = prefMap[nt.key] ?? true;
                return (_jsxs("div", { className: "flex items-center justify-between py-3 px-4 bg-gray-50 rounded-xl", children: [_jsxs("div", { children: [_jsx("div", { className: "text-sm font-medium text-gray-900", children: nt.label }), _jsx("div", { className: "text-xs text-gray-400 mt-0.5", children: nt.desc })] }), _jsx(Toggle, { checked: enabled, onChange: v => toggleMutation.mutate({ eventType: nt.key, enabled: v }), disabled: toggleMutation.isPending })] }, nt.key));
            }) }) }));
}
// =============================================================================
// Shared components
// =============================================================================
function Card({ title, subtitle, action, children }) {
    return (_jsxs("div", { className: "bg-white rounded-xl border border-gray-200 p-6", children: [_jsxs("div", { className: "flex items-start justify-between mb-5", children: [_jsxs("div", { children: [_jsx("h2", { className: "text-base font-semibold text-gray-900", children: title }), subtitle && _jsx("p", { className: "text-sm text-gray-500 mt-0.5", children: subtitle })] }), action] }), children] }));
}
function Row({ label, children }) {
    return (_jsxs("div", { className: "flex items-center justify-between py-2 border-b border-gray-50 last:border-0", children: [_jsx("span", { className: "text-sm text-gray-500", children: label }), _jsx("span", { className: "text-sm text-gray-900", children: children })] }));
}
function Toggle({ checked, onChange, disabled = false }) {
    return (_jsx("button", { onClick: () => !disabled && onChange(!checked), disabled: disabled, className: clsx('relative w-10 h-5 rounded-full transition-colors flex-shrink-0', 'focus:outline-none focus:ring-2 focus:ring-offset-1 focus:ring-blue-500', checked ? 'bg-blue-600' : 'bg-gray-300', disabled && 'opacity-50 cursor-not-allowed'), children: _jsx("span", { className: clsx('absolute top-0.5 w-4 h-4 rounded-full bg-white shadow transition-transform', checked ? 'left-5' : 'left-0.5') }) }));
}
function Spinner() {
    return (_jsxs("div", { className: "flex items-center justify-center py-8 text-gray-400", children: [_jsx(Loader2, { size: 18, className: "animate-spin mr-2" }), " Loading\u2026"] }));
}
