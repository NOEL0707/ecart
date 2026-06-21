package ecart.com.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class CorrelationFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String correlationId = headerOrGenerated(request.getHeader(RequestContext.CORRELATION_ID_HEADER));
        MDC.put("correlationId", correlationId);
        putIfPresent("userId", request.getHeader(RequestContext.USER_ID_HEADER));
        putIfPresent("adminId", request.getHeader(RequestContext.ADMIN_ID_HEADER));
        response.setHeader(RequestContext.CORRELATION_ID_HEADER, correlationId);
        response.setHeader("Cache-Control", "no-store");
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }

    private String headerOrGenerated(String value) {
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value.trim();
    }

    private void putIfPresent(String key, String value) {
        if (value != null && !value.isBlank()) {
            MDC.put(key, value.trim());
        }
    }
}
