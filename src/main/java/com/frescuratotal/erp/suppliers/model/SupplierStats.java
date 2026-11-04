package com.frescuratotal.erp.suppliers.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Numbers used to judge how reliable a supplier is over time. */
public record SupplierStats(int lotsReceived, BigDecimal totalReceived, BigDecimal totalWaste,
                            int rejectedInspections) {

    /** Share of received units that ended up as waste, as a percentage. */
    public BigDecimal wastePercentage() {
        if (totalReceived.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return totalWaste.multiply(BigDecimal.valueOf(100))
                .divide(totalReceived, 2, RoundingMode.HALF_UP);
    }
}
