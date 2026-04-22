package com.tradetracker.tax;

import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.design.*;
import net.sf.jasperreports.engine.type.*;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Generates an ATO CGT schedule PDF using the JasperReports programmatic API.
 * No .jrxml binary file — the design is built entirely in Java.
 *
 * Layout: A4 landscape. One row per disposal event, summary totals at the foot.
 */
@Service
public class CgtPdfReportService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String FONT  = "Helvetica";
    private static final Color  NAVY  = new Color(0x1e, 0x40, 0xaf);
    private static final Color  STRIPE= new Color(0xf9, 0xfa, 0xfb);
    private static final Color  AMBER = new Color(0x92, 0x40, 0x0e);

    // Column layout: name, x, width
    private static final Object[][] COLS = {
        {"disposalDate",    0,   72},
        {"ticker",         72,   52},
        {"quantity",       124,  48},
        {"proceeds",       172,  82},
        {"costBase",       254,  82},
        {"capitalGain",    336,  82},
        {"discountApplied",418,  50},
        {"assessableGain", 468,  82},
        {"acquisitionDate",550,  72},
        {"holdingDays",    622,  50},
    };
    private static final int PAGE_W = 762;
    private static final String[] COL_HEADERS =
        {"Disposal","Security","Qty","Proceeds","Cost Base","Capital Gain","Disc.","Assessable","Acquired","Days"};

    // ── Public API ────────────────────────────────────────────────────────────

    public byte[] generate(String portfolioName, int financialYear,
                           List<TaxReportService.CgtEventView> events,
                           AustralianCgtService.TaxYearCgtSummary summary) {
        try {
            JasperDesign design = buildDesign(financialYear);
            JasperReport  report = JasperCompileManager.compileReport(design);
            JasperPrint   print  = JasperFillManager.fillReport(
                report,
                buildParams(portfolioName, financialYear, summary),
                new JRBeanCollectionDataSource(toRows(events))
            );
            return JasperExportManager.exportReportToPdf(print);
        } catch (JRException e) {
            throw new ReportGenerationException("CGT PDF generation failed: " + e.getMessage(), e);
        }
    }

    // ── Design construction ───────────────────────────────────────────────────

    private JasperDesign buildDesign(int fy) throws JRException {
        JasperDesign d = new JasperDesign();
        d.setName("CGT_FY" + fy);
        d.setPageWidth(842); d.setPageHeight(595);   // A4 landscape
        d.setLeftMargin(40); d.setRightMargin(40);
        d.setTopMargin(36);  d.setBottomMargin(36);
        d.setColumnWidth(PAGE_W);

        addParams(d);
        addFields(d);
        addTitle(d);
        addColumnHeader(d);
        addDetail(d);
        addSummary(d);
        return d;
    }

    private void addParams(JasperDesign d) throws JRException {
        for (String n : List.of("PORTFOLIO","FY","GAINS","LOSSES","DISCOUNT","NET_CGT","CARRIED")) {
            JRDesignParameter p = new JRDesignParameter();
            p.setName(n); p.setValueClass(String.class);
            d.addParameter(p);
        }
    }

    private void addFields(JasperDesign d) throws JRException {
        for (Object[] col : COLS) {
            JRDesignField f = new JRDesignField();
            f.setName((String) col[0]); f.setValueClass(String.class);
            d.addField(f);
        }
    }

    // ── Title band ────────────────────────────────────────────────────────────

    private void addTitle(JasperDesign d) {
        JRDesignBand b = band(82);

        // Blue header bar
        b.addElement(rect(0, 0, PAGE_W, 34, NAVY, NAVY));

        // Title text (static)
        b.addElement(staticText("Capital Gains Tax Report", 8, 6, 500, 22, 14, true, Color.WHITE, null));

        // Portfolio name + FY from parameters — must use JRDesignTextField, not staticText
        // JRDesignStaticText renders its text literally: "$P{PORTFOLIO}" would print as-is.
        b.addElement(paramField("$P{PORTFOLIO} \u00b7 FY $P{FY}", 8, 28, 380, 12, 8, false, Color.WHITE));
        b.addElement(staticText(LocalDate.now().format(DATE_FMT), 600, 28, 162, 12, 8, false, Color.WHITE, null));

        // Disclaimer
        b.addElement(staticText(
            "INDICATIVE ONLY \u2014 verify with a registered tax agent before lodging your return",
            0, 46, PAGE_W, 14, 7, false, AMBER, new Color(0xff, 0xf7, 0xed)));

        b.addElement(staticText("Page ", 630, 64, 40, 14, 8, false, Color.GRAY, null));
        b.addElement(variableField("$V{PAGE_NUMBER}", 670, 64, 92, 14, 8, false, Color.GRAY));

        d.setTitle(b);
    }

    // ── Column header ──────────────────────────────────────────────────────────

    private void addColumnHeader(JasperDesign d) {
        JRDesignBand b = band(20);
        for (int i = 0; i < COLS.length; i++) {
            int x = (int) COLS[i][1], w = (int) COLS[i][2];
            JRDesignStaticText t = staticText(COL_HEADERS[i], x, 1, w, 16, 8, true, Color.WHITE, NAVY);
            t.setMode(ModeEnum.OPAQUE);
            if (i >= 2) t.setHorizontalTextAlign(HorizontalTextAlignEnum.RIGHT);
            b.addElement(t);
        }
        d.setColumnHeader(b);
    }

    // ── Detail band ───────────────────────────────────────────────────────────

    private void addDetail(JasperDesign d) throws JRException {
        JRDesignBand b = band(15);

        // Alternating stripe background
        JRDesignRectangle stripe = new JRDesignRectangle();
        stripe.setX(0); stripe.setY(0); stripe.setWidth(PAGE_W); stripe.setHeight(15);
        stripe.setMode(ModeEnum.OPAQUE);
        stripe.setBackcolor(new java.awt.Color(0xf9,0xfa,0xfb));
        b.addElement(stripe);

        // Data cells
        for (int i = 0; i < COLS.length; i++) {
            int x = (int) COLS[i][1], w = (int) COLS[i][2];
            String fname = (String) COLS[i][0];
            JRDesignTextField tf = new JRDesignTextField();
            tf.setX(x); tf.setY(1); tf.setWidth(w); tf.setHeight(13);
            tf.setFontName(FONT); tf.setFontSize(8f);
            JRDesignExpression expr = new JRDesignExpression();
            expr.setText("$F{" + fname + "}");
            tf.setExpression(expr);
            if (i >= 2) tf.setHorizontalTextAlign(HorizontalTextAlignEnum.RIGHT);
            // Colour negative capital gains red
            if (fname.equals("capitalGain") || fname.equals("assessableGain")) {
                tf.setForecolor(java.awt.Color.RED);
            }
            b.addElement(tf);
        }

        ((JRDesignSection) d.getDetailSection()).addBand(b);
    }

    // ── Summary band ──────────────────────────────────────────────────────────

    private void addSummary(JasperDesign d) {
        JRDesignBand b = band(130);

        // Divider
        b.addElement(line(0, 10, PAGE_W));

        int y = 20;
        b.addElement(summaryPair("Total gross capital gains",       "$P{GAINS}",    y, false)); y += 18;
        b.addElement(summaryPair("Less: current year capital losses","($P{LOSSES})", y, false)); y += 18;
        b.addElement(summaryPair("Less: 50% CGT discount",          "($P{DISCOUNT})",y, false)); y += 18;
        b.addElement(line(330, y+2, 432));
        y += 8;
        b.addElement(summaryPair("Net assessable capital gain",     "$P{NET_CGT}",  y, true));  y += 22;
        b.addElement(summaryPair("Losses carried forward",          "$P{CARRIED}",  y, false));

        // Footnote
        b.addElement(staticText(
            "Source: TradeTracker  ·  All amounts in AUD  ·  Australian financial year 1 July – 30 June",
            0, 115, PAGE_W, 12, 7, false, Color.GRAY, null));

        d.setSummary(b);
    }

    // ── Component helpers ─────────────────────────────────────────────────────

    private JRDesignBand band(int h) {
        JRDesignBand b = new JRDesignBand(); b.setHeight(h); return b;
    }

    private JRDesignStaticText staticText(String text, int x, int y, int w, int h,
                                           int size, boolean bold,
                                           Color fg, Color bg) {
        JRDesignStaticText t = new JRDesignStaticText();
        t.setText(text); t.setX(x); t.setY(y); t.setWidth(w); t.setHeight(h);
        t.setFontName(FONT); t.setFontSize((float) size); t.setBold(bold);
        if (fg != null) t.setForecolor(fg);
        if (bg != null) { t.setBackcolor(bg); t.setMode(ModeEnum.OPAQUE); }
        return t;
    }

    /** Creates a text field whose content is a JasperReports expression (e.g. "$P{NAME}"). */
    private JRDesignTextField paramField(String expression, int x, int y, int w, int h,
                                          int size, boolean bold, Color fg) {
        JRDesignTextField tf = new JRDesignTextField();
        tf.setX(x); tf.setY(y); tf.setWidth(w); tf.setHeight(h);
        tf.setFontName(FONT); tf.setFontSize((float) size); tf.setBold(bold);
        if (fg != null) tf.setForecolor(fg);
        JRDesignExpression expr = new JRDesignExpression();
        expr.setText(expression);
        tf.setExpression(expr);
        return tf;
    }

    /** Creates a text field for a JasperReports variable expression. */
    private JRDesignTextField variableField(String expression, int x, int y, int w, int h,
                                             int size, boolean bold, Color fg) {
        return paramField(expression, x, y, w, h, size, bold, fg);
    }

    private JRDesignRectangle rect(int x, int y, int w, int h, Color fg, Color bg) {
        JRDesignRectangle r = new JRDesignRectangle();
        r.setX(x); r.setY(y); r.setWidth(w); r.setHeight(h);
        if (fg != null) r.setForecolor(fg);
        if (bg != null) { r.setBackcolor(bg); r.setMode(ModeEnum.OPAQUE); }
        return r;
    }

    private JRDesignLine line(int x, int y, int w) {
        JRDesignLine l = new JRDesignLine();
        l.setX(x); l.setY(y); l.setWidth(w); l.setHeight(1);
        l.setForecolor(new Color(0xe5, 0xe7, 0xeb));
        return l;
    }

    /**
     * Renders a label + value pair in the summary band.
     * Label on the left half, value right-aligned on the right half.
     */
    private JRDesignFrame summaryPair(String label, String paramExpr, int y, boolean bold) {
        JRDesignFrame frame = new JRDesignFrame();
        frame.setX(260); frame.setY(y); frame.setWidth(502); frame.setHeight(16);
        frame.setMode(ModeEnum.TRANSPARENT);

        // Label
        JRDesignStaticText lbl = staticText(label, 0, 1, 310, 14, 9, bold, Color.DARK_GRAY, null);
        frame.addElement(lbl);

        // Value (from parameter)
        JRDesignTextField val = new JRDesignTextField();
        val.setX(320); val.setY(1); val.setWidth(182); val.setHeight(14);
        val.setFontName(FONT); val.setFontSize(9f); val.setBold(bold);
        val.setHorizontalTextAlign(HorizontalTextAlignEnum.RIGHT);
        JRDesignExpression expr = new JRDesignExpression();
        expr.setText(paramExpr);
        val.setExpression(expr);
        frame.addElement(val);

        return frame;
    }

    // ── Parameter map ─────────────────────────────────────────────────────────

    private Map<String, Object> buildParams(String name, int fy,
                                             AustralianCgtService.TaxYearCgtSummary s) {
        BigDecimal discount = s.totalGrossGains()
            .subtract(s.totalCurrentYearLosses())
            .subtract(s.netAssessableCgt())
            .max(BigDecimal.ZERO);

        return Map.of(
            "PORTFOLIO", name,
            "FY",        (fy - 1) + "–" + String.valueOf(fy).substring(2),
            "GAINS",     aud(s.totalGrossGains()),
            "LOSSES",    aud(s.totalCurrentYearLosses()),
            "DISCOUNT",  aud(discount),
            "NET_CGT",   aud(s.netAssessableCgt()),
            "CARRIED",   aud(s.lossesCarriedForward())
        );
    }

    // ── Data rows ─────────────────────────────────────────────────────────────

    private List<Map<String, String>> toRows(List<TaxReportService.CgtEventView> events) {
        return events.stream().map(e -> {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("disposalDate",   fmt(e.disposalDate()));
            row.put("ticker",         e.ticker());
            row.put("quantity",       e.quantity().stripTrailingZeros().toPlainString());
            row.put("proceeds",       aud(e.proceeds()));
            row.put("costBase",       aud(e.costBase()));
            row.put("capitalGain",    audSigned(e.capitalGain()));
            row.put("discountApplied",e.discountApplied() ? "50%" : "—");
            row.put("assessableGain", audSigned(e.assessableGain()));
            row.put("acquisitionDate",fmt(e.acquisitionDate()));
            row.put("holdingDays",    String.valueOf(e.holdingDays()));
            return row;
        }).toList();
    }

    private String fmt(LocalDate d)     { return d == null ? "" : d.format(DATE_FMT); }
    private String aud(BigDecimal v)    { return v == null ? "—" : String.format("$%,.2f", v.abs()); }
    private String audSigned(BigDecimal v) {
        if (v == null) return "—";
        return v.compareTo(BigDecimal.ZERO) < 0
            ? String.format("-$%,.2f", v.abs())
            : String.format("$%,.2f", v);
    }

    public static class ReportGenerationException extends RuntimeException {
        public ReportGenerationException(String msg, Throwable cause) { super(msg, cause); }
    }
}
