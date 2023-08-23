package com.bingchunmoli.util;

import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.Map;

public interface SignUtil {
    /**
     * 获取请求的签名参数
     * @return
     */
    Map<String, Object> getSignParam(HttpServletRequest request) throws IOException;

    /**
     * 根据请求参数进行sha1签名验证
     * @param signParam 签名参数
     * @return 签名验证是否成
     */
    boolean verify(Map<String, Object> signParam) throws InvalidKeyException, SignatureException, NoSuchAlgorithmException;

    /**
     * 比较签名
     * @param request 请求
     * @return 比较签名
     */
    boolean compare(HttpServletRequest request) throws IOException, SignatureException, NoSuchAlgorithmException, InvalidKeyException;
}
