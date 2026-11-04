package com.frescuratotal.erp.suppliers.view;

import com.frescuratotal.erp.common.ConsoleReader;
import com.frescuratotal.erp.inventory.model.Lot;
import com.frescuratotal.erp.inventory.model.Product;
import com.frescuratotal.erp.suppliers.model.Supplier;
import com.frescuratotal.erp.suppliers.model.SupplierStats;
import java.util.List;

/** Console view of the suppliers module. */
public class SupplierView {

    public int showMenu() {
        System.out.println();
        System.out.println("=== SUPPLIERS MODULE ===");
        System.out.println("1. List suppliers");
        System.out.println("2. Supplier profile (catalog, history and reliability)");
        System.out.println("3. Register a new supplier");
        System.out.println("4. Add a product to a supplier catalog");
        System.out.println("0. Back");
        return ConsoleReader.readInt("Choose an option: ");
    }

    public void showSuppliers(List<Supplier> suppliers) {
        System.out.println();
        System.out.printf("%-4s %-34s %-18s %-22s %s%n", "ID", "Name", "NIT", "Contact", "Active");
        System.out.println("-".repeat(88));
        for (Supplier supplier : suppliers) {
            System.out.printf("%-4d %-34s %-18s %-22s %s%n", supplier.id(), supplier.name(),
                    supplier.nit(), supplier.contactPerson(), supplier.active() ? "Yes" : "No");
        }
    }

    public void showProfile(Supplier supplier, List<Product> catalog, List<Lot> history,
                            SupplierStats stats) {
        System.out.println();
        System.out.println("--- " + supplier.name() + " ---");
        System.out.println("NIT:      " + supplier.nit());
        System.out.println("Contact:  " + supplier.contactPerson() + " | " + supplier.phone()
                + " | " + supplier.email());
        System.out.println("Address:  " + supplier.address());
        System.out.println("Since:    " + supplier.registeredAt().toLocalDate());

        System.out.println();
        System.out.println("Product catalog (" + catalog.size() + "):");
        for (Product product : catalog) {
            System.out.printf("  - %-24s %-10s (%s)%n", product.name(), product.unit(), product.categoryName());
        }

        System.out.println();
        System.out.println("Reliability:");
        System.out.println("  Lots delivered:        " + stats.lotsReceived());
        System.out.println("  Units received:        " + stats.totalReceived());
        System.out.println("  Units lost as waste:   " + stats.totalWaste()
                + " (" + stats.wastePercentage() + "%)");
        System.out.println("  Rejected inspections:  " + stats.rejectedInspections());

        System.out.println();
        System.out.println("Last deliveries:");
        System.out.printf("  %-20s %-22s %10s %-11s %s%n", "Lot", "Product", "Received", "Expires", "Status");
        for (Lot lot : history.subList(0, Math.min(8, history.size()))) {
            System.out.printf("  %-20s %-22s %10.2f %-11s %s%n", lot.getLotNumber(), lot.getProductName(),
                    lot.getInitialQuantity(), lot.getExpirationDate(), lot.getStatus().getLabel());
        }
    }

    public void showProducts(List<Product> products) {
        System.out.println();
        System.out.printf("%-4s %-26s %-10s %s%n", "ID", "Product", "Unit", "Category");
        for (Product product : products) {
            System.out.printf("%-4d %-26s %-10s %s%n", product.id(), product.name(), product.unit(),
                    product.categoryName());
        }
    }

    public void showMessage(String message) {
        System.out.println(message);
    }

    public void showError(String message) {
        System.err.println(message);
    }
}
