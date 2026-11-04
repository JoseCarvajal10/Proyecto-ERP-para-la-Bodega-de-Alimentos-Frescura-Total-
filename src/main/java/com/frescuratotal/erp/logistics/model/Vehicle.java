package com.frescuratotal.erp.logistics.model;

import java.math.BigDecimal;

/** A delivery vehicle. */
public record Vehicle(int id, String plate, String model, BigDecimal capacity, String status) {
}
