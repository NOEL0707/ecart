package ecart.com.service;

import ecart.com.config.EcartProperties;
import ecart.com.exception.BadRequestException;
import ecart.com.exception.ForbiddenException;
import org.springframework.stereotype.Service;

@Service
public class AdminAuthorizationService {
    private final EcartProperties properties;

    public AdminAuthorizationService(EcartProperties properties) {
        this.properties = properties;
    }

    public void authorize(String adminId, String adminRole) {
        if (adminId == null || adminId.isBlank()) {
            throw new BadRequestException("MISSING_ADMIN_ID", "X-Admin-Id header is required.");
        }
        if (adminRole == null || adminRole.isBlank()) {
            throw new BadRequestException("MISSING_ADMIN_ROLE", "X-Admin-Role header is required.");
        }
        boolean allowed = properties.allowedAdminRoles().stream()
                .anyMatch(role -> role.equalsIgnoreCase(adminRole.trim()));
        if (!allowed) {
            throw new ForbiddenException("ADMIN_FORBIDDEN", "Admin role is not authorized.");
        }
    }
}
