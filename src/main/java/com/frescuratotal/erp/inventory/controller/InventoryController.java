package com.frescuratotal.erp.inventory.controller;

import com.frescuratotal.erp.common.ConsoleReader;
import com.frescuratotal.erp.common.AppLogger;
import com.frescuratotal.erp.common.Role;
import com.frescuratotal.erp.common.UserSession;
import com.frescuratotal.erp.inventory.model.InspectionResult;
import com.frescuratotal.erp.inventory.model.Lot;
import com.frescuratotal.erp.inventory.model.LotDao;
import com.frescuratotal.erp.inventory.model.LotStatus;
import com.frescuratotal.erp.inventory.model.Product;
import com.frescuratotal.erp.inventory.model.ProductDao;
import com.frescuratotal.erp.inventory.model.QualityDao;
import com.frescuratotal.erp.inventory.model.WasteDao;
import com.frescuratotal.erp.inventory.view.InventoryView;
import com.frescuratotal.erp.suppliers.model.Supplier;
import com.frescuratotal.erp.suppliers.model.SupplierDao;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Controller of the inventory module.
 * It connects what the user chooses in the view with the data in the model.
 */
public class InventoryController {

    private final LotDao lotDao = new LotDao();
    private final ProductDao productDao = new ProductDao();
    private final SupplierDao supplierDao = new SupplierDao();
    private final WasteDao wasteDao = new WasteDao();
    private final QualityDao qualityDao = new QualityDao();
    private final InventoryView view = new InventoryView();

    public void start() {
        boolean running = true;

        while (running) {
            int option = view.showMenu();

            try {
                switch (option) {
                    case 1 -> view.showLots(lotDao.findAll());
                    case 2 -> showLotTraceability();
                    case 3 -> showLotsByLocation();
                    case 4 -> showLotsByStatus();
                    case 5 -> receiveMerchandise();
                    case 6 -> quarantineLot();
                    case 7 -> registerWaste();
                    case 8 -> inspectLots();
                    case 9 -> runExpiryCheck();
                    case 10 -> view.showWaste(wasteDao.findRecent(20));
                    case 0 -> running = false;
                    default -> view.showMessage("Invalid option, try again.");
                }
            } catch (SQLException e) {
                AppLogger.error("Inventory database operation failed", e);
                view.showError("Database error: " + e.getMessage());
            } catch (IllegalArgumentException | IllegalStateException e) {
                view.showError(e.getMessage());
            }
        }
    }

    /** Daily process: also called when the application starts. */
    public int[] refreshExpiryStatuses() throws SQLException {
        return lotDao.refreshExpiryStatuses(LocalDate.now());
    }

    private boolean allowed(Role... roles) {
        if (UserSession.can(roles)) {
            return true;
        }
        view.showMessage("Your role (" + UserSession.getRole().getLabel() + ") cannot do this.");
        return false;
    }

    // ---------- queries and traceability ----------

    private void showLotTraceability() throws SQLException {
        int lotId = ConsoleReader.readInt("Lot id: ");
        Optional<Lot> lot = lotDao.findById(lotId);

        if (lot.isPresent()) {
            view.showLotDetail(lot.get(), lotDao.findDeliveries(lotId));
        } else {
            view.showMessage("Lot not found.");
        }
    }

    private void showLotsByLocation() throws SQLException {
        String location = ConsoleReader.readRequired("Shelf location (aisle-shelf, e.g. A-05): ");
        String[] parts = location.split("-");
        if (parts.length != 2) {
            view.showMessage("Use the format aisle-shelf, for example A-05.");
            return;
        }
        String shelf = parts[1].trim();
        if (shelf.length() == 1 && Character.isDigit(shelf.charAt(0))) {
            shelf = "0" + shelf;
        }
        view.showLots(lotDao.findByLocation(parts[0].trim(), shelf));
    }

    private void showLotsByStatus() throws SQLException {
        System.out.println("1. Available  2. Near Expiry  3. Expired  4. In Quarantine");
        int option = ConsoleReader.readInt("Status: ");
        if (option < 1 || option > 4) {
            view.showMessage("Invalid status.");
            return;
        }
        view.showLots(lotDao.findByStatus(LotStatus.fromId(option)));
    }

    // ---------- receiving merchandise ----------

    private void receiveMerchandise() throws SQLException {
        if (!allowed(Role.WAREHOUSE_OPERATOR)) {
            return;
        }
        List<Supplier> suppliers = supplierDao.findAll();
        view.showSupplierChoices(suppliers);
        Optional<Supplier> supplier = supplierDao.findById(ConsoleReader.readInt("Supplier id: "));
        if (supplier.isEmpty() || !supplier.get().active()) {
            view.showMessage("Supplier not found or inactive.");
            return;
        }

        List<Product> catalog = productDao.findCatalogBySupplier(supplier.get().id());
        if (catalog.isEmpty()) {
            view.showMessage("This supplier has no products in its catalog yet.");
            return;
        }
        view.showProducts(catalog);
        int productId = ConsoleReader.readInt("Product id: ");
        Optional<Product> product = catalog.stream().filter(p -> p.id() == productId).findFirst();
        if (product.isEmpty()) {
            view.showMessage("That product is not in the catalog of this supplier.");
            return;
        }

        BigDecimal quantity = ConsoleReader.readPositiveDecimal(
                "Quantity received (" + product.get().unit() + "): ");
        LocalDate expiration = ConsoleReader.readDate("Expiration date (yyyy-MM-dd): ");
        if (expiration.isBefore(LocalDate.now())) {
            view.showMessage("You cannot receive a lot that is already expired.");
            return;
        }
        BigDecimal purchasePrice = ConsoleReader.readPositiveDecimal("Purchase price per unit: ");
        BigDecimal salePrice = ConsoleReader.readPositiveDecimal(
                "Sale price per unit (base price is " + product.get().basePrice() + "): ");
        String aisle = ConsoleReader.readRequired("Aisle (letter, e.g. A): ").toUpperCase();
        String shelf = ConsoleReader.readRequired("Shelf (number, e.g. 05): ");

        Lot lot = new Lot();
        lot.setProductId(product.get().id());
        lot.setSupplierId(supplier.get().id());
        lot.setExpirationDate(expiration);
        lot.setInitialQuantity(quantity);
        lot.setPurchasePrice(purchasePrice);
        lot.setSalePrice(salePrice);
        lot.setAisle(aisle);
        lot.setShelf(shelf);
        lot.setStatus(Lot.statusFor(expiration));

        lotDao.insert(lot);
        view.showMessage("Lot registered: " + lot.getLotNumber() + " (id " + lot.getId()
                + ", status " + lot.getStatus().getLabel() + ")");
    }

    // ---------- quality and waste ----------

    private void quarantineLot() throws SQLException {
        if (!allowed(Role.WAREHOUSE_OPERATOR, Role.QUALITY_INSPECTOR)) {
            return;
        }
        Optional<Lot> lot = lotDao.findById(ConsoleReader.readInt("Lot id: "));
        if (lot.isEmpty()) {
            view.showMessage("Lot not found.");
            return;
        }
        if (lot.get().getStatus() == LotStatus.IN_QUARANTINE) {
            view.showMessage("That lot is already in quarantine.");
            return;
        }
        String reason = ConsoleReader.readRequired("What did you find (damaged package, strange smell...): ");
        if (ConsoleReader.confirm("Block the whole lot " + lot.get().getLotNumber() + " for sale?")) {
            qualityDao.quarantine(lot.get().getId(), reason, UserSession.getUsername());
            view.showMessage("Lot blocked. A quality inspector must decide its destiny.");
        }
    }

    private void inspectLots() throws SQLException {
        if (!allowed(Role.QUALITY_INSPECTOR)) {
            return;
        }
        List<Lot> pending = lotDao.findPendingInspection();
        if (pending.isEmpty()) {
            view.showMessage("There are no lots waiting for inspection.");
            return;
        }
        view.showLots(pending);
        int lotId = ConsoleReader.readInt("Lot id to inspect: ");
        if (pending.stream().noneMatch(lot -> lot.getId() == lotId)) {
            view.showMessage("That lot is not waiting for inspection.");
            return;
        }

        System.out.println("1. Approve (the lot returns to sale)   2. Reject (the lot becomes waste)");
        int decision = ConsoleReader.readInt("Result: ");
        String notes = ConsoleReader.readLine("Notes (optional): ");

        if (decision == 1) {
            qualityDao.inspect(lotId, InspectionResult.APPROVED, UserSession.getUsername(), null, notes);
            view.showMessage("Lot approved and back on sale.");
        } else if (decision == 2) {
            String reason = ConsoleReader.readRequired("Reason for rejection: ");
            qualityDao.inspect(lotId, InspectionResult.REJECTED, UserSession.getUsername(), reason, notes);
            view.showMessage("Lot rejected: all remaining units were written off as waste.");
        } else {
            view.showMessage("Invalid result.");
        }
    }

    private void registerWaste() throws SQLException {
        if (!allowed(Role.WAREHOUSE_OPERATOR)) {
            return;
        }
        Optional<Lot> lot = lotDao.findById(ConsoleReader.readInt("Lot id: "));
        if (lot.isEmpty()) {
            view.showMessage("Lot not found.");
            return;
        }
        System.out.println("The lot has " + lot.get().getQuantity() + " units left.");
        BigDecimal quantity = ConsoleReader.readPositiveDecimal("Units lost: ");
        String reason = ConsoleReader.readRequired("Reason (expired, damaged...): ");

        wasteDao.register(lot.get().getId(), quantity, reason, UserSession.getUsername());
        view.showMessage("Waste registered.");
    }

    private void runExpiryCheck() throws SQLException {
        if (!allowed(Role.WAREHOUSE_OPERATOR)) {
            return;
        }
        int[] changes = refreshExpiryStatuses();
        view.showMessage("Expiry check done: " + changes[0] + " lots marked Expired, "
                + changes[1] + " lots marked Near Expiry.");

        List<Lot> expired = lotDao.findExpiredWithStock();
        if (!expired.isEmpty()) {
            view.showMessage("These expired lots still have units on the shelves:");
            view.showLots(expired);
            if (ConsoleReader.confirm("Write off all of them as waste now?")) {
                for (Lot lot : expired) {
                    wasteDao.register(lot.getId(), lot.getQuantity(), "Expired product",
                            UserSession.getUsername());
                }
                view.showMessage(expired.size() + " lots written off.");
            }
        }
    }
}
