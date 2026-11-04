package com.frescuratotal.erp.sales.model;

/** What a confirmed sale produces: the sale itself and its invoice. */
public record SaleResult(Sale sale, Invoice invoice) {
}
