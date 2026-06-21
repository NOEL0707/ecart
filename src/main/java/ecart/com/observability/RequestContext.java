package ecart.com.observability;

import java.util.Optional;
import org.slf4j.MDC;

public final class RequestContext {
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String ADMIN_ID_HEADER = "X-Admin-Id";
    public static final String ADMIN_ROLE_HEADER = "X-Admin-Role";
    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private RequestContext() {
    }

    public static String correlationId() {
        return Optional.ofNullable(MDC.get("correlationId")).orElse("unknown");
    }
}
