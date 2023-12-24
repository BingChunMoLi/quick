package com.bingchunmoli.util;

import com.bingchunmoli.bean.SignParamDTO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
public class MD5SignUtil implements SignUtil{

    @Override
    public SignParamDTO getSignParam(HttpServletRequest request) throws IOException {
        return null;
    }

    @Override
    public boolean verify(HttpServletRequest request) {
        return false;
    }
}
