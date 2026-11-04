package com.frescuratotal.erp.common;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Singleton that provides connections to the MySQL database.
 */
public final class DatabaseConnection {

    private static DatabaseConnection instance;

    private DatabaseConnection() {
    }

    public static synchronized DatabaseConnection getInstance() {
        if (instance == null) {
            instance = new DatabaseConnection();
        }
        return instance;
    }

    /** Opens a new connection each time, so callers can close it safely. */
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(
                DatabaseConfig.URL, DatabaseConfig.USER, DatabaseConfig.PASSWORD);
    }
}
