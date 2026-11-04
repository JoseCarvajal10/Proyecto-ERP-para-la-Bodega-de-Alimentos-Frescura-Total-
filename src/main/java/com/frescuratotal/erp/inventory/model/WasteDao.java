package com.frescuratotal.erp.inventory.model;

import com.frescuratotal.erp.common.DatabaseConnection;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Data Access Object for waste (merma) records. */
public class WasteDao {

    /**
     * Removes units from a lot and records the reason, all in a single transaction:
     * either both things happen or none of them.
     */
    public void register(int lotId, BigDecimal quantity, String reason, String user)
            throws SQLException {
        try (Connection connection = DatabaseConnection.getInstance().getConnection()) {
            connection.setAutoCommit(false);
            try {
                register(connection, lotId, null, quantity, reason, user);
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    /** Same as above but inside a transaction opened by the caller. */
    static void register(Connection connection, int lotId, Integer inspectionId,
                         BigDecimal quantity, String reason, String user) throws SQLException {
        BigDecimal current = lockAndReadQuantity(connection, lotId);
        if (quantity.compareTo(current) > 0) {
            throw new IllegalArgumentException("The lot only has " + current + " units left.");
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE lotes_inventario SET cantidad = cantidad - ? WHERE id_lote = ?")) {
            statement.setBigDecimal(1, quantity);
            statement.setInt(2, lotId);
            statement.executeUpdate();
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO registro_mermas (id_lote, id_inspeccion, cantidad_perdida, motivo, "
                + "fecha_registro, usuario_registro) VALUES (?, ?, ?, ?, ?, ?)")) {
            statement.setInt(1, lotId);
            if (inspectionId == null) {
                statement.setNull(2, java.sql.Types.INTEGER);
            } else {
                statement.setInt(2, inspectionId);
            }
            statement.setBigDecimal(3, quantity);
            statement.setString(4, reason);
            statement.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now()));
            statement.setString(6, user);
            statement.executeUpdate();
        }
    }

    /** Locks the lot row until the transaction ends and returns its current quantity. */
    static BigDecimal lockAndReadQuantity(Connection connection, int lotId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT cantidad FROM lotes_inventario WHERE id_lote = ? FOR UPDATE")) {
            statement.setInt(1, lotId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalArgumentException("Lot " + lotId + " does not exist.");
                }
                return result.getBigDecimal("cantidad");
            }
        }
    }

    /** Most recent waste records, newest first. */
    public List<WasteRecord> findRecent(int limit) throws SQLException {
        String sql = "SELECT m.id_registro, m.id_lote, l.numero_lote, p.nombre AS producto, "
                + "m.cantidad_perdida, m.motivo, m.fecha_registro, m.usuario_registro "
                + "FROM registro_mermas m "
                + "JOIN lotes_inventario l ON l.id_lote = m.id_lote "
                + "JOIN productos p ON p.id_producto = l.id_producto "
                + "ORDER BY m.fecha_registro DESC, m.id_registro DESC LIMIT ?";
        List<WasteRecord> records = new ArrayList<>();

        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    records.add(new WasteRecord(
                            result.getInt("id_registro"),
                            result.getInt("id_lote"),
                            result.getString("numero_lote"),
                            result.getString("producto"),
                            result.getBigDecimal("cantidad_perdida"),
                            result.getString("motivo"),
                            result.getTimestamp("fecha_registro").toLocalDateTime(),
                            result.getString("usuario_registro")));
                }
            }
        }
        return records;
    }
}
