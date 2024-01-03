package com.bingchunmoli.util;

import com.bingchunmoli.autoconfigure.redis.util.RedisUtil;
import com.bingchunmoli.properties.InterceptorsAutoConfigurationProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * SHA256WithRSA签名工具类
 *
 * @author MoLi
 */
@Slf4j
public class SHA256WithRSASignUtil extends RSAAbstractSignUtil {
    public SHA256WithRSASignUtil(ObjectMapper om, InterceptorsAutoConfigurationProperties.SignProperties sign, RedisUtil redisUtil) {
        super(om, sign, redisUtil);
    }
}
