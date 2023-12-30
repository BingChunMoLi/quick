package com.bingchunmoli.bean;

import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @author moli
 */
@Data
@Accessors(chain = true)
public class SignParamDTO {
    private String unsignedStr;
    private String signatureStr;

    @Data
    @Builder
    public static class SignParamDTOBuilder{
        private CustomParamDTO sign;
        private CustomParamDTO nonce;
        private CustomParamDTO timestamp;
    }
}
