package com.bingchunmoli.util;

import com.bingchunmoli.autoconfigure.redis.util.RedisUtil;
import com.bingchunmoli.bean.CustomParamDTO;
import com.bingchunmoli.bean.SignParamDTO;
import com.bingchunmoli.exception.SignConfigException;
import com.bingchunmoli.exception.SignParamIllegalArgumentException;
import com.bingchunmoli.properties.InterceptorsAutoConfigurationProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import java.util.stream.Collectors;

/**
 * SHA256WithRSA签名工具类
 *
 * @author MoLi
 */
@Slf4j
@RequiredArgsConstructor
public class SHA256WithRSASignUtil extends AbstractSignUtil {

    private final ObjectMapper om;
    private final InterceptorsAutoConfigurationProperties.SignProperties sign;
    private final RedisUtil redisUtil;
    private final static String BODY_KEY = "body";

    @Override
    public boolean verify(HttpServletRequest request) {
        SignParamDTO signParam = getSignParam(request);
        return doVerify(signParam);
    }

    @SneakyThrows(Exception.class)
    private boolean doVerify(SignParamDTO signParam) {
        Signature signature = Signature.getInstance(sign.getAlgorithm());
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PublicKey publicKey;
        try {
            publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(sign.getPublicKey().getBytes(StandardCharsets.UTF_8))));
        } catch (InvalidKeySpecException e) {
            throw new SignConfigException("公钥错误", e);
        }
        signature.initVerify(publicKey);
        signature.update(signParam.getUnsignedStr().getBytes(StandardCharsets.UTF_8));
        if (log.isDebugEnabled()) {
            log.debug("doVerify, signParam: {}", signParam);
        }
        return signature.verify(Base64.getDecoder().decode(signParam.getSignatureStr().getBytes(StandardCharsets.UTF_8)));
    }


    /**
     * 获取请求的签名参数
     * 构建方式
     * 协议 路径\n
     * 请求参数 authorization=token&amp;str=world
     * 协议路径小写, 参数字母表排序
     *
     * @param request 请求
     * @return 请求的需要签名的参数
     */
    @Override
    @SneakyThrows
    public SignParamDTO getSignParam(HttpServletRequest request) {
        SortedMap<String, String> paramMap = new TreeMap<>();
        String contentType = request.getContentType();
        if (contentType != null && MediaType.parseMediaType(contentType).includes(MediaType.APPLICATION_JSON)) {
            String bodyStr = request.getReader().lines().collect(Collectors.joining());
            paramMap.put(BODY_KEY, bodyStr);
        }
        SignParamDTO.SignParamDTOBuilder signParamBuilder = verifyCustomParam(request, paramMap.get(BODY_KEY));
        paramMap.put("authorization", Optional.ofNullable(request.getHeader("authorization")).orElse(""));
        Map<String, String[]> parameterMap = request.getParameterMap();
        if (log.isTraceEnabled()) {
            parameterMap.forEach((k, v) -> log.trace("request.parameterMap;key: {}, value: {}", k, v));
        }
        SortedMap<String, String> finalParamMap = new TreeMap<>();
        parameterMap.forEach((k, v) -> finalParamMap.put(k, v[0]));
        paramMap.putAll(finalParamMap);
        CustomParamDTO timestamp = signParamBuilder.getTimestamp();
        if (sign.getTimestamp().isEnable() && timestamp.isHasValue()) {
            paramMap.put(timestamp.getName(), timestamp.getValue());
        }
        CustomParamDTO nonce = signParamBuilder.getNonce();
        if (sign.getNonce().isEnable() && nonce.isHasValue()) {
            paramMap.put(nonce.getName(), nonce.getValue());
        }
        SignParamDTO signParam = new SignParamDTO();
        signParam.setSignatureStr(signParamBuilder.getSign().getValue());
        if (!InterceptorsAutoConfigurationProperties.SignProperties.ParameterPosition.HEAD.equals(sign.getSign().getParameterPosition())) {
            paramMap.remove(signParamBuilder.getSign().getName());
        }
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(request.getMethod().toLowerCase())
                .append(" ")
                .append(request.getRequestURI());
        if (!paramMap.isEmpty()) {
            stringBuilder.append("\n");
            paramMap = paramMap.entrySet().stream().filter(v -> !sign.getIgnoreParametersList().contains(v.getKey())).collect(TreeMap::new, (k, v) -> k.put(v.getKey(), v.getValue()), TreeMap::putAll);
            paramMap.forEach((k,v)-> stringBuilder.append(k).append("=").append(v).append("&"));
            stringBuilder.deleteCharAt(stringBuilder.length() - 1);
        }
        return signParam.setUnsignedStr(stringBuilder.toString());
    }

    /**
     * 验证自定义参数并提取
     * sign: 必须配置并参数存在
     * timestamp: 是否配置并是否过期
     * nonce: 是否配置并是否已使用(需要redis)
     *
     * @param request 请求
     * @param cacheBody 缓存的body
     * @return 自定义参数builder 含有构建sign参数的基本信息缓存
     */
    private SignParamDTO.SignParamDTOBuilder verifyCustomParam(HttpServletRequest request, String cacheBody) {
        if (!sign.getSign().isEnable()) {
            throw new SignConfigException("sign isn't enable");
        }
        if (!isCustomParamEnable(sign.getSign())) {
            throw new SignConfigException("sign miss config");
        }
        SignParamDTO.SignParamDTOBuilder.SignParamDTOBuilderBuilder signParamBuilder = SignParamDTO.SignParamDTOBuilder.builder();
        InterceptorsAutoConfigurationProperties.SignProperties.CustomParam customParamTimestamp = sign.getTimestamp();
        if (isCustomParamEnable(customParamTimestamp) && customParamTimestamp.isEnable()) {
            CustomParamDTO timestampParam = getCustomParam(customParamTimestamp, request, cacheBody);
            long timestamp = Optional.ofNullable(timestampParam)
                    .map(CustomParamDTO::getValue)
                    .map(Long::parseLong)
                    .orElseThrow(() -> new SignParamIllegalArgumentException(MessageFormatter.format("customParam {} is wrong, miss timestamp requestParam", timestampParam).getMessage()));
            long currentTimeMillis = System.currentTimeMillis();
            if (timestamp < 0 || currentTimeMillis - timestamp > sign.getSignValidTime()) {
                throw new SignParamIllegalArgumentException(MessageFormatter.format("customParam {} is wrong, currentTime: {}", timestampParam, currentTimeMillis).getMessage());
            }
            timestampParam.setHasValue(true);
            signParamBuilder.timestamp(timestampParam);
        }
        InterceptorsAutoConfigurationProperties.SignProperties.CustomParam customParamNonce = sign.getNonce();
        if (isCustomParamEnable(customParamNonce) && customParamNonce.isEnable()) {
            CustomParamDTO nonceParam = getCustomParam(customParamNonce, request, cacheBody);
            String nonce = Optional.ofNullable(nonceParam)
                    .map(CustomParamDTO::getValue)
                    .orElseThrow(() -> new SignParamIllegalArgumentException(MessageFormatter.format("customParam {} is wrong, miss nonce requestParam", customParamNonce).getMessage()));
            String redisNonce = redisUtil.getObject(sign.getNonceRedisPrefix() + nonce);
            if (StringUtils.hasText(redisNonce)) {
                throw new SignParamIllegalArgumentException(MessageFormatter.format("customParam {} is wrong, repeated nonce", customParamNonce).getMessage());
            }
            nonceParam.setHasValue(true);
            signParamBuilder.nonce(nonceParam);
        }
        String signStr = Optional.ofNullable(getCustomParam(sign.getSign(), request, cacheBody))
                .map(CustomParamDTO::getValue)
                .orElseThrow(() -> new SignParamIllegalArgumentException(MessageFormatter.format("customParam {} is wrong, miss sign requestParam", this.sign.getSign()).getMessage()));
        signParamBuilder.sign(CustomParamDTO.builder()
                .name(sign.getSign().getName())
                .value(signStr).build());
        return signParamBuilder.build();
    }

    /**
     * 判断自定义参数是否启用
     *
     * @param customParam 自定义参数
     * @return 如果customParam不为null并且customParam.name不为空字符串则表示已配置返回true
     */
    private boolean isCustomParamEnable(InterceptorsAutoConfigurationProperties.SignProperties.CustomParam customParam) {
        return customParam != null && StringUtils.hasText(sign.getNonce().getName());
    }

    /**
     * 根据customParam配置获取customParam的name和value
     *
     * @param customParam customParam配置
     * @param request     请求
     * @param cacheBody 缓存的body
     * @return CustomParamDTO实体
     */
    private CustomParamDTO getCustomParam(InterceptorsAutoConfigurationProperties.SignProperties.CustomParam customParam, HttpServletRequest request, String cacheBody) {
        if (!customParam.isEnable()) {
            return null;
        }
        String name = customParam.getName();
        CustomParamDTO.CustomParamDTOBuilder builder = CustomParamDTO.builder().name(name);
        switch (customParam.getParameterPosition()) {
            case HEAD -> builder.value(request.getHeader(name));
            case QUERY -> builder.value(request.getParameter(name));
            case BODY -> builder.value(getCustomParamByBody(request, name, cacheBody));
            case ALL -> builder.value(Optional.ofNullable(request.getHeader(name))
                    .orElseGet(() -> getCustomParamByBody(request, name, cacheBody)));
        }
        return builder.build();
    }

    /**
     * 从请求body中获取customParam
     * 如果contentType复合json则从json中递归获取
     * 否则使用request.getParameter 从表单或请求参数中获取
     *
     * @param request         请求
     * @param customParamName 自定义参数的name
     * @param cacheBody 缓存的body
     * @return customParam的值，可能为空
     */
    @SneakyThrows
    private String getCustomParamByBody(HttpServletRequest request, String customParamName, String cacheBody) {
        if (StringUtils.hasText(cacheBody)) {
            JsonNode jsonNode = om.readTree(cacheBody);
            List<String> value = jsonNode.findValuesAsText(customParamName);
            if (!value.isEmpty()) {
                return value.get(0);
            } else {
                return request.getParameter(customParamName);
            }
        }
        return null;
    }

}
