package com.frescuratotal.erp.inventory.model;

/**
 * Possible states of an inventory lot.
 * The ids match the rows of the estados_lote table.
 */
public enum LotStatus {

    AVAILABLE(1, "Available"),
    NEAR_EXPIRY(2, "Near Expiry"),
    EXPIRED(3, "Expired"),
    IN_QUARANTINE(4, "In Quarantine");

    private final int id;
    private final String label;

    LotStatus(int id, String label) {
        this.id = id;
        this.label = label;
    }

    public int getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    /** Converts a database id into the matching status. */
    public static LotStatus fromId(int id) {
        for (LotStatus status : values()) {
            if (status.id == id) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown lot status id: " + id);
    }
}
