package com.bingchunmoli.test.controller;

import com.bingchunmoli.annotation.ExecutionTime;
import com.bingchunmoli.annotation.Log;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping
public class TestController {

    @Log
    @ExecutionTime
    @RequestMapping
    public String test(){
        return "test";
    }

    @GetMapping("hello")
    public String hello(String str){
        return "hello " + str;
    }

    @PostMapping("hello")
    public String postHello(String str){
        return "hello " + str;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Obj{
        private String str;
    }
    @PostMapping("hello2")
    public String hello2(@RequestBody Obj obj){
        return "hello " + obj.getStr();
    }
}
