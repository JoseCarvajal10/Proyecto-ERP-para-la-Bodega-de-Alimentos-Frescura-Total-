package com.frescuratotal.erp.sales.view;

import com.frescuratotal.erp.sales.model.Company;
import com.frescuratotal.erp.sales.model.Invoice;
import com.frescuratotal.erp.sales.model.Sale;
import com.frescuratotal.erp.sales.model.SaleLine;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import javax.imageio.ImageIO;

/**
 * Draws the visual electronic invoice as a PNG image filled with the real data of the sale.
 * The text of the invoice is in Spanish because it is a Colombian (DIAN) document.
 */
public class InvoiceRenderer {

    private static final int WIDTH = 820;
    private static final int MARGIN = 40;
    private static final Color GREEN = new Color(27, 94, 32);
    private static final Color LIGHT_GREY = new Color(238, 238, 238);

    private final NumberFormat money = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-CO"));

    public InvoiceRenderer() {
        money.setMaximumFractionDigits(2);
        money.setMinimumFractionDigits(2);
    }

    /** Creates the PNG file inside the given folder and returns its path. */
    public Path render(Sale sale, Invoice invoice, Company company, Path outputDir) throws IOException {
        System.setProperty("java.awt.headless", "true");
        int height = 650 + sale.getLines().size() * 28;
        BufferedImage image = new BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, WIDTH, height);

            int y = drawHeader(g, company, invoice);
            y = drawInvoiceData(g, sale, invoice, y);
            y = drawLines(g, sale, y);
            y = drawTotals(g, invoice, y);
            drawFooter(g, invoice, y);
        } finally {
            g.dispose();
        }

        Files.createDirectories(outputDir);
        Path file = outputDir.resolve(invoice.number() + ".png");
        ImageIO.write(image, "png", file.toFile());
        return file;
    }

    private int drawHeader(Graphics2D g, Company company, Invoice invoice) {
        g.setColor(GREEN);
        g.fillRect(0, 0, WIDTH, 124);
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 26));
        g.drawString(company.name(), MARGIN, 45);
        g.setFont(new Font("SansSerif", Font.PLAIN, 14));
        g.drawString("NIT: " + company.nit(), MARGIN, 68);
        g.drawString(company.address() + " - " + company.city(), MARGIN, 88);
        g.drawString("Tel: " + company.phone() + "  |  " + company.email(), MARGIN, 108);

        g.setFont(new Font("SansSerif", Font.BOLD, 16));
        drawRight(g, "FACTURA ELECTRÓNICA DE VENTA", WIDTH - MARGIN, 45);
        g.setFont(new Font("SansSerif", Font.BOLD, 22));
        drawRight(g, invoice.number(), WIDTH - MARGIN, 76);
        return 156;
    }

    private int drawInvoiceData(Graphics2D g, Sale sale, Invoice invoice, int startY) {
        int y = startY;
        DateTimeFormatter format = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        g.setColor(Color.BLACK);

        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        g.drawString("Datos de la factura", MARGIN, y);
        g.drawString("Datos del cliente", WIDTH / 2 + 10, y);
        g.setColor(GREEN);
        g.drawLine(MARGIN, y + 6, WIDTH - MARGIN, y + 6);

        g.setColor(Color.BLACK);
        g.setFont(new Font("SansSerif", Font.PLAIN, 13));
        y += 28;
        g.drawString("Fecha de emisión: " + invoice.issueDate().format(format), MARGIN, y);
        g.drawString("Cliente: " + cut(sale.getCustomer().name(), 34), WIDTH / 2 + 10, y);
        y += 22;
        g.drawString("Venta: " + sale.getNumber(), MARGIN, y);
        g.drawString("NIT/CC: " + sale.getCustomer().document(), WIDTH / 2 + 10, y);
        y += 22;
        g.drawString("Forma de pago: Contado", MARGIN, y);
        g.drawString("Tipo: " + sale.getCustomer().typeName(), WIDTH / 2 + 10, y);
        y += 22;
        g.drawString("Atendido por: " + sale.getUser(), MARGIN, y);
        g.drawString("Dirección: " + cut(sale.getCustomer().address(), 34), WIDTH / 2 + 10, y);
        return y + 40;
    }

    private int drawLines(Graphics2D g, Sale sale, int startY) {
        int y = startY;
        int[] right = {170, 400, 465, 560, 625, 685, WIDTH - MARGIN};

        g.setColor(GREEN);
        g.fillRect(MARGIN, y - 18, WIDTH - 2 * MARGIN, 26);
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        g.drawString("Lote", MARGIN + 6, y);
        g.drawString("Producto", 180, y);
        drawRight(g, "Cant.", right[2], y);
        drawRight(g, "V. unitario", right[3], y);
        drawRight(g, "Desc.", right[4], y);
        drawRight(g, "IVA", right[5], y);
        drawRight(g, "Total", right[6], y);

        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        int row = 0;
        for (SaleLine line : sale.getLines()) {
            y += 28;
            if (row++ % 2 == 0) {
                g.setColor(LIGHT_GREY);
                g.fillRect(MARGIN, y - 18, WIDTH - 2 * MARGIN, 28);
            }
            g.setColor(Color.BLACK);
            g.drawString(line.lotNumber().replace("LT-", ""), MARGIN + 6, y);
            g.drawString(cut(line.productName(), 24), 180, y);
            drawRight(g, line.quantity().stripTrailingZeros().toPlainString(), right[2], y);
            drawRight(g, money.format(line.unitPrice()), right[3], y);
            drawRight(g, percent(line.discountRate()), right[4], y);
            drawRight(g, percent(line.taxRate()), right[5], y);
            drawRight(g, money.format(line.lineTotal()), right[6], y);
        }
        return y + 50;
    }

    private int drawTotals(Graphics2D g, Invoice invoice, int startY) {
        int y = startY;
        int labelX = WIDTH - MARGIN - 260;
        g.setColor(Color.BLACK);
        g.setFont(new Font("SansSerif", Font.PLAIN, 14));

        g.drawString("Subtotal", labelX, y);
        drawRight(g, money.format(invoice.subtotal()), WIDTH - MARGIN, y);
        y += 24;
        g.drawString("Descuentos", labelX, y);
        drawRight(g, "-" + money.format(invoice.discount()), WIDTH - MARGIN, y);
        y += 24;
        g.drawString("IVA", labelX, y);
        drawRight(g, money.format(invoice.tax()), WIDTH - MARGIN, y);
        y += 14;

        g.setColor(GREEN);
        g.fillRect(labelX - 10, y, 270 + 10, 36);
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 16));
        g.drawString("TOTAL A PAGAR", labelX, y + 24);
        drawRight(g, money.format(invoice.total()), WIDTH - MARGIN - 6, y + 24);
        return y + 80;
    }

    private void drawFooter(Graphics2D g, Invoice invoice, int startY) throws IOException {
        int y = startY;
        g.setColor(Color.BLACK);
        g.setFont(new Font("SansSerif", Font.BOLD, 13));
        g.drawString("CUFE (simulado):", MARGIN, y);
        g.setFont(new Font("Monospaced", Font.PLAIN, 12));
        g.drawString(invoice.cufe().substring(0, 48), MARGIN, y + 22);
        g.drawString(invoice.cufe().substring(48), MARGIN, y + 40);

        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g.setColor(Color.DARK_GRAY);
        g.drawString("Resolución DIAN No. 18764000000000 (simulada) - Régimen: Responsable de IVA", MARGIN, y + 74);
        g.drawString("Representación gráfica de la factura electrónica de venta.", MARGIN, y + 92);
        g.drawString("Documento generado con fines académicos: el CUFE y el código QR son simulados.", MARGIN, y + 110);

        drawQr(g, invoice.qrLink(), WIDTH - MARGIN - 170, y - 20, 170);
    }

    /** Draws a real QR code (ZXing) that contains the invoice link. */
    private void drawQr(Graphics2D g, String content, int x, int y, int size) throws IOException {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0);
            int modules = matrix.getWidth();
            int cell = Math.max(2, size / modules);
            g.setColor(Color.BLACK);
            for (int row = 0; row < modules; row++) {
                for (int col = 0; col < modules; col++) {
                    if (matrix.get(col, row)) {
                        g.fillRect(x + col * cell, y + row * cell, cell, cell);
                    }
                }
            }
        } catch (WriterException e) {
            throw new IOException("Could not create the QR code", e);
        }
    }

    private void drawRight(Graphics2D g, String text, int rightX, int y) {
        FontMetrics metrics = g.getFontMetrics();
        g.drawString(text, rightX - metrics.stringWidth(text), y);
    }

    private String percent(BigDecimal rate) {
        return rate.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + "%";
    }

    private String cut(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
