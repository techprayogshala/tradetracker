# TradeTracker Development Notes

---

## STAGE 5 CHECKPOINT (2026-04-26)

**Status**: COMPLETE - Column sorting on all tables, alignment fixes

**New Features (COMPLETE)**:
- Sorting on all tables (Trades, Holdings, Open Parcels, CGT Events, Dashboard) ✅
- Clickable column headers with sort direction indicators (↑/↓, ChevronUp/Down) ✅
- Backend server-side sorting with `sort` and `sortDir` parameters ✅

**Tables with Sorting**:
- **Dashboard** - Holdings table (7 columns)
- **Portfolio** - Holdings table (5 columns)
- **Trades** - All 8 data columns (Date, Security, Type, Quantity, Price, Fees, Source)
- **Tax** - CGT Events table (10 columns) + Open Parcels table (9 columns)

**Backend Endpoints Updated**:
- `GET /v1/portfolios/{id}/holdings?sort=field&sortDir=asc|desc`
- `GET /v1/portfolios/{id}/trades?sort=field&sortDir=asc|desc`
- `GET /v1/portfolios/{id}/tax/cgt-events?sort=field&sortDir=asc|desc`
- `GET /v1/portfolios/{id}/tax/open-parcels?sort=field&sortDir=asc|desc`

**Fixes Applied**:

1. **TradeEventRepository.java** (`tradetracker-portfolio/.../repository/TradeEventRepository.java`):
   - Removed hardcoded `ORDER BY t.tradeDate DESC, t.createdAt DESC` from JPQL query
   - Was overriding dynamic sort parameter from API

2. **PortfolioController.java** (`tradetracker-api/.../PortfolioController.java`):
   - Added `sortDir` parameter to holdings and trades endpoints
   - Trades endpoint: `PageRequest.of(page, size, Sort.by(direction, sort))`

3. **PortfolioService.java** (`tradetracker-portfolio/.../service/PortfolioService.java`):
   - Added `sortDir` parameter to `getHoldings()`
   - Fixed comparator logic: removed `.reversed()` from all cases, apply `desc` only when sortDir="desc"

4. **TaxController.java** (`tradetracker-api/.../TaxController.java`):
   - Added `sortDir` parameter to `getCgtEvents()` and `getOpenParcels()` endpoints

5. **TaxReportService.java** (`tradetracker-tax/.../TaxReportService.java`):
   - Added `sortDir` parameter to `getCgtEvents()` and `getOpenParcels()`
   - Fixed comparator logic for both methods (same fix as PortfolioService)

6. **TradesPage.tsx** (`frontend/src/pages/TradesPage.tsx`):
   - Fixed `sort` param format: changed from `sort: \`${sortField},${sortDir}\`` to separate params
   - Added width and sortKey support to COLUMNS config
   - Security column uses `sortKey: 'security.ticker'` for correct backend field
   - Total column marked as `isCalculated: true` (non-sortable, calculated field)
   - All columns left-aligned for consistent appearance

7. **PortfolioPage.tsx** (`frontend/src/pages/PortfolioPage.tsx`):
   - Added sort direction toggle with ↑/↓ indicators
   - Updated `useHoldings` hook to include `sortDir` parameter

8. **TaxPage.tsx** (`frontend/src/pages/TaxPage.tsx`):
   - CGT Events table: added EVENT_COLS config with sort support
   - Open Parcels table: added PARCEL_COLS config with sort support
   - All columns left-aligned

9. **DashboardPage.tsx** (`frontend/src/pages/DashboardPage.tsx`):
   - Added COLS config with alignment and toggleSort function
   - Holdings table has sorting on all 7 columns
   - Columns properly aligned (Security left, numeric columns right)

10. **usePortfolios.ts** (`frontend/src/hooks/usePortfolios.ts`):
    - `useHoldings()` hook updated to accept `sortDir` parameter
    - Removed staleTime/refetchOnWindowFocus to allow proper re-fetching

**Column Alignment**:
- All tables now use left-aligned columns for consistency
- TradesPage, TaxPage (both tables), DashboardPage all left-aligned
- PortfolioPage uses sort buttons with ↑/↓ indicators

---

## STAGE 4 CHECKPOINT (2026-04-25)

**Status**: COMPLETE - Edit/Delete trades feature working, Swagger working, auth flow fixed

**New Features (COMPLETE)**:
- Edit trade modal (EditTradeModal component) ✅
- Delete trade modal (DeleteTradeModal component) ✅
- Edit/Delete buttons on each trade row ✅
- Backend PUT/DELETE endpoints for trades ✅

**Fixes Applied**:

1. **BaseEntity.java** (`tradetracker-portfolio/.../entity/BaseEntity.java`):
   - Added `getVersion()` and `setVersion(Long)` methods for JPA optimistic locking
   - Was causing NPE: `Cannot invoke 'java.lang.Long.longValue()' because 'current' is null`

2. **application.yml** (`tradetracker-app/src/main/resources/application.yml`):
   - Changed Keycloak JWK URI from `http://keycloak:8080` → `http://localhost:8081`
   - Backend runs locally (outside Docker), needs localhost to reach Keycloak
   - Docker uses `keycloak:8080`, localhost uses `localhost:8081`

3. **apiClient.ts** (`frontend/src/lib/apiClient.ts`):
   - Fixed `window._keycloak_` → `window._keycloak` (wrong variable name)
   - Token wasn't being attached to requests, causing all API calls to fail with 401

4. **App.tsx** (`frontend/src/App.tsx`):
   - Added `authReady` state that waits for both Keycloak init AND authentication
   - Only renders the app after auth is confirmed
   - Shows "Authenticating..." screen until ready
   - Provision call only fires after authReady is true

5. **usePortfolios.js** (`frontend/src/hooks/usePortfolios.js`):
   - Simplified - removed redundant auth checks (App.tsx handles auth now)
   - All hooks use standard React Query patterns without auth guards
   - Fixed duplicate function declarations (was causing "Identifier already declared" error)

6. **SecurityConfig.java** (`tradetracker-security/.../SecurityConfig.java`):
   - Added `.cors(cors -> {})` to enable CORS filter for Spring Boot 4.0

7. **springdoc-openapi** (`pom.xml`):
   - Upgraded from 2.3.0 → 3.0.3 for Spring Boot 4.0 compatibility
   - Fixed error: `NoSuchMethodError: ControllerAdviceBean.<init>`

8. **SettingsPage.tsx** (`frontend/src/pages/SettingsPage.tsx`):
   - Fixed `parseStatus` naming conflict with query result
   - Changed to use `status` field name to avoid collision
   - Fixed `refetchInterval` callback type error

9. **tsconfig.json** (`frontend/tsconfig.json`):
   - Added `"types": ["vite/client"]` for `import.meta.env` support
   - Installed `@types/lodash` for Recharts types

10. **Docker Desktop** troubleshooting:
    - Reset Docker data: `rm -rf ~/Library/Containers/com.docker.docker/Data`
    - Fixed Docker config by removing `credsStore: desktop`
    - Changed context to `desktop-linux`

11. **nginx.conf** (`frontend/nginx.conf`):
    - Changed proxy from `http://backend:8080` → `http://host.docker.internal:8080`

**Service URLs**:
- Frontend (Docker): `http://localhost:3002` (Vite dev server with hot reload)
- Backend API: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/api/swagger-ui/index.html`
- Keycloak (Docker internal): `http://keycloak:8080`
- Keycloak (host): `http://localhost:8081`

**Docker Compose Services**:
```bash
docker compose up -d postgres redis keycloak frontend
```

**Frontend Development**:
- Docker frontend uses `tradetracker-frontend:dev` image with Vite dev server
- Source code mounted as volume for hot reload
- Access at `http://localhost:3002`
- API calls to `http://backend:8080/api` (inside Docker network)

**Backend Development** (run locally):
- Start with `mvn spring-boot:run` from `tradetracker-app` directory
- Connects to Docker Keycloak at `http://localhost:8081`
- Connects to Docker PostgreSQL at `localhost:5432`

---

## STAGE 3 CHECKPOINT (2026-04-24)

**Status**: Bulk CSV import for trades, drag-and-drop upload, flexible column mapping

**New Features**:
- Bulk import trades via CSV file upload
- Drag-and-drop file support
- Auto-detection of header row (skips non-header rows)
- Flexible column mapping: recognises various header names like Code/Ticker, Qty/Quantity, Price/Rate, etc.
- Backend bulk trade endpoint: `POST /v1/portfolios/{id}/trades/bulk`

**Fixes Applied**:

1. **PortfolioController.java** (`tradetracker-api/.../PortfolioController.java`):
   - Added `/trades/bulk` endpoint accepting array of trades
   - Iterates and creates each trade via `recordTrade()`

2. **TradesPage.tsx** (`frontend/src/pages/TradesPage.tsx`):
   - Added `BulkUploadModal` component with CSV parsing
   - Custom CSV parser handles quoted fields, multiple header names
   - Auto-finds header row by searching for column indicators
   - Adds "Import CSV" button next to Export/Add trade buttons
   - Supports drag-and-drop file upload

3. **SecurityConfig.java** (`tradetracker-security/.../SecurityConfig.java`):
   - Added `.cors(cors -> {})` to enable CORS filter for Spring Boot 4.0

---

## STAGE 2 CHECKPOINT (2026-04-22)

**Status**: All features working (Dashboard, Holdings, Trades, Tax PDF download, CORS)

**Working Features**:
- Frontend loads and renders
- Keycloak login/authentication works
- Backend API with JWT authentication
- Database migrations applied (37 tables)
- Portfolio creation and viewing
- Holdings page loads and displays holdings
- Trades page now works (fixed enum query issue)
- Tax & CGT PDF download works

**Fixes Applied**:

1. **TradeEventRepository.java** (`tradetracker-portfolio/.../repository/TradeEventRepository.java`):
   - Changed `t.tradeType.name = :type` to `t.tradeType = :type` (tradeType is an enum, not an entity)
   - Changed parameter type from `String` to `TradeEvent.TradeType`

2. **PortfolioService.java** (`tradetracker-portfolio/.../service/PortfolioService.java`):
   - Added `TradeEvent.TradeType.valueOf(tradeType)` conversion in `listTrades()` method

3. **SecurityConfig.java** (`tradetracker-security/.../SecurityConfig.java`):
   - Added `.cors(cors -> {})` to enable CORS filter for Spring Boot 4.0
   - Without this, the CorsFilter bean in InfrastructureConfig is not wired into SecurityFilterChain

4. **CgtPdfReportService.java** (`tradetracker-tax/.../CgtPdfReportService.java`):
   - Changed `buildParams()` return from immutable `Map.of()` to mutable `HashMap` (JasperReports modifies the map internally)
   - Removed Unicode characters (`\u00b7` middle dot, `\u2014` em-dash) from JasperReports expressions that caused syntax errors
   - Fixed expression syntax: `"$P{PORTFOLIO} + \" - FY \" + $P{FY}"` instead of literal Unicode

**Errors Fixed**:
- `org.hibernate.query.sqm.UnknownPathException: Could not interpret attribute 'name' of basic-valued path` (TradeEvent tradeType enum)
- `net.sf.jasperreports.engine.JRException: Syntax error on token "Invalid Character"` (Unicode in JasperReports)
- `java.lang.UnsupportedOperationException: ImmutableCollections.uoe` (Map.of() is immutable)

---

## STAGE 1 CHECKPOINT (2026-04-22)

**Status**: Application loads, database reset and migrations applied fresh.

**Working Features**:
- Frontend loads and renders
- Keycloak login/authentication works
- Backend API with JWT authentication
- Database migrations applied successfully (37 tables)
- Portfolio creation and viewing

**Frontend Changes**:
- `frontend/src/main.tsx` - Keycloak singleton fix
- `frontend/src/lib/apiClient.ts` - Token interceptor
- `frontend/src/App.tsx` - Removed infinite reload, provision with delay
- `frontend/src/components/layout/AppLayout.tsx` - Loading guards, custom isActive for nav
- `frontend/src/hooks/usePortfolios.ts` - Error handling, staleTime
- `frontend/src/pages/DashboardPage.tsx` - Added usePortfolios import

**Backend Changes**:
- `tradetracker-app/src/main/resources/application.yml` - JWK-set-uri only (no issuer)
- `tradetracker-app/src/main/java/.../InfrastructureConfig.java` - Changed to use jwk-set-uri
- Disabled @Cacheable on getHoldings() - caused Jackson deserialization issues

**Database Reset Required**:
```bash
docker compose stop backend
docker exec postgres psql -U tradetracker -d tradetracker -c "
DROP SCHEMA public CASCADE;
CREATE SCHEMA public;
CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\";
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS timescaledb;
"
docker compose up -d backend
```

---

## Historical Notes

## Current Status

### 2026-04-22 - Application Working!

The application now loads and most modules are working. Remaining fixes applied:

1. **Database reset** - Reset database completely with fresh migrations after bytea corruption issues
2. **Frontend routing** - Fixed AppLayout to guard against undefined portfolioId:
   - Added `isLoading` check to prevent navigation before portfolios loaded
   - Changed `navTo()` to return `'#'` if no activePortfolio
   - Added null check: `(portfolios ?? []).map(...)`
3. **usePortfolios hook** - Added `retry: false, throwOnError: false` to prevent query errors

### Keycloak / Backend Authentication

**Issue**: Backend JWT validation using JWK-set-uri only (no issuer check) because:
- Keycloak inside Docker returns `host.docker.internal:8081` in issuer
- Browser's JWT has `localhost:8081` as issuer
- Using `jwk-set-uri` bypasses issuer validation

**Current Config**:
```yaml
spring.security.oauth2.resourceserver.jwt:
  jwk-set-uri: http://keycloak:8080/realms/tradetracker/protocol/openid-connect/certs
```

### Database Issues Resolved

**bytea corruption**: Hibernate was reading some columns as BYTEA incorrectly. Root cause:
- Migrations V1-V7 in target/classes used OID type
- Migrations V8-V18 in src/main/resources used VARCHAR
- Hibernate cached OID metadata from old V1 migration

**Solution**: Reset database completely:
```bash
docker compose down backend
docker exec postgres psql -U tradetracker -d postgres -c "DROP DATABASE IF EXISTS tradetracker;"
docker exec postgres psql -U tradetracker -d postgres -c "CREATE DATABASE tradetracker;"
docker exec postgres psql -U tradetracker -d tradetracker -c "CREATE EXTENSION IF NOT EXISTS uuid-ossp;"
docker exec postgres psql -U tradetracker -d tradetracker -c "CREATE EXTENSION IF NOT EXISTS pg_trgm;"
docker compose up -d backend
```

---

### Keycloak Loading/CONNECTION_REFUSED

**Problem**: Browser shows ERR_CONNECTION_REFUSED for keycloak.mjs

**Solution Applied**:
1. Set `KC_HOSTNAME_STRICT: "false"` in docker-compose.yml
2. Restarted Keycloak
3. Now returns correct URLs

**Status**: Should be resolved after last Keycloak restart.

## Key Configuration Settings

### docker-compose.yml - Keycloak
```yaml
keycloak:
  environment:
    KC_HOSTNAME_STRICT: "false"
```

### realm-export.json
```json
{
  "sslRequired": "none",
  ...
}
```

## Past Fixes

### 2026-04-21 - Java Version
Changed from Java 25 to Java 21 because Spring Boot repackaging doesn't support Java 25.

### 2026-04-21 - Main Class Typo
Fixed `TradetrackerApplication` to `TradeTrackerApplication` in pom.xml.

### 2026-04-21 - Unnamed Variables
Java 21 doesn't support unnamed variables (`_`) in lambdas. Replaced with proper variable names.

### 2026-04-21 - Duplicate YAML Keys
Merged multiple `spring:` keys in application.yml.

### 2026-04-21 - Flyway Version Conflicts
Renamed migration files from V1/V2 to unique versions V4-V18.

### 2026-04-21 - Schema Validation
- Changed `ddl-auto: validate` to `ddl-auto: none`
- Entity columns use `columnDefinition = "char(3)"` instead of `length = 3`

### 2026-04-21 - Quartz Memory Store
Changed Quartz job-store-type from jdbc to memory to bypass PostgreSQL JDBC BYTEA compatibility issue.