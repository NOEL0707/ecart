package ecart.com.exception;

import org.springframework.http.HttpStatus;

public class BadRequestException extends EcartException {
    public BadRequestException(String errorCode, String message) {
        super(HttpStatus.BAD_REQUEST, errorCode, message);
    }
}
