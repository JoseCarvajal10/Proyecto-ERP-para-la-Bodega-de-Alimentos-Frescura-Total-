package com.frescuratotal.erp.sales.model;

import com.frescuratotal.erp.common.DatabaseConnection;
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

/** Data Access Object for customers. */
public class CustomerDao {

    private static final String BASE_QUERY =
            "SELECT c.id_cliente, c.nombre, c.documento, c.email, c.telefono, c.direccion, "
            + "c.id_tipo, t.nombre AS tipo FROM clientes c "
            + "JOIN tipos_cliente t ON t.id_tipo = c.id_tipo ";

    public List<Customer> findAll() throws SQLException {
        List<Customer> customers = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(BASE_QUERY + "ORDER BY c.nombre");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                customers.add(mapRow(result));
            }
        }
        return customers;
    }

    public Optional<Customer> findById(int customerId) throws SQLException {
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(BASE_QUERY + "WHERE c.id_cliente = ?")) {
            statement.setInt(1, customerId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(mapRow(result)) : Optional.empty();
            }
        }
    }

    public boolean existsByDocument(String document) throws SQLException {
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT 1 FROM clientes WHERE documento = ?")) {
            statement.setString(1, document);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    public int insert(Customer customer) throws SQLException {
        String sql = "INSERT INTO clientes (nombre, documento, email, telefono, direccion, id_tipo, "
                + "fecha_registro) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, customer.name());
            statement.setString(2, customer.document());
            statement.setString(3, customer.email());
            statement.setString(4, customer.phone());
            statement.setString(5, customer.address());
            statement.setInt(6, customer.typeId());
            statement.setTimestamp(7, Timestamp.valueOf(LocalDateTime.now()));
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    private Customer mapRow(ResultSet result) throws SQLException {
        return new Customer(result.getInt("id_cliente"), result.getString("nombre"),
                result.getString("documento"), result.getString("email"), result.getString("telefono"),
                result.getString("direccion"), result.getInt("id_tipo"), result.getString("tipo"));
    }
}
