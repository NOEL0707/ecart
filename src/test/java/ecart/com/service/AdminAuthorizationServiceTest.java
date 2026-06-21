package ecart.com.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ecart.com.config.EcartProperties;
import ecart.com.exception.BadRequestException;
import ecart.com.exception.ForbiddenException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdminAuthorizationServiceTest {

    private EcartProperties properties;
    private AdminAuthorizationService authorizationService;

    @BeforeEach
    void setUp() {
        properties = new EcartProperties(
                new EcartProperties.Discount(3, 10),
                new EcartProperties.Admin(List.of("ADMIN", "SUPERUSER"))
        );
        authorizationService = new AdminAuthorizationService(properties);
    }

    @Test
    void testAuthorizeSuccess() {
        assertDoesNotThrow(() -> authorizationService.authorize("admin123", "ADMIN"));
        assertDoesNotThrow(() -> authorizationService.authorize("admin123", "superuser"));
        assertDoesNotThrow(() -> authorizationService.authorize("admin123", "  ADMIN  "));
    }

    @Test
    void testAuthorizeThrowsBadRequestWhenAdminIdMissing() {
        BadRequestException exception = assertThrows(BadRequestException.class, () ->
                authorizationService.authorize(null, "ADMIN")
        );
        assertEquals("MISSING_ADMIN_ID", exception.errorCode());

        exception = assertThrows(BadRequestException.class, () ->
                authorizationService.authorize("   ", "ADMIN")
        );
        assertEquals("MISSING_ADMIN_ID", exception.errorCode());
    }

    @Test
    void testAuthorizeThrowsBadRequestWhenAdminRoleMissing() {
        BadRequestException exception = assertThrows(BadRequestException.class, () ->
                authorizationService.authorize("admin123", null)
        );
        assertEquals("MISSING_ADMIN_ROLE", exception.errorCode());

        exception = assertThrows(BadRequestException.class, () ->
                authorizationService.authorize("admin123", "  ")
        );
        assertEquals("MISSING_ADMIN_ROLE", exception.errorCode());
    }

    @Test
    void testAuthorizeThrowsForbiddenWhenRoleNotAllowed() {
        ForbiddenException exception = assertThrows(ForbiddenException.class, () ->
                authorizationService.authorize("admin123", "USER")
        );
        assertEquals("ADMIN_FORBIDDEN", exception.errorCode());
    }
}
