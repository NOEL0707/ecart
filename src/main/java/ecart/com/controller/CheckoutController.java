package ecart.com.controller;

import ecart.com.dto.CheckoutRequest;
import ecart.com.dto.OrderResponse;
import ecart.com.observability.RequestContext;
import ecart.com.service.CheckoutResult;
import ecart.com.service.CheckoutService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/checkout")
public class CheckoutController {
    private final CheckoutService checkoutService;

    public CheckoutController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> checkout(
            @RequestHeader(RequestContext.USER_ID_HEADER) String userId,
            @RequestHeader(RequestContext.IDEMPOTENCY_KEY_HEADER) String idempotencyKey,
            @Valid @RequestBody(required = false) CheckoutRequest request
    ) {
        CheckoutResult result = checkoutService.checkout(userId, idempotencyKey, request);
        HttpStatus status = result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status)
                .header(RequestContext.IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                .body(OrderResponse.from(result.order()));
    }
}
