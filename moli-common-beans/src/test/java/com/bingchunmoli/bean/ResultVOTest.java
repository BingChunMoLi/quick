package com.bingchunmoli.bean;

import com.bingchunmoli.bean.enums.CodeEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResultVOTest {

    @Test
    void okShouldUseSuccessCode() {
        ResultVO<String> result = ResultVO.ok("data");

        assertEquals(CodeEnum.SUCCESS.getCode(), result.getCode());
        assertEquals(CodeEnum.SUCCESS.getMsg(), result.getMsg());
        assertEquals("data", result.getData());
    }

    @Test
    void failBySystemShouldUseFailureCode() {
        ResultVO<String> result = ResultVO.failBySystem("error");

        assertEquals(CodeEnum.FAILURE.getCode(), result.getCode());
        assertEquals(CodeEnum.FAILURE.getMsg(), result.getMsg());
        assertEquals("error", result.getData());
    }
}
