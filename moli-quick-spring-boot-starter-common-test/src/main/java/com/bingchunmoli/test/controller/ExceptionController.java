package com.bingchunmoli.test.controller;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ExceptionController {

    @ExceptionHandler
    public String testException(Exception e){
        return "出现异常啦" + e.getMessage();
    }
}
