package ecart.com.dto;

import ecart.com.model.Order;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        String orderId,
        String userId,
        List<CartItemResponse> items,
        long subtotal,
        String discountCode,
        Integer discountPercent,
        long discountAmount,
        long total,
        String status,
        Instant createdAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.id(),
                order.userId(),
                order.items().stream().map(CartItemResponse::from).toList(),
                order.subtotal(),
                order.discountCode(),
                order.discountPercent(),
                order.discountAmount(),
                order.total(),
                order.status(),
                order.createdAt()
        );
    }
}
