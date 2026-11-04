package com.frescuratotal.erp.sales.model;

import com.frescuratotal.erp.inventory.model.Lot;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** One line of a sale: units taken from one specific lot. Rates are fractions (0.50 = 50%). */
public record SaleLine(int lotId, String lotNumber, int productId, String productName,
                       BigDecimal quantity, BigDecimal unitPrice, BigDecimal discountRate,
                       BigDecimal discountAmount, BigDecimal taxRate, BigDecimal taxAmount,
                       BigDecimal subtotal, BigDecimal lineTotal) {

    /** Builds a line and calculates its amounts. */
    public static SaleLine create(Lot lot, BigDecimal quantity, BigDecimal discountRate,
                                  BigDecimal taxRate) {
        BigDecimal subtotal = quantity.multiply(lot.getSalePrice()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal discount = subtotal.multiply(discountRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxable = subtotal.subtract(discount);
        BigDecimal tax = taxable.multiply(taxRate).setScale(2, RoundingMode.HALF_UP);

        return new SaleLine(lot.getId(), lot.getLotNumber(), lot.getProductId(), lot.getProductName(),
                quantity, lot.getSalePrice(), discountRate, discount, taxRate, tax,
                subtotal, taxable.add(tax));
    }
}
