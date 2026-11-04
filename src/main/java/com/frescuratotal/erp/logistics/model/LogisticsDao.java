package com.frescuratotal.erp.logistics.model;

import com.frescuratotal.erp.common.DatabaseConnection;
import com.frescuratotal.erp.sales.model.SaleStatus;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Data Access Object for dispatch, routes, vehicles and drivers. */
public class LogisticsDao {

    /** Dispatch panel: sales In Preparation that are not in any active route yet. */
    public List<DispatchOrder> findPendingDispatch() throws SQLException {
        String sql = "SELECT v.id_venta, v.numero_venta, c.nombre, c.direccion, v.monto_total "
                + "FROM ventas v JOIN clientes c ON c.id_cliente = v.id_cliente "
                + "WHERE v.id_estado = ? AND NOT EXISTS (SELECT 1 FROM ventas_ruta vr "
                + "JOIN rutas_entrega r ON r.id_ruta = vr.id_ruta "
                + "WHERE vr.id_venta = v.id_venta AND r.estado <> 'Cancelada') "
                + "ORDER BY v.fecha_venta";
        List<DispatchOrder> orders = new ArrayList<>();

        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, SaleStatus.IN_PREPARATION.getId());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    orders.add(new DispatchOrder(result.getInt("id_venta"), result.getString("numero_venta"),
                            result.getString("nombre"), result.getString("direccion"),
                            result.getBigDecimal("monto_total")));
                }
            }
        }
        return orders;
    }

    /** Active vehicles that are not assigned to a planned or running route. */
    public List<Vehicle> findAvailableVehicles() throws SQLException {
        String sql = "SELECT id_vehiculo, placa, modelo, capacidad, estado FROM vehiculos "
                + "WHERE estado = 'Activo' AND id_vehiculo NOT IN (SELECT a.id_vehiculo "
                + "FROM asignaciones_entrega a JOIN rutas_entrega r ON r.id_ruta = a.id_ruta "
                + "WHERE r.estado IN ('Planificada', 'En Transito')) ORDER BY placa";
        List<Vehicle> vehicles = new ArrayList<>();

        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                vehicles.add(new Vehicle(result.getInt("id_vehiculo"), result.getString("placa"),
                        result.getString("modelo"), result.getBigDecimal("capacidad"),
                        result.getString("estado")));
            }
        }
        return vehicles;
    }

    /** Active drivers that are not assigned to a planned or running route. */
    public List<Driver> findAvailableDrivers() throws SQLException {
        String sql = "SELECT id_conductor, nombre, numero_licencia, telefono FROM conductores "
                + "WHERE activo = TRUE AND id_conductor NOT IN (SELECT a.id_conductor "
                + "FROM asignaciones_entrega a JOIN rutas_entrega r ON r.id_ruta = a.id_ruta "
                + "WHERE r.estado IN ('Planificada', 'En Transito')) ORDER BY nombre";
        List<Driver> drivers = new ArrayList<>();

        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                drivers.add(new Driver(result.getInt("id_conductor"), result.getString("nombre"),
                        result.getString("numero_licencia"), result.getString("telefono")));
            }
        }
        return drivers;
    }

    /**
     * Creates a route, assigns the vehicle and driver and adds the orders, in one transaction.
     *
     * @return the id of the new route
     */
    public int createRoute(String name, int vehicleId, int driverId, List<Integer> saleIds, String user)
            throws SQLException {
        try (Connection connection = DatabaseConnection.getInstance().getConnection()) {
            connection.setAutoCommit(false);
            try {
                LocalDateTime now = LocalDateTime.now();
                int routeId = insertRoute(connection, name, user, now);

                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO asignaciones_entrega (id_ruta, id_vehiculo, id_conductor, "
                        + "fecha_asignacion, usuario_asignacion) VALUES (?, ?, ?, ?, ?)")) {
                    statement.setInt(1, routeId);
                    statement.setInt(2, vehicleId);
                    statement.setInt(3, driverId);
                    statement.setTimestamp(4, Timestamp.valueOf(now));
                    statement.setString(5, user);
                    statement.executeUpdate();
                }

                int order = 1;
                for (int saleId : saleIds) {
                    addStop(connection, routeId, saleId, order++);
                }
                connection.commit();
                return routeId;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    /** The vehicle leaves: the route goes In Transit and every order of it too. */
    public int startRoute(int routeId) throws SQLException {
        try (Connection connection = DatabaseConnection.getInstance().getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE rutas_entrega SET estado = 'En Transito', hora_salida_real = ? "
                        + "WHERE id_ruta = ? AND estado = 'Planificada'")) {
                    statement.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
                    statement.setInt(2, routeId);
                    if (statement.executeUpdate() == 0) {
                        throw new IllegalStateException("Only a planned route can start.");
                    }
                }

                int orders;
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE ventas SET id_estado = ? WHERE id_venta IN "
                        + "(SELECT id_venta FROM ventas_ruta WHERE id_ruta = ?)")) {
                    statement.setInt(1, SaleStatus.IN_TRANSIT.getId());
                    statement.setInt(2, routeId);
                    orders = statement.executeUpdate();
                }
                connection.commit();
                return orders;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    /**
     * Registers that a customer received an order. When it was the last order of the route,
     * the route is completed.
     *
     * @return true if the route was completed with this delivery
     */
    public boolean markDelivered(int saleId, String receivedBy) throws SQLException {
        try (Connection connection = DatabaseConnection.getInstance().getConnection()) {
            connection.setAutoCommit(false);
            try {
                int stopId;
                int routeId;
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT vr.id, vr.id_ruta FROM ventas_ruta vr "
                        + "JOIN rutas_entrega r ON r.id_ruta = vr.id_ruta "
                        + "WHERE vr.id_venta = ? AND r.estado = 'En Transito' "
                        + "AND vr.fecha_entrega IS NULL FOR UPDATE")) {
                    statement.setInt(1, saleId);
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            throw new IllegalArgumentException("That sale is not waiting for delivery in a route in transit.");
                        }
                        stopId = result.getInt("id");
                        routeId = result.getInt("id_ruta");
                    }
                }

                LocalDateTime now = LocalDateTime.now();
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE ventas_ruta SET fecha_entrega = ?, recibido_por = ? WHERE id = ?")) {
                    statement.setTimestamp(1, Timestamp.valueOf(now));
                    statement.setString(2, receivedBy);
                    statement.setInt(3, stopId);
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE ventas SET id_estado = ? WHERE id_venta = ?")) {
                    statement.setInt(1, SaleStatus.DELIVERED.getId());
                    statement.setInt(2, saleId);
                    statement.executeUpdate();
                }

                boolean completed = false;
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM ventas_ruta WHERE id_ruta = ? AND fecha_entrega IS NULL")) {
                    statement.setInt(1, routeId);
                    try (ResultSet result = statement.executeQuery()) {
                        result.next();
                        completed = result.getInt(1) == 0;
                    }
                }
                if (completed) {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "UPDATE rutas_entrega SET estado = 'Completada', hora_llegada_real = ? "
                            + "WHERE id_ruta = ?")) {
                        statement.setTimestamp(1, Timestamp.valueOf(now));
                        statement.setInt(2, routeId);
                        statement.executeUpdate();
                    }
                }
                connection.commit();
                return completed;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    public List<DeliveryRoute> findRoutes(int limit) throws SQLException {
        String sql = "SELECT r.id_ruta, r.nombre_ruta, r.estado, r.fecha_creacion, "
                + "ve.placa, co.nombre AS conductor, "
                + "(SELECT COUNT(*) FROM ventas_ruta s WHERE s.id_ruta = r.id_ruta) AS paradas, "
                + "(SELECT COUNT(*) FROM ventas_ruta s WHERE s.id_ruta = r.id_ruta AND s.fecha_entrega IS NOT NULL) AS entregadas "
                + "FROM rutas_entrega r "
                + "LEFT JOIN asignaciones_entrega a ON a.id_ruta = r.id_ruta "
                + "LEFT JOIN vehiculos ve ON ve.id_vehiculo = a.id_vehiculo "
                + "LEFT JOIN conductores co ON co.id_conductor = a.id_conductor "
                + "ORDER BY r.id_ruta DESC LIMIT ?";
        List<DeliveryRoute> routes = new ArrayList<>();

        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    routes.add(new DeliveryRoute(result.getInt("id_ruta"), result.getString("nombre_ruta"),
                            RouteStatus.fromDbValue(result.getString("estado")),
                            result.getTimestamp("fecha_creacion").toLocalDateTime(),
                            result.getString("placa"), result.getString("conductor"),
                            result.getInt("paradas"), result.getInt("entregadas")));
                }
            }
        }
        return routes;
    }

    public List<RouteStop> findStops(int routeId) throws SQLException {
        String sql = "SELECT vr.id_venta, v.numero_venta, c.nombre, vr.orden_entrega, vr.direccion_destino, "
                + "vr.fecha_entrega, vr.recibido_por FROM ventas_ruta vr "
                + "JOIN ventas v ON v.id_venta = vr.id_venta "
                + "JOIN clientes c ON c.id_cliente = v.id_cliente "
                + "WHERE vr.id_ruta = ? ORDER BY vr.orden_entrega";
        List<RouteStop> stops = new ArrayList<>();

        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, routeId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    Timestamp delivered = result.getTimestamp("fecha_entrega");
                    stops.add(new RouteStop(result.getInt("id_venta"), result.getString("numero_venta"),
                            result.getString("nombre"), result.getInt("orden_entrega"),
                            result.getString("direccion_destino"),
                            delivered == null ? null : delivered.toLocalDateTime(),
                            result.getString("recibido_por")));
                }
            }
        }
        return stops;
    }

    private int insertRoute(Connection connection, String name, String user, LocalDateTime now)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO rutas_entrega (nombre_ruta, fecha_creacion, usuario_creacion, "
                + "hora_salida_estimada, hora_llegada_estimada, estado) VALUES (?, ?, ?, ?, ?, 'Planificada')",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, name);
            statement.setTimestamp(2, Timestamp.valueOf(now));
            statement.setString(3, user);
            statement.setTimestamp(4, Timestamp.valueOf(now.plusHours(1)));
            statement.setTimestamp(5, Timestamp.valueOf(now.plusHours(5)));
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    /** Adds an order to the route after checking it is really waiting in the dispatch panel. */
    private void addStop(Connection connection, int routeId, int saleId, int order) throws SQLException {
        String address;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT c.direccion, v.id_estado FROM ventas v "
                + "JOIN clientes c ON c.id_cliente = v.id_cliente WHERE v.id_venta = ? FOR UPDATE")) {
            statement.setInt(1, saleId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next() || result.getInt("id_estado") != SaleStatus.IN_PREPARATION.getId()) {
                    throw new IllegalArgumentException("Sale " + saleId + " is not In Preparation.");
                }
                address = result.getString("direccion");
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM ventas_ruta vr JOIN rutas_entrega r ON r.id_ruta = vr.id_ruta "
                + "WHERE vr.id_venta = ? AND r.estado <> 'Cancelada'")) {
            statement.setInt(1, saleId);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    throw new IllegalArgumentException("Sale " + saleId + " already belongs to a route.");
                }
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO ventas_ruta (id_ruta, id_venta, orden_entrega, direccion_destino) VALUES (?, ?, ?, ?)")) {
            statement.setInt(1, routeId);
            statement.setInt(2, saleId);
            statement.setInt(3, order);
            statement.setString(4, address);
            statement.executeUpdate();
        }
    }
}
