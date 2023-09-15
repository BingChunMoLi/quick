package com.bingchunmoli.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "moli.interceptor")
public class InterceptorsAutoConfigurationProperties {
    private SignProperties sign;

    @Data
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
        /**
         * 时间参数
         */
        private TimeParam time = new TimeParam();
        /**
         * 随机数参数
         */
        private NonceParam nonce = new NonceParam();

        private SignParam sign = new SignParam();

        private List<CustomParam> customParamList = Collections.emptyList();
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

        public interface CustomParam{

            /**
             * 是否启用
             * @return 是否启用
             */
            boolean isEnable();

            /**
             * 参数名称
             * @return 参数名称
             */
            String getName();

            /**
             * 参数位置枚举
             * @return 参数位置枚举
             */
            ParameterPosition getParameterPosition();

        }

        /**
         * 随机数参数
         */
        @Data
        public static class NonceParam implements CustomParam{
            private boolean enable = false;
            /**
             * 随机数name
             */
            private String name = "nonce";
            /**
             * 参数位置
             */
            private ParameterPosition parameterPosition = ParameterPosition.ALL;
        }

        @Data
        public static class TimeParam implements CustomParam{
            private boolean enable = false;
            /**
             * 时间戳name
             */
            private String name = "timestamp";
            /**
             * 参数位置
             */
            private ParameterPosition parameterPosition = ParameterPosition.ALL;
        }

        @Data
        public static class SignParam implements CustomParam{
            private boolean enable = true;
            /**
             * 签名参数name
             */
            private String name = "sign";
            /**
             * 参数位置
             */
            private ParameterPosition parameterPosition = ParameterPosition.ALL;
        }

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
