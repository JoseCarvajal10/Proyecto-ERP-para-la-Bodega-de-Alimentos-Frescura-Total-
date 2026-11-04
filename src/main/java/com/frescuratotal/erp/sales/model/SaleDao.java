package com.frescuratotal.erp.sales.model;

import com.frescuratotal.erp.common.DatabaseConnection;
import com.frescuratotal.erp.inventory.model.LotStatus;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Data Access Object for sales and their lines. */
public class SaleDao {

    private final InvoiceDao invoiceDao = new InvoiceDao();

    private static final String HEADER_QUERY =
            "SELECT v.id_venta, v.numero_venta, v.fecha_venta, v.subtotal, v.total_descuento, "
            + "v.total_impuesto, v.monto_total, v.id_estado, v.usuario_registro, "
            + "c.id_cliente, c.nombre, c.documento, c.email, c.telefono, c.direccion, c.id_tipo, "
            + "t.nombre AS tipo "
            + "FROM ventas v JOIN clientes c ON c.id_cliente = v.id_cliente "
            + "JOIN tipos_cliente t ON t.id_tipo = c.id_tipo ";

    /**
     * Confirms a sale in ONE transaction: it checks and locks the lots, saves the sale and its
     * lines, discounts the units from the inventory and issues the invoice.
     * If anything fails, nothing is saved.
     */
    public SaleResult createSale(Customer customer, List<SaleLine> lines, String user, Company company)
            throws SQLException {
        try (Connection connection = DatabaseConnection.getInstance().getConnection()) {
            connection.setAutoCommit(false);
            try {
                lockAndValidateLots(connection, lines);

                Sale sale = new Sale();
                sale.setCustomer(customer);
                sale.setDateTime(LocalDateTime.now());
                sale.setUser(user);
                sale.setLines(lines);
                sale.setId(insertSale(connection, sale));
                sale.setNumber(String.format("VTA-%d-%05d", LocalDate.now().getYear(), sale.getId()));
                updateSaleNumber(connection, sale);

                for (SaleLine line : lines) {
                    insertLine(connection, sale.getId(), line);
                    discountStock(connection, line);
                }

                Invoice invoice = Invoice.issue(sale, company);
                invoiceDao.insert(connection, invoice);
                connection.commit();
                return new SaleResult(sale, invoice);
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    /** The most recent sales (header only, without lines). */
    public List<Sale> findRecent(int limit) throws SQLException {
        List<Sale> sales = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     HEADER_QUERY + "ORDER BY v.fecha_venta DESC, v.id_venta DESC LIMIT ?")) {
            statement.setInt(1, limit);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    sales.add(mapHeader(result));
                }
            }
        }
        return sales;
    }

    /** One sale with all its lines. */
    public Optional<Sale> findById(int saleId) throws SQLException {
        try (Connection connection = DatabaseConnection.getInstance().getConnection()) {
            Sale sale;
            try (PreparedStatement statement = connection.prepareStatement(HEADER_QUERY + "WHERE v.id_venta = ?")) {
                statement.setInt(1, saleId);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        return Optional.empty();
                    }
                    sale = mapHeader(result);
                }
            }

            String linesSql = "SELECT d.id_lote, l.numero_lote, l.id_producto, p.nombre, d.cantidad_vendida, "
                    + "d.precio_unitario, d.descuento_aplicado, d.monto_descuento, d.tasa_impuesto, "
                    + "d.monto_impuesto, d.subtotal, d.total_linea FROM detalles_venta d "
                    + "JOIN lotes_inventario l ON l.id_lote = d.id_lote "
                    + "JOIN productos p ON p.id_producto = l.id_producto "
                    + "WHERE d.id_venta = ? ORDER BY d.id_detalle";
            List<SaleLine> lines = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(linesSql)) {
                statement.setInt(1, saleId);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        lines.add(new SaleLine(result.getInt("id_lote"), result.getString("numero_lote"),
                                result.getInt("id_producto"), result.getString("nombre"),
                                result.getBigDecimal("cantidad_vendida"), result.getBigDecimal("precio_unitario"),
                                result.getBigDecimal("descuento_aplicado"), result.getBigDecimal("monto_descuento"),
                                result.getBigDecimal("tasa_impuesto"), result.getBigDecimal("monto_impuesto"),
                                result.getBigDecimal("subtotal"), result.getBigDecimal("total_linea")));
                    }
                }
            }
            sale.setLines(lines);
            return Optional.of(sale);
        }
    }

    /** Moves a Registered sale to In Preparation so it shows up in the dispatch panel. */
    public boolean sendToPreparation(int saleId) throws SQLException {
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE ventas SET id_estado = ? WHERE id_venta = ? AND id_estado = ?")) {
            statement.setInt(1, SaleStatus.IN_PREPARATION.getId());
            statement.setInt(2, saleId);
            statement.setInt(3, SaleStatus.REGISTERED.getId());
            return statement.executeUpdate() > 0;
        }
    }

    // ---------- private helpers of the transaction ----------

    /** Locks every lot used by the sale and checks that it can still be sold in full. */
    private void lockAndValidateLots(Connection connection, List<SaleLine> lines) throws SQLException {
        Map<Integer, BigDecimal> neededByLot = new LinkedHashMap<>();
        for (SaleLine line : lines) {
            neededByLot.merge(line.lotId(), line.quantity(), BigDecimal::add);
        }

        for (Map.Entry<Integer, BigDecimal> entry : neededByLot.entrySet()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT cantidad, id_estado, fecha_vencimiento FROM lotes_inventario "
                    + "WHERE id_lote = ? FOR UPDATE")) {
                statement.setInt(1, entry.getKey());
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        throw new IllegalStateException("Lot " + entry.getKey() + " does not exist.");
                    }
                    int status = result.getInt("id_estado");
                    boolean sellable = status == LotStatus.AVAILABLE.getId()
                            || status == LotStatus.NEAR_EXPIRY.getId();
                    Date expiration = result.getDate("fecha_vencimiento");

                    if (!sellable || expiration.toLocalDate().isBefore(LocalDate.now())) {
                        throw new IllegalStateException("Lot " + entry.getKey() + " can no longer be sold.");
                    }
                    if (result.getBigDecimal("cantidad").compareTo(entry.getValue()) < 0) {
                        throw new IllegalStateException("Lot " + entry.getKey()
                                + " does not have enough units anymore. Try the sale again.");
                    }
                }
            }
        }
    }

    private int insertSale(Connection connection, Sale sale) throws SQLException {
        String sql = "INSERT INTO ventas (numero_venta, id_cliente, fecha_venta, subtotal, total_descuento, "
                + "total_impuesto, monto_total, id_estado, usuario_registro) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, "TMP-" + UUID.randomUUID());
            statement.setInt(2, sale.getCustomer().id());
            statement.setTimestamp(3, Timestamp.valueOf(sale.getDateTime()));
            statement.setBigDecimal(4, sale.getSubtotal());
            statement.setBigDecimal(5, sale.getDiscount());
            statement.setBigDecimal(6, sale.getTax());
            statement.setBigDecimal(7, sale.getTotal());
            statement.setInt(8, SaleStatus.REGISTERED.getId());
            statement.setString(9, sale.getUser());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    private void updateSaleNumber(Connection connection, Sale sale) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE ventas SET numero_venta = ? WHERE id_venta = ?")) {
            statement.setString(1, sale.getNumber());
            statement.setInt(2, sale.getId());
            statement.executeUpdate();
        }
    }

    private void insertLine(Connection connection, int saleId, SaleLine line) throws SQLException {
        String sql = "INSERT INTO detalles_venta (id_venta, id_lote, cantidad_vendida, precio_unitario, "
                + "descuento_aplicado, monto_descuento, tasa_impuesto, monto_impuesto, subtotal, total_linea) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, saleId);
            statement.setInt(2, line.lotId());
            statement.setBigDecimal(3, line.quantity());
            statement.setBigDecimal(4, line.unitPrice());
            statement.setBigDecimal(5, line.discountRate());
            statement.setBigDecimal(6, line.discountAmount());
            statement.setBigDecimal(7, line.taxRate());
            statement.setBigDecimal(8, line.taxAmount());
            statement.setBigDecimal(9, line.subtotal());
            statement.setBigDecimal(10, line.lineTotal());
            statement.executeUpdate();
        }
    }

    /** Takes the sold units out of the lot automatically. */
    private void discountStock(Connection connection, SaleLine line) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE lotes_inventario SET cantidad = cantidad - ? WHERE id_lote = ?")) {
            statement.setBigDecimal(1, line.quantity());
            statement.setInt(2, line.lotId());
            statement.executeUpdate();
        }
    }

    private Sale mapHeader(ResultSet result) throws SQLException {
        Sale sale = new Sale();
        sale.setId(result.getInt("id_venta"));
        sale.setNumber(result.getString("numero_venta"));
        sale.setDateTime(result.getTimestamp("fecha_venta").toLocalDateTime());
        sale.setStatus(SaleStatus.fromId(result.getInt("id_estado")));
        sale.setUser(result.getString("usuario_registro"));
        sale.setCustomer(new Customer(result.getInt("id_cliente"), result.getString("nombre"),
                result.getString("documento"), result.getString("email"), result.getString("telefono"),
                result.getString("direccion"), result.getInt("id_tipo"), result.getString("tipo")));
        sale.setTotals(result.getBigDecimal("subtotal"), result.getBigDecimal("total_descuento"),
                result.getBigDecimal("total_impuesto"), result.getBigDecimal("monto_total"));
        return sale;
    }
}
