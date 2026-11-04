package com.frescuratotal.erp.logistics.view;

import com.frescuratotal.erp.common.ConsoleReader;
import com.frescuratotal.erp.logistics.model.DeliveryRoute;
import com.frescuratotal.erp.logistics.model.DispatchOrder;
import com.frescuratotal.erp.logistics.model.Driver;
import com.frescuratotal.erp.logistics.model.RouteStop;
import com.frescuratotal.erp.logistics.model.Vehicle;
import java.util.List;

/** Console view of the logistics module. */
public class LogisticsView {

    public int showMenu() {
        System.out.println();
        System.out.println("=== LOGISTICS MODULE ===");
        System.out.println("1. Dispatch panel (sales In Preparation)");
        System.out.println("2. Create a route (assign vehicle, driver and orders)");
        System.out.println("3. Start a route (orders go In Transit)");
        System.out.println("4. Register a delivery");
        System.out.println("5. List routes");
        System.out.println("6. Route details");
        System.out.println("0. Back");
        return ConsoleReader.readInt("Choose an option: ");
    }

    public void showDispatchPanel(List<DispatchOrder> orders) {
        System.out.println();
        System.out.println("--- DISPATCH PANEL ---");
        if (orders.isEmpty()) {
            System.out.println("There are no sales waiting for a vehicle.");
            return;
        }
        System.out.printf("%-4s %-16s %-28s %-40s %14s%n", "ID", "Sale", "Customer", "Address", "Total");
        System.out.println("-".repeat(106));
        for (DispatchOrder order : orders) {
            System.out.printf("%-4d %-16s %-28s %-40s %,14.2f%n", order.saleId(), order.saleNumber(),
                    order.customerName(), cut(order.address(), 40), order.total());
        }
    }

    public void showVehicles(List<Vehicle> vehicles) {
        System.out.println();
        System.out.printf("%-4s %-8s %-24s %10s%n", "ID", "Plate", "Model", "Capacity");
        for (Vehicle vehicle : vehicles) {
            System.out.printf("%-4d %-8s %-24s %10.0f%n", vehicle.id(), vehicle.plate(), vehicle.model(),
                    vehicle.capacity());
        }
    }

    public void showDrivers(List<Driver> drivers) {
        System.out.println();
        System.out.printf("%-4s %-28s %-16s%n", "ID", "Driver", "License");
        for (Driver driver : drivers) {
            System.out.printf("%-4d %-28s %-16s%n", driver.id(), driver.name(), driver.licenseNumber());
        }
    }

    public void showRoutes(List<DeliveryRoute> routes) {
        System.out.println();
        System.out.printf("%-4s %-26s %-12s %-8s %-24s %s%n", "ID", "Route", "Status", "Plate", "Driver", "Delivered");
        System.out.println("-".repeat(92));
        for (DeliveryRoute route : routes) {
            System.out.printf("%-4d %-26s %-12s %-8s %-24s %d/%d%n", route.id(), route.name(),
                    route.status().getLabel(), route.vehiclePlate(), route.driverName(),
                    route.delivered(), route.stops());
        }
    }

    public void showStops(List<RouteStop> stops) {
        System.out.println();
        System.out.printf("%-3s %-5s %-16s %-28s %-17s %s%n", "#", "Sale", "Number", "Customer", "Delivered", "Received by");
        for (RouteStop stop : stops) {
            System.out.printf("%-3d %-5d %-16s %-28s %-17s %s%n", stop.order(), stop.saleId(),
                    stop.saleNumber(), stop.customerName(),
                    stop.deliveredAt() == null ? "pending"
                            : stop.deliveredAt().toString().replace('T', ' ').substring(0, 16),
                    stop.receivedBy() == null ? "" : stop.receivedBy());
        }
    }

    public void showMessage(String message) {
        System.out.println(message);
    }

    public void showError(String message) {
        System.err.println(message);
    }

    private String cut(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
