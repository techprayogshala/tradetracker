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
import java.util.stream.Collectors;

/**
 * Generates an ATO CGT schedule PDF using the JasperReports programmatic API.
 * No .jrxml binary file — the design is built entirely in Java.
 *
 * Layout: A4 landscape. One row per disposal event, summary totals at the foot.
 */
@Service
public class CgtPdfReportService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String FONT  = "SansSerif";
private static final Color NAVY  = new Color(0x1e, 0x40, 0xaf);
    private static final Color AMBER = new Color(0x92, 0x40, 0x0e);
    private static final Color BORDER = new Color(0xe5, 0xe7, 0xea);

    // Column layout: name, x, width
    private static final Object[][] COLS = {
        {"disposalDate",    10,   80},
        {"ticker",        100,   60},
        {"quantity",      170,   60},
        {"proceeds",      240,   90},
        {"costBase",      340,   90},
        {"capitalGain",   440,   90},
        {"discountApplied",540,  50},
        {"assessableGain",600,   90},
    };
    private static final int PAGE_W = 720;
    private static final String[] COL_HEADERS =
        {"Disposal Date","Security","Qty","Proceeds","Cost Base","Capital Gain","Disc","Assessable"};

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
        d.setLeftMargin(30); d.setRightMargin(30);
        d.setTopMargin(25);  d.setBottomMargin(25);
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
        JRDesignBand b = band(50);

        // Title text
        b.addElement(staticText("Capital Gains Tax Schedule", 10, 5, 300, 20, 14, true, NAVY, null));

        // Portfolio name + FY using a simple parameterized field
        JRDesignTextField ptf = new JRDesignTextField();
        ptf.setX(10); ptf.setY(25); ptf.setWidth(350); ptf.setHeight(14);
        ptf.setFontName(FONT); ptf.setFontSize(10f);
        ptf.setForecolor(Color.DARK_GRAY);
        JRDesignExpression expr = new JRDesignExpression();
        expr.setText("$P{PORTFOLIO} + \" - FY \" + $P{FY}");
        ptf.setExpression(expr);
        b.addElement(ptf);
        
        // Date
        b.addElement(staticText(LocalDate.now().format(DATE_FMT), 620, 5, 100, 14, 9, false, Color.GRAY, null));

        // Disclaimer
        b.addElement(staticText(
            "INDICATIVE ONLY - Verify with a registered tax agent before lodging",
            10, 38, 500, 10, 8, false, AMBER, null));

        d.setTitle(b);
    }

    // ── Column header ──────────────────────────────────────────────────────────

    private void addColumnHeader(JasperDesign d) {
        JRDesignBand b = band(22);
        
        // Header background with border
        JRDesignRectangle headerBg = new JRDesignRectangle();
        headerBg.setX(0); headerBg.setY(0); headerBg.setWidth(PAGE_W); headerBg.setHeight(20);
        headerBg.setMode(ModeEnum.OPAQUE);
        headerBg.setBackcolor(NAVY);
        b.addElement(headerBg);
        
        for (int i = 0; i < COLS.length; i++) {
            int x = (int) COLS[i][1], w = (int) COLS[i][2];
            JRDesignStaticText t = staticText(COL_HEADERS[i], x, 3, w, 16, 9, true, Color.WHITE, null);
            t.setMode(ModeEnum.TRANSPARENT);
            if (i >= 2) t.setHorizontalTextAlign(HorizontalTextAlignEnum.RIGHT);
            b.addElement(t);
        }
        
        // Bottom border
        JRDesignLine headerLine = new JRDesignLine();
        headerLine.setX(0); headerLine.setY(20); headerLine.setWidth(PAGE_W); headerLine.setHeight(1);
        headerLine.setForecolor(BORDER);
        b.addElement(headerLine);
        
        d.setColumnHeader(b);
    }

    // ── Detail band ───────────────────────────────────────────────────────────

    private void addDetail(JasperDesign d) throws JRException {
        JRDesignBand b = band(18);
        
        // Border line for row
        JRDesignLine rowLine = new JRDesignLine();
        rowLine.setX(0); rowLine.setY(16); rowLine.setWidth(PAGE_W); rowLine.setHeight(1);
        rowLine.setForecolor(BORDER);
        b.addElement(rowLine);
        
        // Data cells
        for (int i = 0; i < COLS.length; i++) {
            int x = (int) COLS[i][1], w = (int) COLS[i][2];
            String fname = (String) COLS[i][0];
            JRDesignTextField tf = new JRDesignTextField();
            tf.setX(x); tf.setY(2); tf.setWidth(w); tf.setHeight(14);
            tf.setFontName(FONT); tf.setFontSize(9f);
            JRDesignExpression expr = new JRDesignExpression();
            expr.setText("$F{" + fname + "}");
            tf.setExpression(expr);
            if (i >= 2) tf.setHorizontalTextAlign(HorizontalTextAlignEnum.RIGHT);
            if (fname.equals("capitalGain") || fname.equals("assessableGain")) {
                tf.setForecolor(java.awt.Color.RED);
            }
            b.addElement(tf);
        }

        ((JRDesignSection) d.getDetailSection()).addBand(b);
    }

    // ── Summary band ──────────────────────────────────────────────────────────

    private void addSummary(JasperDesign d) {
        JRDesignBand b = band(100);

        // Divider
        b.addElement(line(0, 0, PAGE_W));

        int y = 15;
        b.addElement(summaryPair("Total gross capital gains",       "$P{GAINS}",    y, true)); y += 14;
        b.addElement(summaryPair("Less: current year capital losses","($P{LOSSES})", y, true)); y += 14;
        b.addElement(summaryPair("Less: 50% CGT discount",          "($P{DISCOUNT})",y, true)); y += 14;
        b.addElement(line(300, y, 420)); y += 6;
        b.addElement(summaryPair("Net assessable capital gain",     "$P{NET_CGT}",  y, true));  y += 14;
        b.addElement(summaryPair("Losses carried forward",       "$P{CARRIED}",  y, false));

        // Footnote
        b.addElement(staticText(
            "TradeTracker - Australian financial year 1 July - 30 June",
            10, 85, PAGE_W, 10, 8, false, Color.GRAY, null));

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
     */
    private JRDesignFrame summaryPair(String label, String paramExpr, int y, boolean bold) {
        JRDesignFrame frame = new JRDesignFrame();
        frame.setX(300); frame.setY(y); frame.setWidth(420); frame.setHeight(14);
        frame.setMode(ModeEnum.TRANSPARENT);

        // Label
        JRDesignStaticText lbl = staticText(label, 0, 0, 210, 14, 9, bold, Color.DARK_GRAY, null);
        frame.addElement(lbl);

        // Value (from parameter)
        JRDesignTextField val = new JRDesignTextField();
        val.setX(220); val.setY(0); val.setWidth(200); val.setHeight(14);
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

        Map<String, Object> params = new HashMap<>();
        params.put("PORTFOLIO", name);
        params.put("FY", (fy - 1) + "-" + String.valueOf(fy).substring(2));
        params.put("GAINS", aud(s.totalGrossGains()));
        params.put("LOSSES", aud(s.totalCurrentYearLosses()));
        params.put("DISCOUNT", aud(discount));
        params.put("NET_CGT", aud(s.netAssessableCgt()));
        params.put("CARRIED", aud(s.lossesCarriedForward()));
        return params;
    }

    // ── Data rows ─────────────────────────────────────────────────────────────

    private List<Map<String, String>> toRows(List<TaxReportService.CgtEventView> events) {
        return events.stream().map(e -> {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("disposalDate",   fmt(e.disposalDate()));
            row.put("ticker",       e.ticker());
            row.put("quantity",     e.quantity().stripTrailingZeros().toPlainString());
            row.put("proceeds",    aud(e.proceeds()));
            row.put("costBase",   aud(e.costBase()));
            row.put("capitalGain",audSigned(e.capitalGain()));
            row.put("discountApplied",e.discountApplied() ? "50%" : "—");
            row.put("assessableGain",audSigned(e.assessableGain()));
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
