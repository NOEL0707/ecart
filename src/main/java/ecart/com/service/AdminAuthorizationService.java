package ecart.com.service;

import ecart.com.config.EcartProperties;
import ecart.com.exception.BadRequestException;
import ecart.com.exception.ForbiddenException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AdminAuthorizationService {
    private final EcartProperties properties;

    public AdminAuthorizationService(EcartProperties properties) {
        this.properties = properties;
    }

    public void authorize(String adminId, String adminRole) {
        log.info("Authorizing admin request. adminId: {}, adminRole: {}", adminId, adminRole);
        if (adminId == null || adminId.isBlank()) {
            log.warn("Authorization failed: X-Admin-Id header is missing or blank");
            throw new BadRequestException("MISSING_ADMIN_ID", "X-Admin-Id header is required.");
        }
        if (adminRole == null || adminRole.isBlank()) {
            log.warn("Authorization failed: X-Admin-Role header is missing or blank");
            throw new BadRequestException("MISSING_ADMIN_ROLE", "X-Admin-Role header is required.");
        }
        boolean allowed = properties.allowedAdminRoles().stream()
                .anyMatch(role -> role.equalsIgnoreCase(adminRole.trim()));
        if (!allowed) {
            log.warn("Authorization failed: adminId {} with role {} is not authorized. Allowed roles: {}",
                    adminId, adminRole, properties.allowedAdminRoles());
            throw new ForbiddenException("ADMIN_FORBIDDEN", "Admin role is not authorized.");
        }
        log.info("Authorization successful for adminId: {} with role: {}", adminId, adminRole);
    }
}
