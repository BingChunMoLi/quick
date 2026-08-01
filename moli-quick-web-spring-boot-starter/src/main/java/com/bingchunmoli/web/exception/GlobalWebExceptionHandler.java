package com.bingchunmoli.web.exception;

import com.bingchunmoli.web.autoconfigure.QuickWebProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps common Spring MVC failures to a stable {@code ResultVO} error contract.
 */
@Order(Ordered.LOWEST_PRECEDENCE)
@RestControllerAdvice
public class GlobalWebExceptionHandler extends AbstractWebExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalWebExceptionHandler.class);

    private final QuickWebProperties.ExceptionHandling properties;

    public GlobalWebExceptionHandler(WebExceptionResponseFactory responseFactory,
                                     QuickWebProperties properties) {
        super(responseFactory);
        this.properties = properties.getExceptionHandling();
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<?> handleApiException(ApiException exception, HttpServletRequest request) {
        return response(
                exception.getStatus(), exception.getCode(), exception.getMessage(), request, exception.getDetails());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        return validationResponse(request, fieldErrors(exception));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<?> handleBindException(BindException exception, HttpServletRequest request) {
        return validationResponse(request, fieldErrors(exception));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<?> handleConstraintViolation(
            ConstraintViolationException exception, HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : exception.getConstraintViolations()) {
            errors.putIfAbsent(violation.getPropertyPath().toString(), violation.getMessage());
        }
        return validationResponse(request, errors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<?> handleMethodValidation(
            HandlerMethodValidationException exception, HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getAllErrors().forEach(error ->
                errors.putIfAbsent("request", defaultMessage(error.getDefaultMessage(), "参数校验失败")));
        return validationResponse(request, errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> handleUnreadableMessage(
            HttpMessageNotReadableException exception, HttpServletRequest request) {
        return response(
                HttpStatus.BAD_REQUEST,
                properties.getMalformedJsonCode(),
                "请求 JSON 解析失败",
                request,
                null);
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            ServletRequestBindingException.class
    })
    public ResponseEntity<?> handleBadRequest(Exception exception, HttpServletRequest request) {
        return response(
                HttpStatus.BAD_REQUEST,
                properties.getValidationErrorCode(),
                "请求参数错误",
                request,
                null);
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<?> handleNotFound(Exception exception, HttpServletRequest request) {
        return response(
                HttpStatus.NOT_FOUND,
                properties.getNotFoundCode(),
                "请求资源不存在",
                request,
                null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<?> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
        return response(
                HttpStatus.METHOD_NOT_ALLOWED,
                properties.getMethodNotAllowedCode(),
                "请求方法不支持",
                request,
                null);
    }

    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<?> handleSpringErrorResponse(
            ErrorResponseException exception, HttpServletRequest request) {
        String code = exception.getStatusCode().is4xxClientError()
                ? properties.getClientErrorCode()
                : properties.getSystemErrorCode();
        String message = defaultMessage(exception.getBody().getDetail(), "请求处理失败");
        return response(exception.getStatusCode(), code, message, request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleUnexpected(Exception exception, HttpServletRequest request) {
        LOGGER.error("Unhandled web request exception, path={}", request.getRequestURI(), exception);
        String message = properties.isIncludeExceptionMessage()
                ? defaultMessage(exception.getMessage(), "系统执行出错")
                : "系统执行出错";
        return response(
                HttpStatus.INTERNAL_SERVER_ERROR,
                properties.getSystemErrorCode(),
                message,
                request,
                null);
    }

    private ResponseEntity<?> validationResponse(HttpServletRequest request, Map<String, String> errors) {
        return response(
                HttpStatus.BAD_REQUEST,
                properties.getValidationErrorCode(),
                "请求参数校验失败",
                request,
                errors);
    }

    private Map<String, String> fieldErrors(BindException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(
                    fieldError.getField(), defaultMessage(fieldError.getDefaultMessage(), "参数校验失败"));
        }
        return errors;
    }

    private String defaultMessage(String message, String fallback) {
        return message == null || message.isBlank() ? fallback : message;
    }
}
