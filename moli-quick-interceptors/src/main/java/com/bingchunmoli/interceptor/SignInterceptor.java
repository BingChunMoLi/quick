package com.bingchunmoli.interceptor;


import com.bingchunmoli.properties.InterceptorsAutoConfigurationProperties;
import com.bingchunmoli.util.SignUtil;
import jakarta.annotation.Nonnull;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.ContentCachingRequestWrapper;

/**
 * 签名拦截器
 *
 * @author MoLi
 */
@Slf4j
@RequiredArgsConstructor
@ConditionalOnClass({SignUtil.class})
public class SignInterceptor implements HandlerInterceptor {

    private final InterceptorsAutoConfigurationProperties interceptorsAutoConfigurationProperties;
    private final SignUtil signUtil;

    @Override
    public boolean preHandle(@Nonnull HttpServletRequest request, @Nonnull HttpServletResponse response, @Nonnull Object handler) throws Exception {
        final InterceptorsAutoConfigurationProperties.SignProperties sign = interceptorsAutoConfigurationProperties.getSign();
        if (isNotEnable(sign.getSign().isEnable())) {
            return true;
        }
        if (request.getMethod().equalsIgnoreCase("OPTIONS")) {
            return true;
        }
        if (sign.getIgnorePathList().contains(request.getRequestURI())) {
            return true;
        }
        //如果请求使用jsonBody方式并且没有包装过可重复读取,则包装可重复读取ServletRequest
        if (request.getContentType() != null && MediaType.APPLICATION_JSON.includes(MediaType.parseMediaType(request.getContentType())) && !(request instanceof ContentCachingRequestWrapper)) {
            request = new ContentCachingRequestWrapper(request);
        }
        return signUtil.verify(request);
    }


    @Override
    public void postHandle(@Nonnull HttpServletRequest request,@Nonnull HttpServletResponse response,@Nonnull Object handler, ModelAndView modelAndView) throws Exception {
        HandlerInterceptor.super.postHandle(request, response, handler, modelAndView);
    }


    @Override
    public void afterCompletion(@Nonnull HttpServletRequest request,@Nonnull HttpServletResponse response,@Nonnull Object handler, Exception ex) throws Exception {
        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
    }

    private boolean isNotEnable(boolean isEnable) {
        return !isEnable;
    }


}
