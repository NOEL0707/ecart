package ecart.com.service;

import ecart.com.dto.AdminSummaryResponse;
import ecart.com.dto.GenerateDiscountCodeRequest;
import ecart.com.exception.ConflictException;
import ecart.com.model.DiscountCode;
import ecart.com.repository.DiscountRepository;
import ecart.com.repository.OrderRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        long eligibleOrderNumber = orderRepository.findOldestUngeneratedNthOrder(nthOrder)
                .orElseThrow(() -> new ConflictException(
                        "DISCOUNT_NOT_ELIGIBLE",
                        "No undiscounted nth order is currently eligible for discount code generation."
                ));
        return discountRepository.findByTriggeredOrderNumber(eligibleOrderNumber)
                .orElseGet(() -> createDiscountCode(request, eligibleOrderNumber));
    }

    @Transactional(readOnly = true)
    public AdminSummaryResponse summary() {
        var totals = orderRepository.getReportTotals();
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
