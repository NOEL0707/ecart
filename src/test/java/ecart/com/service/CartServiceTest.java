package ecart.com.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ecart.com.dto.AddCartItemRequest;
import ecart.com.exception.BadRequestException;
import ecart.com.model.Cart;
import ecart.com.model.CartItem;
import ecart.com.repository.CartRepository;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;

    @InjectMocks
    private CartService cartService;

    @Test
    void testAddItemSuccess() {
        String userId = "user-123";
        AddCartItemRequest request = new AddCartItemRequest("  SKU-1  ", "  Item Name  ", 500L, 3);
        Cart initialCart = new Cart("cart-id-123", "user-123", Collections.emptyList());
        CartItem expectedUpsertedItem = new CartItem("SKU-1", "Item Name", 500L, 3);
        Cart updatedCart = new Cart("cart-id-123", "user-123", List.of(expectedUpsertedItem));

        when(cartRepository.getOrCreateActiveCart("user-123"))
                .thenReturn(initialCart)
                .thenReturn(updatedCart);

        Cart result = cartService.addItem(userId, request);

        assertNotNull(result);
        assertEquals("cart-id-123", result.id());
        assertEquals("user-123", result.userId());
        assertEquals(1, result.items().size());
        assertEquals("SKU-1", result.items().get(0).sku());

        verify(cartRepository, times(2)).getOrCreateActiveCart("user-123");
        verify(cartRepository).upsertItem(eq("cart-id-123"), eq(expectedUpsertedItem));
    }

    @Test
    void testAddItemThrowsBadRequestWhenUserIdMissing() {
        AddCartItemRequest request = new AddCartItemRequest("SKU-1", "Item Name", 500L, 3);

        BadRequestException exception = assertThrows(BadRequestException.class, () ->
                cartService.addItem(null, request)
        );
        assertEquals("MISSING_USER_ID", exception.errorCode());

        exception = assertThrows(BadRequestException.class, () ->
                cartService.addItem("   ", request)
        );
        assertEquals("MISSING_USER_ID", exception.errorCode());
    }

    @Test
    void testGetActiveCartSuccess() {
        String userId = "  user-123  ";
        Cart expectedCart = new Cart("cart-id-123", "user-123", Collections.emptyList());

        when(cartRepository.getOrCreateActiveCart("user-123")).thenReturn(expectedCart);

        Cart result = cartService.getActiveCart(userId);

        assertNotNull(result);
        assertEquals("cart-id-123", result.id());
        verify(cartRepository).getOrCreateActiveCart("user-123");
    }

    @Test
    void testGetActiveCartThrowsBadRequestWhenUserIdMissing() {
        BadRequestException exception = assertThrows(BadRequestException.class, () ->
                cartService.getActiveCart(null)
        );
        assertEquals("MISSING_USER_ID", exception.errorCode());

        exception = assertThrows(BadRequestException.class, () ->
                cartService.getActiveCart("   ")
        );
        assertEquals("MISSING_USER_ID", exception.errorCode());
    }
}
