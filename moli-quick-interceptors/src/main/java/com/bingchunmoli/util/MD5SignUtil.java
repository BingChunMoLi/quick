package com.bingchunmoli.util;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class MD5SignUtil implements SignUtil{

    @Override
    public Map<String, Object> getSignParam(HttpServletRequest request) throws IOException {
        return null;
    }

    @Override
    public boolean verify(Map<String, Object> signParam, String requestSignStr) throws InvalidKeyException, SignatureException, NoSuchAlgorithmException {
        return false;
    }

    @Override
    public boolean compare(HttpServletRequest request) throws IOException, SignatureException, NoSuchAlgorithmException, InvalidKeyException {
        return false;
    }
}
