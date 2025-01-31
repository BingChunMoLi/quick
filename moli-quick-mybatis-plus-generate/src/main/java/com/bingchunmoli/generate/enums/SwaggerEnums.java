package com.bingchunmoli.generate.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author moli
 */
@Getter
@AllArgsConstructor
public enum SwaggerEnums {

    V2(2, "swagger"),
    V3(3, "springfox");

    private final int version;
    private final String libraryName;
}
