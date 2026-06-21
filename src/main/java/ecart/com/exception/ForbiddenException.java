package ecart.com.exception;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends EcartException {
    public ForbiddenException(String errorCode, String message) {
        super(HttpStatus.FORBIDDEN, errorCode, message);
    }
}
