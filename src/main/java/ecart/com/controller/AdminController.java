package ecart.com.controller;

import ecart.com.dto.AdminSummaryResponse;
import ecart.com.dto.DiscountCodeResponse;
import ecart.com.dto.GenerateDiscountCodeRequest;
import ecart.com.observability.RequestContext;
import ecart.com.service.AdminAuthorizationService;
import ecart.com.service.AdminService;
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
@RequestMapping("/api/v1/admin")
public class AdminController {
    private final AdminService adminService;
    private final AdminAuthorizationService authorizationService;

    public AdminController(AdminService adminService, AdminAuthorizationService authorizationService) {
        this.adminService = adminService;
        this.authorizationService = authorizationService;
    }

    @PostMapping("/discount-codes")
    @ResponseStatus(HttpStatus.CREATED)
    public DiscountCodeResponse generateDiscountCode(
            @RequestHeader(RequestContext.ADMIN_ID_HEADER) String adminId,
            @RequestHeader(RequestContext.ADMIN_ROLE_HEADER) String adminRole,
            @Valid @RequestBody GenerateDiscountCodeRequest request
    ) {
        authorizationService.authorize(adminId, adminRole);
        return DiscountCodeResponse.from(adminService.generateDiscountCode(request));
    }

    @GetMapping("/reports/summary")
    public AdminSummaryResponse summary(
            @RequestHeader(RequestContext.ADMIN_ID_HEADER) String adminId,
            @RequestHeader(RequestContext.ADMIN_ROLE_HEADER) String adminRole
    ) {
        authorizationService.authorize(adminId, adminRole);
        return adminService.summary();
    }
}
