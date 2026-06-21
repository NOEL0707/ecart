package ecart.com.model;

import java.time.Instant;

public record DiscountCode(
        String code,
        int discountPercent,
        String status,
        long triggeredByOrderNumber,
        String usedByOrderId,
        Instant createdAt,
        Instant expiresAt,
        Instant usedAt
) {
}
