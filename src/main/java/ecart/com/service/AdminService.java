package ecart.com.service;

import ecart.com.dto.AdminSummaryResponse;
import ecart.com.dto.GenerateDiscountCodeRequest;
import ecart.com.exception.ConflictException;
import ecart.com.model.DiscountCode;
import ecart.com.repository.DiscountRepository;
import ecart.com.repository.OrderRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class AdminService {
    private final DiscountRepository discountRepository;
    private final OrderRepository orderRepository;

    public AdminService(DiscountRepository discountRepository, OrderRepository orderRepository) {
        this.discountRepository = discountRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public DiscountCode generateDiscountCode(GenerateDiscountCodeRequest request) {
        long nthOrder = request.nthOrder();
        log.info("Request to generate discount code for nthOrder: {}, discountPercent: {}, expiresInDays: {}",
                nthOrder, request.discountPercent(), request.expiresInDays());
        long eligibleOrderNumber = orderRepository.findOldestUngeneratedNthOrder(nthOrder)
                .orElseThrow(() -> {
                    log.warn("Discount code generation failed: No undiscounted nth order is currently eligible for nthOrder: {}", nthOrder);
                    return new ConflictException(
                            "DISCOUNT_NOT_ELIGIBLE",
                            "No undiscounted nth order is currently eligible for discount code generation."
                    );
                });
        log.info("Found eligible order number: {} for nthOrder: {}", eligibleOrderNumber, nthOrder);
        return discountRepository.findByTriggeredOrderNumber(eligibleOrderNumber)
                .map(code -> {
                    log.info("Discount code already exists for order number: {}. Returning code: {}", eligibleOrderNumber, code.code());
                    return code;
                })
                .orElseGet(() -> {
                    DiscountCode code = createDiscountCode(request, eligibleOrderNumber);
                    log.info("Generated new discount code: {} for order number: {}", code.code(), eligibleOrderNumber);
                    return code;
                });
    }

    @Transactional(readOnly = true)
    public AdminSummaryResponse summary() {
        log.info("Retrieving admin summary report");
        var totals = orderRepository.getReportTotals();
        log.info("Admin summary retrieved: itemsPurchasedCount={}, revenue={}, totalDiscountGiven={}, ordersCount={}",
                totals.itemsPurchasedCount(), totals.revenue(), totals.totalDiscountGiven(), totals.ordersCount());
        return new AdminSummaryResponse(
                totals.itemsPurchasedCount(),
                totals.revenue(),
                discountRepository.listSummaries(),
                totals.totalDiscountGiven(),
                totals.ordersCount()
        );
    }

    private DiscountCode createDiscountCode(GenerateDiscountCodeRequest request, long orderCount) {
        Instant now = Instant.now();
        Instant expiresAt = request.expiresInDays() == null ? null : now.plus(request.expiresInDays(), ChronoUnit.DAYS);
        String code = "SAVE" + request.discountPercent() + "-" + String.format("%06d", orderCount);
        return discountRepository.create(code, request.discountPercent(), orderCount, now, expiresAt);
    }
}
