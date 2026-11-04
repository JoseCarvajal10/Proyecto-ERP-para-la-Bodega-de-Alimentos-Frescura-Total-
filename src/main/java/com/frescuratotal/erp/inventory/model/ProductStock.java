package com.frescuratotal.erp.inventory.model;

import java.math.BigDecimal;

/** A product together with the quantity that can be sold right now. */
public record ProductStock(Product product, BigDecimal availableQuantity) {
}
