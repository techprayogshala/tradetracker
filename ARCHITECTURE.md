# TradeTracker — Architecture & Component Reference

> Open-source portfolio tracker built as a Sharesight alternative.
> Java 21 / Spring Boot 3 modular monolith backend · React + Vite frontend.

---

## Table of Contents

1. [System Overview](#system-overview)
2. [Backend Modules](#backend-modules)
3. [Frontend Components](#frontend-components)
4. [Infrastructure Services](#infrastructure-services)
5. [Database Schema](#database-schema)
6. [API Endpoints](#api-endpoints)
7. [Scheduler Jobs](#scheduler-jobs)
8. [Test Coverage](#test-coverage)
9. [Dependency Graph](#dependency-graph)

---

## System Overview

TradeTracker is a **Spring Boot modular monolith** — all domain modules compile into a single deployable JAR. Module boundaries are enforced at the Maven level; modules communicate via Spring `ApplicationEvents`, not direct bean cross-injection.

```
┌──────────────────────────────────────────────────────────────┐
│  React + Vite (port 3000)                                    │
│  Keycloak PKCE auth  ·  TanStack Query  ·  Tailwind CSS     │
└──────────────┬───────────────────────────────────────────────┘
               │ HTTP /api/v1/...
┌──────────────▼───────────────────────────────────────────────┐
│  Spring Boot 3.3  (port 8080)  context-path=/api            │
│  Spring Security  ·  JWT / OIDC resource server             │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  REST Controllers (tradetracker-api)                  │   │
│  │  PortfolioController  TradeController  TaxController  │   │
│  │  BrokerController  PerformanceController  Settings    │   │
│  └───────────────────────┬──────────────────────────────┘   │
│  ┌────────────┐ ┌────────▼────────┐ ┌──────────────────┐   │
│  │ calculation│ │    portfolio     │ │       tax        │   │
│  │ TWR · MWR  │ │ Entities · Repos │ │ CGT · Parcels   │   │
│  │ Performance│ │ PortfolioService │ │ TaxReportService │   │
│  └────────────┘ └────────┬────────┘ └──────────────────┘   │
│  ┌────────────┐ ┌────────▼────────┐ ┌──────────────────┐   │
│  │  broker    │ │   marketdata    │ │  notification    │   │
│  │ Adapters   │ │  PriceSync      │ │  Outbox · Email  │   │
│  │ BrokerSync │ │  AlphaVantage   │ │  Templates       │   │
│  └────────────┘ └─────────────────┘ └──────────────────┘   │
│  ┌────────────┐ ┌─────────────────┐ ┌──────────────────┐   │
│  │ scheduler  │ │    document     │ │     security     │   │
│  │ Quartz jobs│ │ PDF parser      │ │  JWT · Keycloak  │   │
│  └────────────┘ └─────────────────┘ └──────────────────┘   │
└──────────────────────────┬───────────────────────────────────┘
                           │
        ┌──────────────────┼───────────────────┐
        ▼                  ▼                   ▼
  PostgreSQL 16      Redis 7              MinIO
  + TimescaleDB   (cache / sessions)   (PDF/report storage)
        
  Redpanda (Kafka)   RabbitMQ           Keycloak 24
  (trade events)     (notifications)    (OIDC / auth)
```

---

## Backend Modules

### `tradetracker-common`

Shared domain primitives used by every other module. No Spring beans — pure Java.

| File | Purpose |
|---|---|
| `domain/TradeType.java` | Enum: `BUY`, `SELL`, `DIVIDEND`, `RETURN_OF_CAPITAL`, `SPLIT`, `TRANSFER_IN`, `TRANSFER_OUT` |
| `domain/Money.java` | Value object wrapping `BigDecimal` + `Currency` for monetary amounts |
| `domain/CorporateAction.java` | Sealed interface hierarchy: `StockSplit`, `Merger`, `SpinOff`, `RightsIssue`, `ReturnOfCapital` |
| `exception/TradeTrackerException.java` | Abstract base class for all domain exceptions |

---

### `tradetracker-security`

Spring Security configuration. Validates JWTs issued by Keycloak and maps `realm_access.roles` to Spring `GrantedAuthority` objects.

| File | Purpose |
|---|---|
| `SecurityConfig.java` | Configures Spring Security filter chain: stateless JWT resource server, public paths (`/actuator/health`, Swagger UI), `@EnableMethodSecurity` for `@PreAuthorize` on individual methods |
| — | `keycloakJwtConverter()` — extracts `ROLE_USER` / `ROLE_ADMIN` from the JWT `realm_access.roles` claim |

---

### `tradetracker-portfolio`

Core domain — the largest module. Owns all JPA entities, repositories, and the central `PortfolioService`.

#### Entities (`entity/`)

| File | Purpose |
|---|---|
| `BaseEntity.java` | Abstract superclass: UUID PK, `@Version` optimistic lock, `@CreatedDate` / `@LastModifiedDate` via JPA Auditing |
| `User.java` | App user linked to a Keycloak subject (`keycloak_sub`). Owns a collection of `Portfolio`s |
| `Security.java` | Listed security (stock/ETF). Identified by `(ticker, exchange)` unique pair. Holds ISIN, currency, asset class |
| `Portfolio.java` | Named portfolio with a base currency and parcel matching strategy (`FIFO` / `LIFO` / `MINIMISE_CGT`) |
| `Account.java` | Brokerage account within a portfolio (e.g. "CommSec — Joint"). A portfolio can have multiple accounts |
| `TradeEvent.java` | Immutable record of one trade (BUY/SELL/DIVIDEND etc.). Builder pattern with required-field validation. Computes `totalCost()` and `costPerUnit()` |
| `TaxParcel.java` | Atomic CGT unit. Created by each BUY; `quantityRemaining` decremented by each SELL via `recordDisposal()`. Carries `costPerUnit`, `acquisitionDate`, CGT discount eligibility check |

#### Repositories (`repository/Repositories.java`)

All five repositories are in one file for discoverability:

| Repository | Key methods |
|---|---|
| `UserRepository` | `findByKeycloakSub()` |
| `PortfolioRepository` | `findByIdAndUserKeycloakSub()`, `clearDefaultForUser()` |
| `AccountRepository` | `findByPortfolioId()` |
| `TradeEventRepository` | `findFiltered()` (paginated with optional ticker/type/date filters), `findByIdWithDetails()` |
| `TaxParcelRepository` | `findOpenParcels()` (FIFO-ordered), `findAllOpenByPortfolio()`, `aggregateHoldings()` (single query: sum qty + cost base per security) |

#### Services (`service/`)

| File | Purpose |
|---|---|
| `PortfolioService.java` | **The core service.** Provisions users, manages portfolio CRUD, records trades (dispatching to the correct parcel operation), aggregates holdings with live prices. Uses `@Lazy` self-injection to ensure `@Cacheable` on `getHoldings()` fires via the Spring proxy even when called internally |
| `PriceService.java` | Reads latest `adjusted_close` from TimescaleDB `security_prices` using a `DISTINCT ON` query. Results cached in Redis |

#### Parcel Matching (`matching/`)

| File | Purpose |
|---|---|
| `ParcelMatchingStrategy.java` | Interface with shared `allocate()` default method. Defines `SellEvent`, `OpenParcel`, and `ParcelAllocation` value records |
| `MatchingStrategyImpls.java` | Three `@Component` implementations: `FifoMatchingStrategy`, `LifoMatchingStrategy`, `MinimiseCgtMatchingStrategy` |
| `InsufficientHoldingsException.java` | Thrown when sell quantity exceeds open parcel holdings |

**MinimiseCGT priority order** (ATO-correct):
1. Parcels with a capital **loss** — crystallises losses first
2. Parcels eligible for the **50% CGT discount** (held > 12 months)
3. Short-term gain parcels — smallest gain first

#### Events (`event/PortfolioEvents.java`)

Three Spring `ApplicationEvent` records published by `PortfolioService`:
- `TradeRecordedEvent` — triggers price backfill (marketdata module) and trade confirmation email (notification module)
- `PortfolioCreatedEvent` — triggers initial price sync for new portfolios
- `CorporateActionAppliedEvent` — triggers full portfolio recalculation

---

### `tradetracker-calculation`

Pure computation. No JPA entities — queries the database via `JdbcClient` for performance.

| File | Purpose |
|---|---|
| `ReturnsCalculationService.java` | **TWR** (Time-Weighted Return): chain-links sub-period HPRs across cashflow events. **MWR** (Money-Weighted Return / IRR): Newton-Raphson with bisection fallback. Both accept value-object inputs; no database access |
| `PerformanceService.java` | Reads `portfolio_snapshots` and `trade_events` tables to build TWR time-series and MWR IRR cashflow stream for a requested period. Falls back to a live lateral-join snapshot if no historical data exists yet |

**Sub-period record:**
```
HPR = endValue / (startValue + externalCashFlow) - 1
TWR = Π(1 + HPRᵢ) - 1    (chain-linked)
```

---

### `tradetracker-tax`

Australian CGT engine. Depends on `tradetracker-portfolio` for entity access.

| File | Purpose |
|---|---|
| `AustralianCgtService.java` | Core CGT calculator. `calculateDisposal()` determines gross gain, applies 50% CGT discount (individuals/trusts, held > 12 months, not a loss). `summariseTaxYear()` applies ATO-correct loss ordering: losses offset non-discountable gains first, then discountable gains pre-discount. `calculateFrankingCredit()` grosses up franked dividends |
| `TaxReportService.java` | Queries `parcel_disposals` table (written by `PortfolioService` on every SELL) for real disposal prices. Provides CGT summary, disposal event list, open parcel unrealised CGT, dividend income by holding |
| `CgtPdfReportService.java` | Generates ATO-formatted CGT schedule PDF using the JasperReports programmatic API (no `.jrxml` file). A4 landscape, alternating row stripes, parameterised header, capital gain colouring (negative = red), summary totals band |

---

### `tradetracker-broker`

Broker API integrations. New brokers require one class + `@Component`.

| File | Purpose |
|---|---|
| `BrokerAdapters.java` | `BrokerAdapter` interface + three implementations: `CommSecAdapter` (CSV export parsing), `SelfWealthAdapter` (REST API), `InteractiveBrokersAdapter` (IBKR Client Portal Web API) |
| `BrokerSyncService.java` | Orchestrates sync across all active connections: decrypts token, calls adapter, deduplicates by `externalRef`, calls `PortfolioService.recordTrade()` for new trades, writes raw payload to `broker_raw_imports` audit table |
| `TokenEncryptionService.java` | AES-256-GCM with PBKDF2 key derivation. Each token encrypted with its own random 96-bit IV. Key derived from `INTERNAL_JWT_SECRET` env var — never stored in database |

**CommSec note:** No public API exists. The CSV adapter parses the "Transaction History" export format: `"Bought 100 CBA at $105.50 (Brokerage $9.95)"`.

---

### `tradetracker-marketdata`

Price data ingestion from Alpha Vantage.

| File | Purpose |
|---|---|
| `AlphaVantageClient.java` | REST client for Alpha Vantage API. `fetchRecentPrices()` (last 100 days, compact output), `fetchDailyAdjusted()` (full history), `fetchCurrentPrice()` (global quote), `fetchFxRate()` (currency exchange rate) |
| `PriceSyncService.java` | Bulk-upserts prices into TimescaleDB `security_prices` hypertable via `ON CONFLICT DO UPDATE`. `syncRecentPrices()` iterates the watch list with 12s sleep (respects Alpha Vantage free tier: 5 req/min). `syncFxRates()` fetches daily FX rates for all non-USD currencies in use |
| `TradeEventMarketDataListener.java` | `@Async @EventListener` for `TradeRecordedEvent`. Detects new securities (no price history) and triggers a full historical backfill asynchronously |

---

### `tradetracker-notification`

Transactional email outbox. Guarantees at-least-once delivery even if the mail server is temporarily unavailable.

| File | Purpose |
|---|---|
| `NotificationService.java` | **Outbox writer:** `enqueue()` inserts a row into `notification_outbox` within the caller's transaction. **Outbox poller:** `@Scheduled(fixedDelay=30_000)` picks up `PENDING` rows, calls the mail sender, marks `SENT`/`FAILED`. Up to 3 retry attempts before marking `FAILED` |
| `EmailTemplateService.java` | Renders HTML email templates for: `TRADE_CONFIRMED`, `DAILY_SUMMARY`, `CGT_REMINDER`, `PRICE_ALERT`. Uses string interpolation — no template engine dependency |
| `EmailContent.java` | Record: `(subject, htmlBody)` |
| `NotificationEventListener.java` | `@Async @EventListener` for `TradeRecordedEvent` — enqueues trade confirmation emails. `enqueueCgtDiscountReminders()` — called by the weekly Quartz job, scans for parcels approaching 12-month CGT eligibility |

---

### `tradetracker-scheduler`

Quartz JDBC-backed scheduler. All jobs persist in PostgreSQL — cluster-safe and restart-safe.

| File | Purpose |
|---|---|
| `AutowiringSpringBeanJobFactory.java` | Extends `SpringBeanJobFactory` + `ApplicationContextAware`. **Critical:** without this, `@Autowired` fields in Quartz jobs are null at runtime because Quartz instantiates jobs via `newInstance()` without Spring involvement |
| `QuartzConfig.java` | Registers the autowiring job factory on the `SchedulerFactoryBean` via `SchedulerFactoryBeanCustomizer` |
| `SchedulerConfig.java` | Defines all 6 `JobDetail` + `Trigger` `@Bean` pairs |
| `SchedulerJobs.java` | Four job implementations (see [Scheduler Jobs](#scheduler-jobs)) |
| `BrokerSyncQuartzJob.java` | Quartz wrapper for `BrokerSyncService.syncAll()` |
| `CgtDiscountReminderJob.java` | Quartz wrapper for `NotificationEventListener.enqueueCgtDiscountReminders()` |

---

### `tradetracker-document`

PDF trade confirmation ingestion pipeline.

| File | Purpose |
|---|---|
| `PdfTradeExtractor.java` | PDFBox text extraction + broker detection + strategy dispatch. `CommSecPdfParser` and `SelfWealthPdfParser` implement `TradeConfirmationParser` using regex with confidence scoring (0.0–1.0). Unknown broker returns `ExtractionResult.failed()` |
| `DocumentService.java` | Uploads PDF bytes to MinIO S3, records metadata in `document_uploads`, triggers `@Async` parse. Tracks status through `PENDING → PARSING → PARSED/FAILED`. Parsed trade data stored as JSONB for user review before committing as a `TradeEvent` |

---

### `tradetracker-api`

REST controllers only — no business logic. All controllers delegate immediately to a service.

| File | Endpoints |
|---|---|
| `PortfolioController.java` | `POST /provision`, `GET/POST /v1/portfolios`, `GET/PUT/DELETE /v1/portfolios/{id}`, `GET /v1/portfolios/{id}/holdings`, `GET/POST /v1/portfolios/{id}/trades` |
| `PerformanceController.java` | `GET /v1/portfolios/{id}/performance?period=1Y` |
| `TaxController.java` | `GET /v1/portfolios/{id}/tax/cgt-summary`, `/cgt-events`, `/open-parcels`, `/dividends`, `/report/pdf`, `/report/csv` |
| `BrokerController.java` | `POST /v1/documents/upload`, `GET /v1/documents/{id}`, `GET /v1/broker/connections`, `POST /v1/broker/connections/{id}/sync` |
| `SettingsController.java` | `GET/PUT /v1/settings/profile`, `GET /v1/settings/notifications`, `PUT /v1/settings/notifications/{eventType}`, `PUT /v1/settings/notifications/webhook` |

---

### `tradetracker-app`

Spring Boot entry point. Wires all modules into a single JAR.

| File | Purpose |
|---|---|
| `TradeTrackerApplication.java` | `@SpringBootApplication` with `@EnableCaching`, `@EnableAsync`, `@EnableScheduling` |
| `InfrastructureConfig.java` | `@Bean` definitions for: `S3Client` (MinIO), `RestClient.Builder`, `RedisCacheManager` (per-cache TTLs), `CorsFilter`, `OpenAPI` (Swagger with Keycloak PKCE OAuth2 flow) |
| `GlobalExceptionHandler.java` | `@RestControllerAdvice` mapping all domain exceptions to RFC 7807 Problem Detail responses: 400 validation, 403 access denied, 404 not found, 413 file too large, 422 insufficient holdings, 500 internal error |

---

## Frontend Components

### `src/main.tsx`
App bootstrap. Wraps the entire tree in:
1. `ReactKeycloakProvider` — PKCE flow, `login-required` on load
2. `QueryClientProvider` — TanStack Query with 30s stale time, `refetchOnWindowFocus: false`

### `src/App.tsx`
Root component. Calls `POST /v1/portfolios/provision` on every authenticated load (idempotent — creates the User row on first login). Defines the React Router tree.

### `src/lib/apiClient.ts`
Axios instance with `baseURL = VITE_API_BASE_URL ?? 'http://localhost:8080/api'`. `initApiAuth(keycloak)` installs a request interceptor that refreshes the access token if it expires within 30 seconds, then sets `Authorization: Bearer ...`.

### `src/hooks/usePortfolios.ts`
Central hook file. All data-fetching logic lives here. Exports:

| Hook | Description |
|---|---|
| `usePortfolios()` | Fetches portfolio list, tracks `activePortfolio` in local state |
| `usePortfolio(id)` | Fetches a single portfolio summary |
| `useHoldings(portfolioId)` | Current holdings with market value and unrealised P&L |
| `usePerformance(portfolioId, period)` | TWR time-series for the dashboard chart |
| `useTrades(portfolioId, filters)` | Paginated trade history |
| `useCreateTrade(portfolioId)` | Mutation: creates a trade, invalidates holdings/portfolios cache |
| `useCgtSummary(portfolioId, fy)` | CGT year summary |
| `useBrokerConnections()` | List of connected broker accounts |
| `useNotificationPrefs()` | Notification preferences + `toggle` mutation |

### `src/components/layout/AppLayout.tsx`
Persistent layout: left sidebar (portfolio switcher, nav links, user info/sign-out) + main content `<Outlet>`.

### Pages

| Page | Route | Description |
|---|---|---|
| `DashboardPage.tsx` | `/dashboard` | 4 summary cards (value, cost base, unrealised gain, TWR), area chart with period switcher (1M/3M/6M/YTD/1Y/3Y/ALL), holdings table |
| `PortfolioPage.tsx` | `/portfolio/:id` | Holdings list with click-to-expand parcel panel. Each parcel shows quantity, acquisition date, holding days, CGT discount eligibility countdown, cost vs market value progress bar |
| `TradesPage.tsx` | `/portfolio/:id/trades` | Paginated trade history with ticker search and type filter. `AddTradeModal` with real-time total cost preview, client-side validation, and cache invalidation on success |
| `TaxPage.tsx` | `/portfolio/:id/tax` | Three-tab layout: CGT Breakdown (ATO waterfall table), Disposal Events (with 50% discount badge), Open Parcels (unrealised CGT, eligibility countdown). PDF + CSV download buttons. Financial year selector |
| `SettingsPage.tsx` | `/settings` | Five-tab layout: Profile (edit display name), Portfolios (create/list), Broker Accounts (connection status, manual sync), Import PDF (drag-and-drop with parse status polling), Notifications (per-event-type email toggles) |

---

## Infrastructure Services

| Service | Port | Purpose |
|---|---|---|
| **Keycloak 24** | 8081 | OIDC/OAuth2 identity provider. `tradetracker` realm pre-imported with frontend (PKCE) and backend (service account) clients. Registration enabled in dev |
| **PostgreSQL 16 + TimescaleDB** | 5432 | Primary database. TimescaleDB extensions enable hypertables for `security_prices`, `fx_rates`, `portfolio_snapshots` |
| **Redis 7** | 6379 | Cache for `portfolios` (5 min TTL), `holdings` (5 min), `prices` (10 min), `performance` (6 h), `cgt-summary` (12 h) |
| **MinIO** | 9000 / 9001 | S3-compatible object storage. Three buckets: `trade-confirmations`, `reports`, `market-data-raw` |
| **Redpanda** | 19092 | Kafka-compatible message bus for async trade/price events |
| **RabbitMQ** | 5672 / 8083 | AMQP for notification delivery queue |
| **MailHog** | 1025 / 8025 | Local SMTP sink — captures all outbound emails in a web UI (dev only) |
| **Prometheus** | 9090 | Scrapes `/api/actuator/prometheus` from the backend every 15 seconds |
| **Grafana** | 3001 | Dashboards connected to Prometheus, Loki, and Tempo |
| **Loki** | 3100 | Log aggregation. Promtail ships Docker container logs via Docker SD |
| **Tempo** | 3200 / 4318 | Distributed tracing. Spring Boot exports OTLP traces to port 4318 |
| **Redpanda Console** | 8082 | Kafka topic browser UI |

---

## Database Schema

### Tables by module

#### `security` module
| Table | Description |
|---|---|
| `users` | App users linked to Keycloak `sub` claim |
| `audit_log` | Immutable append-only record of all data mutations |

#### `portfolio` module
| Table | Description |
|---|---|
| `securities` | Listed securities master (ticker, exchange, ISIN, currency) |
| `portfolios` | Named portfolios with parcel matching strategy |
| `accounts` | Brokerage accounts within a portfolio |
| `trade_events` | Every trade (BUY/SELL/DIVIDEND etc.) with price, quantity, fees |
| `tax_parcels` | Open/disposed CGT parcels. Source of truth for all holdings |
| `parcel_disposals` | Ledger of SELL → parcel matches. Stores `disposal_price` and computed `capital_gain` |
| `dividends` | Dividend payments with franking credits |
| `portfolio_snapshots` | EOD market value + cost base per portfolio (TimescaleDB hypertable) |
| `cost_base_adjustments` | Return-of-capital reduction audit trail per parcel |

#### `marketdata` module
| Table | Description |
|---|---|
| `security_prices` | Daily OHLCV + adjusted close (TimescaleDB hypertable, quarterly partitions) |
| `fx_rates` | Daily FX rates USD → other currencies (TimescaleDB hypertable, yearly partitions) |
| `corporate_actions` | Splits, mergers, spin-offs, rights issues, return-of-capital events |
| `price_watch_list` | Securities to sync prices for (auto-populated by trigger when a parcel is created) |
| `price_sync_log` | History of price sync jobs |

#### `broker` module
| Table | Description |
|---|---|
| `broker_connections` | OAuth tokens (AES-256-GCM encrypted), sync status per portfolio |
| `broker_raw_imports` | Raw broker API payloads kept for audit (JSONB) |

#### `notification` module
| Table | Description |
|---|---|
| `notification_preferences` | Per-user per-event-type channel preferences |
| `notification_outbox` | Transactional outbox for at-least-once email delivery |

#### `document` module
| Table | Description |
|---|---|
| `document_uploads` | PDF upload metadata, MinIO S3 key, parse status and confidence score |

#### `scheduler` module
| Tables | Description |
|---|---|
| `qrtz_*` (8 tables) | Standard Quartz JDBC job store tables |

---

## API Endpoints

All paths are prefixed with `/api` (Spring `context-path`).

### Authentication
All endpoints require `Authorization: Bearer <JWT>` except:
- `GET /actuator/health`
- `GET /v3/api-docs/**`
- `GET /swagger-ui/**`

### Portfolio & Holdings
```
POST   /api/v1/portfolios/provision          Idempotent user provisioning (call on every login)
GET    /api/v1/portfolios                    List user's portfolios with totals
POST   /api/v1/portfolios                    Create portfolio
GET    /api/v1/portfolios/{id}               Get single portfolio
PUT    /api/v1/portfolios/{id}               Update name / strategy / default flag
DELETE /api/v1/portfolios/{id}               Delete portfolio
GET    /api/v1/portfolios/{id}/holdings      Holdings with market value and unrealised P&L
GET    /api/v1/portfolios/{id}/performance   TWR/MWR time-series (?period=1Y)
```

### Trades
```
GET    /api/v1/portfolios/{id}/trades        Paginated trades (?ticker&tradeType&from&to)
POST   /api/v1/portfolios/{id}/trades        Record a trade (BUY creates parcel, SELL matches)
```

### Tax & CGT
```
GET    /api/v1/portfolios/{id}/tax/cgt-summary      Year summary (?financialYear=2024)
GET    /api/v1/portfolios/{id}/tax/cgt-events        Disposal event list
GET    /api/v1/portfolios/{id}/tax/open-parcels      Open parcels with unrealised CGT
GET    /api/v1/portfolios/{id}/tax/dividends         Dividend income + franking credits
GET    /api/v1/portfolios/{id}/tax/report/pdf        Download ATO CGT schedule PDF
GET    /api/v1/portfolios/{id}/tax/report/csv        Download disposal events CSV
```

### Broker & Documents
```
POST   /api/v1/documents/upload              Upload PDF trade confirmation (multipart)
GET    /api/v1/documents/{id}               Poll parse status
GET    /api/v1/broker/connections            List broker connections
POST   /api/v1/broker/connections/{id}/sync  Trigger manual sync
```

### Settings
```
GET    /api/v1/settings/profile              User profile
PUT    /api/v1/settings/profile              Update display name, currency, tax country
GET    /api/v1/settings/notifications        Notification preferences
PUT    /api/v1/settings/notifications/{type} Enable/disable a notification type
PUT    /api/v1/settings/notifications/webhook Set webhook URL
```

---

## Scheduler Jobs

All jobs use Quartz JDBC job store (PostgreSQL-backed, cluster-safe).

| Job | Schedule | Purpose |
|---|---|---|
| `NightlyPriceSyncJob` | 8:00 PM UTC (6:00 AM AEST) | Syncs last 100 days of adjusted close prices for all watched securities. 12s sleep between calls (Alpha Vantage free tier: 5 req/min) |
| `FxRateSyncJob` | 8:30 PM UTC | Fetches daily FX rates for all non-USD currencies used in portfolios and securities |
| `PortfolioSnapshotJob` | 9:00 PM UTC | Records EOD market value and cost base for every portfolio into `portfolio_snapshots` (single SQL `INSERT ... SELECT ... ON CONFLICT`) |
| `BrokerSyncQuartzJob` | 7:00 PM UTC (5:00 AM AEST) | Syncs trades from all active broker connections before markets open |
| `CgtDiscountReminderJob` | Sunday 11:00 PM UTC (Monday 9:00 AM AEST) | Scans for parcels reaching 12-month CGT eligibility within 7 days and enqueues reminder emails |
| `StaleHoldingsCacheEvictJob` | Every 10 minutes | Clears Redis `holdings`, `prices`, and `portfolios` caches so the UI shows near-live data |

---

## Test Coverage

| Test File | Module | Type | Tests |
|---|---|---|---|
| `ReturnsCalculationServiceTest` | calculation | Unit | TWR single/multi-period, cashflow distortion, empty input, losing period; MWR simple/intermediate cashflow, insoluble IRR; SubPeriod HPR boundary cases |
| `AustralianCgtServiceTest` | tax | Unit | Short-term gain (no discount), long-term gain (50% discount), capital loss, company entity (no discount), ATO loss ordering, prior-year loss carry-in, franking credit grossup, 12-month boundary |
| `ParcelMatchingStrategyTest` | portfolio | Unit | FIFO oldest-first, multi-parcel spanning, exact match, insufficient holdings; LIFO newest-first, FIFO vs LIFO comparison; MinimiseCGT loss-first, discount-before-short-term, smallest-gain-first, 12-month boundary; invariants (total allocation = sell qty, proceeds = sell price) |
| `BrokerAdapterTest` | broker | Unit | CommSec BUY/SELL parsing, comma-separated quantity, date format, bad row, empty CSV, multi-line CSV, since-date filter |
| `PdfParserTest` | document | Unit | CommSec PDF BUY/SELL parsing, field extraction, date parsing, SelfWealth parsing, broker detection, CSV row parsing |
| `PortfolioServiceIntegrationTest` | portfolio | Integration (Testcontainers TimescaleDB) | User provisioning idempotency, portfolio creation, BUY cost base (incl. fees), SELL FIFO reduction, multiple BUY aggregation, oversell exception, cross-user access denied |
| `SecurityConfigTest` | security | Integration (@WebMvcTest) | `/actuator/health` public, Swagger UI public, `/v1/portfolios` requires auth, tax endpoint requires auth |

---

## Dependency Graph

Arrows indicate "depends on":

```
tradetracker-app
  └── tradetracker-api
        ├── tradetracker-portfolio ──► tradetracker-common
        ├── tradetracker-calculation──► tradetracker-common
        │                           └► tradetracker-portfolio
        ├── tradetracker-tax ────────► tradetracker-common
        │                           └► tradetracker-portfolio
        ├── tradetracker-security ───► tradetracker-common
        ├── tradetracker-broker ─────► tradetracker-common
        │                           └► tradetracker-portfolio
        ├── tradetracker-document ───► tradetracker-common
        └── [transitive via app]
  └── tradetracker-marketdata ──────► tradetracker-common
  │                                └► tradetracker-portfolio
  └── tradetracker-notification ────► tradetracker-common
  │                                └► tradetracker-portfolio
  └── tradetracker-scheduler ───────► tradetracker-common
                                    ├► tradetracker-marketdata
                                    ├► tradetracker-broker
                                    └► tradetracker-notification
```

**Design rule:** `tradetracker-portfolio` must never depend on `tradetracker-tax`. Cross-domain communication uses Spring `ApplicationEvents`.
