package com.frescuratotal.erp.sales.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lines the salesperson has added but not confirmed yet.
 * It remembers how much of each lot is already taken so two lines never use the same units.
 */
public class SaleCart {

    private final List<SaleLine> lines = new ArrayList<>();
    private final Map<Integer, BigDecimal> reservedByLot = new HashMap<>();

    public void add(List<SaleLine> newLines) {
        for (SaleLine line : newLines) {
            lines.add(line);
            reservedByLot.merge(line.lotId(), line.quantity(), BigDecimal::add);
        }
    }

    public BigDecimal reservedFor(int lotId) {
        return reservedByLot.getOrDefault(lotId, BigDecimal.ZERO);
    }

    public List<SaleLine> getLines() {
        return lines;
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    public BigDecimal total() {
        return lines.stream().map(SaleLine::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
