import { useState, useRef } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useKeycloak } from '@react-keycloak/web'
import {
  User, Briefcase, Link2, Upload, Bell,
  Check, Loader2, X, AlertCircle, RefreshCw
} from 'lucide-react'
import clsx from 'clsx'
import api from '../lib/apiClient'
import {
  usePortfolios,
  useBrokerConnections,
  useNotificationPrefs,
  type BrokerConnection,
  type NotificationPref,
} from '../hooks/usePortfolios'

type Tab = 'profile' | 'portfolios' | 'brokers' | 'import' | 'notifications'

const BROKERS = [
  { id: 'COMMSEC',    label: 'CommSec',             note: 'CSV / PDF import' },
  { id: 'SELFWEALTH', label: 'SelfWealth',           note: 'API connected' },
  { id: 'IBKR',       label: 'Interactive Brokers',  note: 'API connected' },
  { id: 'STAKE',      label: 'Stake',                note: 'Coming soon', disabled: true },
  { id: 'PEARLER',    label: 'Pearler',              note: 'Coming soon', disabled: true },
]

const NOTIFICATION_TYPES = [
  { key: 'TRADE_CONFIRMED', label: 'Trade confirmations',
    desc: 'Email when any trade is recorded' },
  { key: 'DAILY_SUMMARY',   label: 'Daily summary',
    desc: 'End-of-day portfolio value email' },
  { key: 'CGT_REMINDER',    label: 'CGT discount reminders',
    desc: '7-day notice before a parcel reaches 12-month CGT eligibility' },
  { key: 'PRICE_ALERT',     label: 'Price alerts',
    desc: 'When a holding moves more than 5% in a day' },
]

// ── Types ─────────────────────────────────────────────────────────────────────

interface UserProfile {
  id: string; email: string; displayName: string
  baseCurrency: string; taxCountry: string
}

interface ParseStatus {
  documentId: string; parseStatus: string
  brokerDetected: string | null; confidence: number | null
  parsedTrades: string | null; errorMessage: string | null
}

// =============================================================================
// Page root
// =============================================================================

export default function SettingsPage() {
  const [tab, setTab] = useState<Tab>('profile')

  const TABS: { id: Tab; label: string; icon: React.ReactNode }[] = [
    { id: 'profile',       label: 'Profile',         icon: <User size={15} /> },
    { id: 'portfolios',    label: 'Portfolios',       icon: <Briefcase size={15} /> },
    { id: 'brokers',       label: 'Broker Accounts',  icon: <Link2 size={15} /> },
    { id: 'import',        label: 'Import PDF',       icon: <Upload size={15} /> },
    { id: 'notifications', label: 'Notifications',    icon: <Bell size={15} /> },
  ]

  return (
    <div className="p-8 max-w-4xl">
      <h1 className="text-2xl font-semibold text-gray-900 mb-6">Settings</h1>
      <div className="flex gap-6">
        <nav className="w-44 flex-shrink-0 space-y-0.5">
          {TABS.map(t => (
            <button key={t.id} onClick={() => setTab(t.id)}
              className={clsx(
                'w-full flex items-center gap-2.5 px-3 py-2 rounded-lg text-sm transition-colors',
                tab === t.id ? 'bg-blue-50 text-blue-700 font-medium'
                             : 'text-gray-600 hover:bg-gray-50'
              )}>
              {t.icon}{t.label}
            </button>
          ))}
        </nav>
        <div className="flex-1 min-w-0">
          {tab === 'profile'       && <ProfileTab />}
          {tab === 'portfolios'    && <PortfoliosTab />}
          {tab === 'brokers'       && <BrokersTab />}
          {tab === 'import'        && <ImportTab />}
          {tab === 'notifications' && <NotificationsTab />}
        </div>
      </div>
    </div>
  )
}

// =============================================================================
// Profile tab
// =============================================================================

function ProfileTab() {
  const { keycloak } = useKeycloak()
  const qc = useQueryClient()
  const [editing, setEditing] = useState(false)
  const [displayName, setDisplayName] = useState('')

  const { data: profile, isLoading } = useQuery({
    queryKey: ['profile'],
    queryFn: async () => {
      const r = await api.get<UserProfile>('/v1/settings/profile')
      return r.data
    },
  })

  const saveMutation = useMutation({
    mutationFn: () => api.put('/v1/settings/profile', { displayName }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['profile'] }); setEditing(false); },
  })

  if (isLoading) return <Card title="Profile"><Spinner /></Card>

  return (
    <Card title="Profile">
      <div className="space-y-3">
        <Row label="Email">{profile?.email ?? keycloak.tokenParsed?.email ?? '—'}</Row>
        <Row label="Display name">
          {editing ? (
            <div className="flex items-center gap-2">
              <input autoFocus value={displayName} onChange={e => setDisplayName(e.target.value)}
                className="flex-1 px-2 py-1 text-sm border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500" />
              <button onClick={() => saveMutation.mutate()} disabled={saveMutation.isPending}
                className="p-1 text-emerald-600 hover:text-emerald-700">
                {saveMutation.isPending ? <Loader2 size={14} className="animate-spin" /> : <Check size={14} />}
              </button>
              <button onClick={() => setEditing(false)} className="p-1 text-gray-400 hover:text-gray-600">
                <X size={14} />
              </button>
            </div>
          ) : (
            <span className="flex items-center gap-2">
              {profile?.displayName ?? '—'}
              <button onClick={() => { setDisplayName(profile?.displayName ?? ''); setEditing(true) }}
                className="text-xs text-blue-500 hover:underline">Edit</button>
            </span>
          )}
        </Row>
        <Row label="Base currency">{profile?.baseCurrency ?? 'AUD'}</Row>
        <Row label="Tax country">{profile?.taxCountry ?? 'AU'}</Row>
        <Row label="Keycloak ID">
          <span className="font-mono text-xs text-gray-400 truncate max-w-xs block">
            {keycloak.tokenParsed?.sub}
          </span>
        </Row>
        <div className="pt-2 border-t border-gray-100">
          <button onClick={() => keycloak.accountManagement()}
            className="text-sm text-blue-600 hover:underline">
            Manage account in Keycloak →
          </button>
        </div>
      </div>
    </Card>
  )
}

// =============================================================================
// Portfolios tab
// =============================================================================

function PortfoliosTab() {
  const { portfolios } = usePortfolios()
  const qc = useQueryClient()
  const [creating, setCreating] = useState(false)
  const [name, setName] = useState('')

  const createMutation = useMutation({
    mutationFn: () => api.post('/v1/portfolios', {
      name, baseCurrency: 'AUD', parcelMatchingStrategy: 'FIFO'
    }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['portfolios'] })
      setName(''); setCreating(false)
    },
  })

  return (
    <Card title="Portfolios"
      action={<button onClick={() => setCreating(true)}
        className="text-sm text-blue-600 hover:underline">+ New</button>}>
      <div className="space-y-2">
        {portfolios.map(p => (
          <div key={p.id} className="flex items-center justify-between py-3 px-4 bg-gray-50 rounded-xl">
            <div>
              <div className="font-medium text-gray-900 text-sm flex items-center gap-2">
                {p.name}
                {p.isDefault && (
                  <span className="text-xs text-blue-600 bg-blue-50 px-1.5 py-0.5 rounded">Default</span>
                )}
              </div>
              <div className="text-xs text-gray-400">
                {p.baseCurrency} · {p.parcelMatchingStrategy}
              </div>
            </div>
            <div className="text-sm font-semibold text-gray-700">
              {new Intl.NumberFormat('en-AU', { style: 'currency', currency: 'AUD' })
                .format(p.totalValue ?? 0)}
            </div>
          </div>
        ))}

        {portfolios.length === 0 && (
          <p className="text-sm text-gray-400 py-4 text-center">No portfolios yet.</p>
        )}

        {creating && (
          <div className="flex items-center gap-2 pt-2">
            <input autoFocus type="text" placeholder="Portfolio name" value={name}
              onChange={e => setName(e.target.value)}
              onKeyDown={e => e.key === 'Enter' && name.trim() && createMutation.mutate()}
              className="flex-1 px-3 py-2 text-sm border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500" />
            <button onClick={() => createMutation.mutate()}
              disabled={!name.trim() || createMutation.isPending}
              className="p-2 bg-blue-600 text-white rounded-lg disabled:opacity-50 hover:bg-blue-700">
              {createMutation.isPending ? <Loader2 size={14} className="animate-spin" /> : <Check size={14} />}
            </button>
            <button onClick={() => setCreating(false)}
              className="p-2 border border-gray-200 rounded-lg hover:bg-gray-50">
              <X size={14} className="text-gray-400" />
            </button>
          </div>
        )}
      </div>
    </Card>
  )
}

// =============================================================================
// Brokers tab
// =============================================================================

function BrokersTab() {
  const qc = useQueryClient()
  const { data: connections = [], isLoading } = useBrokerConnections()

  const syncMutation = useMutation({
    mutationFn: (connId: string) =>
      api.post(`/v1/broker/connections/${connId}/sync`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['broker-connections'] }),
  })

  const connectedIds = new Set(connections.map(c => c.broker))

  return (
    <Card title="Broker Accounts"
      subtitle="Connect your brokerage accounts to automatically import trades.">
      {isLoading ? <Spinner /> : (
        <div className="space-y-3">
          {BROKERS.map(broker => {
            const conn = connections.find(c => c.broker === broker.id)
            const connected = connectedIds.has(broker.id)
            return (
              <div key={broker.id} className={clsx(
                'flex items-center justify-between p-4 rounded-xl border transition-colors',
                connected ? 'bg-emerald-50 border-emerald-200'
                          : 'bg-gray-50 border-gray-200',
                broker.disabled && 'opacity-50 pointer-events-none'
              )}>
                <div>
                  <div className="font-medium text-gray-900 text-sm">{broker.label}</div>
                  <div className="text-xs text-gray-400 mt-0.5">
                    {connected && conn?.lastSyncAt
                      ? `Last synced ${new Date(conn.lastSyncAt).toLocaleDateString('en-AU')}`
                      : broker.note}
                    {connected && conn?.lastSyncStatus && (
                      <span className={clsx('ml-2',
                        conn.lastSyncStatus === 'SUCCESS' ? 'text-emerald-600' : 'text-amber-600')}>
                        · {conn.lastSyncStatus.toLowerCase()}
                      </span>
                    )}
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  {connected ? (
                    <>
                      <button onClick={() => syncMutation.mutate(conn!.id)}
                        disabled={syncMutation.isPending}
                        title="Sync now"
                        className="p-1.5 rounded-lg text-gray-400 hover:text-gray-600 hover:bg-white transition-colors">
                        <RefreshCw size={13} className={syncMutation.isPending ? 'animate-spin' : ''} />
                      </button>
                      <span className="inline-flex items-center gap-1 text-xs text-emerald-700 bg-emerald-100 px-2.5 py-1 rounded-full">
                        <Check size={11} /> Connected
                      </span>
                    </>
                  ) : (
                    <button disabled={broker.disabled}
                      className="text-xs text-blue-600 hover:underline disabled:text-gray-400">
                      {broker.disabled ? 'Coming soon' : 'Connect →'}
                    </button>
                  )}
                </div>
              </div>
            )
          })}
        </div>
      )}
    </Card>
  )
}

// =============================================================================
// PDF Import tab
// =============================================================================

function ImportTab() {
  const { activePortfolio } = usePortfolios()
  const fileRef = useRef<HTMLInputElement>(null)
  const [dragOver, setDragOver] = useState(false)
  const [docId, setDocId] = useState<string | null>(null)

  const { data: parseStatus } = useQuery({
    queryKey: ['doc-status', docId],
    queryFn: async () => {
      const r = await api.get<ParseStatus>(`/v1/documents/${docId}`)
      return r.data
    },
    enabled: !!docId,
    refetchInterval: (data) =>
      data?.parseStatus === 'PARSING' || data?.parseStatus === 'PENDING' ? 2000 : false,
  })

  const uploadMutation = useMutation({
    mutationFn: async (file: File) => {
      if (!activePortfolio) throw new Error('No portfolio selected')
      const fd = new FormData()
      fd.append('file', file)
      fd.append('portfolioId', activePortfolio.id)
      const r = await api.post<{ documentId: string }>('/v1/documents/upload', fd,
        { headers: { 'Content-Type': 'multipart/form-data' } })
      return r.data
    },
    onSuccess: (data) => setDocId(data.documentId),
  })

  const handleFile = (file: File) => {
    if (!file.name.toLowerCase().endsWith('.pdf')) return
    setDocId(null)
    uploadMutation.mutate(file)
  }

  const statusIcon = () => {
    if (!parseStatus) return null
    if (parseStatus.parseStatus === 'PARSING' || parseStatus.parseStatus === 'PENDING')
      return <Loader2 size={16} className="animate-spin text-blue-500" />
    if (parseStatus.parseStatus === 'PARSED')
      return <Check size={16} className="text-emerald-600" />
    return <AlertCircle size={16} className="text-red-500" />
  }

  return (
    <Card title="Import Trade Confirmation (PDF)"
      subtitle="Upload a broker contract note — we'll extract the trade details automatically.">
      {/* Drop zone */}
      <div
        onDragOver={e => { e.preventDefault(); setDragOver(true) }}
        onDragLeave={() => setDragOver(false)}
        onDrop={e => { e.preventDefault(); setDragOver(false); const f = e.dataTransfer.files[0]; if (f) handleFile(f) }}
        onClick={() => fileRef.current?.click()}
        className={clsx(
          'border-2 border-dashed rounded-xl p-8 text-center cursor-pointer transition-colors',
          dragOver ? 'border-blue-400 bg-blue-50'
                   : 'border-gray-300 hover:border-gray-400 hover:bg-gray-50/60'
        )}
      >
        <Upload size={24} className="mx-auto text-gray-400 mb-2" />
        <p className="text-sm font-medium text-gray-600">Drop a PDF here or click to browse</p>
        <p className="text-xs text-gray-400 mt-1">CommSec and SelfWealth contract notes supported</p>
        <input ref={fileRef} type="file" accept=".pdf" className="hidden"
          onChange={e => { const f = e.target.files?.[0]; if (f) handleFile(f) }} />
      </div>

      {/* Upload status */}
      {uploadMutation.isPending && (
        <div className="flex items-center gap-2 mt-3 text-sm text-gray-500">
          <Loader2 size={14} className="animate-spin" /> Uploading…
        </div>
      )}

      {/* Parse status */}
      {parseStatus && (
        <div className={clsx('mt-3 p-4 rounded-xl border text-sm',
          parseStatus.parseStatus === 'PARSED'
            ? 'bg-emerald-50 border-emerald-200 text-emerald-800'
            : parseStatus.parseStatus === 'FAILED'
            ? 'bg-red-50 border-red-200 text-red-800'
            : 'bg-blue-50 border-blue-200 text-blue-800'
        )}>
          <div className="flex items-start gap-2">
            {statusIcon()}
            <div className="flex-1">
              <p className="font-medium">
                {parseStatus.parseStatus === 'PARSED' && 'Trade extracted successfully'}
                {parseStatus.parseStatus === 'PARSING' && 'Parsing in progress…'}
                {parseStatus.parseStatus === 'PENDING' && 'Queued for parsing…'}
                {parseStatus.parseStatus === 'FAILED' && 'Parse failed'}
              </p>
              {parseStatus.brokerDetected && (
                <p className="text-xs mt-0.5">
                  Detected broker: <strong>{parseStatus.brokerDetected}</strong>
                  {parseStatus.confidence != null && (
                    <span className="ml-2">
                      ({Math.round(parseStatus.confidence * 100)}% confidence)
                    </span>
                  )}
                </p>
              )}
              {parseStatus.parseStatus === 'FAILED' && parseStatus.errorMessage && (
                <p className="text-xs mt-1">{parseStatus.errorMessage}</p>
              )}
              {parseStatus.parseStatus === 'PARSED' && (
                <p className="text-xs mt-1">
                  Review and confirm the extracted trade in the Trades page before it's saved.
                </p>
              )}
            </div>
          </div>
        </div>
      )}

      <div className="mt-4 text-xs text-gray-400 space-y-1">
        <p>The extracted trade data is shown for your review before being saved to your portfolio.</p>
      </div>
    </Card>
  )
}

// =============================================================================
// Notifications tab
// =============================================================================

function NotificationsTab() {
  const { data: prefs = [], isLoading, toggle: toggleMutation } = useNotificationPrefs()

  const prefMap = Object.fromEntries(
    prefs.filter((p: NotificationPref) => p.channel === 'EMAIL').map(p => [p.eventType, p.enabled])
  )

  if (isLoading) return <Card title="Notification Preferences"><Spinner /></Card>

  return (
    <Card title="Notification Preferences"
      subtitle="All notifications are sent to your account email address.">
      <div className="space-y-3">
        {NOTIFICATION_TYPES.map(nt => {
          const enabled = prefMap[nt.key] ?? true
          return (
            <div key={nt.key}
              className="flex items-center justify-between py-3 px-4 bg-gray-50 rounded-xl">
              <div>
                <div className="text-sm font-medium text-gray-900">{nt.label}</div>
                <div className="text-xs text-gray-400 mt-0.5">{nt.desc}</div>
              </div>
              <Toggle
                checked={enabled}
                onChange={v => toggleMutation.mutate({ eventType: nt.key, enabled: v })}
                disabled={toggleMutation.isPending}
              />
            </div>
          )
        })}
      </div>
    </Card>
  )
}

// =============================================================================
// Shared components
// =============================================================================

function Card({ title, subtitle, action, children }: {
  title: string; subtitle?: string; action?: React.ReactNode; children: React.ReactNode
}) {
  return (
    <div className="bg-white rounded-xl border border-gray-200 p-6">
      <div className="flex items-start justify-between mb-5">
        <div>
          <h2 className="text-base font-semibold text-gray-900">{title}</h2>
          {subtitle && <p className="text-sm text-gray-500 mt-0.5">{subtitle}</p>}
        </div>
        {action}
      </div>
      {children}
    </div>
  )
}

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex items-center justify-between py-2 border-b border-gray-50 last:border-0">
      <span className="text-sm text-gray-500">{label}</span>
      <span className="text-sm text-gray-900">{children}</span>
    </div>
  )
}

function Toggle({ checked, onChange, disabled = false }: {
  checked: boolean; onChange: (v: boolean) => void; disabled?: boolean
}) {
  return (
    <button onClick={() => !disabled && onChange(!checked)} disabled={disabled}
      className={clsx(
        'relative w-10 h-5 rounded-full transition-colors flex-shrink-0',
        'focus:outline-none focus:ring-2 focus:ring-offset-1 focus:ring-blue-500',
        checked ? 'bg-blue-600' : 'bg-gray-300',
        disabled && 'opacity-50 cursor-not-allowed'
      )}>
      <span className={clsx(
        'absolute top-0.5 w-4 h-4 rounded-full bg-white shadow transition-transform',
        checked ? 'left-5' : 'left-0.5'
      )} />
    </button>
  )
}

function Spinner() {
  return (
    <div className="flex items-center justify-center py-8 text-gray-400">
      <Loader2 size={18} className="animate-spin mr-2" /> Loading…
    </div>
  )
}
