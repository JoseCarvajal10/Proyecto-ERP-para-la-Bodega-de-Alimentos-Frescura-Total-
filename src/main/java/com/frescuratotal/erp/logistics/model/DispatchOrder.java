package com.frescuratotal.erp.logistics.model;

import java.math.BigDecimal;

/** A sale In Preparation that is waiting for a vehicle (an entry of the dispatch panel). */
public record DispatchOrder(int saleId, String saleNumber, String customerName, String address,
                            BigDecimal total) {
}
