package com.frescuratotal.erp.common;

/**
 * Holds the database connection settings.
 * Reads connection settings from environment variables, with local defaults.
 */
public final class DatabaseConfig {

    public static final String HOST = setting("ERP_DB_HOST", "localhost");
    public static final int PORT = Integer.parseInt(setting("ERP_DB_PORT", "3306"));
    public static final String DATABASE = setting("ERP_DB_NAME", "erp_frescura_total");
    public static final String USER = setting("ERP_DB_USER", "root");
    public static final String PASSWORD = setting("ERP_DB_PASSWORD", "");

    public static final String URL = "jdbc:mysql://" + HOST + ":" + PORT + "/" + DATABASE
            + "?useSSL=false&allowPublicKeyRetrieval=true&useUnicode=true&characterEncoding=UTF-8";

    private DatabaseConfig() {
        // Utility class: no instances allowed
    }

    private static String setting(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
