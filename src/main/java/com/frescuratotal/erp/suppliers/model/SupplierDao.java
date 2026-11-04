package com.frescuratotal.erp.suppliers.model;

import com.frescuratotal.erp.common.DatabaseConnection;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Data Access Object for suppliers and their product catalog. */
public class SupplierDao {

    private static final String BASE_QUERY =
            "SELECT id_proveedor, nombre, nit, persona_contacto, email, telefono, direccion, "
            + "activo, fecha_registro FROM proveedores ";

    public List<Supplier> findAll() throws SQLException {
        List<Supplier> suppliers = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(BASE_QUERY + "ORDER BY nombre");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                suppliers.add(mapRow(result));
            }
        }
        return suppliers;
    }

    public Optional<Supplier> findById(int supplierId) throws SQLException {
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     BASE_QUERY + "WHERE id_proveedor = ?")) {
            statement.setInt(1, supplierId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(mapRow(result)) : Optional.empty();
            }
        }
    }

    public boolean existsByNit(String nit) throws SQLException {
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT 1 FROM proveedores WHERE nit = ?")) {
            statement.setString(1, nit);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    /** Saves a new supplier and returns the id given by the database. */
    public int insert(Supplier supplier) throws SQLException {
        String sql = "INSERT INTO proveedores (nombre, nit, persona_contacto, email, telefono, "
                + "direccion, activo, fecha_registro) VALUES (?, ?, ?, ?, ?, ?, TRUE, ?)";
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, supplier.name());
            statement.setString(2, supplier.nit());
            statement.setString(3, supplier.contactPerson());
            statement.setString(4, supplier.email());
            statement.setString(5, supplier.phone());
            statement.setString(6, supplier.address());
            statement.setTimestamp(7, Timestamp.valueOf(LocalDateTime.now()));
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    /** Adds a product to what a supplier is able to provide. Returns false if it was already there. */
    public boolean addToCatalog(int supplierId, int productId) throws SQLException {
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT IGNORE INTO productos_proveedor (id_proveedor, id_producto) VALUES (?, ?)")) {
            statement.setInt(1, supplierId);
            statement.setInt(2, productId);
            return statement.executeUpdate() > 0;
        }
    }

    /** Reliability numbers built from the delivery history of the supplier. */
    public SupplierStats findStats(int supplierId) throws SQLException {
        try (Connection connection = DatabaseConnection.getInstance().getConnection()) {
            int lots = 0;
            BigDecimal received = BigDecimal.ZERO;
            BigDecimal waste = BigDecimal.ZERO;
            int rejected = 0;

            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT COUNT(*), COALESCE(SUM(cantidad_inicial), 0) FROM lotes_inventario "
                    + "WHERE id_proveedor = ?")) {
                statement.setInt(1, supplierId);
                try (ResultSet result = statement.executeQuery()) {
                    result.next();
                    lots = result.getInt(1);
                    received = result.getBigDecimal(2);
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT COALESCE(SUM(m.cantidad_perdida), 0) FROM registro_mermas m "
                    + "JOIN lotes_inventario l ON l.id_lote = m.id_lote WHERE l.id_proveedor = ?")) {
                statement.setInt(1, supplierId);
                try (ResultSet result = statement.executeQuery()) {
                    result.next();
                    waste = result.getBigDecimal(1);
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT COUNT(*) FROM inspecciones_calidad i "
                    + "JOIN lotes_inventario l ON l.id_lote = i.id_lote "
                    + "WHERE l.id_proveedor = ? AND i.resultado = 'Rechazado'")) {
                statement.setInt(1, supplierId);
                try (ResultSet result = statement.executeQuery()) {
                    result.next();
                    rejected = result.getInt(1);
                }
            }
            return new SupplierStats(lots, received, waste, rejected);
        }
    }

    private Supplier mapRow(ResultSet result) throws SQLException {
        return new Supplier(
                result.getInt("id_proveedor"),
                result.getString("nombre"),
                result.getString("nit"),
                result.getString("persona_contacto"),
                result.getString("email"),
                result.getString("telefono"),
                result.getString("direccion"),
                result.getBoolean("activo"),
                result.getTimestamp("fecha_registro").toLocalDateTime());
    }
}
