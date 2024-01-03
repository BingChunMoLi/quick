package com.bingchunmoli.util;

import com.bingchunmoli.autoconfigure.redis.util.RedisUtil;
import com.bingchunmoli.properties.InterceptorsAutoConfigurationProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * @author moli
 */
@Slf4j
public class MD5WithRSASignUtil extends RSAAbstractSignUtil {

    public MD5WithRSASignUtil(ObjectMapper om, InterceptorsAutoConfigurationProperties.SignProperties sign, RedisUtil redisUtil) {
        super(om, sign, redisUtil);
    }

}
