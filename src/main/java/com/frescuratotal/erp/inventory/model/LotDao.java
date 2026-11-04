package com.frescuratotal.erp.inventory.model;

import com.frescuratotal.erp.common.DatabaseConnection;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for inventory lots.
 * It is the only class that writes SQL about the lotes_inventario table.
 */
public class LotDao {

    /** Fills the parameters (?) of a prepared statement. */
    @FunctionalInterface
    private interface Binder {
        void bind(PreparedStatement statement) throws SQLException;
    }

    /** Common SELECT: joins the product and supplier names for traceability. */
    private static final String BASE_QUERY =
            "SELECT l.id_lote, l.numero_lote, l.id_producto, p.nombre AS producto, "
            + "l.id_proveedor, s.nombre AS proveedor, l.fecha_ingreso, l.fecha_vencimiento, "
            + "l.cantidad_inicial, l.cantidad, l.precio_compra, l.precio_venta, "
            + "l.pasillo, l.estante, l.id_estado "
            + "FROM lotes_inventario l "
            + "JOIN productos p ON p.id_producto = l.id_producto "
            + "JOIN proveedores s ON s.id_proveedor = l.id_proveedor ";

    /** Every lot, the ones closest to expiring first. */
    public List<Lot> findAll() throws SQLException {
        return query(BASE_QUERY + "ORDER BY l.fecha_vencimiento, l.id_lote", st -> { });
    }

    public Optional<Lot> findById(int lotId) throws SQLException {
        List<Lot> lots = query(BASE_QUERY + "WHERE l.id_lote = ?", st -> st.setInt(1, lotId));
        return lots.isEmpty() ? Optional.empty() : Optional.of(lots.get(0));
    }

    public List<Lot> findByStatus(LotStatus status) throws SQLException {
        return query(BASE_QUERY + "WHERE l.id_estado = ? ORDER BY l.fecha_vencimiento",
                st -> st.setInt(1, status.getId()));
    }

    /** Lots physically on a shelf (only the ones that still have stock). */
    public List<Lot> findByLocation(String aisle, String shelf) throws SQLException {
        return query(BASE_QUERY + "WHERE UPPER(l.pasillo) = UPPER(?) AND l.estante = ? "
                + "AND l.cantidad > 0 ORDER BY l.fecha_vencimiento", st -> {
                    st.setString(1, aisle);
                    st.setString(2, shelf);
                });
    }

    /** Delivery history of a supplier: every lot it has brought, newest first. */
    public List<Lot> findBySupplier(int supplierId) throws SQLException {
        return query(BASE_QUERY + "WHERE l.id_proveedor = ? ORDER BY l.fecha_ingreso DESC",
                st -> st.setInt(1, supplierId));
    }

    /** Quarantined lots waiting for the decision of a quality inspector. */
    public List<Lot> findPendingInspection() throws SQLException {
        return query(BASE_QUERY + "WHERE l.id_estado = ? AND EXISTS (SELECT 1 FROM registro_cuarentena q "
                + "WHERE q.id_lote = l.id_lote AND q.resuelta = FALSE) ORDER BY l.fecha_vencimiento",
                st -> st.setInt(1, LotStatus.IN_QUARANTINE.getId()));
    }

    /** Expired lots that still have units on the shelves and must be written off. */
    public List<Lot> findExpiredWithStock() throws SQLException {
        return query(BASE_QUERY + "WHERE l.id_estado = ? AND l.cantidad > 0 ORDER BY l.fecha_vencimiento",
                st -> st.setInt(1, LotStatus.EXPIRED.getId()));
    }

    /**
     * Lots of a product that can be sold today, in FEFO order:
     * the lot that expires first comes first.
     */
    public List<Lot> findSellableByProduct(int productId, LocalDate today) throws SQLException {
        return query(BASE_QUERY + "WHERE l.id_producto = ? AND l.id_estado IN (?, ?) "
                + "AND l.cantidad > 0 AND l.fecha_vencimiento >= ? "
                + "ORDER BY l.fecha_vencimiento, l.id_lote", st -> {
                    st.setInt(1, productId);
                    st.setInt(2, LotStatus.AVAILABLE.getId());
                    st.setInt(3, LotStatus.NEAR_EXPIRY.getId());
                    st.setDate(4, Date.valueOf(today));
                });
    }

    /** Registers the entry of a new lot and returns it with its generated id and lot number. */
    public Lot insert(Lot lot) throws SQLException {
        String insertSql = "INSERT INTO lotes_inventario (numero_lote, id_producto, id_proveedor, "
                + "fecha_ingreso, fecha_vencimiento, cantidad_inicial, cantidad, precio_compra, "
                + "precio_venta, pasillo, estante, id_estado) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection connection = DatabaseConnection.getInstance().getConnection()) {
            int nextId = 1;
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery(
                         "SELECT COALESCE(MAX(id_lote), 0) + 1 FROM lotes_inventario")) {
                if (result.next()) {
                    nextId = result.getInt(1);
                }
            }
            LocalDateTime now = LocalDateTime.now();
            lot.setEntryDate(now);
            lot.setLotNumber("LT-" + now.format(DateTimeFormatter.BASIC_ISO_DATE)
                    + "-" + String.format("%03d", nextId));

            try (PreparedStatement statement = connection.prepareStatement(insertSql,
                    Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, lot.getLotNumber());
                statement.setInt(2, lot.getProductId());
                statement.setInt(3, lot.getSupplierId());
                statement.setTimestamp(4, Timestamp.valueOf(now));
                statement.setDate(5, Date.valueOf(lot.getExpirationDate()));
                statement.setBigDecimal(6, lot.getInitialQuantity());
                statement.setBigDecimal(7, lot.getInitialQuantity());
                statement.setBigDecimal(8, lot.getPurchasePrice());
                statement.setBigDecimal(9, lot.getSalePrice());
                statement.setString(10, lot.getAisle());
                statement.setString(11, lot.getShelf());
                statement.setInt(12, lot.getStatus().getId());
                statement.executeUpdate();

                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (keys.next()) {
                        lot.setId(keys.getInt(1));
                    }
                }
            }
        }
        lot.setQuantity(lot.getInitialQuantity());
        return lot;
    }

    /**
     * Daily process: marks as Expired the lots past their date and as Near Expiry the
     * lots with 7 days or fewer left. Lots in quarantine are never touched.
     *
     * @return an array with {lots marked Expired, lots marked Near Expiry}
     */
    public int[] refreshExpiryStatuses(LocalDate today) throws SQLException {
        String expireSql = "UPDATE lotes_inventario SET id_estado = ? "
                + "WHERE id_estado IN (?, ?) AND fecha_vencimiento < ?";
        String nearSql = "UPDATE lotes_inventario SET id_estado = ? "
                + "WHERE id_estado = ? AND fecha_vencimiento >= ? AND fecha_vencimiento <= ?";

        try (Connection connection = DatabaseConnection.getInstance().getConnection()) {
            int expired;
            int near;
            try (PreparedStatement statement = connection.prepareStatement(expireSql)) {
                statement.setInt(1, LotStatus.EXPIRED.getId());
                statement.setInt(2, LotStatus.AVAILABLE.getId());
                statement.setInt(3, LotStatus.NEAR_EXPIRY.getId());
                statement.setDate(4, Date.valueOf(today));
                expired = statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(nearSql)) {
                statement.setInt(1, LotStatus.NEAR_EXPIRY.getId());
                statement.setInt(2, LotStatus.AVAILABLE.getId());
                statement.setDate(3, Date.valueOf(today));
                statement.setDate(4, Date.valueOf(today.plusDays(Lot.NEAR_EXPIRY_DAYS)));
                near = statement.executeUpdate();
            }
            return new int[] {expired, near};
        }
    }

    /** Customers that received products of this lot (traceability backwards). */
    public List<LotDelivery> findDeliveries(int lotId) throws SQLException {
        String sql = "SELECT c.nombre, v.numero_venta, d.cantidad_vendida, v.fecha_venta, ev.nombre AS estado "
                + "FROM detalles_venta d "
                + "JOIN ventas v ON v.id_venta = d.id_venta "
                + "JOIN clientes c ON c.id_cliente = v.id_cliente "
                + "JOIN estados_venta ev ON ev.id_estado = v.id_estado "
                + "WHERE d.id_lote = ? ORDER BY v.fecha_venta";
        List<LotDelivery> deliveries = new ArrayList<>();

        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, lotId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    deliveries.add(new LotDelivery(
                            result.getString("nombre"),
                            result.getString("numero_venta"),
                            result.getBigDecimal("cantidad_vendida"),
                            result.getTimestamp("fecha_venta").toLocalDateTime(),
                            result.getString("estado")));
                }
            }
        }
        return deliveries;
    }

    private List<Lot> query(String sql, Binder binder) throws SQLException {
        List<Lot> lots = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    lots.add(mapRow(result));
                }
            }
        }
        return lots;
    }

    /** Turns one database row into a Lot object. */
    private Lot mapRow(ResultSet result) throws SQLException {
        Lot lot = new Lot();
        lot.setId(result.getInt("id_lote"));
        lot.setLotNumber(result.getString("numero_lote"));
        lot.setProductId(result.getInt("id_producto"));
        lot.setProductName(result.getString("producto"));
        lot.setSupplierId(result.getInt("id_proveedor"));
        lot.setSupplierName(result.getString("proveedor"));
        lot.setEntryDate(result.getTimestamp("fecha_ingreso").toLocalDateTime());
        lot.setExpirationDate(result.getDate("fecha_vencimiento").toLocalDate());
        lot.setInitialQuantity(result.getBigDecimal("cantidad_inicial"));
        lot.setQuantity(result.getBigDecimal("cantidad"));
        lot.setPurchasePrice(result.getBigDecimal("precio_compra"));
        lot.setSalePrice(result.getBigDecimal("precio_venta"));
        lot.setAisle(result.getString("pasillo"));
        lot.setShelf(result.getString("estante"));
        lot.setStatus(LotStatus.fromId(result.getInt("id_estado")));
        return lot;
    }
}
