package ecart.com.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ecart.com.dto.CheckoutRequest;
import ecart.com.exception.BadRequestException;
import ecart.com.exception.ConflictException;
import ecart.com.model.Cart;
import ecart.com.model.CartItem;
import ecart.com.model.DiscountCode;
import ecart.com.model.Order;
import ecart.com.repository.CartRepository;
import ecart.com.repository.DiscountRepository;
import ecart.com.repository.OrderRepository;
import ecart.com.repository.OrderRepository.OrderRecord;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CheckoutServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private DiscountRepository discountRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private HashingService hashingService;

    @InjectMocks
    private CheckoutService checkoutService;

    @Test
    void testCheckoutSuccessWithoutDiscount() {
        String userId = "user-123";
        String idempotencyKey = "key-123";
        CheckoutRequest request = new CheckoutRequest(null);
        String expectedHash = "hash-123";

        CartItem item = new CartItem("SKU-1", "Product 1", 1000L, 2);
        Cart cart = new Cart("cart-123", userId, List.of(item));
        Order order = new Order(
                "ord-123", 1L, userId, List.of(item), 2000L,
                null, null, 0L, 2000L, "PLACED", Instant.now()
        );

        when(hashingService.sha256("discountCode=")).thenReturn(expectedHash);
        when(orderRepository.findByIdempotency(userId, idempotencyKey)).thenReturn(Optional.empty());
        when(cartRepository.findActiveCart(userId)).thenReturn(Optional.of(cart));
        when(orderRepository.nextOrderNumber()).thenReturn(1L);
        when(orderRepository.getOrder(anyString())).thenReturn(order);

        CheckoutResult result = checkoutService.checkout(userId, idempotencyKey, request);

        assertNotNull(result);
        assertFalse(result.replayed());
        assertEquals(order, result.order());

        verify(orderRepository).createOrder(
                anyString(), eq(1L), eq(userId), eq("cart-123"), eq(cart.items()),
                eq(2000L), eq(null), eq(null), eq(0L), eq(2000L),
                eq(idempotencyKey), eq(expectedHash), any(Instant.class)
        );
        verify(cartRepository).markCheckedOut("cart-123");
    }

    @Test
    void testCheckoutSuccessWithValidDiscount() {
        String userId = "user-123";
        String idempotencyKey = "key-123";
        CheckoutRequest request = new CheckoutRequest("SAVE10");
        String expectedHash = "hash-save10";

        CartItem item = new CartItem("SKU-1", "Product 1", 1000L, 2);
        Cart cart = new Cart("cart-123", userId, List.of(item));
        DiscountCode discountCode = new DiscountCode(
                "SAVE10", 10, "ACTIVE", 3L, null,
                Instant.now(), Instant.now().plusSeconds(3600), null
        );
        Order order = new Order(
                "ord-123", 1L, userId, List.of(item), 2000L,
                "SAVE10", 10, 200L, 1800L, "PLACED", Instant.now()
        );

        when(hashingService.sha256("discountCode=SAVE10")).thenReturn(expectedHash);
        when(orderRepository.findByIdempotency(userId, idempotencyKey)).thenReturn(Optional.empty());
        when(cartRepository.findActiveCart(userId)).thenReturn(Optional.of(cart));
        when(discountRepository.find("SAVE10")).thenReturn(Optional.of(discountCode));
        when(orderRepository.nextOrderNumber()).thenReturn(1L);
        when(discountRepository.consume(eq("SAVE10"), anyString(), any(Instant.class))).thenReturn(true);
        when(orderRepository.getOrder(anyString())).thenReturn(order);

        CheckoutResult result = checkoutService.checkout(userId, idempotencyKey, request);

        assertNotNull(result);
        assertFalse(result.replayed());
        assertEquals(order, result.order());

        verify(orderRepository).createOrder(
                anyString(), eq(1L), eq(userId), eq("cart-123"), eq(cart.items()),
                eq(2000L), eq("SAVE10"), eq(10), eq(200L), eq(1800L),
                eq(idempotencyKey), eq(expectedHash), any(Instant.class)
        );
        verify(discountRepository).consume(eq("SAVE10"), anyString(), any(Instant.class));
        verify(cartRepository).markCheckedOut("cart-123");
    }

    @Test
    void testCheckoutIdempotencyCacheHit() {
        String userId = "user-123";
        String idempotencyKey = "key-123";
        CheckoutRequest request = new CheckoutRequest(null);
        String expectedHash = "hash-123";
        OrderRecord existingRecord = new OrderRecord("ord-123", expectedHash);
        Order existingOrder = new Order(
                "ord-123", 1L, userId, Collections.emptyList(), 0L,
                null, null, 0L, 0L, "PLACED", Instant.now()
        );

        when(hashingService.sha256("discountCode=")).thenReturn(expectedHash);
        when(orderRepository.findByIdempotency(userId, idempotencyKey)).thenReturn(Optional.of(existingRecord));
        when(orderRepository.getOrder("ord-123")).thenReturn(existingOrder);

        CheckoutResult result = checkoutService.checkout(userId, idempotencyKey, request);

        assertNotNull(result);
        assertTrue(result.replayed());
        assertEquals(existingOrder, result.order());

        verify(cartRepository, never()).findActiveCart(anyString());
        verify(orderRepository, never()).createOrder(any(), anyLong(), any(), any(), any(), anyLong(), any(), any(), anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    void testCheckoutIdempotencyConflict() {
        String userId = "user-123";
        String idempotencyKey = "key-123";
        CheckoutRequest request = new CheckoutRequest(null);
        String expectedHash = "hash-123";
        OrderRecord existingRecord = new OrderRecord("ord-123", "different-hash");

        when(hashingService.sha256("discountCode=")).thenReturn(expectedHash);
        when(orderRepository.findByIdempotency(userId, idempotencyKey)).thenReturn(Optional.of(existingRecord));

        ConflictException exception = assertThrows(ConflictException.class, () ->
                checkoutService.checkout(userId, idempotencyKey, request)
        );

        assertEquals("IDEMPOTENCY_CONFLICT", exception.errorCode());
        verify(orderRepository, never()).getOrder(anyString());
    }

    @Test
    void testCheckoutThrowsBadRequestWhenUserIdMissing() {
        BadRequestException exception = assertThrows(BadRequestException.class, () ->
                checkoutService.checkout(null, "key-123", new CheckoutRequest(null))
        );
        assertEquals("MISSING_USER_ID", exception.errorCode());

        exception = assertThrows(BadRequestException.class, () ->
                checkoutService.checkout("  ", "key-123", new CheckoutRequest(null))
        );
        assertEquals("MISSING_USER_ID", exception.errorCode());
    }

    @Test
    void testCheckoutThrowsBadRequestWhenIdempotencyKeyMissing() {
        BadRequestException exception = assertThrows(BadRequestException.class, () ->
                checkoutService.checkout("user-123", null, new CheckoutRequest(null))
        );
        assertEquals("MISSING_IDEMPOTENCY_KEY", exception.errorCode());

        exception = assertThrows(BadRequestException.class, () ->
                checkoutService.checkout("user-123", "  ", new CheckoutRequest(null))
        );
        assertEquals("MISSING_IDEMPOTENCY_KEY", exception.errorCode());
    }

    @Test
    void testCheckoutThrowsEmptyCartWhenNoActiveCart() {
        String userId = "user-123";
        String idempotencyKey = "key-123";

        when(hashingService.sha256(anyString())).thenReturn("hash");
        when(orderRepository.findByIdempotency(userId, idempotencyKey)).thenReturn(Optional.empty());
        when(cartRepository.findActiveCart(userId)).thenReturn(Optional.empty());

        ConflictException exception = assertThrows(ConflictException.class, () ->
                checkoutService.checkout(userId, idempotencyKey, new CheckoutRequest(null))
        );

        assertEquals("EMPTY_CART", exception.errorCode());
    }

    @Test
    void testCheckoutThrowsEmptyCartWhenActiveCartIsEmpty() {
        String userId = "user-123";
        String idempotencyKey = "key-123";
        Cart emptyCart = new Cart("cart-123", userId, Collections.emptyList());

        when(hashingService.sha256(anyString())).thenReturn("hash");
        when(orderRepository.findByIdempotency(userId, idempotencyKey)).thenReturn(Optional.empty());
        when(cartRepository.findActiveCart(userId)).thenReturn(Optional.of(emptyCart));

        ConflictException exception = assertThrows(ConflictException.class, () ->
                checkoutService.checkout(userId, idempotencyKey, new CheckoutRequest(null))
        );

        assertEquals("EMPTY_CART", exception.errorCode());
    }

    @Test
    void testCheckoutThrowsInvalidDiscountWhenDiscountNotFound() {
        String userId = "user-123";
        String idempotencyKey = "key-123";
        CheckoutRequest request = new CheckoutRequest("INVALID");
        CartItem item = new CartItem("SKU-1", "Product 1", 1000L, 2);
        Cart cart = new Cart("cart-123", userId, List.of(item));

        when(hashingService.sha256("discountCode=INVALID")).thenReturn("hash");
        when(orderRepository.findByIdempotency(userId, idempotencyKey)).thenReturn(Optional.empty());
        when(cartRepository.findActiveCart(userId)).thenReturn(Optional.of(cart));
        when(discountRepository.find("INVALID")).thenReturn(Optional.empty());

        ConflictException exception = assertThrows(ConflictException.class, () ->
                checkoutService.checkout(userId, idempotencyKey, request)
        );

        assertEquals("INVALID_DISCOUNT_CODE", exception.errorCode());
    }

    @Test
    void testCheckoutThrowsInvalidDiscountWhenDiscountInactiveOrExpired() {
        String userId = "user-123";
        String idempotencyKey = "key-123";
        CheckoutRequest request = new CheckoutRequest("EXPIRED");
        CartItem item = new CartItem("SKU-1", "Product 1", 1000L, 2);
        Cart cart = new Cart("cart-123", userId, List.of(item));
        DiscountCode discountCode = new DiscountCode(
                "EXPIRED", 10, "INACTIVE", 3L, null,
                Instant.now(), Instant.now().minusSeconds(10), null
        );

        when(hashingService.sha256("discountCode=EXPIRED")).thenReturn("hash");
        when(orderRepository.findByIdempotency(userId, idempotencyKey)).thenReturn(Optional.empty());
        when(cartRepository.findActiveCart(userId)).thenReturn(Optional.of(cart));
        when(discountRepository.find("EXPIRED")).thenReturn(Optional.of(discountCode));

        ConflictException exception = assertThrows(ConflictException.class, () ->
                checkoutService.checkout(userId, idempotencyKey, request)
        );

        assertEquals("INVALID_DISCOUNT_CODE", exception.errorCode());
    }

    @Test
    void testCheckoutThrowsInvalidDiscountWhenConsumeFails() {
        String userId = "user-123";
        String idempotencyKey = "key-123";
        CheckoutRequest request = new CheckoutRequest("SAVE10");
        String expectedHash = "hash-save10";

        CartItem item = new CartItem("SKU-1", "Product 1", 1000L, 2);
        Cart cart = new Cart("cart-123", userId, List.of(item));
        DiscountCode discountCode = new DiscountCode(
                "SAVE10", 10, "ACTIVE", 3L, null,
                Instant.now(), Instant.now().plusSeconds(3600), null
        );

        when(hashingService.sha256("discountCode=SAVE10")).thenReturn(expectedHash);
        when(orderRepository.findByIdempotency(userId, idempotencyKey)).thenReturn(Optional.empty());
        when(cartRepository.findActiveCart(userId)).thenReturn(Optional.of(cart));
        when(discountRepository.find("SAVE10")).thenReturn(Optional.of(discountCode));
        when(orderRepository.nextOrderNumber()).thenReturn(1L);
        when(discountRepository.consume(eq("SAVE10"), anyString(), any(Instant.class))).thenReturn(false);

        ConflictException exception = assertThrows(ConflictException.class, () ->
                checkoutService.checkout(userId, idempotencyKey, request)
        );

        assertEquals("INVALID_DISCOUNT_CODE", exception.errorCode());
        verify(cartRepository, never()).markCheckedOut(anyString());
    }
}
