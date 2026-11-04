package com.frescuratotal.erp.inventory.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One sale line that took products from a lot: used to know which customers received it. */
public record LotDelivery(String customerName, String saleNumber, BigDecimal quantity,
                          LocalDateTime saleDate, String saleStatus) {
}
