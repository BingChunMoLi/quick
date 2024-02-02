package com.bingchunmoli.bean;

import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.SignStyle;
import java.util.Locale;

import static java.time.temporal.ChronoField.*;

/**
 * 本地化的日期时间格式化器
 */
public class DateTimeFormatterOfChina {
    /**
     * 默认的日期表示方式 yyyy-MM-dd
     */
    public static final DateTimeFormatter DEFAULT_DATE;

    static {
        DEFAULT_DATE = new DateTimeFormatterBuilder()
                .appendValue(YEAR, 4, 10, SignStyle.EXCEEDS_PAD)
                .appendLiteral('-')
                .appendValue(MONTH_OF_YEAR, 2)
                .appendLiteral('-')
                .appendValue(DAY_OF_MONTH, 2)
                .toFormatter();
    }

    /**
     * 默认的时间表示方式 HH:mm:ss
     */
    public static final DateTimeFormatter DEFAULT_TIME;

    static {
        DEFAULT_TIME = new DateTimeFormatterBuilder()
                .appendValue(HOUR_OF_DAY, 2)
                .appendLiteral(":")
                .appendValue(MINUTE_OF_HOUR, 2)
                .appendLiteral(":")
                .appendValue(SECOND_OF_MINUTE, 2)
                .toFormatter();
    }

    /**
     * 默认的日期时间表示方式 yyyy-MM-dd HH-mm-ss
     */
    public static final DateTimeFormatter DEFAULT_DATE_TIME;

    static {
        DEFAULT_DATE_TIME = new DateTimeFormatterBuilder()
                .appendValue(YEAR, 4, 10, SignStyle.EXCEEDS_PAD)
                .appendLiteral('-')
                .appendValue(MONTH_OF_YEAR, 2)
                .appendLiteral('-')
                .appendValue(DAY_OF_MONTH, 2)
                .optionalStart()
                .appendLiteral(' ')
                .appendValue(HOUR_OF_DAY, 2)
                .appendLiteral(':')
                .appendValue(MINUTE_OF_HOUR, 2)
                .appendLiteral(':')
                .appendValue(SECOND_OF_MINUTE, 2)
                .toFormatter();
    }

    /**
     * 默认的时间表示方式带毫秒 HH:mm:ss.SSS
     */
    public static final DateTimeFormatter DEFAULT_TIME_MILLISECOND;

    static {
        DEFAULT_TIME_MILLISECOND = new DateTimeFormatterBuilder()
                .appendValue(HOUR_OF_DAY, 2)
                .appendLiteral(':')
                .appendValue(MINUTE_OF_HOUR, 2)
                .appendLiteral(':')
                .appendValue(SECOND_OF_MINUTE, 2)
                .optionalStart()
                .appendLiteral('.')
                .appendValue(MILLI_OF_SECOND, 3)
                .toFormatter();
    }

    /**
     * 默认的日期时间表示方式带毫秒 yyyy-MM-dd hh:MM:ss.SSS
     */
    public static final DateTimeFormatter DEFAULT_DATE_TIME_MILLISECOND;

    static {
        DEFAULT_DATE_TIME_MILLISECOND = new DateTimeFormatterBuilder()
                .appendValue(YEAR, 4, 10, SignStyle.EXCEEDS_PAD)
                .appendLiteral('-')
                .appendValue(MONTH_OF_YEAR, 2)
                .appendLiteral('-')
                .appendValue(DAY_OF_MONTH, 2)
                .optionalStart()
                .appendLiteral(' ')
                .appendValue(HOUR_OF_DAY, 2)
                .appendLiteral(':')
                .appendValue(MINUTE_OF_HOUR, 2)
                .appendLiteral(':')
                .appendValue(SECOND_OF_MINUTE, 2)
                .optionalStart()
                .appendLiteral('.')
                .appendValue(MILLI_OF_SECOND, 3)
                .toFormatter();
    }

    /**
     * 中文的日期表示方式 yyyy年MM月dd天
     */
    public static final DateTimeFormatter CHINESE_DATE;

    static {
        CHINESE_DATE = new DateTimeFormatterBuilder()
                .appendValue(YEAR, 4, 10, SignStyle.EXCEEDS_PAD)
                .appendLiteral('年')
                .appendValue(MONTH_OF_YEAR, 2)
                .appendLiteral('月')
                .appendValue(DAY_OF_MONTH, 2)
                .appendLiteral('日')
                .toFormatter(Locale.CHINA);
    }

    /**
     * 中文的时间表示方式 hh时MM分ss秒
     */
    public static final DateTimeFormatter CHINESE_TIME;

    static {
        CHINESE_TIME = new DateTimeFormatterBuilder()
                .appendValue(HOUR_OF_DAY, 2)
                .appendLiteral('时')
                .appendValue(MINUTE_OF_HOUR, 2)
                .appendLiteral('分')
                .appendValue(SECOND_OF_MINUTE, 2)
                .appendLiteral('秒')
                .toFormatter(Locale.CHINA);
    }

    /**
     * 中文的日期时间表示方式 yyyy年MM月dd日 hh时MM分ss秒
     */
    public static final DateTimeFormatter CHINESE_DATE_TIME;

    static {
        CHINESE_DATE_TIME = new DateTimeFormatterBuilder()
                .appendValue(YEAR, 4, 10, SignStyle.EXCEEDS_PAD)
                .appendLiteral('年')
                .appendValue(MONTH_OF_YEAR, 2)
                .appendLiteral('月')
                .appendValue(DAY_OF_MONTH, 2)
                .appendLiteral('日')
                .optionalStart()
                .appendLiteral(' ')
                .appendValue(HOUR_OF_DAY, 2)
                .appendLiteral('时')
                .appendValue(MINUTE_OF_HOUR, 2)
                .appendLiteral('分')
                .appendValue(SECOND_OF_MINUTE, 2)
                .appendLiteral('秒')
                .toFormatter(Locale.CHINA);
    }

    /**
     * 中文的时间表示方式带毫秒 hh时MM分ss秒SSS毫秒
     */
    public static final DateTimeFormatter CHINESE_TIME_MILLISECOND;

    static {
        CHINESE_TIME_MILLISECOND = new DateTimeFormatterBuilder()
                .appendValue(HOUR_OF_DAY, 2)
                .appendLiteral('时')
                .appendValue(MINUTE_OF_HOUR, 2)
                .appendLiteral('分')
                .appendValue(SECOND_OF_MINUTE, 2)
                .appendLiteral('秒')
                .optionalStart()
                .appendValue(MILLI_OF_SECOND, 3)
                .appendLiteral("毫秒")
                .toFormatter(Locale.CHINA);
    }

    /**
     * 中文的日期时间表示方式带毫秒 yyyy年MM月dd日 hh时MM分ss秒SSS毫秒
     */
    public static final DateTimeFormatter CHINESE_DATE_TIME_MILLISECOND;

    static {
        CHINESE_DATE_TIME_MILLISECOND = new DateTimeFormatterBuilder()
                .appendValue(YEAR, 4, 10, SignStyle.EXCEEDS_PAD)
                .appendLiteral('年')
                .appendValue(MONTH_OF_YEAR, 2)
                .appendLiteral('月')
                .appendValue(DAY_OF_MONTH, 2)
                .appendLiteral('日')
                .optionalStart()
                .appendLiteral(' ')
                .appendValue(HOUR_OF_DAY, 2)
                .appendLiteral('时')
                .appendValue(MINUTE_OF_HOUR, 2)
                .appendLiteral('分')
                .appendValue(SECOND_OF_MINUTE, 2)
                .appendLiteral('秒')
                .optionalStart()
                .appendValue(MILLI_OF_SECOND)
                .appendLiteral("毫秒")
                .toFormatter(Locale.CHINA);
    }

    /**
     * 使用/的日期表示方式 yyyy/MM/dd
     */
    public static final DateTimeFormatter SLASH_DATE;

    static {
        SLASH_DATE = new DateTimeFormatterBuilder()
                .appendValue(YEAR, 4, 10, SignStyle.EXCEEDS_PAD)
                .appendLiteral('/')
                .appendValue(MONTH_OF_YEAR, 2)
                .appendLiteral('/')
                .appendValue(DAY_OF_MONTH, 2)
                .toFormatter();
    }

    /**
     * 使用/的日期时间表示方式 yyyy/MM/dd hh:MM:ss
     */
    public static final DateTimeFormatter SLASH_DATE_TIME;

    static {
        SLASH_DATE_TIME = new DateTimeFormatterBuilder()
                .appendValue(YEAR, 4, 10, SignStyle.EXCEEDS_PAD)
                .appendLiteral('/')
                .appendValue(MONTH_OF_YEAR, 2)
                .appendLiteral('/')
                .appendValue(DAY_OF_MONTH, 2)
                .optionalStart()
                .appendLiteral(" ")
                .appendValue(HOUR_OF_DAY, 2)
                .appendLiteral(":")
                .appendValue(MINUTE_OF_HOUR, 2)
                .appendLiteral(":")
                .appendValue(SECOND_OF_MINUTE, 2)
                .toFormatter();
    }

    /**
     * 使用/的日期时间表示方式带毫秒 yyyy/MM/dd hh:MM:ss.SSS
     */
    public static final DateTimeFormatter SLASH_DATE_TIME_MILLISECOND;

    static {
        SLASH_DATE_TIME_MILLISECOND = new DateTimeFormatterBuilder()
                .appendValue(YEAR, 4, 10, SignStyle.EXCEEDS_PAD)
                .appendLiteral('/')
                .appendValue(MONTH_OF_YEAR, 2)
                .appendLiteral('/')
                .appendValue(DAY_OF_MONTH, 2)
                .optionalStart()
                .appendLiteral(" ")
                .appendValue(HOUR_OF_DAY, 2)
                .appendLiteral(":")
                .appendValue(MINUTE_OF_HOUR, 2)
                .appendLiteral(":")
                .appendValue(SECOND_OF_MINUTE, 2)
                .optionalStart()
                .appendLiteral('.')
                .appendValue(MILLI_OF_SECOND, 3)
                .toFormatter();
    }
}
