package com.bingchunmoli.redis;

import com.bingchunmoli.annotation.ExecutionTime;
import com.bingchunmoli.annotation.Log;
import com.bingchunmoli.autoconfigure.redis.util.RedisUtil;
import com.bingchunmoli.bean.ResultVO;
import com.bingchunmoli.test.TestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = TestApplication.class)
public class RedisApplicationTest {
    @MockitoBean
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    RedisUtil redisUtil;

    @Test
    void contextLoads() {
        ValueOperations<String, Object> valueOperations = mock(ValueOperations.class);
        ResultVO<String> result = ResultVO.ok("new Data");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("test:object")).thenReturn(result);

        redisUtil.setObject("test:object", result, 30, TimeUnit.SECONDS);
        ResultVO<String> object = redisUtil.getObject("test:object");

        assertThat(object).isEqualTo(result);
        verify(valueOperations).set("test:object", result, 30, TimeUnit.SECONDS);
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
