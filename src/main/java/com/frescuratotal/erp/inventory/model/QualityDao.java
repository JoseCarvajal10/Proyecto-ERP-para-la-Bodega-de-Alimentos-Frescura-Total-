package com.frescuratotal.erp.inventory.model;

import com.frescuratotal.erp.common.DatabaseConnection;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * Data Access Object for the quality workflow:
 * quarantine of a lot and the inspection that decides its destiny.
 */
public class QualityDao {

    /** Blocks a whole lot for sale until an inspector decides what to do with it. */
    public void quarantine(int lotId, String reason, String user) throws SQLException {
        try (Connection connection = DatabaseConnection.getInstance().getConnection()) {
            connection.setAutoCommit(false);
            try {
                WasteDao.lockAndReadQuantity(connection, lotId);

                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE lotes_inventario SET id_estado = ? WHERE id_lote = ?")) {
                    statement.setInt(1, LotStatus.IN_QUARANTINE.getId());
                    statement.setInt(2, lotId);
                    statement.executeUpdate();
                }

                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO registro_cuarentena (id_lote, motivo, usuario, fecha_registro) "
                        + "VALUES (?, ?, ?, ?)")) {
                    statement.setInt(1, lotId);
                    statement.setString(2, reason);
                    statement.setString(3, user);
                    statement.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()));
                    statement.executeUpdate();
                }
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    /**
     * Records the decision of the quality inspector on a quarantined lot.
     * Approved: the lot goes back to sale with the status its expiration date deserves.
     * Rejected: every remaining unit becomes waste.
     */
    public QualityInspection inspect(int lotId, InspectionResult result, String inspector,
                                     String rejectionReason, String notes) throws SQLException {
        try (Connection connection = DatabaseConnection.getInstance().getConnection()) {
            connection.setAutoCommit(false);
            try {
                BigDecimal quantity = WasteDao.lockAndReadQuantity(connection, lotId);
                LocalDateTime now = LocalDateTime.now();
                int inspectionId = insertInspection(connection, lotId, result, inspector,
                        rejectionReason, notes, now);

                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE registro_cuarentena SET resuelta = TRUE, fecha_resolucion = ?, "
                        + "notas_resolucion = ? WHERE id_lote = ? AND resuelta = FALSE")) {
                    statement.setTimestamp(1, Timestamp.valueOf(now));
                    statement.setString(2, result == InspectionResult.APPROVED
                            ? "Inspection approved: back to sale" : "Inspection rejected: written off");
                    statement.setInt(3, lotId);
                    statement.executeUpdate();
                }

                if (result == InspectionResult.APPROVED) {
                    returnToSale(connection, lotId);
                } else if (quantity.signum() > 0) {
                    WasteDao.register(connection, lotId, inspectionId, quantity,
                            "Lot rejected in quality inspection: " + rejectionReason, inspector);
                }
                connection.commit();
                return new QualityInspection(inspectionId, lotId, now, result, inspector,
                        rejectionReason, notes);
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    private int insertInspection(Connection connection, int lotId, InspectionResult result,
                                 String inspector, String rejectionReason, String notes,
                                 LocalDateTime now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO inspecciones_calidad (id_lote, fecha_inspeccion, resultado, "
                + "nombre_inspector, motivo_rechazo, notas) VALUES (?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, lotId);
            statement.setTimestamp(2, Timestamp.valueOf(now));
            statement.setString(3, result.getDbValue());
            statement.setString(4, inspector);
            statement.setString(5, rejectionReason);
            statement.setString(6, notes);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    /** Puts an approved lot back on sale, choosing its status from its expiration date. */
    private void returnToSale(Connection connection, int lotId) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT fecha_vencimiento FROM lotes_inventario WHERE id_lote = ?")) {
            select.setInt(1, lotId);
            try (ResultSet result = select.executeQuery()) {
                result.next();
                Date expiration = result.getDate("fecha_vencimiento");
                LotStatus status = Lot.statusFor(expiration.toLocalDate());
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE lotes_inventario SET id_estado = ? WHERE id_lote = ?")) {
                    update.setInt(1, status.getId());
                    update.setInt(2, lotId);
                    update.executeUpdate();
                }
            }
        }
    }
}
