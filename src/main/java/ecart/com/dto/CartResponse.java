package ecart.com.dto;

import ecart.com.model.Cart;
import java.util.List;

public record CartResponse(String cartId, String userId, List<CartItemResponse> items, long subtotal) {
    public static CartResponse from(Cart cart) {
        return new CartResponse(
                cart.id(),
                cart.userId(),
                cart.items().stream().map(CartItemResponse::from).toList(),
                cart.subtotal()
        );
    }
}
