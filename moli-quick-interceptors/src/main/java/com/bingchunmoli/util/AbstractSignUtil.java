package com.bingchunmoli.util;

import com.bingchunmoli.autoconfigure.redis.util.RedisUtil;
import com.bingchunmoli.bean.CustomParamDTO;
import com.bingchunmoli.bean.SignParamDTO;
import com.bingchunmoli.exception.SignConfigException;
import com.bingchunmoli.exception.SignParamIllegalArgumentException;
import com.bingchunmoli.filter.CacheFilter;
import com.bingchunmoli.properties.InterceptorsAutoConfigurationProperties;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.util.StringUtils;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 抽象签名工具
 */
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class AbstractSignUtil implements SignUtil{
    protected final static String BODY_KEY = "body";
    protected final ObjectMapper om;
    protected final InterceptorsAutoConfigurationProperties.SignProperties sign;
    protected final RedisUtil redisUtil;

    /**
     * 获取请求的签名参数
     * 构建方式
     * 协议 路径\n
     * 请求参数 authorization=token&amp;str=world
     * HTTP 方法转小写，URI 保留大小写；参数名称排序后转义键和值的边界字符。
     *
     * @param request 请求
     * @return 请求的需要签名的参数
     */
    @Override
    @SneakyThrows
    public SignParamDTO getSignParam(HttpServletRequest request) {
        validateConfiguration();
        boolean jsonRequest = CacheFilter.isJsonRequest(request);
        validateBodyType(request, jsonRequest);
        Map<String, String[]> parameterMap = request.getParameterMap();
        for (String name : parameterMap.keySet()) {
            if ("authorization".equalsIgnoreCase(name) || jsonRequest && BODY_KEY.equalsIgnoreCase(name)) {
                throw new SignParamIllegalArgumentException("Reserved signature field cannot be a request parameter: " + name);
            }
            String[] values = parameterMap.get(name);
            if (values == null || values.length != 1) {
                throw new SignParamIllegalArgumentException("Signed requests require exactly one value per parameter: " + name);
            }
        }
        SortedMap<String, String> paramMap = new TreeMap<>();
        if (jsonRequest) {
            String bodyStr = request.getReader().lines().collect(Collectors.joining());
            paramMap.put(BODY_KEY, bodyStr);
        }
        SignParamDTO.SignParamDTOBuilder signParamBuilder = verifyCustomParam(request, paramMap.get(BODY_KEY));
        if (jsonRequest && signatureComesFromBody(request)) {
            paramMap.put(BODY_KEY, SignatureCanonicalizer.jsonBodyForSigning(paramMap.get(BODY_KEY), sign.getSign().getName()));
        }
        paramMap.put("authorization", Optional.ofNullable(request.getHeader("authorization")).orElse(""));
        if (log.isTraceEnabled()) {
            parameterMap.forEach((k, v) -> log.trace("request.parameterMap;key: {}, value: {}", k, v));
        }
        SortedMap<String, String> finalParamMap = new TreeMap<>();
        parameterMap.forEach((k, v) -> finalParamMap.put(k, v[0]));
        paramMap.putAll(finalParamMap);
        CustomParamDTO timestamp = signParamBuilder.getTimestamp();
        if (timestamp != null && timestamp.isHasValue()) {
            paramMap.put(timestamp.getName(), timestamp.getValue());
        }
        CustomParamDTO nonce = signParamBuilder.getNonce();
        if (nonce != null && nonce.isHasValue()) {
            paramMap.put(nonce.getName(), nonce.getValue());
        }
        SignParamDTO signParam = new SignParamDTO();
        signParam.setSignatureStr(signParamBuilder.getSign().getValue());
        signParam.setNonce(nonce);
        signParam.setTimestamp(timestamp);
        if (!InterceptorsAutoConfigurationProperties.SignProperties.ParameterPosition.HEAD.equals(sign.getSign().getParameterPosition())) {
            paramMap.remove(signParamBuilder.getSign().getName());
        }
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(request.getMethod().toLowerCase(Locale.ROOT))
                .append(" ")
                .append(request.getRequestURI());
        if (!paramMap.isEmpty()) {
            stringBuilder.append("\n");
            paramMap = paramMap.entrySet().stream().filter(v -> !sign.getIgnoreParametersList().contains(v.getKey())).collect(TreeMap::new, (k, v) -> k.put(v.getKey(), v.getValue()), TreeMap::putAll);
            paramMap.forEach((k, v) -> stringBuilder.append(SignatureCanonicalizer.encodeComponent(k)).append("=")
                    .append(SignatureCanonicalizer.encodeComponent(v)).append("&"));
            stringBuilder.deleteCharAt(stringBuilder.length() - 1);
        }
        return signParam.setUnsignedStr(stringBuilder.toString());
    }

    /** Only JSON and Servlet POST form fields have a defined, complete signed representation. */
    @SneakyThrows
    private void validateBodyType(HttpServletRequest request, boolean jsonRequest) {
        if (jsonRequest) return;
        boolean postForm = false;
        if (request.getContentType() != null) {
            try {
                MediaType type = MediaType.parseMediaType(request.getContentType());
                postForm = "application".equals(type.getType()) && "x-www-form-urlencoded".equals(type.getSubtype())
                        && "POST".equalsIgnoreCase(request.getMethod());
            } catch (InvalidMediaTypeException invalid) {
                throw new SignParamIllegalArgumentException("Invalid signed request Content-Type");
            }
        }
        if (postForm) return;
        // Unknown-length/chunked bodies must not slip through a Content-Length-only check.
        if (request.getContentLengthLong() > 0 || request.getInputStream().read() != -1) {
            throw new SignParamIllegalArgumentException("Signed request bodies require JSON or POST URL-encoded form fields");
        }
    }

    private boolean signatureComesFromBody(HttpServletRequest request) {
        var position = sign.getSign().getParameterPosition();
        return position == InterceptorsAutoConfigurationProperties.SignProperties.ParameterPosition.BODY
                || position == InterceptorsAutoConfigurationProperties.SignProperties.ParameterPosition.ALL
                && request.getHeader(sign.getSign().getName()) == null
                && request.getParameter(sign.getSign().getName()) == null;
    }

    /** Claim the nonce with its expiration only after cryptographic verification succeeds. */
    protected boolean completeVerification(SignParamDTO parameters, boolean signatureValid) {
        if (!signatureValid) return false;
        CustomParamDTO timestamp = parameters.getTimestamp();
        if (timestamp != null && timestamp.isHasValue()) {
            validateTimestamp(timestamp);
        }
        CustomParamDTO nonce = parameters.getNonce();
        if (nonce != null && nonce.isHasValue()) {
            if (redisUtil == null) throw new SignConfigException("Nonce verification requires RedisUtil");
            boolean acquired = redisUtil.setObjectIfAbsent(sign.getNonceRedisPrefix() + nonce.getValue(), nonce.getValue(),
                    Math.max(1L, sign.getSignValidTime()), TimeUnit.MILLISECONDS);
            if (!acquired) throw new SignParamIllegalArgumentException("customParam " + nonce.getName() + " is wrong, repeated nonce");
        }
        return true;
    }

    private void validateConfiguration() {
        if (sign.getSignValidTime() <= 0) {
            throw new SignConfigException("Signature validity time must be positive");
        }
        Set<String> protectedNames = new HashSet<>(Set.of(BODY_KEY, "authorization"));
        for (var field : Arrays.asList(sign.getSign(), sign.getTimestamp(), sign.getNonce())) {
            if (field == null || !field.isEnable()) continue;
            if (!StringUtils.hasText(field.getName()) || !protectedNames.add(field.getName().toLowerCase(Locale.ROOT))) {
                throw new SignConfigException("Signature fields must have distinct, non-reserved names");
            }
        }
        for (String ignored : sign.getIgnoreParametersList()) {
            if (ignored != null && protectedNames.contains(ignored.toLowerCase(Locale.ROOT))) {
                throw new SignConfigException("Required signature field cannot be ignored: " + ignored);
            }
        }
    }

    /** Requests may be at most one validity window old; future timestamps are not accepted. */
    private void validateTimestamp(CustomParamDTO parameter) {
        long timestamp;
        try {
            timestamp = Long.parseLong(parameter.getValue());
        } catch (NumberFormatException invalid) {
            throw new SignParamIllegalArgumentException("Invalid signature timestamp: " + parameter.getName());
        }
        long now = currentTimeMillis();
        if (timestamp < 0 || timestamp > now || now - timestamp >= sign.getSignValidTime()) {
            throw new SignParamIllegalArgumentException("Signature timestamp is outside the validity window: " + parameter.getName());
        }
    }

    protected long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    /**
     * 验证自定义参数并提取
     * sign: 必须配置并参数存在
     * timestamp: 是否配置并是否过期
     * nonce: 是否配置并提取；验签成功后再通过 Redis 原子抢占
     *
     * @param request   请求
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
            Optional.ofNullable(timestampParam)
                    .map(CustomParamDTO::getValue)
                    .orElseThrow(() -> new SignParamIllegalArgumentException(MessageFormatter.format("customParam {} is wrong, miss timestamp requestParam", timestampParam).getMessage()));
            validateTimestamp(timestampParam);
            timestampParam.setHasValue(true);
            signParamBuilder.timestamp(timestampParam);
        }
        InterceptorsAutoConfigurationProperties.SignProperties.CustomParam customParamNonce = sign.getNonce();
        if (isCustomParamEnable(customParamNonce) && customParamNonce.isEnable()) {
            CustomParamDTO nonceParam = getCustomParam(customParamNonce, request, cacheBody);
            Optional.ofNullable(nonceParam)
                    .map(CustomParamDTO::getValue)
                    .filter(StringUtils::hasText)
                    .orElseThrow(() -> new SignParamIllegalArgumentException(MessageFormatter.format("customParam {} is wrong, miss nonce requestParam", customParamNonce).getMessage()));
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
        return customParam != null && StringUtils.hasText(customParam.getName());
    }

    /**
     * 根据customParam配置获取customParam的name和value
     *
     * @param customParam customParam配置
     * @param request     请求
     * @param cacheBody   缓存的body
     * @return CustomParamDTO实体
     */
    private CustomParamDTO getCustomParam(InterceptorsAutoConfigurationProperties.SignProperties.CustomParam customParam, HttpServletRequest request, String cacheBody) {
        if (!customParam.isEnable()) {
            return null;
        }
        String name = customParam.getName();
        String header = request.getHeader(name);
        String parameter = request.getParameter(name);
        boolean jsonBodyPosition = customParam.getParameterPosition()
                == InterceptorsAutoConfigurationProperties.SignProperties.ParameterPosition.BODY && cacheBody != null;
        if (header != null && parameter != null || jsonBodyPosition && (parameter != null || header != null)) {
            throw new SignParamIllegalArgumentException("Signature parameter has conflicting sources: " + name);
        }
        if (customParam.getParameterPosition() == InterceptorsAutoConfigurationProperties.SignProperties.ParameterPosition.HEAD
                && parameter != null) {
            throw new SignParamIllegalArgumentException("Header signature parameter cannot also be a request parameter: " + name);
        }
        CustomParamDTO.CustomParamDTOBuilder builder = CustomParamDTO.builder().name(name);
        switch (customParam.getParameterPosition()) {
            case HEAD -> builder.value(request.getHeader(name));
            case QUERY -> builder.value(request.getParameter(name));
            case BODY -> builder.value(getCustomParamByBody(request, name, cacheBody));
            case ALL -> builder.value(Optional.ofNullable(request.getHeader(name))
                    .orElseGet(() -> Optional.ofNullable(request.getParameter(name))
                            .orElseGet(() -> getCustomParamByBody(request, name, cacheBody))));
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
     * @param cacheBody       缓存的body
     * @return customParam的值，可能为空
     */
    private String getCustomParamByBody(HttpServletRequest request, String customParamName, String cacheBody) {
        if (StringUtils.hasText(cacheBody)) {
            JsonNode jsonNode;
            try {
                jsonNode = om.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                        .with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).readTree(cacheBody);
            } catch (JsonProcessingException invalid) {
                throw new SignParamIllegalArgumentException("Invalid JSON signature body");
            }
            List<String> value = jsonNode.findValuesAsText(customParamName);
            if (value.size() > 1) {
                throw new SignParamIllegalArgumentException("JSON signature parameters must be unambiguous: " + customParamName);
            }
            if (!value.isEmpty()) {
                return value.get(0);
            } else {
                return request.getParameter(customParamName);
            }
        }
        return request.getParameter(customParamName);
    }
}
