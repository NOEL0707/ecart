package ecart.com.service;

import ecart.com.dto.AddCartItemRequest;
import ecart.com.exception.BadRequestException;
import ecart.com.model.Cart;
import ecart.com.model.CartItem;
import ecart.com.repository.CartRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartService {
    private final CartRepository cartRepository;

    public CartService(CartRepository cartRepository) {
        this.cartRepository = cartRepository;
    }

    @Transactional
    public Cart addItem(String userId, AddCartItemRequest request) {
        validateUserId(userId);
        Cart cart = cartRepository.getOrCreateActiveCart(userId.trim());
        CartItem item = new CartItem(request.sku().trim(), request.name().trim(), request.unitPrice(), request.quantity());
        cartRepository.upsertItem(cart.id(), item);
        return cartRepository.getOrCreateActiveCart(userId.trim());
    }

    @Transactional(readOnly = true)
    public Cart getActiveCart(String userId) {
        validateUserId(userId);
        return cartRepository.getOrCreateActiveCart(userId.trim());
    }

    private void validateUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new BadRequestException("MISSING_USER_ID", "X-User-Id header is required.");
        }
    }
}
