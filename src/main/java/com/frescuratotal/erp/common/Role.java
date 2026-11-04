package com.frescuratotal.erp.common;

/** User roles and the modules each one may open. */
public enum Role {

    ADMIN("Administrator"),
    WAREHOUSE_OPERATOR("Warehouse operator"),
    QUALITY_INSPECTOR("Quality inspector"),
    SALES("Sales manager"),
    LOGISTICS("Logistics manager");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public boolean canAccess(AppModule module) {
        return switch (this) {
            case ADMIN -> true;
            case WAREHOUSE_OPERATOR -> module == AppModule.INVENTORY || module == AppModule.SUPPLIERS;
            case QUALITY_INSPECTOR -> module == AppModule.INVENTORY;
            case SALES -> module == AppModule.SALES;
            case LOGISTICS -> module == AppModule.LOGISTICS;
        };
    }
}
