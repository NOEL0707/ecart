package ecart.com.repository;

import ecart.com.model.CartItem;
import ecart.com.model.Order;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OrderRepository {
    private final JdbcTemplate jdbcTemplate;

    public OrderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<OrderRecord> findByIdempotency(String userId, String idempotencyKey) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject("""
                    SELECT id, request_hash
                      FROM orders
                     WHERE user_id = ? AND idempotency_key = ?
                    """, (rs, rowNum) -> new OrderRecord(rs.getString("id"), rs.getString("request_hash")), userId, idempotencyKey));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public long nextOrderNumber() {
        Long current = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(order_number), 0) FROM orders", Long.class);
        return (current == null ? 0 : current) + 1;
    }

    public void createOrder(
            String orderId,
            long orderNumber,
            String userId,
            String cartId,
            List<CartItem> items,
            long subtotal,
            String discountCode,
            Integer discountPercent,
            long discountAmount,
            long total,
            String idempotencyKey,
            String requestHash,
            Instant createdAt
    ) {
        jdbcTemplate.update("""
                INSERT INTO orders(
                  id, order_number, user_id, cart_id, subtotal, discount_code, discount_percent,
                  discount_amount, total, status, idempotency_key, request_hash, created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PLACED', ?, ?, ?)
                """, orderId, orderNumber, userId, cartId, subtotal, discountCode, discountPercent, discountAmount,
                total, idempotencyKey, requestHash, InstantMapper.format(createdAt));

        for (CartItem item : items) {
            jdbcTemplate.update("""
                    INSERT INTO order_items(id, order_id, sku, name, unit_price, quantity, line_total)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, "oi_" + UUID.randomUUID(), orderId, item.sku(), item.name(), item.unitPrice(), item.quantity(), item.lineTotal());
        }
    }

    public Order getOrder(String orderId) {
        OrderHeader header = jdbcTemplate.queryForObject("""
                SELECT id, order_number, user_id, subtotal, discount_code, discount_percent,
                       discount_amount, total, status, created_at
                  FROM orders
                 WHERE id = ?
                """, this::mapHeader, orderId);
        List<CartItem> items = jdbcTemplate.query("""
                SELECT sku, name, unit_price, quantity
                  FROM order_items
                 WHERE order_id = ?
                 ORDER BY id ASC
                """, (rs, rowNum) -> new CartItem(
                rs.getString("sku"),
                rs.getString("name"),
                rs.getLong("unit_price"),
                rs.getInt("quantity")
        ), orderId);
        return new Order(header.id(), header.orderNumber(), header.userId(), items, header.subtotal(), header.discountCode(),
                header.discountPercent(), header.discountAmount(), header.total(), header.status(), header.createdAt());
    }

    public ReportTotals getReportTotals() {
        Long itemsPurchased = jdbcTemplate.queryForObject("SELECT COALESCE(SUM(quantity), 0) FROM order_items", Long.class);
        Long revenue = jdbcTemplate.queryForObject("SELECT COALESCE(SUM(total), 0) FROM orders", Long.class);
        Long discountGiven = jdbcTemplate.queryForObject("SELECT COALESCE(SUM(discount_amount), 0) FROM orders", Long.class);
        Long orderCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Long.class);
        return new ReportTotals(
                itemsPurchased == null ? 0 : itemsPurchased,
                revenue == null ? 0 : revenue,
                discountGiven == null ? 0 : discountGiven,
                orderCount == null ? 0 : orderCount
        );
    }

    private OrderHeader mapHeader(ResultSet rs, int rowNum) throws SQLException {
        Integer discountPercent = rs.getObject("discount_percent") == null ? null : rs.getInt("discount_percent");
        return new OrderHeader(
                rs.getString("id"),
                rs.getLong("order_number"),
                rs.getString("user_id"),
                rs.getLong("subtotal"),
                rs.getString("discount_code"),
                discountPercent,
                rs.getLong("discount_amount"),
                rs.getLong("total"),
                rs.getString("status"),
                InstantMapper.parse(rs.getString("created_at"))
        );
    }

    public record OrderRecord(String orderId, String requestHash) {
    }

    public record ReportTotals(long itemsPurchasedCount, long revenue, long totalDiscountGiven, long ordersCount) {
    }

    private record OrderHeader(
            String id,
            long orderNumber,
            String userId,
            long subtotal,
            String discountCode,
            Integer discountPercent,
            long discountAmount,
            long total,
            String status,
            Instant createdAt
    ) {
    }
}
