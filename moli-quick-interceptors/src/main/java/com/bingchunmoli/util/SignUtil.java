package com.bingchunmoli.util;

import com.bingchunmoli.bean.SignParamDTO;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 签名接口
 */
public interface SignUtil {
    /**
     * 获取请求的签名参数
     * @param request 请求
     * @return SignParamDTO
     */
    SignParamDTO getSignParam(HttpServletRequest request);


    /**
     * 根据请求进行签名验证
     * @param request 请求
     * @return 签名验证
     */
    boolean verify(HttpServletRequest request);
}
