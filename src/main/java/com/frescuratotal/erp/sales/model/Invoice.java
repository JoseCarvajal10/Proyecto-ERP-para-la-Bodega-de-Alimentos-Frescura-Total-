package com.frescuratotal.erp.sales.model;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;

/** Electronic invoice of a sale. The CUFE and the QR link are simulated. */
public record Invoice(int id, int saleId, String number, String cufe, String qrLink,
                      LocalDateTime issueDate, BigDecimal subtotal, BigDecimal discount,
                      BigDecimal tax, BigDecimal total) {

    private static final String QR_BASE = "https://catalogo-vpfe.dian.gov.co/document/searchqr?documentkey=";

    /** Issues the invoice of a sale, building a simulated CUFE with SHA-384. */
    public static Invoice issue(Sale sale, Company company) {
        String number = String.format("FT-%06d", sale.getId());
        LocalDateTime issueDate = LocalDateTime.now();
        String source = number + issueDate + sale.getSubtotal() + sale.getTax() + sale.getTotal()
                + company.nit() + sale.getCustomer().document();
        String cufe = sha384(source);

        return new Invoice(0, sale.getId(), number, cufe, QR_BASE + cufe, issueDate,
                sale.getSubtotal(), sale.getDiscount(), sale.getTax(), sale.getTotal());
    }

    private static String sha384(String text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-384").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-384 is not available", e);
        }
    }
}
