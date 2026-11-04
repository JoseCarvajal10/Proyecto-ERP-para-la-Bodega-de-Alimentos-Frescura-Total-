package com.frescuratotal.erp.logistics.model;

/** Status of a delivery route. */
public enum RouteStatus {

    PLANNED("Planificada", "Planned"),
    IN_TRANSIT("En Transito", "In Transit"),
    COMPLETED("Completada", "Completed"),
    CANCELLED("Cancelada", "Cancelled");

    private final String dbValue;
    private final String label;

    RouteStatus(String dbValue, String label) {
        this.dbValue = dbValue;
        this.label = label;
    }

    /** Value stored in the ENUM column of rutas_entrega. */
    public String getDbValue() {
        return dbValue;
    }

    public String getLabel() {
        return label;
    }

    public static RouteStatus fromDbValue(String value) {
        for (RouteStatus status : values()) {
            if (status.dbValue.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown route status: " + value);
    }
}
