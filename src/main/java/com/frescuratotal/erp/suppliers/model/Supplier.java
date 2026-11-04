package com.frescuratotal.erp.suppliers.model;

import java.time.LocalDateTime;

/** A company that supplies products to the warehouse. */
public record Supplier(int id, String name, String nit, String contactPerson, String email,
                       String phone, String address, boolean active, LocalDateTime registeredAt) {
}
