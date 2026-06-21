package ecart.com.exception;

import org.springframework.http.HttpStatus;

public class ConflictException extends EcartException {
    public ConflictException(String errorCode, String message) {
        super(HttpStatus.CONFLICT, errorCode, message);
    }
}
