package ecart.com.model;

import java.util.List;

public record Cart(String id, String userId, List<CartItem> items) {
    public long subtotal() {
        return items.stream().mapToLong(CartItem::lineTotal).sum();
    }
}
