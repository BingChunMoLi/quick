package com.bingchunmoli.interceptor;


import com.bingchunmoli.properties.InterceptorsAutoConfigurationProperties;
import com.bingchunmoli.util.SignUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;


@Slf4j
@RequiredArgsConstructor
@ConditionalOnClass({ObjectMapper.class, InterceptorsAutoConfigurationProperties.class})
public class SignInterceptor implements HandlerInterceptor {

    private final InterceptorsAutoConfigurationProperties interceptorsAutoConfigurationProperties;
    private final SignUtil signUtil;
    private final ObjectMapper om;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        final InterceptorsAutoConfigurationProperties.SignProperties sign = interceptorsAutoConfigurationProperties.getSign();
        if (isNotEnable(sign.isEnable())) {
            return true;
        }
        if (request.getMethod().equalsIgnoreCase("OPTION")) {
            return true;
        }
        if (sign.getIgnorePathList().contains(request.getRequestURI())) {
            return true;
        }
        return signUtil.compare(request);
    }


    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        HandlerInterceptor.super.postHandle(request, response, handler, modelAndView);
    }


    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
    }

    private boolean isNotEnable(boolean isEnable) {
        return !isEnable;
    }


}
