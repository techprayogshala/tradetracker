# TradeTracker — Developer Guide

> Step-by-step guide for new developers: from zero to a running local environment,
> understanding the codebase, running tests, and making your first change.

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [First-time Setup](#first-time-setup)
3. [Understanding the Running Stack](#understanding-the-running-stack)
4. [Project Layout](#project-layout)
5. [How a Request Flows](#how-a-request-flows)
6. [Making Changes](#making-changes)
7. [Running Tests](#running-tests)
8. [Common Development Tasks](#common-development-tasks)
9. [Debugging](#debugging)
10. [Environment Variables](#environment-variables)
11. [Troubleshooting](#troubleshooting)

---

## Prerequisites

Install these before anything else.

| Tool | Version | Install |
|---|---|---|
| **Docker Desktop** | Latest | [docs.docker.com](https://docs.docker.com/get-docker/) — allocate at least **6 GB RAM** |
| **Java 21** | Temurin 21 | `sdk install java 21-tem` (via [SDKMAN](https://sdkman.io)) |
| **Maven 3.9** | 3.9+ | `brew install maven` or use `./mvnw` wrapper |
| **Node.js 20** | 20 LTS | `nvm install 20` (via [nvm](https://github.com/nvm-sh/nvm)) |
| **Git** | Any | Included on most systems |

**Verify your setup:**
```bash
java -version        # Should print: openjdk 21...
mvn -version         # Should print: Apache Maven 3.9...
node -version        # Should print: v20...
docker info          # Should not error
```

---

## First-time Setup

### Step 1 — Clone the repository

```bash
git clone https://github.com/your-org/tradetracker.git
cd tradetracker
```

### Step 2 — Configure environment variables

```bash
cp .env.example .env
```

The defaults in `.env.example` work for local development without changes. You only need to edit `.env` if you want real market data (see [Getting a free API key](#getting-a-free-api-key)).

### Step 3 — Start the full local stack

```bash
docker compose up
```

This will pull images and start 15 services. The first run takes 3–5 minutes. You'll know it's ready when you see:

```
backend    | Started TradeTrackerApplication in 4.2 seconds
frontend   | VITE ready in 800 ms
```

**If you only want the infrastructure** (database, Redis, Keycloak etc.) and run the backend locally instead:

```bash
docker compose up postgres redis keycloak minio redpanda rabbitmq mailhog
```

Then run the backend:

```bash
mvn spring-boot:run -pl tradetracker-app \
  -Dspring-boot.run.profiles=dev
```

And the frontend:

```bash
cd frontend
npm install
npm run dev
```

### Step 4 — Open the app

| Service | URL | Credentials |
|---|---|---|
| **Frontend** | http://localhost:3000 | Register a new account |
| **API Swagger UI** | http://localhost:8080/api/swagger-ui.html | — |
| **Keycloak Admin** | http://localhost:8081 | admin / admin |
| **Grafana** | http://localhost:3001 | admin / admin |
| **MailHog** | http://localhost:8025 | — (all emails appear here) |
| **MinIO Console** | http://localhost:9001 | tradetracker / minio_dev |
| **Redpanda Console** | http://localhost:8082 | — |
| **RabbitMQ** | http://localhost:8083 | tradetracker / rabbit_dev |

### Step 5 — Register your first user

1. Go to http://localhost:3000
2. You will be redirected to Keycloak
3. Click **Register** and fill in your details
4. You'll be redirected back and a default portfolio is created automatically

### Getting a free API key

For live market prices, sign up at [alphavantage.co](https://www.alphavantage.co/support/#api-key) (free, instant). Add to `.env`:

```
ALPHA_VANTAGE_API_KEY=your_key_here
```

Restart the backend: `docker compose restart backend`

---

## Understanding the Running Stack

After `docker compose up`, these are the key services and how they relate:

```
Browser
  │
  ▼ :3000
React app (Vite dev server)
  │  Proxies /api → backend:8080
  │
  ▼ :8080/api
Spring Boot backend
  │
  ├─► :5432  PostgreSQL + TimescaleDB   (primary database)
  ├─► :6379  Redis                      (cache, 5–12 min TTL)
  ├─► :8081  Keycloak                   (validates JWTs)
  ├─► :9000  MinIO                      (PDF/report storage)
  ├─► :19092 Redpanda                   (Kafka-compat events)
  └─► :5672  RabbitMQ                   (email queue)

Observability (passive):
  :9090 Prometheus  ◄── scrapes /api/actuator/prometheus
  :3001 Grafana     ◄── queries Prometheus + Loki + Tempo
  :3100 Loki        ◄── receives logs from Promtail
  :3200 Tempo       ◄── receives OTLP traces from backend
```

The backend uses Spring's `context-path=/api`, so every endpoint is:
- Full URL: `http://localhost:8080/api/v1/portfolios`
- Frontend calls: `baseURL/v1/portfolios` (baseURL already includes `/api`)

---

## Project Layout

```
tradetracker/
├── pom.xml                        Root Maven POM (dependency versions)
├── Dockerfile.backend             Multi-stage Maven build → JRE runtime
├── docker-compose.yml             Full local dev stack (15 services)
├── docker-compose.test.yml        Lighter stack for E2E tests
├── .env.example                   Template for environment variables
├── ARCHITECTURE.md                Complete component reference
├── DEVELOPER_GUIDE.md             This file
├── CONTRIBUTING.md                PR process, conventions, module rules
│
├── tradetracker-common/           Shared domain primitives (no Spring)
├── tradetracker-security/         Spring Security + Keycloak JWT config
├── tradetracker-portfolio/        Core: entities, repos, PortfolioService
├── tradetracker-calculation/      TWR, MWR, PerformanceService
├── tradetracker-tax/              CGT engine, PDF reports
├── tradetracker-broker/           CommSec/SelfWealth/IBKR adapters
├── tradetracker-marketdata/       Alpha Vantage price sync
├── tradetracker-notification/     Email outbox and templates
├── tradetracker-scheduler/        Quartz jobs (nightly price sync etc.)
├── tradetracker-document/         PDF trade confirmation parser
├── tradetracker-api/              REST controllers only
├── tradetracker-app/              Spring Boot entry point + global config
│
├── frontend/                      React + Vite + TypeScript + Tailwind
│   ├── src/
│   │   ├── main.tsx               App bootstrap (Keycloak + QueryClient)
│   │   ├── App.tsx                Router + provision call on login
│   │   ├── lib/apiClient.ts       Axios + auto token refresh
│   │   ├── hooks/usePortfolios.ts All shared data-fetching hooks
│   │   ├── components/layout/     AppLayout (sidebar + nav)
│   │   └── pages/                 DashboardPage, TradesPage, TaxPage...
│   ├── e2e/                       Playwright end-to-end tests
│   └── playwright.config.ts
│
└── infra/                         Config files for infrastructure services
    ├── keycloak/realm-export.json Pre-configured Keycloak realm
    ├── prometheus/
    ├── grafana/
    ├── loki/
    └── tempo/
```

### Inside a backend module

Each `tradetracker-*` module follows the same structure:

```
tradetracker-portfolio/
└── src/
    ├── main/java/com/tradetracker/portfolio/
    │   ├── entity/         JPA entity classes
    │   ├── repository/     Spring Data JPA repositories
    │   ├── service/        Business logic
    │   ├── matching/       Parcel matching strategies
    │   └── event/          Spring ApplicationEvent records
    └── test/java/com/tradetracker/portfolio/
        └── *Test.java      JUnit 5 tests
```

---

## How a Request Flows

Let's trace a **BUY trade** from the browser to the database:

### 1. Frontend — `TradesPage.tsx`

The user fills in the Add Trade form and clicks "Save trade":

```typescript
// TradesPage.tsx
const mutation = useMutation({
  mutationFn: (data) => api.post(
    `/v1/portfolios/${portfolioId}/trades`,
    { ticker: 'CBA', exchange: 'ASX', tradeType: 'BUY',
      quantity: 100, price: 105.50, fees: 9.95,
      currency: 'AUD', tradeDate: '2024-03-15' }
  ),
  onSuccess: () => {
    qc.invalidateQueries(['trades', portfolioId])
    qc.invalidateQueries(['holdings', portfolioId])
  }
})
```

### 2. HTTP — Axios interceptor adds JWT

```typescript
// apiClient.ts
config.headers.Authorization = `Bearer ${keycloak.token}`
```

Request arrives at: `POST http://localhost:8080/api/v1/portfolios/{id}/trades`

### 3. Spring Security — JWT validation

`SecurityConfig` routes the request through the JWT resource server filter. Keycloak JWKS endpoint validates the signature. The `keycloakJwtConverter` extracts `ROLE_USER` from the token.

### 4. REST Controller — `PortfolioController`

```java
// PortfolioController.java
@PostMapping("/{portfolioId}/trades")
public TradeDto createTrade(@AuthenticationPrincipal Jwt jwt,
                            @PathVariable UUID portfolioId,
                            @Valid @RequestBody CreateTradeRequest req) {
    var t = svc.recordTrade(jwt.getSubject(), portfolioId, new TradeCommand(...));
    return new TradeDto(t.getId(), ...);
}
```

`@Valid` triggers Bean Validation. `jwt.getSubject()` is the Keycloak `sub` claim.

### 5. Service — `PortfolioService.recordTrade()`

```java
// Looks up the portfolio (validates ownership)
Portfolio portfolio = requirePortfolio(keycloakSub, portfolioId);

// Creates or retrieves the Security record
Security security = securityRepo
    .findByTickerAndExchange("CBA", "ASX")
    .orElseGet(() -> securityRepo.save(new Security("CBA", "ASX", "AUD")));

// Builds and saves the TradeEvent
TradeEvent trade = TradeEvent.builder()
    .portfolio(portfolio).security(security)
    .tradeType(BUY).quantity(100).price(105.50).fees(9.95)
    .tradeDate(2024-03-15)
    .build();
tradeRepo.save(trade);

// BUY → create a TaxParcel
// costPerUnit = (105.50 × 100 + 9.95) / 100 = 105.5995
createParcel(portfolio, security, trade);
```

### 6. Tax parcel created

```java
TaxParcel parcel = new TaxParcel(
    portfolio, security, trade,
    quantity=100,
    costPerUnit=105.5995,   // includes fees
    currency="AUD",
    acquisitionDate=2024-03-15
);
parcelRepo.save(parcel);
```

### 7. Event published

```java
events.publishEvent(new TradeRecordedEvent(trade.getId(), portfolioId, BUY));
```

Two async listeners react:
- `TradeEventMarketDataListener` — checks if CBA has price history; if not, triggers backfill
- `NotificationEventListener` — enqueues a trade confirmation email in `notification_outbox`

### 8. Response

The controller maps the saved `TradeEvent` to a `TradeDto` and returns HTTP 201.

### 9. Frontend cache invalidation

TanStack Query invalidates `['trades', portfolioId]` and `['holdings', portfolioId]`, causing the holdings table to refetch and show the new CBA position.

---

## Making Changes

### Adding a new REST endpoint

1. Add the method to the relevant controller in `tradetracker-api/`
2. Add a corresponding service method in the relevant module
3. If it needs a new DB column, create a Flyway migration in `tradetracker-app/src/main/resources/db/migration/<module>/V{n}__description.sql`
4. Add a new hook to `frontend/src/hooks/usePortfolios.ts` if the frontend needs it

### Adding a new broker

1. Create a class in `tradetracker-broker/src/main/java/.../broker/adapter/`
2. Implement `BrokerAdapter` and annotate with `@Component("BROKER_ID")`
3. Implement `brokerId()`, `fetchTrades()`, `fetchCashBalances()`
4. Add the broker to `BROKERS` array in `frontend/src/pages/SettingsPage.tsx`
5. Write a unit test — see `BrokerAdapterTest.java` for the pattern

No other changes needed. `BrokerSyncService` autowires all `BrokerAdapter` beans automatically.

### Adding a new PDF parser

1. Create a class in `tradetracker-document/` implementing `TradeConfirmationParser`
2. Annotate with `@Component`
3. Implement `brokerId()`, `canParse(text)`, `parse(text)`
4. `PdfTradeExtractor` picks it up automatically via Spring's `List<TradeConfirmationParser>` injection

### Adding a Flyway migration

```
tradetracker-app/src/main/resources/db/migration/<module>/V{next_n}__description.sql
```

**Rules:**
- Never modify an existing migration — always add a new one
- The version number must be higher than all existing migrations in that subdirectory
- Test locally: `docker compose up postgres` then `mvn flyway:migrate -pl tradetracker-app`

### Changing the domain model

1. Modify the JPA entity in `tradetracker-portfolio/entity/`
2. Write a Flyway migration for the schema change
3. Update `spring.jpa.hibernate.ddl-auto: validate` will catch mismatches at startup

---

## Running Tests

### Unit tests (fast — no Docker needed)

```bash
# Run all unit tests across the 5 modules that have them
mvn test \
  -pl tradetracker-calculation,tradetracker-tax,tradetracker-document,tradetracker-portfolio,tradetracker-broker \
  -B
```

These tests have zero external dependencies — they run in milliseconds.

### Integration tests (requires Docker)

The `PortfolioServiceIntegrationTest` uses Testcontainers to spin up a real TimescaleDB instance:

```bash
# Run all tests including integration tests
mvn verify -B

# Run only the integration test
mvn test -pl tradetracker-portfolio -Dtest=PortfolioServiceIntegrationTest
```

Testcontainers pulls `timescale/timescaledb:latest-pg16` automatically. First run is slow (image download); subsequent runs use the Docker cache.

### Frontend type checking and linting

```bash
cd frontend
npm install
npm run type-check   # TypeScript compilation check
npm run lint         # ESLint
npm run build        # Full production build
```

### End-to-end tests

Requires the full stack running:

```bash
# Start the test stack (includes E2E test user seeding)
docker compose -f docker-compose.test.yml up -d

# Wait for backend healthy, then run:
cd frontend
npm run e2e          # Headless Chromium
npm run e2e:ui       # Interactive Playwright UI
```

The E2E tests cover: dashboard loading, trade form validation, trade submission, tax page tabs, settings tabs.

---

## Common Development Tasks

### Inspect the database

```bash
# Connect to PostgreSQL
docker exec -it postgres psql -U tradetracker -d tradetracker

# Useful queries:
SELECT * FROM portfolios;
SELECT * FROM tax_parcels WHERE is_fully_disposed = false LIMIT 10;
SELECT * FROM parcel_disposals ORDER BY created_at DESC LIMIT 5;
SELECT security_id, COUNT(*), MAX(price_date) FROM security_prices GROUP BY security_id;
```

### Check what emails were sent

Open http://localhost:8025 — MailHog captures all SMTP traffic in dev.

### Inspect the Redis cache

```bash
docker exec -it redis redis-cli -a tradetracker
> KEYS *
> TTL holdings::some-uuid
> GET holdings::some-uuid
```

### Force a nightly price sync manually

```bash
# Hit the actuator to trigger a Quartz job immediately
curl -X POST http://localhost:8080/api/actuator/quartz/jobs/DEFAULT/nightlyPriceSync
```

Or via the API — create a trade for a new ticker and the `TradeEventMarketDataListener` will trigger a backfill automatically.

### Tail the application logs with Loki

Open Grafana at http://localhost:3001 → Explore → Loki → query:
```logql
{container="backend"} |= "ERROR"
{container="backend"} | json | level="INFO"
```

### See distributed traces

Open Grafana → Explore → Tempo → search by service `tradetracker`.

### Check Quartz job status

```bash
SELECT j.job_name, t.trigger_state, 
       to_timestamp(t.next_fire_time/1000) AS next_fire
FROM qrtz_triggers t
JOIN qrtz_job_details j ON j.job_name = t.job_name
ORDER BY t.next_fire_time;
```

### Reset everything and start fresh

```bash
docker compose down -v   # Removes all volumes (data is lost)
docker compose up
```

---

## Debugging

### Backend in IntelliJ IDEA

1. Open the project root in IntelliJ (it auto-detects Maven multi-module)
2. Start the infrastructure: `docker compose up postgres redis keycloak minio redpanda rabbitmq mailhog`
3. Create a Run Configuration:
   - **Main class:** `com.tradetracker.app.TradeTrackerApplication`
   - **Working directory:** `tradetracker-app/`
   - **Environment variables:** Copy from `.env` or set manually
4. Set a breakpoint and click Debug

**Key environment variables for local run:**
```
DB_HOST=localhost
DB_USER=tradetracker
DB_PASSWORD=tradetracker_dev
REDIS_HOST=localhost
REDIS_PASSWORD=redis_dev
KEYCLOAK_ISSUER_URI=http://localhost:8081/realms/tradetracker
AWS_ENDPOINT_URL=http://localhost:9000
AWS_ACCESS_KEY_ID=tradetracker
AWS_SECRET_ACCESS_KEY=minio_dev
KAFKA_BOOTSTRAP_SERVERS=localhost:19092
RABBITMQ_HOST=localhost
SMTP_HOST=localhost
SMTP_PORT=1025
```

### Frontend in VS Code

```bash
cd frontend
npm run dev
```

The Vite dev server hot-reloads on every save. The browser console shows TanStack Query devtools if you add `ReactQueryDevtools` to `main.tsx`.

### Remote debugging the Docker container

```bash
# Add to backend service in docker-compose.yml:
environment:
  JAVA_TOOL_OPTIONS: "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
ports:
  - "5005:5005"
```

Then attach IntelliJ Remote JVM Debug to `localhost:5005`.

### Log levels

Temporarily increase log detail without restarting:

```bash
# Enable SQL logging
curl -X POST http://localhost:8080/api/actuator/loggers/org.hibernate.SQL \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel": "DEBUG"}'

# Enable all tradetracker logs
curl -X POST http://localhost:8080/api/actuator/loggers/com.tradetracker \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel": "DEBUG"}'
```

---

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `DB_HOST` | `postgres` | PostgreSQL hostname |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `tradetracker` | Database name |
| `DB_USER` | `tradetracker` | Database username |
| `DB_PASSWORD` | `tradetracker` | Database password |
| `REDIS_HOST` | `redis` | Redis hostname |
| `REDIS_PASSWORD` | `tradetracker` | Redis password |
| `KAFKA_BOOTSTRAP_SERVERS` | `redpanda:9092` | Kafka bootstrap servers |
| `RABBITMQ_HOST` | `rabbitmq` | RabbitMQ hostname |
| `RABBITMQ_USER` | `tradetracker` | RabbitMQ username |
| `RABBITMQ_PASSWORD` | `tradetracker` | RabbitMQ password |
| `KEYCLOAK_ISSUER_URI` | `http://keycloak:8080/realms/tradetracker` | OIDC issuer |
| `AWS_ENDPOINT_URL` | `http://minio:9000` | S3/MinIO endpoint |
| `AWS_ACCESS_KEY_ID` | `tradetracker` | S3 access key |
| `AWS_SECRET_ACCESS_KEY` | `tradetracker` | S3 secret key |
| `SMTP_HOST` | `mailhog` | SMTP hostname |
| `SMTP_PORT` | `1025` | SMTP port |
| `INTERNAL_JWT_SECRET` | (required in prod) | 32+ char secret for AES token encryption |
| `ALPHA_VANTAGE_API_KEY` | `demo` | Alpha Vantage API key (demo = no real data) |
| `OPEN_EXCHANGE_RATES_APP_ID` | (optional) | FX rates API key |

Frontend (Vite) environment variables (set in `.env` or Docker environment):

| Variable | Default | Description |
|---|---|---|
| `VITE_API_BASE_URL` | `http://localhost:8080/api` | Backend API base URL |
| `VITE_KEYCLOAK_URL` | `http://localhost:8081` | Keycloak server URL |
| `VITE_KEYCLOAK_REALM` | `tradetracker` | Keycloak realm name |
| `VITE_KEYCLOAK_CLIENT_ID` | `tradetracker-frontend` | Keycloak client ID |

---

## Troubleshooting

### "Port already in use"

```bash
# Find what's using port 5432
lsof -i :5432
# Or on Windows: netstat -ano | findstr :5432
```

Common culprits: a local PostgreSQL installation. Either stop it or change the port mapping in `docker-compose.yml`.

### Backend won't start — "Flyway migration failed"

This usually means you have a stale migration. Check:

```bash
docker exec postgres psql -U tradetracker -d tradetracker \
  -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_on DESC LIMIT 5;"
```

If a migration is marked `success=false`, fix the SQL and run:

```bash
docker exec postgres psql -U tradetracker -d tradetracker \
  -c "DELETE FROM flyway_schema_history WHERE success=false;"
docker compose restart backend
```

### "Keycloak connection refused" on startup

Keycloak takes 30–60 seconds to start. The backend retries JWT validation lazily (only on the first request), so this is usually fine. If it persists:

```bash
docker compose logs keycloak | tail -20
```

### Frontend shows blank after login

Check the browser console. Common causes:
1. `provision` endpoint returned 500 — check backend logs
2. Keycloak token doesn't include `sub` claim — check realm config at http://localhost:8081
3. CORS error — ensure `VITE_API_BASE_URL` matches what the backend expects

### TimescaleDB queries failing

If you see errors like `function create_hypertable does not exist`, the TimescaleDB extension wasn't created. Run:

```bash
docker exec postgres psql -U tradetracker -d tradetracker \
  -c "CREATE EXTENSION IF NOT EXISTS timescaledb CASCADE;"
```

Then restart the backend to re-run Flyway migrations.

### Tests fail with "Cannot connect to Docker daemon"

Testcontainers requires Docker. Ensure Docker Desktop is running. On Linux, add your user to the `docker` group:

```bash
sudo usermod -aG docker $USER
newgrp docker
```

### Quartz jobs not firing

1. Check the `qrtz_triggers` table — are `trigger_state` values `WAITING` or `BLOCKED`?
2. Check `spring.quartz.auto-startup` is not `false` in your active profile
3. Verify `@EnableScheduling` is present on `TradeTrackerApplication`

```bash
# Check trigger states
docker exec postgres psql -U tradetracker -d tradetracker \
  -c "SELECT job_name, trigger_state FROM qrtz_triggers;"
```

### "Address already in use: 5005" when debugging

Another debug session is attached. Either kill it or change the debug port in `docker-compose.yml`.

---

## Next Steps

Once you have the app running and understand the basics:

1. **Read `ARCHITECTURE.md`** — detailed component descriptions for every Java class and React component
2. **Read `CONTRIBUTING.md`** — PR process, module rules, test requirements
3. **Pick a small task** — add a new notification type, add a new chart to the dashboard, or improve the PDF parser for a new broker
4. **Write a test first** — all new features should have unit tests. See the existing tests for patterns

The codebase follows a consistent pattern: controllers delegate to services, services publish events, listeners react asynchronously. If you understand the BUY trade flow in [How a Request Flows](#how-a-request-flows), you understand 80% of the codebase.
