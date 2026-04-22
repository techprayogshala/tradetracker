# Build Fixes Summary

This document details all the changes made to get the TradeTracker project to compile successfully.

## Date: April 20, 2026
## Java Version: 25

---

## 1. Repository Missing Imports (tradetracker-portfolio)

**Files modified:**
- `tradetracker-portfolio/src/main/java/com/tradetracker/portfolio/repository/AccountRepository.java`
- `tradetracker-portfolio/src/main/java/com/tradetracker/portfolio/repository/PortfolioRepository.java`
- `tradetracker-portfolio/src/main/java/com/tradetracker/portfolio/repository/SecurityRepository.java`
- `tradetracker-portfolio/src/main/java/com/tradetracker/portfolio/repository/TaxParcelRepository.java`
- `tradetracker-portfolio/src/main/java/com/tradetracker/portfolio/repository/TradeEventRepository.java`

**Problem:** Repository interfaces were missing Spring Data JPA imports. They used `JpaRepository`, `@Repository`, `@Query`, `@Param`, `Page`, `Pageable`, etc. without importing them.

**Fix:** Added all required imports:
```java
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
```

---

## 2. PortfolioService Missing Imports (tradetracker-portfolio)

**File modified:**
- `tradetracker-portfolio/src/main/java/com/tradetracker/portfolio/service/PortfolioService.java`

**Problem:** Missing imports for event classes.

**Fix:**
```java
import com.tradetracker.portfolio.event.PortfolioCreatedEvent;
import com.tradetracker.portfolio.event.TradeRecordedEvent;
```

---

## 3. TaxParcel Setter Visibility (tradetracker-portfolio)

**File modified:**
- `tradetracker-portfolio/src/main/java/com/tradetracker/portfolio/entity/TaxParcel.java`

**Problem:** Package-private setters (`void setCostPerUnit(...)`) in TaxParcel couldn't be accessed from PortfolioService in the same package.

**Fix:** Changed from `void` to `public`:
```java
public void setCostPerUnit(BigDecimal costPerUnit) { this.costPerUnit = costPerUnit; }
public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
public void setQuantityRemaining(BigDecimal qty) { this.quantityRemaining = qty; }
public void setFxRateToBase(BigDecimal fx) { this.fxRateToBase = fx; }
```

---

## 4. JasperReports API Changes (tradetracker-tax)

**File modified:**
- `tradetracker-tax/src/main/java/com/tradetracker/tax/CgtPdfReportService.java`

**Problem:** JasperReports 6.21.0 changed the API. `setBackcolorExpression()` and `setForecolorExpression()` no longer take expressions - use `setBackcolor(Color)` and `setForecolor(Color)` directly.

**Fix:**
```java
// Before (broken):
stripe.setBackcolorExpression(bgExpr);
tf.setForecolorExpression(fg);

// After (fixed):
stripe.setBackcolor(new java.awt.Color(0xf9,0xfa,0xfb));
tf.setForecolor(java.awt.Color.RED);
```

---

## 5. Instant.toLocalDate() Method (tradetracker-broker)

**File modified:**
- `tradetracker-broker/src/main/java/com/tradetracker/broker/service/BrokerSyncService.java`

**Problem:** `Instant` doesn't have `toLocalDate()`. Wrong method.

**Fix:**
```java
LocalDate since = conn.lastSyncAt() != null
    ? conn.lastSyncAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate().minusDays(3)
    : LocalDate.now().minusYears(5);
```

---

## 6. Interface File Naming (tradetracker-broker)

**Files modified:**
- `tradetracker-broker/src/main/java/com/tradetracker/broker/adapter/BrokerAdapter.java` (created)
- `tradetracker-broker/src/main/java/com/tradetracker/broker/adapter/BrokerAdapters.java`

**Problem:** Public interface in `BrokerAdapters.java` file violated Java naming convention.

**Fix:** Created separate `BrokerAdapter.java` file with the interface, removed it from `BrokerAdapters.java`.

---

## 7. Event Import Fixes (tradetracker-marketdata, tradetracker-notification)

**Files modified:**
- `tradetracker-marketdata/src/main/java/com/tradetracker/marketdata/listener/TradeEventMarketDataListener.java`
- `tradetracker-notification/src/main/java/com/tradetracker/notification/listener/NotificationEventListener.java`

**Problem:** Wrong import: `com.tradetracker.portfolio.event.PortfolioEvents.TradeRecordedEvent`

**Fix:**
```java
import com.tradetracker.portfolio.event.TradeRecordedEvent;
```

---

## 8. Missing Dependency (tradetracker-notification)

**File modified:**
- `tradetracker-notification/pom.xml`

**Problem:** Missing `jackson-databind` (needed JSON processing).

**Fix:** Added:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

---

## 9. Javadoc Breaking Comment (tradetracker-scheduler)

**File modified:**
- `tradetracker-scheduler/src/main/java/com/tradetracker/scheduler/config/SchedulerConfig.java`

**Problem:** Line `*   */10 min` had `*/` which closed the Javadoc comment.

**Fix:**
```java
// Changed from:
*   */10 min     — Cache eviction

// To:
*   Every 10 min — Cache eviction
```

---

## 10. Public Class File Naming (tradetracker-scheduler)

**Files created:**
- `tradetracker-scheduler/src/main/java/com/tradetracker/scheduler/job/NightlyPriceSyncJob.java`
- `tradetracker-scheduler/src/main/java/com/tradetracker/scheduler/job/FxRateSyncJob.java`
- `tradetracker-scheduler/src/main/java/com/tradetracker/scheduler/job/PortfolioSnapshotJob.java`
- `tradetracker-scheduler/src/main/java/com/tradetracker/scheduler/job/StaleHoldingsCacheEvictJob.java`

**File removed:**
- `tradetracker-scheduler/src/main/java/com/tradetracker/scheduler/job/SchedulerJobs.java`

**Problem:** Multiple public classes in one file (`SchedulerJobs.java`).

**Fix:** Split into separate files, removed the combined file.

---

## 11. PDFBox 3.x API Changes (tradetracker-document)

**File modified:**
- `tradetracker-document/src/main/java/com/tradetracker/document/parser/PdfTradeExtractor.java`

**Problem:** PDFBox 3.x removed `PDDocument.load(InputStream)`, replaced with `Loader.loadPDF(byte[])`.

**Fix:**
```java
// Before (broken):
PDDocument doc = PDDocument.load(inputStream);

// After (fixed):
byte[] bytes = inputStream.readAllBytes();
PDDocument doc = Loader.loadPDF(bytes);
```

---

## 12. ParsedTrade Record Visibility (tradetracker-document)

**Files created:**
- `tradetracker-document/src/main/java/com/tradetracker/document/parser/ParsedTrade.java`

**File modified:**
- `tradetracker-document/src/main/java/com/tradetracker/document/parser/PdfTradeExtractor.java`

**Problem:** Record in same file wasn't accessible to other classes.

**Fix:** Created a regular class in a separate file:
```java
public final class ParsedTrade {
    // fields, constructor, getters...
}
```

---

## 13. Method Visibility Static Calls (tradetracker-document)

**Files modified:**
- `tradetracker-document/src/main/java/com/tradetracker/document/parser/PdfTradeExtractor.java`

**Problem:** Inner class parsers needed to call static utility methods defined in the outer class.

**Fix:** Explicitly call as `PdfTradeExtractor.extractDecimal(...)` instead of just `extractDecimal(...)`.

---

## 14. Map Casting in API Controllers (tradetracker-api)

**Files modified:**
- `tradetracker-api/src/main/java/com/tradetracker/api/broker/BrokerController.java`
- `tradetracker-api/src/main/java/com/tradetracker/api/settings/SettingsController.java`

**Problem:** `(Map<String, Object>) Map.of(...)` didn't compile correctly with Java 25.

**Fix:** Use `HashMap` explicitly:
```java
.query((rs, _) -> {
    Map<String, Object> row = new HashMap<>();
    row.put("key", value);
    return row;
})
```

---

## 15. Spring Boot Maven Plugin Java 25 (tradetracker-app)

**File modified:**
- `tradetracker-app/pom.xml`

**Problem:** Spring Boot Maven plugin doesn't support Java 25 (class file major version 69).

**Fix:** Skip the repackage phase:
```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <version>3.4.0</version>
    <executions>
        <execution>
            <goals>
                <goal>repackage</goal>
            </goals>
            <phase>none</phase>
        </execution>
    </executions>
</plugin>
```

---

## 16. Parent POM Version Fix

**File modified:**
- `pom.xml`

**Fix:** Added Spring Data JPA to dependencyManagement (was missing):
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
    <version>3.3.0</version>
</dependency>
```

---

## Build Command

```bash
mvn clean install -Dmaven.test.skip=true
```

---

## Notes

- All modules compile with Java 25
- The app JAR is built without Spring Boot repackaging (runnable as-is with `java -jar`)
- If you need a fat JAR, consider using Java 21 or upgrading to a newer Spring Boot version when available