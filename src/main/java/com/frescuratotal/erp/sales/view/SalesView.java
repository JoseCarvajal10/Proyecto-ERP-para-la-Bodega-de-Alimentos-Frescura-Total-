package com.frescuratotal.erp.sales.view;

import com.frescuratotal.erp.common.ConsoleReader;
import com.frescuratotal.erp.inventory.model.ProductStock;
import com.frescuratotal.erp.sales.model.Customer;
import com.frescuratotal.erp.sales.model.Invoice;
import com.frescuratotal.erp.sales.model.Sale;
import com.frescuratotal.erp.sales.model.SaleLine;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Console view of the sales module. */
public class SalesView {

    public int showMenu() {
        System.out.println();
        System.out.println("=== SALES MODULE ===");
        System.out.println("1. New sale");
        System.out.println("2. Recent sales");
        System.out.println("3. Sale details and invoice");
        System.out.println("4. Send a sale to preparation");
        System.out.println("5. Customers");
        System.out.println("6. Register a customer");
        System.out.println("0. Back");
        return ConsoleReader.readInt("Choose an option: ");
    }

    public void showCustomers(List<Customer> customers) {
        System.out.println();
        System.out.printf("%-4s %-34s %-14s %s%n", "ID", "Name", "Document", "Type");
        System.out.println("-".repeat(70));
        for (Customer customer : customers) {
            System.out.printf("%-4d %-34s %-14s %s%n", customer.id(), customer.name(),
                    customer.document(), customer.typeName());
        }
    }

    public void showProducts(List<ProductStock> products) {
        System.out.println();
        System.out.printf("%-4s %-26s %-10s %12s%n", "ID", "Product", "Unit", "Available");
        System.out.println("-".repeat(56));
        for (ProductStock stock : products) {
            System.out.printf("%-4d %-26s %-10s %12.2f%n", stock.product().id(), stock.product().name(),
                    stock.product().unit(), stock.availableQuantity());
        }
    }

    public void showLines(List<SaleLine> lines) {
        System.out.println();
        System.out.printf("%-20s %-22s %9s %12s %6s %6s %14s%n",
                "Lot", "Product", "Qty", "Unit price", "Disc.", "VAT", "Line total");
        System.out.println("-".repeat(95));
        for (SaleLine line : lines) {
            System.out.printf("%-20s %-22s %9.2f %,12.2f %5.0f%% %5.0f%% %,14.2f%n", line.lotNumber(),
                    line.productName(), line.quantity(), line.unitPrice(),
                    line.discountRate().multiply(BigDecimal.valueOf(100)),
                    line.taxRate().multiply(BigDecimal.valueOf(100)), line.lineTotal());
        }
    }

    public void showCartTotal(BigDecimal total) {
        System.out.printf("Total so far: %,.2f%n", total);
    }

    public void showSales(List<Sale> sales) {
        System.out.println();
        System.out.printf("%-4s %-16s %-17s %-28s %14s  %s%n", "ID", "Number", "Date", "Customer", "Total", "Status");
        System.out.println("-".repeat(100));
        for (Sale sale : sales) {
            System.out.printf("%-4d %-16s %-17s %-28s %,14.2f  %s%n", sale.getId(), sale.getNumber(),
                    sale.getDateTime().toString().replace('T', ' ').substring(0, 16),
                    sale.getCustomer().name(), sale.getTotal(), sale.getStatus().getLabel());
        }
    }

    public void showSaleDetail(Sale sale, Optional<Invoice> invoice) {
        System.out.println();
        System.out.println("--- Sale " + sale.getNumber() + " ---");
        System.out.println("Customer: " + sale.getCustomer().name() + " (" + sale.getCustomer().typeName() + ")");
        System.out.println("Date:     " + sale.getDateTime().toString().replace('T', ' ').substring(0, 16));
        System.out.println("Status:   " + sale.getStatus().getLabel());
        showLines(sale.getLines());
        System.out.printf("Subtotal: %,.2f | Discounts: %,.2f | VAT: %,.2f | TOTAL: %,.2f%n",
                sale.getSubtotal(), sale.getDiscount(), sale.getTax(), sale.getTotal());
        invoice.ifPresent(inv -> System.out.println("Invoice:  " + inv.number() + " | CUFE " + inv.cufe().substring(0, 24) + "..."));
    }

    public void showInvoiceCreated(Invoice invoice, Path file) {
        System.out.println();
        System.out.println("Invoice " + invoice.number() + " issued.");
        System.out.println("Image saved at: " + file.toAbsolutePath());
    }

    public void showMessage(String message) {
        System.out.println(message);
    }

    public void showError(String message) {
        System.err.println(message);
    }
}
