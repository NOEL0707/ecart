package ecart.com.dto;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
        Instant timestamp,
        int status,
        String errorCode,
        String message,
        String correlationId,
        String path,
        List<ErrorDetail> details
) {
}
