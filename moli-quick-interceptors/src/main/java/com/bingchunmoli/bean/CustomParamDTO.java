package com.bingchunmoli.bean;

import lombok.Builder;
import lombok.Data;

/**
 * 自定义参数DTO
 * @author moli
 */
@Data
@Builder
public class CustomParamDTO {
    private boolean hasValue;
    private String name;
    private String value;
}
