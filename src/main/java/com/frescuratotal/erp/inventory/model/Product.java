package com.frescuratotal.erp.inventory.model;

import java.math.BigDecimal;

/** A product of the catalog (tomatoes, milk, rice...). */
public record Product(int id, String name, String unit, int categoryId,
                      String categoryName, BigDecimal basePrice) {
}
