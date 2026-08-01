package com.bingchunmoli.web.exception;

import com.bingchunmoli.bean.ResultVO;
import org.springframework.http.ResponseEntity;

/**
 * Builds the repository's standard {@link ResultVO} error response.
 */
public class DefaultWebExceptionResponseFactory implements WebExceptionResponseFactory {

    @Override
    public ResponseEntity<ResultVO<WebErrorDetails>> create(WebErrorContext context) {
        WebErrorDetails details = new WebErrorDetails(
                context.timestamp(), context.path(), context.requestId(), context.details());
        ResultVO<WebErrorDetails> body = new ResultVO<>(context.code(), context.message(), details);
        return ResponseEntity.status(context.status()).body(body);
    }
}
