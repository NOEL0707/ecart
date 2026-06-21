package ecart.com.dto;

public record DiscountCodeSummary(
        String code,
        int discountPercent,
        String status,
        long triggeredByOrderNumber,
        String usedByOrderId
) {
}
