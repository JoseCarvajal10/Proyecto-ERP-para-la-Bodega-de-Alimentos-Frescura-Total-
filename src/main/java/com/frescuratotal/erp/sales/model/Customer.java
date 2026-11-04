package com.frescuratotal.erp.sales.model;

/** A customer of the warehouse: wholesale (typeId 1) or retail (typeId 2). */
public record Customer(int id, String name, String document, String email, String phone,
                       String address, int typeId, String typeName) {
}
