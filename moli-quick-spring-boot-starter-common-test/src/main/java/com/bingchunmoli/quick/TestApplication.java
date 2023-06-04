package com.bingchunmoli.quick;

import com.bingchunmoli.annotation.EnableAop;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * @author BingChunMoLi
 */
@EnableAsync
@EnableAop
@SpringBootApplication
public class TestApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }
}
