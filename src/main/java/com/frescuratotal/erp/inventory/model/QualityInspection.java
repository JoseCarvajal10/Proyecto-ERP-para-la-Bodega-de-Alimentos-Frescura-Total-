package com.frescuratotal.erp.inventory.model;

import java.time.LocalDateTime;

/** Result of the inspection made by a quality inspector on a quarantined lot. */
public record QualityInspection(int id, int lotId, LocalDateTime date, InspectionResult result,
                                String inspector, String rejectionReason, String notes) {
}
