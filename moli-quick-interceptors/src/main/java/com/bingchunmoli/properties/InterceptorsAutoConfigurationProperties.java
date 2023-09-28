package com.bingchunmoli.properties;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "moli.interceptor")
public class InterceptorsAutoConfigurationProperties {
    private SignProperties sign;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SignProperties{
        private boolean enable = false;
        /**
         * 签名算法
         */
        private String algorithm = "SHA256WithRSA";
        /**
         * 是否包含请求头(Host,Content-Type,UA)
         */
        private Boolean inHeader = false;


        private List<CustomParam> customParamList = new ArrayList<>();
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

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class CustomParam{
            private boolean enable = false;
            /**
             * 随机数name
             */
            private String name;
            /**
             * 是否是签名key
             */
            private boolean signStr = false;
            /**
             * 参数位置
             */
            private ParameterPosition parameterPosition = ParameterPosition.ALL;
        }

        /**
         * 随机数参数
         */
        @Deprecated
        public static class NonceParam extends CustomParam{
            private String name = "nonce";
        }

        @Deprecated
        public static class TimeParam extends CustomParam{
            private String name = "timestamp";
        }

        @Deprecated
        public static class SignParam extends CustomParam{
            private String name = "sign";
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
             * 依次探测
             */
            ALL()
        }
    }

}
