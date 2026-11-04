package com.frescuratotal.erp.sales.model;

import com.frescuratotal.erp.common.DatabaseConnection;
import com.frescuratotal.erp.inventory.model.LotStatus;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

/** Data Access Object for the preconfigured promotions. */
public class PromotionDao {

    private static final BigDecimal DEFAULT_RATE = new BigDecimal("0.50");

    /** Discount applied to Near Expiry lots; 50% if no promotion is configured. */
    public BigDecimal findNearExpiryDiscountRate() throws SQLException {
        String sql = "SELECT tasa_descuento FROM promociones WHERE activa = TRUE AND aplica_estado_lote = ? "
                + "AND (fecha_inicio IS NULL OR fecha_inicio <= ?) "
                + "AND (fecha_fin IS NULL OR fecha_fin >= ?) ORDER BY tasa_descuento DESC LIMIT 1";

        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, LotStatus.NEAR_EXPIRY.getId());
            statement.setDate(2, Date.valueOf(LocalDate.now()));
            statement.setDate(3, Date.valueOf(LocalDate.now()));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getBigDecimal(1) : DEFAULT_RATE;
            }
        }
    }
}
