package com.bingchunmoli.bean;

import lombok.Builder;
import lombok.Data;

/**
 * @author moli
 */
@Data
@Builder
public class CustomParamDTO {
    private boolean hasValue;
    private String name;
    private String value;
}
