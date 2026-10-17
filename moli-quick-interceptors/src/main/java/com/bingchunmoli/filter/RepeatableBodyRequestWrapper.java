package com.bingchunmoli.filter;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.WebUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;

/** Bounded request-body snapshot. Each reader/stream starts at the beginning of the same bytes. */
public final class RepeatableBodyRequestWrapper extends HttpServletRequestWrapper {
    private final byte[] body;

    public RepeatableBodyRequestWrapper(HttpServletRequest request, int maximumBytes) throws IOException {
        super(request);
        if (maximumBytes < 1 || maximumBytes == Integer.MAX_VALUE) throw new IllegalArgumentException("Invalid body limit");
        if (request.getContentLengthLong() > maximumBytes) throw new BodyTooLargeException();
        ContentCachingRequestWrapper cached = WebUtils.getNativeRequest(request, ContentCachingRequestWrapper.class);
        byte[] prefix = cached == null ? new byte[0] : cached.getContentAsByteArray();
        if (prefix.length > maximumBytes) throw new BodyTooLargeException();
        byte[] remaining = request.getInputStream().readNBytes(maximumBytes - prefix.length + 1);
        if (prefix.length + remaining.length > maximumBytes) throw new BodyTooLargeException();
        ByteArrayOutputStream snapshot = new ByteArrayOutputStream(prefix.length + remaining.length);
        snapshot.write(prefix); snapshot.write(remaining);
        body = snapshot.toByteArray();
        if (request.getContentLengthLong() >= 0 && body.length != request.getContentLengthLong())
            throw new IOException("Cannot cache the complete JSON body; register CacheFilter before body-consuming filters");
    }

    @Override
    public ServletInputStream getInputStream() {
        return new ServletInputStream() {
            private final ByteArrayInputStream input = new ByteArrayInputStream(body);
            private boolean listenerRegistered;

            @Override public int read() { return input.read(); }
            @Override public int read(byte[] bytes, int offset, int length) { return input.read(bytes, offset, length); }
            @Override public int available() { return input.available(); }
            @Override public boolean isFinished() { return input.available() == 0; }
            @Override public boolean isReady() { return true; }
            @Override public void setReadListener(ReadListener listener) {
                if (listener == null) throw new IllegalArgumentException("ReadListener is required");
                if (listenerRegistered) throw new IllegalStateException("ReadListener already registered");
                listenerRegistered = true;
                try {
                    if (!isFinished()) listener.onDataAvailable();
                    if (isFinished()) listener.onAllDataRead();
                } catch (IOException | RuntimeException error) { listener.onError(error); }
            }
        };
    }

    @Override
    public BufferedReader getReader() throws IOException {
        String encoding = getCharacterEncoding();
        return new BufferedReader(encoding == null
                ? new InputStreamReader(getInputStream(), StandardCharsets.UTF_8)
                : new InputStreamReader(getInputStream(), encoding));
    }

    static final class BodyTooLargeException extends IOException {
        BodyTooLargeException() { super("JSON request body exceeds the signature cache limit"); }
    }
}
