package com.bingchunmoli.redis;

import com.bingchunmoli.annotation.ExecutionTime;
import com.bingchunmoli.annotation.Log;
import com.bingchunmoli.autoconfigure.redis.util.RedisUtil;
import com.bingchunmoli.bean.ResultVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@SpringBootTest
public class RedisApplicationTest {
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    RedisUtil redisUtil;

    @Test
    void contextLoads() throws InterruptedException {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            list.add(i);
        }
        redisTemplate.opsForValue().set("t", list, 30, TimeUnit.SECONDS);
        System.out.println(redisTemplate.opsForValue().get("t"));
        redisUtil.setObject("a", list, 30, TimeUnit.SECONDS);
        Object a = redisUtil.getObject("a");
        System.out.println(a);
        Thread.sleep(10000);
        redisUtil.setObject("test:object", ResultVO.ok("new Data"));
        ResultVO<String> object = redisUtil.getObject("test:object");
        System.out.println(object);
    }


    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @Log
    @ExecutionTime
    void testLog(){
        log();
    }

    @Log
    @ExecutionTime
    void log(){
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        System.out.println("测试");
    }
}

