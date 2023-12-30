package com.bingchunmoli.properties;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 拦截器自动配置属性
 *
 * @author MoLi
 */
@Data
@ConfigurationProperties(prefix = "moli.interceptor")
public class InterceptorsAutoConfigurationProperties {
    private SignProperties sign;

    /**
     * 签名配置属性
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SignProperties{
        /**
         * 签名算法
         */
        private String algorithm = "SHA256WithRSA";
        /**
         * 时间
         */
        private CustomParam timestamp;
        /**
         * 随机字符
         */
        private CustomParam nonce;
        /**
         * 签名字符串
         */
        private CustomParam sign;

        /**
         * 忽略请求路径
         */
        private List<String> ignorePathList = new ArrayList<>();
        /**
         * 忽略请求参数,默认为file
         */
        private List<String> ignoreParametersList = List.of("file");

        /**
         * 私钥
         */
        private String privateKey;

        /**
         * 公钥
         */
        private String publicKey;

        /**
         * 签名有效期
         */
        private long signValidTime = 2000;

        private String nonceRedisPrefix = "moli:sign:nonce:";

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class CustomParam{
            private boolean enable = false;
            /**
             * name
             */
            private String name;
            /**
             * 参数位置
             */
            private ParameterPosition parameterPosition = ParameterPosition.ALL;
        }

        @Getter
        public enum ParameterPosition{
            /**
             * 请求头
             */
            HEAD(),
            /**
             * 请求路径参数
             */
            QUERY(),
            /**
             * 请求体(非GET请求)
             */
            BODY(),
            /**
             * 从HEAD, QUERY, BODY顺序依次探测
             */
            ALL()
        }
    }

}
