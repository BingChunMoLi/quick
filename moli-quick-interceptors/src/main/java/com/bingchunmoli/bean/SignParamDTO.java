package com.bingchunmoli.bean;

import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 签名参数DTO
 * @author moli
 */
@Data
@Accessors(chain = true)
public class SignParamDTO {
    private String unsignedStr;
    private String signatureStr;

    /**
     * 签名参数builder
     */
    @Data
    @Builder
    public static class SignParamDTOBuilder{
        private CustomParamDTO sign;
        private CustomParamDTO nonce;
        private CustomParamDTO timestamp;
    }
}
