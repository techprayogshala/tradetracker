package com.tradetracker.notification.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Renders HTML email templates for each notification event type.
 * Uses simple string interpolation — replace with Thymeleaf or Freemarker
 * when templates become more complex.
 */
@Service
public class EmailTemplateService {

    public EmailContent render(String eventType, String payloadJson) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = new com.fasterxml.jackson.databind.ObjectMapper()
                .readValue(payloadJson, Map.class);
            return switch (eventType) {
                case "TRADE_CONFIRMED"  -> tradeConfirmed(payload);
                case "DAILY_SUMMARY"    -> dailySummary(payload);
                case "CGT_REMINDER"     -> cgtReminder(payload);
                case "PRICE_ALERT"      -> priceAlert(payload);
                default                 -> generic(eventType, payload);
            };
        } catch (Exception e) {
            return new EmailContent("TradeTracker Notification", "<p>An event occurred in your portfolio.</p>");
        }
    }

    // ── Templates ─────────────────────────────────────────────────────────────

    private EmailContent tradeConfirmed(Map<String, Object> p) {
        String ticker    = str(p, "ticker");
        String tradeType = str(p, "tradeType");
        String quantity  = str(p, "quantity");
        String price     = str(p, "price");
        String total     = str(p, "totalCost");
        String date      = str(p, "tradeDate");

        boolean isBuy = "BUY".equals(tradeType);
        String colour = isBuy ? "#059669" : "#dc2626";
        String verb   = isBuy ? "Purchased" : "Sold";

        return new EmailContent(
            "%s %s — Trade Confirmed".formatted(verb, ticker),
            """
            %s
            <div style="max-width:560px;margin:0 auto;font-family:system-ui,sans-serif">
              <div style="background:#1e40af;color:white;padding:24px 32px;border-radius:12px 12px 0 0">
                <h1 style="margin:0;font-size:20px">TradeTracker</h1>
              </div>
              <div style="padding:32px;background:white;border:1px solid #e5e7eb;border-top:none;border-radius:0 0 12px 12px">
                <h2 style="margin-top:0;color:#111827">Trade Confirmed</h2>
                <div style="background:#f9fafb;border-radius:8px;padding:20px;margin:16px 0">
                  <table style="width:100%%;border-collapse:collapse">
                    <tr><td style="color:#6b7280;padding:4px 0">Type</td>
                        <td style="text-align:right;font-weight:600;color:%s">%s</td></tr>
                    <tr><td style="color:#6b7280;padding:4px 0">Security</td>
                        <td style="text-align:right;font-weight:600">%s</td></tr>
                    <tr><td style="color:#6b7280;padding:4px 0">Quantity</td>
                        <td style="text-align:right">%s units</td></tr>
                    <tr><td style="color:#6b7280;padding:4px 0">Price</td>
                        <td style="text-align:right">$%s</td></tr>
                    <tr><td style="color:#6b7280;padding:4px 0;border-top:1px solid #e5e7eb">Total</td>
                        <td style="text-align:right;font-weight:700;font-size:18px;border-top:1px solid #e5e7eb">$%s</td></tr>
                  </table>
                </div>
                <p style="color:#6b7280;font-size:14px">Trade date: %s</p>
                <p style="color:#9ca3af;font-size:12px;margin-top:24px">
                  This is a confirmation only. TradeTracker is not financial advice.
                </p>
              </div>
            </div>
            """.formatted(HTML_HEAD, colour, tradeType, ticker, quantity, price, total, date)
        );
    }

    private EmailContent dailySummary(Map<String, Object> p) {
        String portfolioName = str(p, "portfolioName");
        String totalValue    = str(p, "totalValue");
        String dayChange     = str(p, "dayChange");
        String dayChangePct  = str(p, "dayChangePct");
        boolean positive     = !dayChange.startsWith("-");
        String colour        = positive ? "#059669" : "#dc2626";
        String sign          = positive ? "+" : "";

        return new EmailContent(
            "Daily Summary — %s".formatted(portfolioName),
            """
            %s
            <div style="max-width:560px;margin:0 auto;font-family:system-ui,sans-serif">
              <div style="background:#1e40af;color:white;padding:24px 32px;border-radius:12px 12px 0 0">
                <h1 style="margin:0;font-size:20px">TradeTracker — Daily Summary</h1>
              </div>
              <div style="padding:32px;background:white;border:1px solid #e5e7eb;border-top:none;border-radius:0 0 12px 12px">
                <h2 style="margin-top:0">%s</h2>
                <div style="display:flex;gap:24px;margin:16px 0">
                  <div style="flex:1;background:#f9fafb;padding:16px;border-radius:8px">
                    <div style="color:#6b7280;font-size:12px;text-transform:uppercase">Portfolio Value</div>
                    <div style="font-size:24px;font-weight:700;color:#111827">$%s</div>
                  </div>
                  <div style="flex:1;background:#f9fafb;padding:16px;border-radius:8px">
                    <div style="color:#6b7280;font-size:12px;text-transform:uppercase">Day Change</div>
                    <div style="font-size:24px;font-weight:700;color:%s">%s$%s (%s%%)</div>
                  </div>
                </div>
              </div>
            </div>
            """.formatted(HTML_HEAD, portfolioName, totalValue, colour, sign, dayChange, dayChangePct)
        );
    }

    private EmailContent cgtReminder(Map<String, Object> p) {
        String ticker   = str(p, "ticker");
        String date     = str(p, "discountEligibleDate");
        return new EmailContent(
            "CGT Discount Reminder — %s".formatted(ticker),
            """
            %s
            <div style="max-width:560px;margin:0 auto;font-family:system-ui,sans-serif;padding:32px">
              <h2>50%% CGT Discount Approaching</h2>
              <p>Your holding in <strong>%s</strong> will become eligible for the
              50%% CGT discount on <strong>%s</strong>.</p>
              <p>Selling before this date will result in higher tax on any capital gain.</p>
              <p style="color:#9ca3af;font-size:12px">This is a reminder only, not tax advice.</p>
            </div>
            """.formatted(HTML_HEAD, ticker, date)
        );
    }

    private EmailContent priceAlert(Map<String, Object> p) {
        return new EmailContent("Price Alert — " + str(p, "ticker"),
            HTML_HEAD + "<p>Price alert triggered for " + str(p, "ticker") + ".</p>");
    }

    private EmailContent generic(String eventType, Map<String, Object> p) {
        return new EmailContent("TradeTracker — " + eventType,
            HTML_HEAD + "<p>Event: " + eventType + "</p>");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? "" : v.toString();
    }

    private static final String HTML_HEAD = """
        <!DOCTYPE html><html><head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        </head><body style="margin:0;padding:16px;background:#f3f4f6">
        """;
}
