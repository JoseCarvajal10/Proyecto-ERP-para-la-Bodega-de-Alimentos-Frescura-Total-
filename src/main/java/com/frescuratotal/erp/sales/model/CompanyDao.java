package com.frescuratotal.erp.sales.model;

import com.frescuratotal.erp.common.DatabaseConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Data Access Object for the company information printed on invoices. */
public class CompanyDao {

    public Company find() throws SQLException {
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT nombre_empresa, nit, direccion, ciudad, telefono, email "
                     + "FROM informacion_empresa ORDER BY id_empresa LIMIT 1");
             ResultSet result = statement.executeQuery()) {
            if (!result.next()) {
                throw new IllegalStateException("The informacion_empresa table is empty.");
            }
            return new Company(result.getString("nombre_empresa"), result.getString("nit"),
                    result.getString("direccion"), result.getString("ciudad"),
                    result.getString("telefono"), result.getString("email"));
        }
    }
}
