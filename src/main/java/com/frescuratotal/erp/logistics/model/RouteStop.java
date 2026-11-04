package com.frescuratotal.erp.logistics.model;

import java.time.LocalDateTime;

/** One delivery of a route. deliveredAt is null until the customer receives the order. */
public record RouteStop(int saleId, String saleNumber, String customerName, int order,
                        String address, LocalDateTime deliveredAt, String receivedBy) {
}
