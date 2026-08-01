package com.bingchunmoli.web.exception;

import java.time.Instant;

/**
 * Common diagnostic data carried by failed {@code ResultVO} responses.
 */
public record WebErrorDetails(Instant timestamp, String path, String requestId, Object details) {
}
