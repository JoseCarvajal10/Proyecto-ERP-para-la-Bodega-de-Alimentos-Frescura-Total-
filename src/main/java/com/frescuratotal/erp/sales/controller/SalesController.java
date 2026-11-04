package com.frescuratotal.erp.sales.controller;

import com.frescuratotal.erp.common.ConsoleReader;
import com.frescuratotal.erp.common.AppLogger;
import com.frescuratotal.erp.common.Role;
import com.frescuratotal.erp.common.UserSession;
import com.frescuratotal.erp.inventory.model.LotDao;
import com.frescuratotal.erp.inventory.model.Product;
import com.frescuratotal.erp.inventory.model.ProductDao;
import com.frescuratotal.erp.inventory.model.ProductStock;
import com.frescuratotal.erp.sales.model.Company;
import com.frescuratotal.erp.sales.model.CompanyDao;
import com.frescuratotal.erp.sales.model.Customer;
import com.frescuratotal.erp.sales.model.CustomerDao;
import com.frescuratotal.erp.sales.model.FefoAllocator;
import com.frescuratotal.erp.sales.model.Invoice;
import com.frescuratotal.erp.sales.model.InvoiceDao;
import com.frescuratotal.erp.sales.model.PromotionDao;
import com.frescuratotal.erp.sales.model.Sale;
import com.frescuratotal.erp.sales.model.SaleCart;
import com.frescuratotal.erp.sales.model.SaleDao;
import com.frescuratotal.erp.sales.model.SaleLine;
import com.frescuratotal.erp.sales.model.SaleResult;
import com.frescuratotal.erp.sales.view.InvoiceRenderer;
import com.frescuratotal.erp.sales.view.SalesView;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Controller of the sales module: smart sales (FEFO + promotions) and one-click invoicing. */
public class SalesController {

    private static final Path INVOICE_FOLDER = Path.of("invoices");

    private final SaleDao saleDao = new SaleDao();
    private final CustomerDao customerDao = new CustomerDao();
    private final ProductDao productDao = new ProductDao();
    private final InvoiceDao invoiceDao = new InvoiceDao();
    private final CompanyDao companyDao = new CompanyDao();
    private final FefoAllocator allocator = new FefoAllocator(new LotDao(), new PromotionDao());
    private final InvoiceRenderer renderer = new InvoiceRenderer();
    private final SalesView view = new SalesView();

    public void start() {
        boolean running = true;

        while (running) {
            int option = view.showMenu();

            try {
                switch (option) {
                    case 1 -> newSale();
                    case 2 -> view.showSales(saleDao.findRecent(20));
                    case 3 -> showSaleDetail();
                    case 4 -> sendToPreparation();
                    case 5 -> view.showCustomers(customerDao.findAll());
                    case 6 -> registerCustomer();
                    case 0 -> running = false;
                    default -> view.showMessage("Invalid option, try again.");
                }
            } catch (SQLException e) {
                AppLogger.error("Sales database operation failed", e);
                view.showError("Database error: " + e.getMessage());
            } catch (IOException e) {
                AppLogger.error("Invoice image generation failed", e);
                view.showError("Could not create the invoice image: " + e.getMessage());
            } catch (IllegalArgumentException | IllegalStateException e) {
                view.showError(e.getMessage());
            }
        }
    }

    private boolean allowed() {
        if (UserSession.can(Role.SALES)) {
            return true;
        }
        view.showMessage("Your role (" + UserSession.getRole().getLabel() + ") cannot do this.");
        return false;
    }

    // ---------- new sale ----------

    private void newSale() throws SQLException, IOException {
        if (!allowed()) {
            return;
        }
        view.showCustomers(customerDao.findAll());
        Optional<Customer> customer = customerDao.findById(ConsoleReader.readInt("Customer id: "));
        if (customer.isEmpty()) {
            view.showMessage("Customer not found.");
            return;
        }

        SaleCart cart = new SaleCart();
        boolean adding = true;
        while (adding) {
            List<ProductStock> stock = productDao.findSellableWithStock(LocalDate.now());
            view.showProducts(stock);
            int productId = ConsoleReader.readInt("Product id to add (0 to finish): ");

            if (productId == 0) {
                adding = false;
            } else {
                addProduct(cart, productId);
            }
        }

        if (cart.isEmpty()) {
            view.showMessage("Empty sale, nothing was saved.");
            return;
        }
        view.showLines(cart.getLines());
        view.showCartTotal(cart.total());
        if (!ConsoleReader.confirm("Confirm the sale?")) {
            view.showMessage("Sale cancelled, nothing was saved.");
            return;
        }

        Company company = companyDao.find();
        SaleResult result = saleDao.createSale(customer.get(), cart.getLines(),
                UserSession.getUsername(), company);
        Path file = renderer.render(result.sale(), result.invoice(), company, INVOICE_FOLDER);
        view.showMessage("Sale " + result.sale().getNumber() + " confirmed. Stock updated.");
        view.showInvoiceCreated(result.invoice(), file);
    }

    private void addProduct(SaleCart cart, int productId) throws SQLException {
        Optional<Product> product = productDao.findById(productId);
        if (product.isEmpty()) {
            view.showMessage("Product not found.");
            return;
        }
        BigDecimal quantity = ConsoleReader.readPositiveDecimal(
                "Quantity of " + product.get().name() + " (" + product.get().unit() + "): ");

        try {
            List<SaleLine> lines = allocator.allocate(product.get(), quantity, cart);
            cart.add(lines);
            view.showMessage("Added using FEFO (soonest expiry first):");
            view.showLines(lines);
        } catch (IllegalStateException e) {
            view.showMessage(e.getMessage());
        }
    }

    // ---------- queries and updates ----------

    private void showSaleDetail() throws SQLException, IOException {
        Optional<Sale> sale = saleDao.findById(ConsoleReader.readInt("Sale id: "));
        if (sale.isEmpty()) {
            view.showMessage("Sale not found.");
            return;
        }
        Optional<Invoice> invoice = invoiceDao.findBySaleId(sale.get().getId());
        view.showSaleDetail(sale.get(), invoice);

        if (invoice.isPresent() && ConsoleReader.confirm("Generate the invoice image again?")) {
            Path file = renderer.render(sale.get(), invoice.get(), companyDao.find(), INVOICE_FOLDER);
            view.showInvoiceCreated(invoice.get(), file);
        }
    }

    private void sendToPreparation() throws SQLException {
        if (!allowed()) {
            return;
        }
        int saleId = ConsoleReader.readInt("Sale id: ");
        if (saleDao.sendToPreparation(saleId)) {
            view.showMessage("The sale is now In Preparation and appears in the dispatch panel.");
        } else {
            view.showMessage("Only sales in Registered status can be sent to preparation.");
        }
    }

    private void registerCustomer() throws SQLException {
        if (!allowed()) {
            return;
        }
        String name = ConsoleReader.readRequired("Name: ");
        String document = ConsoleReader.readRequired("NIT or ID number: ");
        if (customerDao.existsByDocument(document)) {
            view.showMessage("A customer with that document already exists.");
            return;
        }
        String email = ConsoleReader.readLine("Email: ");
        String phone = ConsoleReader.readLine("Phone: ");
        String address = ConsoleReader.readRequired("Address: ");
        System.out.println("1. Wholesale  2. Retail");
        int type = ConsoleReader.readInt("Customer type: ");
        if (type != 1 && type != 2) {
            view.showMessage("Invalid type.");
            return;
        }

        int id = customerDao.insert(new Customer(0, name, document, email, phone, address, type,
                type == 1 ? "Mayorista" : "Minorista"));
        view.showMessage("Customer registered with id " + id + ".");
    }
}
