package com.frescuratotal.erp.logistics.controller;

import com.frescuratotal.erp.common.ConsoleReader;
import com.frescuratotal.erp.common.AppLogger;
import com.frescuratotal.erp.common.Role;
import com.frescuratotal.erp.common.UserSession;
import com.frescuratotal.erp.logistics.model.DispatchOrder;
import com.frescuratotal.erp.logistics.model.Driver;
import com.frescuratotal.erp.logistics.model.LogisticsDao;
import com.frescuratotal.erp.logistics.model.Vehicle;
import com.frescuratotal.erp.logistics.view.LogisticsView;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Controller of the logistics module: dispatch panel, routes and delivery tracking. */
public class LogisticsController {

    private final LogisticsDao logisticsDao = new LogisticsDao();
    private final LogisticsView view = new LogisticsView();

    public void start() {
        boolean running = true;

        while (running) {
            int option = view.showMenu();

            try {
                switch (option) {
                    case 1 -> view.showDispatchPanel(logisticsDao.findPendingDispatch());
                    case 2 -> createRoute();
                    case 3 -> startRoute();
                    case 4 -> registerDelivery();
                    case 5 -> view.showRoutes(logisticsDao.findRoutes(20));
                    case 6 -> showRouteDetails();
                    case 0 -> running = false;
                    default -> view.showMessage("Invalid option, try again.");
                }
            } catch (SQLException e) {
                AppLogger.error("Logistics database operation failed", e);
                view.showError("Database error: " + e.getMessage());
            } catch (IllegalArgumentException | IllegalStateException e) {
                view.showError(e.getMessage());
            }
        }
    }

    private boolean allowed() {
        if (UserSession.can(Role.LOGISTICS)) {
            return true;
        }
        view.showMessage("Your role (" + UserSession.getRole().getLabel() + ") cannot do this.");
        return false;
    }

    private void createRoute() throws SQLException {
        if (!allowed()) {
            return;
        }
        List<DispatchOrder> pending = logisticsDao.findPendingDispatch();
        view.showDispatchPanel(pending);
        if (pending.isEmpty()) {
            return;
        }

        List<Vehicle> vehicles = logisticsDao.findAvailableVehicles();
        List<Driver> drivers = logisticsDao.findAvailableDrivers();
        if (vehicles.isEmpty() || drivers.isEmpty()) {
            view.showMessage("There is no free vehicle or driver right now.");
            return;
        }

        view.showVehicles(vehicles);
        int vehicleId = ConsoleReader.readInt("Vehicle id: ");
        if (vehicles.stream().noneMatch(v -> v.id() == vehicleId)) {
            view.showMessage("That vehicle is not available.");
            return;
        }
        view.showDrivers(drivers);
        int driverId = ConsoleReader.readInt("Driver id: ");
        if (drivers.stream().noneMatch(d -> d.id() == driverId)) {
            view.showMessage("That driver is not available.");
            return;
        }

        String name = ConsoleReader.readRequired("Route name: ");
        List<Integer> saleIds = readSaleIds();
        if (saleIds.isEmpty()) {
            view.showMessage("You must choose at least one sale.");
            return;
        }

        int routeId = logisticsDao.createRoute(name, vehicleId, driverId, saleIds, UserSession.getUsername());
        view.showMessage("Route " + routeId + " created with " + saleIds.size()
                + " orders. Start it when the vehicle leaves.");
    }

    /** Reads sale ids written like 3,5,9. */
    private List<Integer> readSaleIds() {
        String line = ConsoleReader.readRequired("Sale ids separated by commas (e.g. 51,52): ");
        List<Integer> ids = new ArrayList<>();
        for (String part : line.split(",")) {
            try {
                int id = Integer.parseInt(part.trim());
                if (!ids.contains(id)) {
                    ids.add(id);
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("'" + part.trim() + "' is not a valid sale id.");
            }
        }
        return ids;
    }

    private void startRoute() throws SQLException {
        if (!allowed()) {
            return;
        }
        view.showRoutes(logisticsDao.findRoutes(20));
        int routeId = ConsoleReader.readInt("Route id to start: ");
        int orders = logisticsDao.startRoute(routeId);
        view.showMessage("Route started: " + orders + " orders are now In Transit.");
    }

    private void registerDelivery() throws SQLException {
        if (!allowed()) {
            return;
        }
        int saleId = ConsoleReader.readInt("Sale id delivered: ");
        String receivedBy = ConsoleReader.readRequired("Received by (name): ");
        boolean completed = logisticsDao.markDelivered(saleId, receivedBy);

        view.showMessage("Delivery registered. The sale is now Delivered.");
        if (completed) {
            view.showMessage("That was the last order: the route is Completed.");
        }
    }

    private void showRouteDetails() throws SQLException {
        view.showRoutes(logisticsDao.findRoutes(20));
        int routeId = ConsoleReader.readInt("Route id: ");
        view.showStops(logisticsDao.findStops(routeId));
    }
}
