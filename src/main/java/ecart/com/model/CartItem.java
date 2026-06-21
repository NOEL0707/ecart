package ecart.com.model;

public record CartItem(String sku, String name, long unitPrice, int quantity) {
    public long lineTotal() {
        return unitPrice * quantity;
    }
}
