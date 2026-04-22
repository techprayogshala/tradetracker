# TradeTracker

> Open-source portfolio tracker replicating Sharesight — built on Java 21 + Spring Boot 3 + React.

---

## Quick start

```bash
# 1. Clone and configure
git clone https://github.com/your-org/tradetracker.git
cd tradetracker
cp .env.example .env        # No changes needed for local dev

# 2. Start everything
docker compose up

# 3. Open
# Frontend:          http://localhost:3000
# API (Swagger UI):  http://localhost:8080/api/swagger-ui.html
# Keycloak admin:    http://localhost:8081  (admin / admin)
# Grafana:           http://localhost:3001  (admin / admin)
# MailHog:           http://localhost:8025
# MinIO console:     http://localhost:9001  (tradetracker / minio_dev)
# Redpanda Console:  http://localhost:8082
# RabbitMQ:          http://localhost:8083  (tradetracker / rabbit_dev)
```

First login: register via Keycloak at http://localhost:3000 (registration is enabled in the dev realm).

---

## Architecture

**Backend** — Java 21 / Spring Boot 3.3 modular monolith. All domain modules compile into a single deployable JAR. Module boundaries are enforced via Maven — no cross-module JPA joins.

**Frontend** — React 18 + Vite + TypeScript + Tailwind CSS.

**Auth** — Keycloak 24 (OIDC/OAuth2). Spring Security validates JWTs via `oauth2ResourceServer`. Frontend uses PKCE flow via `keycloak-js`.

**Database** — PostgreSQL 16 + TimescaleDB. Flyway migrations are co-located with each module under `tradetracker-app/src/main/resources/db/migration/<module>/`.

**Observability** — Micrometer auto-exports metrics to Prometheus → Grafana. Structured logs ship to Loki. Distributed traces go to Tempo via OTLP. All zero-config in Spring Boot 3.

### Modules

| Module | Responsibility |
|---|---|
| `tradetracker-common` | Shared domain records, exceptions, utilities |
| `tradetracker-security` | JWT config, Keycloak role extraction |
| `tradetracker-portfolio` | Portfolios, accounts, holdings, positions |
| `tradetracker-calculation` | TWR, MWR, unrealised gains engine |
| `tradetracker-tax` | CGT engine, parcel matching, franking credits |
| `tradetracker-broker` | Broker API integrations, trade ingestion |
| `tradetracker-marketdata` | Price sync, FX rates, corporate actions |
| `tradetracker-notification` | Email and webhook dispatch |
| `tradetracker-scheduler` | Quartz jobs — price sync, FX, reports |
| `tradetracker-document` | PDF parsing (PDFBox + Tika), report export |
| `tradetracker-api` | REST controllers, DTOs, OpenAPI spec |
| `tradetracker-app` | Spring Boot entry point, wires all modules |

### Key design decisions

**Virtual threads** — `spring.threads.virtual.enabled=true` eliminates blocking I/O concerns for JDBC, broker API calls, and PDF parsing without reactive complexity.

**Parcel matching strategies** — `FIFO`, `LIFO`, and `MINIMISE_CGT` are Spring `@Component` beans looked up by name. Adding a new strategy is a single class + zero config.

**TimescaleDB hypertables** — `security_prices` and `fx_rates` are TimescaleDB hypertables with automatic quarterly/yearly partitioning. Range queries over years of daily price data are sub-second.

**Flyway per-module migrations** — each module owns its schema under `db/migration/<module>/`. The app module's Flyway config includes all locations.

---

## Build (without Docker)

```bash
# Backend
mvn clean package -DskipTests
java -jar tradetracker-app/target/tradetracker-app-0.1.0-SNAPSHOT.jar

# Frontend
cd frontend
npm install
npm run dev
```

---

## Phase roadmap

| Phase | Status | Scope |
|---|---|---|
| 1 — Core MVP | 🔨 In progress | Auth, manual trades, holdings, TWR/MWR, P&L display |
| 2 — Data Ingestion | Planned | Broker APIs (CommSec, IBKR), price sync, PDF parser, FX rates, corporate actions |
| 3 — Tax & Reporting | Planned | CGT engine, franking credits, ATO PDF reports, notifications |

---

## Contributing

1. Fork the repo and create a feature branch
2. Raise a GitHub Issue for any architecture-level changes before implementing
3. Each module must have its own `@SpringBootTest` integration test using Testcontainers
4. Run `mvn verify` before opening a PR

---

*TradeTracker is not financial or tax advice. Verify all calculations with a registered tax agent before lodging.*
