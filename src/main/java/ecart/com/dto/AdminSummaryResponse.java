package ecart.com.dto;

import java.util.List;

public record AdminSummaryResponse(
        long itemsPurchasedCount,
        long revenue,
        List<DiscountCodeSummary> discountCodes,
        long totalDiscountGiven,
        long ordersCount
) {
}
