package com.frescuratotal.erp.inventory.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Products removed from the inventory (expired, damaged, rejected...). */
public record WasteRecord(int id, int lotId, String lotNumber, String productName,
                          BigDecimal quantity, String reason, LocalDateTime date, String user) {
}
