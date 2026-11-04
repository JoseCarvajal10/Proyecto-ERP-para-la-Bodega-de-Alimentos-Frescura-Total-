package com.frescuratotal.erp.inventory.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Represents one batch of homogeneous products received in the warehouse.
 * The lot id is the key used for full traceability.
 */
public class Lot {

    /** Lots with this many days or fewer before expiring are "Near Expiry". */
    public static final int NEAR_EXPIRY_DAYS = 7;

    private int id;
    private String lotNumber;
    private int productId;
    private String productName;
    private int supplierId;
    private String supplierName;
    private LocalDateTime entryDate;
    private LocalDate expirationDate;
    private BigDecimal initialQuantity;
    private BigDecimal quantity;
    private BigDecimal purchasePrice;
    private BigDecimal salePrice;
    private String aisle;
    private String shelf;
    private LotStatus status;

    public Lot() {
    }

    /** Status a lot should have according only to its expiration date. */
    public static LotStatus statusFor(LocalDate expirationDate) {
        long days = ChronoUnit.DAYS.between(LocalDate.now(), expirationDate);
        if (days < 0) {
            return LotStatus.EXPIRED;
        }
        return days <= NEAR_EXPIRY_DAYS ? LotStatus.NEAR_EXPIRY : LotStatus.AVAILABLE;
    }

    /** Days left before expiration (negative if already expired). */
    public long daysUntilExpiry() {
        return ChronoUnit.DAYS.between(LocalDate.now(), expirationDate);
    }

    public boolean isNearExpiry() {
        return status == LotStatus.NEAR_EXPIRY
                || (isSellable() && daysUntilExpiry() <= NEAR_EXPIRY_DAYS);
    }

    /** A lot can only be sold if it is Available or Near Expiry. */
    public boolean isSellable() {
        return status == LotStatus.AVAILABLE || status == LotStatus.NEAR_EXPIRY;
    }

    /** Shelf location in the format used by the warehouse, e.g. A-05. */
    public String getLocation() {
        return aisle + "-" + shelf;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getLotNumber() { return lotNumber; }
    public void setLotNumber(String lotNumber) { this.lotNumber = lotNumber; }

    public int getProductId() { return productId; }
    public void setProductId(int productId) { this.productId = productId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public int getSupplierId() { return supplierId; }
    public void setSupplierId(int supplierId) { this.supplierId = supplierId; }

    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }

    public LocalDateTime getEntryDate() { return entryDate; }
    public void setEntryDate(LocalDateTime entryDate) { this.entryDate = entryDate; }

    public LocalDate getExpirationDate() { return expirationDate; }
    public void setExpirationDate(LocalDate expirationDate) { this.expirationDate = expirationDate; }

    public BigDecimal getInitialQuantity() { return initialQuantity; }
    public void setInitialQuantity(BigDecimal initialQuantity) { this.initialQuantity = initialQuantity; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public BigDecimal getPurchasePrice() { return purchasePrice; }
    public void setPurchasePrice(BigDecimal purchasePrice) { this.purchasePrice = purchasePrice; }

    public BigDecimal getSalePrice() { return salePrice; }
    public void setSalePrice(BigDecimal salePrice) { this.salePrice = salePrice; }

    public String getAisle() { return aisle; }
    public void setAisle(String aisle) { this.aisle = aisle; }

    public String getShelf() { return shelf; }
    public void setShelf(String shelf) { this.shelf = shelf; }

    public LotStatus getStatus() { return status; }
    public void setStatus(LotStatus status) { this.status = status; }
}
