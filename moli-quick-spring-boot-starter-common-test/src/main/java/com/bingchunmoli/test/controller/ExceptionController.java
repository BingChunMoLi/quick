package com.bingchunmoli.test.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class ExceptionController {

    @ExceptionHandler
    public String testException(Exception e){
        log.error("e: ", e);
        return "出现异常啦" + e.getMessage();
    }
}
