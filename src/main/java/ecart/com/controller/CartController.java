package ecart.com.controller;

import ecart.com.dto.AddCartItemRequest;
import ecart.com.dto.CartResponse;
import ecart.com.observability.RequestContext;
import ecart.com.service.CartService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cart")
public class CartController {
    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public CartResponse addItem(
            @RequestHeader(RequestContext.USER_ID_HEADER) String userId,
            @Valid @RequestBody AddCartItemRequest request
    ) {
        return CartResponse.from(cartService.addItem(userId, request));
    }

    @GetMapping
    public CartResponse getCart(@RequestHeader(RequestContext.USER_ID_HEADER) String userId) {
        return CartResponse.from(cartService.getActiveCart(userId));
    }
}
