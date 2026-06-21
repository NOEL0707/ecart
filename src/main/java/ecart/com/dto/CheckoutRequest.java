package ecart.com.dto;

import jakarta.validation.constraints.Size;

public record CheckoutRequest(@Size(max = 64) String discountCode) {
    public String normalizedDiscountCode() {
        return discountCode == null || discountCode.isBlank() ? null : discountCode.trim().toUpperCase();
    }
}
