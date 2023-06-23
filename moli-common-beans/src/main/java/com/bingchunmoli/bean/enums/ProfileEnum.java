package com.bingchunmoli.bean.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 常用环境
 *
 * @author MoLi
 */
@Getter
@AllArgsConstructor
public enum ProfileEnum {

    /**
     * 环境美剧
     */
    DEV("dev"), PROD("prod"), TEST("test"), PRE("pre");

    private final String profile;
}

