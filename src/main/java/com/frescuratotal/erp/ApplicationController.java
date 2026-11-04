package com.frescuratotal.erp;

import com.frescuratotal.erp.common.AppModule;
import com.frescuratotal.erp.common.AppLogger;
import com.frescuratotal.erp.common.ConsoleReader;
import com.frescuratotal.erp.common.DatabaseConnection;
import com.frescuratotal.erp.common.Role;
import com.frescuratotal.erp.common.UserSession;
import com.frescuratotal.erp.inventory.controller.InventoryController;
import com.frescuratotal.erp.logistics.controller.LogisticsController;
import com.frescuratotal.erp.sales.controller.SalesController;
import com.frescuratotal.erp.suppliers.controller.SupplierController;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Controller of the whole application: checks the database, asks who is using the system,
 * runs the daily expiry process and opens the module chosen from the main menu.
 */
public class ApplicationController {

    public void run() {
        AppLogger.info("Application started");
        System.out.println("==========================================");
        System.out.println("   ERP - FRESCURA TOTAL (food warehouse)  ");
        System.out.println("==========================================");

        if (!databaseIsReachable()) {
            return;
        }

        try {
            chooseUser();
            runDailyExpiryCheck();
            mainMenu();
        } catch (IllegalStateException e) {
            AppLogger.info("Application input closed");
            System.out.println();
            System.out.println("Input closed. Goodbye!");
        }
        AppLogger.info("Application stopped");
    }

    private boolean databaseIsReachable() {
        try (Connection connection = DatabaseConnection.getInstance().getConnection()) {
            return connection.isValid(3);
        } catch (SQLException e) {
            AppLogger.error("Database connection failed", e);
            System.err.println("Could not connect to the database: " + e.getMessage());
            System.err.println("Check that MySQL is running, docs/database.sql was executed and the ERP_DB_* settings are correct.");
            return false;
        }
    }

    private void chooseUser() {
        System.out.println();
        System.out.println("Who is using the system?");
        Role[] roles = Role.values();
        for (int i = 0; i < roles.length; i++) {
            System.out.println((i + 1) + ". " + roles[i].getLabel());
        }

        int choice = 0;
        while (choice < 1 || choice > roles.length) {
            choice = ConsoleReader.readInt("Choose your role: ");
        }
        String name = ConsoleReader.readRequired("Your name: ");
        UserSession.start(name, roles[choice - 1]);
        System.out.println("Welcome, " + name + " (" + roles[choice - 1].getLabel() + ").");
    }

    /** Simulates the process that runs once a day to update the status of every lot. */
    private void runDailyExpiryCheck() {
        try {
            int[] changes = new InventoryController().refreshExpiryStatuses();
            System.out.println("Daily expiry check: " + changes[0] + " lots marked Expired, "
                    + changes[1] + " lots marked Near Expiry.");
        } catch (SQLException e) {
            AppLogger.error("Daily expiry check failed", e);
            System.err.println("The expiry check failed: " + e.getMessage());
        }
    }

    private void mainMenu() {
        boolean running = true;

        while (running) {
            List<AppModule> modules = new ArrayList<>();
            for (AppModule module : AppModule.values()) {
                if (UserSession.getRole().canAccess(module)) {
                    modules.add(module);
                }
            }

            System.out.println();
            System.out.println("=== MAIN MENU (" + UserSession.getRole().getLabel() + ") ===");
            for (int i = 0; i < modules.size(); i++) {
                System.out.println((i + 1) + ". " + describe(modules.get(i)));
            }
            int changeUser = modules.size() + 1;
            System.out.println(changeUser + ". Change user");
            System.out.println("0. Exit");

            int option = ConsoleReader.readInt("Choose an option: ");
            if (option == 0) {
                running = false;
            } else if (option == changeUser) {
                chooseUser();
            } else if (option >= 1 && option <= modules.size()) {
                open(modules.get(option - 1));
            } else {
                System.out.println("Invalid option, try again.");
            }
        }
        System.out.println("Goodbye!");
    }

    private void open(AppModule module) {
        switch (module) {
            case INVENTORY -> new InventoryController().start();
            case SUPPLIERS -> new SupplierController().start();
            case SALES -> new SalesController().start();
            case LOGISTICS -> new LogisticsController().start();
        }
    }

    private String describe(AppModule module) {
        return switch (module) {
            case INVENTORY -> "Inventory and quality";
            case SUPPLIERS -> "Suppliers and purchases";
            case SALES -> "Sales and invoicing";
            case LOGISTICS -> "Logistics and deliveries";
        };
    }
}
