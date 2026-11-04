package com.frescuratotal.erp.inventory.model;

/** Outcome of a quality inspection of a lot in quarantine. */
public enum InspectionResult {
    APPROVED("Aprobado"),
    REJECTED("Rechazado");

    private final String dbValue;

    InspectionResult(String dbValue) {
        this.dbValue = dbValue;
    }

    /** Value stored in the ENUM column of inspecciones_calidad. */
    public String getDbValue() {
        return dbValue;
    }
}
