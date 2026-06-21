package ecart.com.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class HashingService {
    public String sha256(String value) {
        log.debug("Generating SHA-256 hash for input of length {}", value == null ? 0 : value.length());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            String result = hex.toString();
            log.debug("SHA-256 hash successfully generated");
            return result;
        } catch (NoSuchAlgorithmException ex) {
            log.error("SHA-256 algorithm is not available in the environment", ex);
            throw new IllegalStateException("SHA-256 is unavailable.", ex);
        }
    }
}
