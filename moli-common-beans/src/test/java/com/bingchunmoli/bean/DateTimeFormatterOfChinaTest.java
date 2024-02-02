package com.bingchunmoli.bean;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;


public class DateTimeFormatterOfChinaTest {
    private final LocalDateTime now = LocalDateTime.now();

    @Test
    void defaultDateTest(){
        String defaultDateStr = now.format(DateTimeFormatterOfChina.DEFAULT_DATE);
        String defaultLocalDateStr = now.toLocalDate().format(DateTimeFormatterOfChina.DEFAULT_DATE);
        Assertions.assertEquals(defaultDateStr, defaultLocalDateStr);
        LocalDate.parse(defaultDateStr);
        LocalDate.parse(defaultLocalDateStr);
    }

    @Test
    void defaultTimeTest(){
        String defaultTimeStr = now.format(DateTimeFormatterOfChina.DEFAULT_TIME);
        String defaultLocalTimeStr = now.toLocalTime().format(DateTimeFormatterOfChina.DEFAULT_TIME);
        Assertions.assertEquals(defaultTimeStr, defaultLocalTimeStr);
        LocalTime.parse(defaultTimeStr, DateTimeFormatterOfChina.DEFAULT_TIME);
        LocalTime.parse(defaultLocalTimeStr, DateTimeFormatterOfChina.DEFAULT_TIME);
    }

    @Test
    void defaultDateTimeTest(){
        String defaultDateTimeStr = now.format(DateTimeFormatterOfChina.DEFAULT_DATE_TIME);
        LocalDateTime.parse(defaultDateTimeStr, DateTimeFormatterOfChina.DEFAULT_DATE_TIME);
    }

    @Test
    void defaultTimeMillisecondTest(){
        String defaultTimeMillisecond = now.format(DateTimeFormatterOfChina.DEFAULT_TIME_MILLISECOND);
        LocalTime.parse(defaultTimeMillisecond, DateTimeFormatterOfChina.DEFAULT_TIME_MILLISECOND);
    }

    @Test
    void defaultDateTimeMillisecondTest(){
        String defaultDateTimeStr = now.format(DateTimeFormatterOfChina.DEFAULT_DATE_TIME_MILLISECOND);
        LocalDateTime.parse(defaultDateTimeStr, DateTimeFormatterOfChina.DEFAULT_DATE_TIME_MILLISECOND);
    }


    @Test
    void chineseDateTest(){
        String chineseDateStr = now.format(DateTimeFormatterOfChina.CHINESE_DATE);
        String chineseLocalDateStr = now.toLocalDate().format(DateTimeFormatterOfChina.CHINESE_DATE);
        Assertions.assertEquals(chineseDateStr, chineseLocalDateStr);
        LocalDate.parse(chineseDateStr, DateTimeFormatterOfChina.CHINESE_DATE);
    }

    @Test
    void chineseTimeTest(){
        String chineseTimeStr = now.format(DateTimeFormatterOfChina.CHINESE_TIME);
        String chineseLocalTimeStr = now.toLocalTime().format(DateTimeFormatterOfChina.CHINESE_TIME);
        Assertions.assertEquals(chineseTimeStr, chineseLocalTimeStr);
        LocalTime.parse(chineseTimeStr, DateTimeFormatterOfChina.CHINESE_TIME);
        LocalTime.parse(chineseLocalTimeStr, DateTimeFormatterOfChina.CHINESE_TIME);
    }

    @Test
    void chineseDateTimeTest(){
        String chineseDateTimeStr = now.format(DateTimeFormatterOfChina.CHINESE_DATE_TIME);
        LocalDateTime.parse(chineseDateTimeStr, DateTimeFormatterOfChina.CHINESE_DATE_TIME);
    }

    @Test
    void chineseTimeMillisecondTest(){
        String chineseTimeMillisecondStr = now.format(DateTimeFormatterOfChina.CHINESE_TIME_MILLISECOND);
        String chineseLocalTimeMillisecondStr = now.toLocalTime().format(DateTimeFormatterOfChina.CHINESE_TIME_MILLISECOND);
        Assertions.assertEquals(chineseTimeMillisecondStr, chineseLocalTimeMillisecondStr);
        LocalTime.parse(chineseTimeMillisecondStr, DateTimeFormatterOfChina.CHINESE_TIME_MILLISECOND);
    }

    @Test
    void chineseDateTimeMillisecondTest(){
        String chineseDateTimeStr = now.format(DateTimeFormatterOfChina.CHINESE_DATE_TIME_MILLISECOND);
        LocalDateTime.parse(chineseDateTimeStr, DateTimeFormatterOfChina.CHINESE_DATE_TIME_MILLISECOND);
    }

    @Test
    void slashDateTest(){
        String slashDateStr = now.format(DateTimeFormatterOfChina.SLASH_DATE);
        String slashLocalDateStr = now.toLocalDate().format(DateTimeFormatterOfChina.SLASH_DATE);
        Assertions.assertEquals(slashDateStr, slashLocalDateStr);
        LocalDate.parse(slashDateStr, DateTimeFormatterOfChina.SLASH_DATE);
    }

    @Test
    void slashDateTimeTest(){
        String slashDateTimeStr = now.format(DateTimeFormatterOfChina.SLASH_DATE_TIME);
        LocalDateTime.parse(slashDateTimeStr, DateTimeFormatterOfChina.SLASH_DATE_TIME);
    }

    @Test
    void slashDateTimeMillisecondTest(){
        String slashDateTimeMillisecondStr = now.format(DateTimeFormatterOfChina.SLASH_DATE_TIME_MILLISECOND);
        LocalDateTime.parse(slashDateTimeMillisecondStr, DateTimeFormatterOfChina.SLASH_DATE_TIME_MILLISECOND);
    }
}