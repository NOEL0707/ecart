package ecart.com.dto;

import ecart.com.model.DiscountCode;
import java.time.Instant;

public record DiscountCodeResponse(
        String code,
        int discountPercent,
        long triggeredByOrderNumber,
        String status,
        Instant expiresAt
) {
    public static DiscountCodeResponse from(DiscountCode discountCode) {
        return new DiscountCodeResponse(
                discountCode.code(),
                discountCode.discountPercent(),
                discountCode.triggeredByOrderNumber(),
                discountCode.status(),
                discountCode.expiresAt()
        );
    }
}
