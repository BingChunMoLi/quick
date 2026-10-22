package com.bingchunmoli.codegen.web;

import com.bingchunmoli.codegen.CodegenException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestControllerAdvice(assignableTypes = CodegenController.class)
public class CodegenExceptionHandler {
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> malformedJson(org.springframework.http.converter.HttpMessageNotReadableException error) {
        return ResponseEntity.badRequest().body(Map.of("diagnostics", List.of("Invalid JSON configuration")));
    }
    @ExceptionHandler(CodegenException.class)
    public ResponseEntity<Map<String, Object>> diagnostic(CodegenException error) {
        return ResponseEntity.badRequest().body(Map.of("diagnostics", List.of(error.getMessage())));
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> internal(Exception error) {
        if (error instanceof org.springframework.web.ErrorResponse response) {
            return ResponseEntity.status(response.getStatusCode()).headers(response.getHeaders())
                    .body(Map.of("diagnostics", List.of("Invalid code generation request")));
        }
        return ResponseEntity.internalServerError().body(Map.of("diagnostics", List.of("Generation failed; check configuration and server environment")));
    }
}
