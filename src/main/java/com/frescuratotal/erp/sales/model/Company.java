package com.frescuratotal.erp.sales.model;

/** Data of "Frescura Total" printed on every invoice. */
public record Company(String name, String nit, String address, String city, String phone, String email) {
}
