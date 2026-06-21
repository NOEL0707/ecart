package ecart.com.service;

import ecart.com.dto.CheckoutRequest;
import ecart.com.exception.BadRequestException;
import ecart.com.exception.ConflictException;
import ecart.com.model.Cart;
import ecart.com.model.DiscountCode;
import ecart.com.repository.CartRepository;
import ecart.com.repository.DiscountRepository;
import ecart.com.repository.OrderRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheckoutService {
    private final CartRepository cartRepository;
    private final DiscountRepository discountRepository;
    private final OrderRepository orderRepository;
    private final HashingService hashingService;

    public CheckoutService(
            CartRepository cartRepository,
            DiscountRepository discountRepository,
            OrderRepository orderRepository,
            HashingService hashingService
    ) {
        this.cartRepository = cartRepository;
        this.discountRepository = discountRepository;
        this.orderRepository = orderRepository;
        this.hashingService = hashingService;
    }

    @Transactional
    public CheckoutResult checkout(String userId, String idempotencyKey, CheckoutRequest request) {
        validateUserId(userId);
        validateIdempotencyKey(idempotencyKey);
        String normalizedUserId = userId.trim();
        String normalizedKey = idempotencyKey.trim();
        String discountCode = request == null ? null : request.normalizedDiscountCode();
        String requestHash = hashingService.sha256("discountCode=" + (discountCode == null ? "" : discountCode));

        var existing = orderRepository.findByIdempotency(normalizedUserId, normalizedKey);
        if (existing.isPresent()) {
            if (!existing.get().requestHash().equals(requestHash)) {
                throw new ConflictException("IDEMPOTENCY_CONFLICT", "Idempotency-Key was reused with a different request body.");
            }
            return new CheckoutResult(orderRepository.getOrder(existing.get().orderId()), true);
        }

        Cart cart = cartRepository.findActiveCart(normalizedUserId)
                .orElseThrow(() -> new ConflictException("EMPTY_CART", "Active cart is empty."));
        if (cart.items().isEmpty()) {
            throw new ConflictException("EMPTY_CART", "Active cart is empty.");
        }

        long subtotal = cart.subtotal();
        DiscountApplication discount = calculateDiscount(discountCode, subtotal);
        long total = Math.max(0, subtotal - discount.amount());
        Instant now = Instant.now();
        String orderId = "ord_" + UUID.randomUUID();
        long orderNumber = orderRepository.nextOrderNumber();

        orderRepository.createOrder(
                orderId,
                orderNumber,
                normalizedUserId,
                cart.id(),
                cart.items(),
                subtotal,
                discount.code(),
                discount.percent(),
                discount.amount(),
                total,
                normalizedKey,
                requestHash,
                now
        );

        if (discount.code() != null && !discountRepository.consume(discount.code(), orderId, now)) {
            throw new ConflictException("INVALID_DISCOUNT_CODE", "Discount code is invalid, expired, or already used.");
        }

        cartRepository.markCheckedOut(cart.id());
        return new CheckoutResult(orderRepository.getOrder(orderId), false);
    }

    private DiscountApplication calculateDiscount(String discountCode, long subtotal) {
        if (discountCode == null) {
            return new DiscountApplication(null, null, 0);
        }
        DiscountCode discount = discountRepository.find(discountCode)
                .orElseThrow(() -> new ConflictException("INVALID_DISCOUNT_CODE", "Discount code is invalid, expired, or already used."));
        Instant now = Instant.now();
        if (!"ACTIVE".equals(discount.status()) || (discount.expiresAt() != null && !discount.expiresAt().isAfter(now))) {
            throw new ConflictException("INVALID_DISCOUNT_CODE", "Discount code is invalid, expired, or already used.");
        }
        long amount = BigDecimal.valueOf(subtotal)
                .multiply(BigDecimal.valueOf(discount.discountPercent()))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                .longValue();
        return new DiscountApplication(discount.code(), discount.discountPercent(), amount);
    }

    private void validateUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new BadRequestException("MISSING_USER_ID", "X-User-Id header is required.");
        }
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BadRequestException("MISSING_IDEMPOTENCY_KEY", "Idempotency-Key header is required.");
        }
    }

    private record DiscountApplication(String code, Integer percent, long amount) {
    }
}
