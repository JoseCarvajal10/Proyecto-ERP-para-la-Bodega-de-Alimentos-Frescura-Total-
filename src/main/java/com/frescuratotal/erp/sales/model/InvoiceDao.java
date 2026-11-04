package com.frescuratotal.erp.sales.model;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import com.frescuratotal.erp.common.DatabaseConnection;

/** Data Access Object for electronic invoices. */
public class InvoiceDao {

    /** Saves the invoice inside the transaction of the sale. */
    void insert(Connection connection, Invoice invoice) throws SQLException {
        String sql = "INSERT INTO facturas (id_venta, numero_factura, cufe, enlace_qr, fecha_emision, "
                + "subtotal, total_descuento, monto_impuesto, monto_total) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, invoice.saleId());
            statement.setString(2, invoice.number());
            statement.setString(3, invoice.cufe());
            statement.setString(4, invoice.qrLink());
            statement.setTimestamp(5, Timestamp.valueOf(invoice.issueDate()));
            statement.setBigDecimal(6, invoice.subtotal());
            statement.setBigDecimal(7, invoice.discount());
            statement.setBigDecimal(8, invoice.tax());
            statement.setBigDecimal(9, invoice.total());
            statement.executeUpdate();
        }
    }

    public Optional<Invoice> findBySaleId(int saleId) throws SQLException {
        String sql = "SELECT id_factura, id_venta, numero_factura, cufe, enlace_qr, fecha_emision, "
                + "subtotal, total_descuento, monto_impuesto, monto_total FROM facturas WHERE id_venta = ?";
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, saleId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Invoice(result.getInt("id_factura"), result.getInt("id_venta"),
                        result.getString("numero_factura"), result.getString("cufe"),
                        result.getString("enlace_qr"), result.getTimestamp("fecha_emision").toLocalDateTime(),
                        result.getBigDecimal("subtotal"), result.getBigDecimal("total_descuento"),
                        result.getBigDecimal("monto_impuesto"), result.getBigDecimal("monto_total")));
            }
        }
    }
}
