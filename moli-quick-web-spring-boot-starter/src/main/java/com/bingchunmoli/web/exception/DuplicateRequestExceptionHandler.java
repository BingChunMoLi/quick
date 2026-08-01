package com.bingchunmoli.web.exception;

import com.bingchunmoli.idempotency.exception.DuplicateRequestException;
import com.bingchunmoli.web.autoconfigure.QuickWebProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps idempotency conflicts without exposing the internal storage key.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class DuplicateRequestExceptionHandler extends AbstractWebExceptionHandler {

    private final QuickWebProperties.ExceptionHandling properties;

    public DuplicateRequestExceptionHandler(WebExceptionResponseFactory responseFactory, QuickWebProperties properties) {
        super(responseFactory);
        this.properties = properties.getExceptionHandling();
    }

    @ExceptionHandler(DuplicateRequestException.class)
    public ResponseEntity<?> handle(DuplicateRequestException exception, HttpServletRequest request) {
        return response(
                HttpStatus.CONFLICT,
                properties.getDuplicateRequestCode(),
                "重复请求，请稍后重试",
                request,
                null);
    }
}
