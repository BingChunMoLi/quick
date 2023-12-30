package com.bingchunmoli.util;

import com.bingchunmoli.bean.SignParamDTO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * MD5签名接口 未实现
 *
 * @author MoLi
 */
@Slf4j
@RequiredArgsConstructor
public class MD5SignUtil implements SignUtil{

    @Override
    public SignParamDTO getSignParam(HttpServletRequest request) {
        return null;
    }

    @Override
    public boolean verify(HttpServletRequest request) {
        return false;
    }
}
