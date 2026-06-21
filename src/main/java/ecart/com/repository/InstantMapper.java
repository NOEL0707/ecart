package ecart.com.repository;

import java.time.Instant;

final class InstantMapper {
    private InstantMapper() {
    }

    static String format(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    static Instant parse(String value) {
        return value == null ? null : Instant.parse(value);
    }
}
