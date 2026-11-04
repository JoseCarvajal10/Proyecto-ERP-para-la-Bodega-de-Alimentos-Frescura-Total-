package com.frescuratotal.erp.sales.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** A sale to a customer, made of one or more lines. */
public class Sale {

    private int id;
    private String number;
    private Customer customer;
    private LocalDateTime dateTime;
    private SaleStatus status = SaleStatus.REGISTERED;
    private String user;
    private final List<SaleLine> lines = new ArrayList<>();
    private BigDecimal subtotal = BigDecimal.ZERO;
    private BigDecimal discount = BigDecimal.ZERO;
    private BigDecimal tax = BigDecimal.ZERO;
    private BigDecimal total = BigDecimal.ZERO;

    /** Replaces the lines and recalculates every total from them. */
    public void setLines(List<SaleLine> newLines) {
        lines.clear();
        lines.addAll(newLines);
        subtotal = sum(SaleLine::subtotal);
        discount = sum(SaleLine::discountAmount);
        tax = sum(SaleLine::taxAmount);
        total = sum(SaleLine::lineTotal);
    }

    /** Sets the totals directly (used when only the sale header is loaded). */
    public void setTotals(BigDecimal subtotal, BigDecimal discount, BigDecimal tax, BigDecimal total) {
        this.subtotal = subtotal;
        this.discount = discount;
        this.tax = tax;
        this.total = total;
    }

    private BigDecimal sum(java.util.function.Function<SaleLine, BigDecimal> field) {
        return lines.stream().map(field).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }

    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }

    public LocalDateTime getDateTime() { return dateTime; }
    public void setDateTime(LocalDateTime dateTime) { this.dateTime = dateTime; }

    public SaleStatus getStatus() { return status; }
    public void setStatus(SaleStatus status) { this.status = status; }

    public String getUser() { return user; }
    public void setUser(String user) { this.user = user; }

    public List<SaleLine> getLines() { return lines; }

    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getDiscount() { return discount; }
    public BigDecimal getTax() { return tax; }
    public BigDecimal getTotal() { return total; }
}
