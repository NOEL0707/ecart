package ecart.com.model;

import java.time.Instant;
import java.util.List;

public record Order(
        String id,
        long orderNumber,
        String userId,
        List<CartItem> items,
        long subtotal,
        String discountCode,
        Integer discountPercent,
        long discountAmount,
        long total,
        String status,
        Instant createdAt
) {
}
