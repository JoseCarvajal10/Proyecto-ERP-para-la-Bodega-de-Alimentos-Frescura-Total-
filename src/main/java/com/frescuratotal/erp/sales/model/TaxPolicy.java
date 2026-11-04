package com.frescuratotal.erp.sales.model;

import java.math.BigDecimal;

/**
 * Simplified VAT rules: fresh food categories are not charged,
 * processed categories (beverages and canned goods) are charged 19%.
 */
public final class TaxPolicy {

    private static final int BEVERAGES = 6;
    private static final int CANNED = 7;
    private static final BigDecimal VAT = new BigDecimal("0.19");

    private TaxPolicy() {
    }

    public static BigDecimal rateFor(int categoryId) {
        return (categoryId == BEVERAGES || categoryId == CANNED) ? VAT : BigDecimal.ZERO;
    }
}
