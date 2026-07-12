package com.bingchunmoli.autoconfigure.redis.util;

import org.springframework.data.redis.core.RedisTemplate;

/**
 * Test double with the same shape as the optional project RedisUtil.
 */
public class RedisUtil {

    public final RedisTemplate redisTemplate;

    public RedisUtil(RedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
}
