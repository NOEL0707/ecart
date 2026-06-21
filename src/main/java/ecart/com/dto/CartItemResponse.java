package ecart.com.dto;

import ecart.com.model.CartItem;

public record CartItemResponse(String sku, String name, long unitPrice, int quantity, long lineTotal) {
    public static CartItemResponse from(CartItem item) {
        return new CartItemResponse(item.sku(), item.name(), item.unitPrice(), item.quantity(), item.lineTotal());
    }
}
