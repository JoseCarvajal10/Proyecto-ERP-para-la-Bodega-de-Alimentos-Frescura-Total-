package com.frescuratotal.erp.inventory.model;

import com.frescuratotal.erp.common.DatabaseConnection;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Data Access Object for products and their supplier catalog. */
public class ProductDao {

    private static final String BASE_QUERY =
            "SELECT p.id_producto, p.nombre, p.unidad_medida, p.id_categoria, "
            + "c.nombre AS categoria, p.precio_base "
            + "FROM productos p JOIN categorias_producto c ON c.id_categoria = p.id_categoria ";

    public List<Product> findAll() throws SQLException {
        return list(BASE_QUERY + "ORDER BY p.nombre", null);
    }

    public Optional<Product> findById(int productId) throws SQLException {
        List<Product> products = list(BASE_QUERY + "WHERE p.id_producto = ?", productId);
        return products.isEmpty() ? Optional.empty() : Optional.of(products.get(0));
    }

    /** Products a given supplier is able to provide. */
    public List<Product> findCatalogBySupplier(int supplierId) throws SQLException {
        return list(BASE_QUERY + "JOIN productos_proveedor pp ON pp.id_producto = p.id_producto "
                + "WHERE pp.id_proveedor = ? ORDER BY p.nombre", supplierId);
    }

    /** Products that have stock which can be sold today, with the total available quantity. */
    public List<ProductStock> findSellableWithStock(LocalDate today) throws SQLException {
        String sql = "SELECT p.id_producto, p.nombre, p.unidad_medida, p.id_categoria, "
                + "c.nombre AS categoria, p.precio_base, SUM(l.cantidad) AS disponible "
                + "FROM productos p "
                + "JOIN categorias_producto c ON c.id_categoria = p.id_categoria "
                + "JOIN lotes_inventario l ON l.id_producto = p.id_producto "
                + "WHERE l.id_estado IN (?, ?) AND l.cantidad > 0 AND l.fecha_vencimiento >= ? "
                + "GROUP BY p.id_producto, p.nombre, p.unidad_medida, p.id_categoria, c.nombre, p.precio_base "
                + "ORDER BY p.nombre";
        List<ProductStock> stock = new ArrayList<>();

        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, LotStatus.AVAILABLE.getId());
            statement.setInt(2, LotStatus.NEAR_EXPIRY.getId());
            statement.setDate(3, Date.valueOf(today));
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    BigDecimal available = result.getBigDecimal("disponible");
                    stock.add(new ProductStock(mapRow(result), available));
                }
            }
        }
        return stock;
    }

    private List<Product> list(String sql, Integer parameter) throws SQLException {
        List<Product> products = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (parameter != null) {
                statement.setInt(1, parameter);
            }
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    products.add(mapRow(result));
                }
            }
        }
        return products;
    }

    private Product mapRow(ResultSet result) throws SQLException {
        return new Product(
                result.getInt("id_producto"),
                result.getString("nombre"),
                result.getString("unidad_medida"),
                result.getInt("id_categoria"),
                result.getString("categoria"),
                result.getBigDecimal("precio_base"));
    }
}
