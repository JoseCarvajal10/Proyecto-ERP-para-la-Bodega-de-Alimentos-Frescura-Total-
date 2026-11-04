package com.frescuratotal.erp.sales.model;

/** Life cycle of a sale. The ids match the rows of the estados_venta table. */
public enum SaleStatus {

    REGISTERED(1, "Registered"),
    IN_PREPARATION(2, "In Preparation"),
    IN_TRANSIT(3, "In Transit"),
    DELIVERED(4, "Delivered"),
    CANCELLED(5, "Cancelled");

    private final int id;
    private final String label;

    SaleStatus(int id, String label) {
        this.id = id;
        this.label = label;
    }

    public int getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public static SaleStatus fromId(int id) {
        for (SaleStatus status : values()) {
            if (status.id == id) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown sale status id: " + id);
    }
}
