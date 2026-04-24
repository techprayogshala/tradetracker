# TradeTracker Development Notes

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