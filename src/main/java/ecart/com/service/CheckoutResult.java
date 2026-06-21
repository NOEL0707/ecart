package ecart.com.service;

import ecart.com.model.Order;

public record CheckoutResult(Order order, boolean replayed) {
}
