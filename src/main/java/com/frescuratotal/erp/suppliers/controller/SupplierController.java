package com.frescuratotal.erp.suppliers.controller;

import com.frescuratotal.erp.common.ConsoleReader;
import com.frescuratotal.erp.common.AppLogger;
import com.frescuratotal.erp.common.Role;
import com.frescuratotal.erp.common.UserSession;
import com.frescuratotal.erp.inventory.model.LotDao;
import com.frescuratotal.erp.inventory.model.Product;
import com.frescuratotal.erp.inventory.model.ProductDao;
import com.frescuratotal.erp.suppliers.model.Supplier;
import com.frescuratotal.erp.suppliers.model.SupplierDao;
import com.frescuratotal.erp.suppliers.view.SupplierView;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;

/** Controller of the suppliers module. */
public class SupplierController {

    private final SupplierDao supplierDao = new SupplierDao();
    private final ProductDao productDao = new ProductDao();
    private final LotDao lotDao = new LotDao();
    private final SupplierView view = new SupplierView();

    public void start() {
        boolean running = true;

        while (running) {
            int option = view.showMenu();

            try {
                switch (option) {
                    case 1 -> view.showSuppliers(supplierDao.findAll());
                    case 2 -> showProfile();
                    case 3 -> registerSupplier();
                    case 4 -> addProductToCatalog();
                    case 0 -> running = false;
                    default -> view.showMessage("Invalid option, try again.");
                }
            } catch (SQLException e) {
                AppLogger.error("Supplier database operation failed", e);
                view.showError("Database error: " + e.getMessage());
            }
        }
    }

    private void showProfile() throws SQLException {
        view.showSuppliers(supplierDao.findAll());
        int supplierId = ConsoleReader.readInt("Supplier id: ");
        Optional<Supplier> supplier = supplierDao.findById(supplierId);

        if (supplier.isEmpty()) {
            view.showMessage("Supplier not found.");
            return;
        }
        view.showProfile(supplier.get(), productDao.findCatalogBySupplier(supplierId),
                lotDao.findBySupplier(supplierId), supplierDao.findStats(supplierId));
    }

    private void registerSupplier() throws SQLException {
        if (!UserSession.can(Role.WAREHOUSE_OPERATOR)) {
            view.showMessage("Your role cannot register suppliers.");
            return;
        }
        String name = ConsoleReader.readRequired("Company name: ");
        String nit = ConsoleReader.readRequired("NIT (e.g. 900123456-7): ");
        if (supplierDao.existsByNit(nit)) {
            view.showMessage("A supplier with that NIT already exists.");
            return;
        }
        String contact = ConsoleReader.readRequired("Contact person: ");
        String email = ConsoleReader.readRequired("Email: ");
        String phone = ConsoleReader.readLine("Phone: ");
        String address = ConsoleReader.readLine("Address: ");

        Supplier supplier = new Supplier(0, name, nit, contact, email, phone, address, true,
                LocalDateTime.now());
        int id = supplierDao.insert(supplier);
        view.showMessage("Supplier registered with id " + id + ". Now add the products it can provide.");
    }

    private void addProductToCatalog() throws SQLException {
        if (!UserSession.can(Role.WAREHOUSE_OPERATOR)) {
            view.showMessage("Your role cannot change supplier catalogs.");
            return;
        }
        view.showSuppliers(supplierDao.findAll());
        int supplierId = ConsoleReader.readInt("Supplier id: ");
        if (supplierDao.findById(supplierId).isEmpty()) {
            view.showMessage("Supplier not found.");
            return;
        }
        view.showProducts(productDao.findAll());
        int productId = ConsoleReader.readInt("Product id to add: ");
        Optional<Product> product = productDao.findById(productId);

        if (product.isEmpty()) {
            view.showMessage("Product not found.");
        } else if (supplierDao.addToCatalog(supplierId, productId)) {
            view.showMessage(product.get().name() + " added to the catalog.");
        } else {
            view.showMessage("That product was already in the catalog.");
        }
    }
}
