package ecart.com.repository;

import ecart.com.model.Cart;
import ecart.com.model.CartItem;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CartRepository {
    private final JdbcTemplate jdbcTemplate;

    public CartRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Cart getOrCreateActiveCart(String userId) {
        Optional<String> existingCartId = findActiveCartId(userId);
        String cartId = existingCartId.orElseGet(() -> createCart(userId));
        return getCart(cartId, userId);
    }

    public Optional<Cart> findActiveCart(String userId) {
        return findActiveCartId(userId).map(cartId -> getCart(cartId, userId));
    }

    public void upsertItem(String cartId, CartItem item) {
        String now = InstantMapper.format(Instant.now());
        int updated = jdbcTemplate.update("""
                UPDATE cart_items
                   SET quantity = quantity + ?, name = ?, unit_price = ?, updated_at = ?
                 WHERE cart_id = ? AND sku = ?
                """, item.quantity(), item.name(), item.unitPrice(), now, cartId, item.sku());
        if (updated == 0) {
            jdbcTemplate.update("""
                    INSERT INTO cart_items(id, cart_id, sku, name, unit_price, quantity, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, "ci_" + UUID.randomUUID(), cartId, item.sku(), item.name(), item.unitPrice(), item.quantity(), now, now);
        }
        jdbcTemplate.update("UPDATE carts SET updated_at = ? WHERE id = ?", now, cartId);
    }

    public void markCheckedOut(String cartId) {
        jdbcTemplate.update("UPDATE carts SET status = 'CHECKED_OUT', updated_at = ? WHERE id = ?", InstantMapper.format(Instant.now()), cartId);
    }

    private Optional<String> findActiveCartId(String userId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT id FROM carts WHERE user_id = ? AND status = 'ACTIVE'",
                    String.class,
                    userId
            ));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    private String createCart(String userId) {
        String cartId = "cart_" + UUID.randomUUID();
        String now = InstantMapper.format(Instant.now());
        jdbcTemplate.update("""
                INSERT INTO carts(id, user_id, status, created_at, updated_at)
                VALUES (?, ?, 'ACTIVE', ?, ?)
                """, cartId, userId, now, now);
        return cartId;
    }

    private Cart getCart(String cartId, String userId) {
        List<CartItem> items = jdbcTemplate.query("""
                SELECT sku, name, unit_price, quantity
                  FROM cart_items
                 WHERE cart_id = ?
                 ORDER BY created_at ASC
                """, (rs, rowNum) -> new CartItem(
                rs.getString("sku"),
                rs.getString("name"),
                rs.getLong("unit_price"),
                rs.getInt("quantity")
        ), cartId);
        return new Cart(cartId, userId, items);
    }
}
