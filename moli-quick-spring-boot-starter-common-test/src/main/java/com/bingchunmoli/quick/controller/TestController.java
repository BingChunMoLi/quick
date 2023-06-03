package com.bingchunmoli.quick.controller;

import com.bingchunmoli.annotation.ExecutionTime;
import com.bingchunmoli.annotation.Log;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping
public class TestController {

    @Log
    @ExecutionTime
    @RequestMapping
    public String test(){
        return "test";
    }
}
