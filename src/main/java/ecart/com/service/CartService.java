package ecart.com.service;

import ecart.com.dto.AddCartItemRequest;
import ecart.com.exception.BadRequestException;
import ecart.com.model.Cart;
import ecart.com.model.CartItem;
import ecart.com.repository.CartRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class CartService {
    private final CartRepository cartRepository;

    public CartService(CartRepository cartRepository) {
        this.cartRepository = cartRepository;
    }

    @Transactional
    public Cart addItem(String userId, AddCartItemRequest request) {
        log.info("Adding item to cart for userId: {}, sku: {}, quantity: {}", userId, request.sku(), request.quantity());
        validateUserId(userId);
        Cart cart = cartRepository.getOrCreateActiveCart(userId.trim());
        CartItem item = new CartItem(request.sku().trim(), request.name().trim(), request.unitPrice(), request.quantity());
        cartRepository.upsertItem(cart.id(), item);
        log.info("Item upserted to cart: {}. Re-fetching cart details.", cart.id());
        return cartRepository.getOrCreateActiveCart(userId.trim());
    }

    @Transactional(readOnly = true)
    public Cart getActiveCart(String userId) {
        log.info("Retrieving active cart for userId: {}", userId);
        validateUserId(userId);
        return cartRepository.getOrCreateActiveCart(userId.trim());
    }

    private void validateUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            log.warn("Cart validation failed: X-User-Id header is missing or blank");
            throw new BadRequestException("MISSING_USER_ID", "X-User-Id header is required.");
        }
    }
}
