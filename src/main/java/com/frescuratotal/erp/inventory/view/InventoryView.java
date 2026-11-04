package com.frescuratotal.erp.inventory.view;

import com.frescuratotal.erp.common.ConsoleReader;
import com.frescuratotal.erp.inventory.model.Lot;
import com.frescuratotal.erp.inventory.model.LotDelivery;
import com.frescuratotal.erp.inventory.model.Product;
import com.frescuratotal.erp.inventory.model.WasteRecord;
import com.frescuratotal.erp.suppliers.model.Supplier;
import java.util.List;

/**
 * Console view of the inventory module.
 * It only prints information and reads what the user types.
 */
public class InventoryView {

    /** Shows the menu and returns the option chosen by the user. */
    public int showMenu() {
        System.out.println();
        System.out.println("=== INVENTORY MODULE ===");
        System.out.println(" 1. List all lots");
        System.out.println(" 2. Lot details and traceability");
        System.out.println(" 3. Find lots by shelf location (e.g. A-05)");
        System.out.println(" 4. List lots by status");
        System.out.println(" 5. Receive merchandise (new lot)");
        System.out.println(" 6. Put a lot in quarantine");
        System.out.println(" 7. Register waste (merma)");
        System.out.println(" 8. Quality inspection of quarantined lots");
        System.out.println(" 9. Run the expiry check now");
        System.out.println("10. Waste history");
        System.out.println(" 0. Back");
        return ConsoleReader.readInt("Choose an option: ");
    }

    public void showLots(List<Lot> lots) {
        System.out.println();
        System.out.printf("%-4s %-20s %-22s %9s %-6s %-11s %5s  %s%n",
                "ID", "Lot", "Product", "Qty", "Shelf", "Expires", "Days", "Status");
        System.out.println("-".repeat(98));
        for (Lot lot : lots) {
            System.out.printf("%-4d %-20s %-22s %9.2f %-6s %-11s %5d  %s%n",
                    lot.getId(),
                    lot.getLotNumber(),
                    lot.getProductName(),
                    lot.getQuantity(),
                    lot.getLocation(),
                    lot.getExpirationDate(),
                    lot.daysUntilExpiry(),
                    lot.getStatus().getLabel());
        }
        System.out.println("Total: " + lots.size() + " lots");
    }

    public void showLotDetail(Lot lot, List<LotDelivery> deliveries) {
        System.out.println();
        System.out.println("--- Lot " + lot.getLotNumber() + " ---");
        System.out.println("Product:          " + lot.getProductName());
        System.out.println("Supplier:         " + lot.getSupplierName());
        System.out.println("Entry date:       " + lot.getEntryDate().toLocalDate());
        System.out.println("Expiration date:  " + lot.getExpirationDate()
                + " (" + lot.daysUntilExpiry() + " days)");
        System.out.println("Initial quantity: " + lot.getInitialQuantity());
        System.out.println("Current quantity: " + lot.getQuantity());
        System.out.println("Location:         " + lot.getLocation());
        System.out.println("Status:           " + lot.getStatus().getLabel());
        System.out.println("Sellable:         " + (lot.isSellable() ? "Yes" : "No"));

        System.out.println();
        if (deliveries.isEmpty()) {
            System.out.println("No customer has received products from this lot yet.");
            return;
        }
        System.out.println("Customers that received products from this lot:");
        System.out.printf("  %-30s %-17s %9s %-11s %s%n", "Customer", "Sale", "Qty", "Date", "Sale status");
        for (LotDelivery delivery : deliveries) {
            System.out.printf("  %-30s %-17s %9.2f %-11s %s%n", delivery.customerName(),
                    delivery.saleNumber(), delivery.quantity(), delivery.saleDate().toLocalDate(),
                    delivery.saleStatus());
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

    public void showSupplierChoices(List<Supplier> suppliers) {
        System.out.println();
        System.out.printf("%-4s %s%n", "ID", "Supplier");
        for (Supplier supplier : suppliers) {
            if (supplier.active()) {
                System.out.printf("%-4d %s%n", supplier.id(), supplier.name());
            }
        }
    }

    public void showWaste(List<WasteRecord> records) {
        System.out.println();
        System.out.printf("%-17s %-20s %-20s %9s  %s%n", "Date", "Lot", "Product", "Qty", "Reason");
        System.out.println("-".repeat(100));
        for (WasteRecord record : records) {
            System.out.printf("%-17s %-20s %-20s %9.2f  %s%n",
                    record.date().toString().replace('T', ' ').substring(0, 16),
                    record.lotNumber(), record.productName(), record.quantity(), record.reason());
        }
    }

    public void showMessage(String message) {
        System.out.println(message);
    }

    public void showError(String message) {
        System.err.println(message);
    }
}
