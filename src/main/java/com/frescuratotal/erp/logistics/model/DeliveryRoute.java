package com.frescuratotal.erp.logistics.model;

import java.time.LocalDateTime;

/** A delivery route: one vehicle, one driver and several orders. */
public record DeliveryRoute(int id, String name, RouteStatus status, LocalDateTime createdAt,
                            String vehiclePlate, String driverName, int stops, int delivered) {
}
