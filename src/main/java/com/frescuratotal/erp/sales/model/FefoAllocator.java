package com.frescuratotal.erp.sales.model;

import com.frescuratotal.erp.inventory.model.Lot;
import com.frescuratotal.erp.inventory.model.LotDao;
import com.frescuratotal.erp.inventory.model.Product;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Chooses which lots to sell from using FEFO (First to Expire, First Out):
 * the lot that expires first is used first. Lots that are Near Expiry get the promotion.
 */
public class FefoAllocator {

    private final LotDao lotDao;
    private final PromotionDao promotionDao;

    public FefoAllocator(LotDao lotDao, PromotionDao promotionDao) {
        this.lotDao = lotDao;
        this.promotionDao = promotionDao;
    }

    /**
     * Splits the requested quantity across as many lots as needed.
     *
     * @throws IllegalStateException if there are not enough units to sell
     */
    public List<SaleLine> allocate(Product product, BigDecimal quantity, SaleCart cart)
            throws SQLException {
        BigDecimal promotionRate = promotionDao.findNearExpiryDiscountRate();
        BigDecimal taxRate = TaxPolicy.rateFor(product.categoryId());
        BigDecimal remaining = quantity;
        List<SaleLine> lines = new ArrayList<>();

        for (Lot lot : lotDao.findSellableByProduct(product.id(), LocalDate.now())) {
            if (remaining.signum() <= 0) {
                break;
            }
            BigDecimal free = lot.getQuantity().subtract(cart.reservedFor(lot.getId()));
            if (free.signum() <= 0) {
                continue;
            }
            BigDecimal taken = free.min(remaining);
            BigDecimal discount = lot.isNearExpiry() ? promotionRate : BigDecimal.ZERO;
            lines.add(SaleLine.create(lot, taken, discount, taxRate));
            remaining = remaining.subtract(taken);
        }

        if (remaining.signum() > 0) {
            throw new IllegalStateException("Not enough stock: " + remaining + " "
                    + product.unit() + " of " + product.name() + " cannot be covered.");
        }
        return lines;
    }
}
