# Contributing to TradeTracker

Thanks for considering a contribution! This guide covers the development setup, conventions, and PR process.

## Prerequisites

- Docker Desktop (≥ 6 GB RAM allocated)
- Java 21 (temurin recommended — `sdk install java 21-tem`)
- Node 20 (`nvm install 20`)
- Maven 3.9 (`brew install maven` or use the wrapper `./mvnw`)

## Quick start

```bash
cp .env.example .env
docker compose up
```

Everything runs locally: Postgres, Redis, Keycloak, MinIO, Redpanda, MailHog, Grafana.

Frontend: http://localhost:3000  
API docs: http://localhost:8080/api/swagger-ui.html  
Keycloak: http://localhost:8081 (admin/admin)  
MailHog:  http://localhost:8025

## Project layout

```
tradetracker-common/        Shared domain records (TradeEvent, TaxParcel, etc.)
tradetracker-security/      JWT / Keycloak OIDC config
tradetracker-portfolio/     Entities, repositories, PortfolioService
tradetracker-calculation/   TWR, MWR, PerformanceService
tradetracker-tax/           CGT engine, parcel matching, TaxReportService
tradetracker-broker/        Broker adapters (CommSec, SelfWealth, IBKR)
tradetracker-marketdata/    Price sync, FX rates, AlphaVantage client
tradetracker-notification/  Email outbox, templates
tradetracker-scheduler/     Quartz jobs
tradetracker-document/      PDF parsing, MinIO upload
tradetracker-api/           REST controllers, DTOs
tradetracker-app/           Spring Boot entry point, global config
frontend/                   React + Vite + TypeScript
```

## Running tests

```bash
# All backend tests (requires Docker for Testcontainers)
mvn verify

# Unit tests only (no Docker required)
mvn test -pl tradetracker-calculation,tradetracker-tax,tradetracker-document

# Frontend type-check + lint
cd frontend && npm run type-check && npm run lint

# E2E tests (requires full stack running)
docker compose -f docker-compose.test.yml up -d
cd frontend && npm run e2e
```

## Adding a new broker

1. Create a class in `tradetracker-broker` implementing `BrokerAdapter`
2. Annotate with `@Component("BROKER_ID")` (e.g. `@Component("STAKE")`)
3. Implement `brokerId()`, `fetchTrades()`, `fetchCashBalances()`
4. Add the broker to the `BROKERS` list in `frontend/src/pages/SettingsPage.tsx`
5. Write a unit test in `BrokerAdapterTest`

No other changes required — `BrokerSyncService` auto-discovers adapters via Spring DI.

## Adding a PDF parser

1. Create a class implementing `TradeConfirmationParser` in `tradetracker-document`
2. Annotate with `@Component`
3. Implement `brokerId()`, `canParse(text)`, `parse(text)`
4. The `PdfTradeExtractor` will automatically include it in the strategy chain

## Database migrations

Migrations live in `tradetracker-app/src/main/resources/db/migration/<module>/`.

- File naming: `V{n}__{description}.sql` (e.g. `V2__add_drp_flag.sql`)
- Never modify an existing migration — always create a new one
- Test locally: `docker compose up postgres` then `mvn flyway:migrate -pl tradetracker-app`

## Module dependency rules

Modules can only depend on modules listed below them:

```
tradetracker-api
    └── tradetracker-portfolio, tradetracker-calculation, tradetracker-tax, tradetracker-security
tradetracker-portfolio
    └── tradetracker-common
tradetracker-calculation
    └── tradetracker-common
tradetracker-tax
    └── tradetracker-common, tradetracker-portfolio
```

Cross-module communication should use Spring `ApplicationEvents`, not direct bean injection.

## Code conventions

- **Java**: Google Java Format; records preferred over classes for value objects
- **TypeScript**: No `any`; prefer `unknown` + type guards; functional components only
- **SQL**: Snake_case columns; always include a covering index for foreign keys
- **Tests**: Testcontainers for integration tests; plain JUnit for unit tests
- **Commits**: Conventional Commits format (`feat:`, `fix:`, `test:`, `docs:`)

## Pull request checklist

- [ ] `mvn verify` passes
- [ ] `npm run type-check` passes  
- [ ] New SQL migrations don't modify existing files
- [ ] New broker/parser adapters have unit tests
- [ ] Architecture-level changes have a linked GitHub Issue

## Reporting issues

Use GitHub Issues. For financial calculation bugs, please include:
- The trade inputs that produced the incorrect result
- The expected output with your calculation showing why
- The ATO reference if relevant (e.g. TD 2001/18 for CGT loss ordering)
