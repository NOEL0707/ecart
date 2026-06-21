package ecart.com.repository;

import ecart.com.dto.DiscountCodeSummary;
import ecart.com.model.DiscountCode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DiscountRepository {
    private final JdbcTemplate jdbcTemplate;

    public DiscountRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<DiscountCode> find(String code) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject("""
                    SELECT code, discount_percent, status, triggered_by_order_number, used_by_order_id,
                           created_at, expires_at, used_at
                      FROM discount_codes
                     WHERE code = ?
                    """, this::mapDiscountCode, code));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public boolean consume(String code, String orderId, Instant now) {
        return jdbcTemplate.update("""
                UPDATE discount_codes
                   SET status = 'USED', used_by_order_id = ?, used_at = ?
                 WHERE code = ?
                   AND status = 'ACTIVE'
                   AND (expires_at IS NULL OR expires_at > ?)
                """, orderId, InstantMapper.format(now), code, InstantMapper.format(now)) == 1;
    }

    public Optional<DiscountCode> findByTriggeredOrderNumber(long orderNumber) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject("""
                    SELECT code, discount_percent, status, triggered_by_order_number, used_by_order_id,
                           created_at, expires_at, used_at
                      FROM discount_codes
                     WHERE triggered_by_order_number = ?
                    """, this::mapDiscountCode, orderNumber));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public DiscountCode create(String code, int percent, long triggeredByOrderNumber, Instant createdAt, Instant expiresAt) {
        jdbcTemplate.update("""
                INSERT INTO discount_codes(code, discount_percent, status, triggered_by_order_number, created_at, expires_at)
                VALUES (?, ?, 'ACTIVE', ?, ?, ?)
                """, code, percent, triggeredByOrderNumber, InstantMapper.format(createdAt), InstantMapper.format(expiresAt));
        return find(code).orElseThrow();
    }

    public List<DiscountCodeSummary> listSummaries() {
        return jdbcTemplate.query("""
                SELECT code, discount_percent, status, triggered_by_order_number, used_by_order_id
                  FROM discount_codes
                 ORDER BY triggered_by_order_number ASC
                """, (rs, rowNum) -> new DiscountCodeSummary(
                rs.getString("code"),
                rs.getInt("discount_percent"),
                rs.getString("status"),
                rs.getLong("triggered_by_order_number"),
                rs.getString("used_by_order_id")
        ));
    }

    private DiscountCode mapDiscountCode(ResultSet rs, int rowNum) throws SQLException {
        return new DiscountCode(
                rs.getString("code"),
                rs.getInt("discount_percent"),
                rs.getString("status"),
                rs.getLong("triggered_by_order_number"),
                rs.getString("used_by_order_id"),
                InstantMapper.parse(rs.getString("created_at")),
                InstantMapper.parse(rs.getString("expires_at")),
                InstantMapper.parse(rs.getString("used_at"))
        );
    }
}
