package ecart.com.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ecart.com.dto.AdminSummaryResponse;
import ecart.com.dto.DiscountCodeSummary;
import ecart.com.dto.GenerateDiscountCodeRequest;
import ecart.com.exception.ConflictException;
import ecart.com.model.DiscountCode;
import ecart.com.repository.DiscountRepository;
import ecart.com.repository.OrderRepository;
import ecart.com.repository.OrderRepository.ReportTotals;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private DiscountRepository discountRepository;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private AdminService adminService;

    @Test
    void testGenerateDiscountCodeCreatesNewCode() {
        GenerateDiscountCodeRequest request = new GenerateDiscountCodeRequest(3L, 10, 5);
        long eligibleOrderNumber = 3L;
        DiscountCode generatedCode = new DiscountCode(
                "SAVE10-000003", 10, "ACTIVE", eligibleOrderNumber,
                null, Instant.now(), Instant.now().plusSeconds(3600), null
        );

        when(orderRepository.findOldestUngeneratedNthOrder(3L)).thenReturn(Optional.of(eligibleOrderNumber));
        when(discountRepository.findByTriggeredOrderNumber(eligibleOrderNumber)).thenReturn(Optional.empty());
        when(discountRepository.create(eq("SAVE10-000003"), eq(10), eq(eligibleOrderNumber), any(Instant.class), any(Instant.class)))
                .thenReturn(generatedCode);

        DiscountCode result = adminService.generateDiscountCode(request);

        assertNotNull(result);
        assertEquals("SAVE10-000003", result.code());
        assertEquals(10, result.discountPercent());
        assertEquals(eligibleOrderNumber, result.triggeredByOrderNumber());

        verify(orderRepository).findOldestUngeneratedNthOrder(3L);
        verify(discountRepository).findByTriggeredOrderNumber(eligibleOrderNumber);
        verify(discountRepository).create(eq("SAVE10-000003"), eq(10), eq(eligibleOrderNumber), any(Instant.class), any(Instant.class));
    }

    @Test
    void testGenerateDiscountCodeReturnsExistingCode() {
        GenerateDiscountCodeRequest request = new GenerateDiscountCodeRequest(3L, 10, 5);
        long eligibleOrderNumber = 3L;
        DiscountCode existingCode = new DiscountCode(
                "SAVE10-000003", 10, "ACTIVE", eligibleOrderNumber,
                null, Instant.now(), Instant.now().plusSeconds(3600), null
        );

        when(orderRepository.findOldestUngeneratedNthOrder(3L)).thenReturn(Optional.of(eligibleOrderNumber));
        when(discountRepository.findByTriggeredOrderNumber(eligibleOrderNumber)).thenReturn(Optional.of(existingCode));

        DiscountCode result = adminService.generateDiscountCode(request);

        assertNotNull(result);
        assertEquals("SAVE10-000003", result.code());

        verify(orderRepository).findOldestUngeneratedNthOrder(3L);
        verify(discountRepository).findByTriggeredOrderNumber(eligibleOrderNumber);
        verify(discountRepository, never()).create(any(), eq(10), eq(eligibleOrderNumber), any(), any());
    }

    @Test
    void testGenerateDiscountCodeThrowsConflictWhenNotEligible() {
        GenerateDiscountCodeRequest request = new GenerateDiscountCodeRequest(3L, 10, 5);

        when(orderRepository.findOldestUngeneratedNthOrder(3L)).thenReturn(Optional.empty());

        ConflictException exception = assertThrows(ConflictException.class, () ->
                adminService.generateDiscountCode(request)
        );

        assertEquals("DISCOUNT_NOT_ELIGIBLE", exception.errorCode());
        verify(orderRepository).findOldestUngeneratedNthOrder(3L);
        verify(discountRepository, never()).findByTriggeredOrderNumber(any(Long.class));
    }

    @Test
    void testSummarySuccess() {
        ReportTotals reportTotals = new ReportTotals(25L, 50000L, 2000L, 10L);
        List<DiscountCodeSummary> codeSummaries = List.of(
                new DiscountCodeSummary("SAVE10-000003", 10, "USED", 3L, "ord_1")
        );

        when(orderRepository.getReportTotals()).thenReturn(reportTotals);
        when(discountRepository.listSummaries()).thenReturn(codeSummaries);

        AdminSummaryResponse summary = adminService.summary();

        assertNotNull(summary);
        assertEquals(25, summary.itemsPurchasedCount());
        assertEquals(50000L, summary.revenue());
        assertEquals(2000L, summary.totalDiscountGiven());
        assertEquals(10, summary.ordersCount());
        assertEquals(1, summary.discountCodes().size());
        assertEquals("SAVE10-000003", summary.discountCodes().get(0).code());

        verify(orderRepository).getReportTotals();
        verify(discountRepository).listSummaries();
    }
}
