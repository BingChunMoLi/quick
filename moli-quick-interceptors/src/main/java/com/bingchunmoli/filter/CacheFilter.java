package com.bingchunmoli.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.web.util.WebUtils;

import java.io.IOException;

/**
 * 在验签和 MVC 读取前缓存 JSON 请求体，保证双方均能读取完整内容。
 *
 * @author MoLi
 */
public class CacheFilter extends HttpFilter {

    private static final int CONTENT_CACHE_LIMIT = 1024 * 1024;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (request == null) {
            return;
        }
        if (request instanceof HttpServletRequest http && isJsonRequest(http)
                && WebUtils.getNativeRequest(http, RepeatableBodyRequestWrapper.class) == null) {
            try {
                request = new RepeatableBodyRequestWrapper(http, CONTENT_CACHE_LIMIT);
            } catch (RepeatableBodyRequestWrapper.BodyTooLargeException error) {
                if (response instanceof HttpServletResponse httpResponse) {
                    httpResponse.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, error.getMessage());
                    return;
                }
                throw error;
            }
        }
        super.doFilter(request, response, chain);
    }

    public static boolean isJsonRequest(HttpServletRequest request) {
        if (request.getContentType() == null) return false;
        try {
            MediaType type = MediaType.parseMediaType(request.getContentType());
            return MediaType.APPLICATION_JSON.includes(type)
                    || type.getType().equals("application") && type.getSubtype().endsWith("+json");
        } catch (InvalidMediaTypeException error) { return false; }
    }
}
