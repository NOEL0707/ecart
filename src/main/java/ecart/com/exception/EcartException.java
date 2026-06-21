package ecart.com.exception;

import org.springframework.http.HttpStatus;

public abstract class EcartException extends RuntimeException {
    private final HttpStatus status;
    private final String errorCode;

    protected EcartException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus status() {
        return status;
    }

    public String errorCode() {
        return errorCode;
    }
}
