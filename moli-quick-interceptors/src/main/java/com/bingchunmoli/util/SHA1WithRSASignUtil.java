package com.bingchunmoli.util;

import com.bingchunmoli.properties.InterceptorsAutoConfigurationProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class SHA1WithRSASignUtil extends AbstractSignUtil{

    private final ObjectMapper om;
    private final InterceptorsAutoConfigurationProperties.SignProperties sign;


    /**
     * 获取请求的签名参数
     *
     * @param request 请求
     * @return 请求的需要签名的参数
     */
    @Override
    public Map<String,Object> getSignParam(HttpServletRequest request) throws IOException {
        Map<String, String[]> parameterMap = request.getParameterMap();
        if (log.isTraceEnabled()) {
            parameterMap.forEach((k, v) -> log.trace("request.parameterMap;key: {}, value: {}", k, v));
        }
        Map<String, Object> signMap = new HashMap<>(parameterMap);
        String contentType = request.getContentType();
        if (!request.getMethod().equalsIgnoreCase("GET")) {
            MediaType contentMediaType = MediaType.parseMediaType(contentType);
            if (contentMediaType.includes(MediaType.APPLICATION_JSON)) {
                String bodyStr = request.getReader().lines().collect(Collectors.joining());
                JsonNode jsonNode = om.readTree(bodyStr);
                HashMap<String, Object> jsonBodyMap = new HashMap<>();
                jsonLeaf(jsonNode, jsonBodyMap);
                signMap.putAll(jsonBodyMap);
            }
//            if (contentMediaType.includes(MediaType.APPLICATION_FORM_URLENCODED)) {}
//            if (contentMediaType.includes(MediaType.MULTIPART_FORM_DATA)) {}
        }
        Map<String, Object> headerMap = new HashMap<>();
        if (sign.getInHeader()) {
            headerMap.put("host", request.getRemoteHost().toLowerCase());
            headerMap.put("content-type", contentType);
            headerMap.put("User_Agent", request.getHeader("User_Agent"));
            if (log.isTraceEnabled()) {
                headerMap.forEach((k, v) -> log.trace("headerMap; key: {}, value: {}", k, v));
            }
        }
        sign.getCustomParamList().stream()
                .filter(InterceptorsAutoConfigurationProperties.SignProperties.CustomParam::isEnable)
                .forEach(v -> customParameter(request, v, signMap));
        return signMap;
    }

    /**
     * 根据请求参数进行sha256withRSA签名验证
     *
     * @param signParam 签名参数
     * @return 签名
     */
    @Override
    public boolean verify(Map<String, Object> signParam) throws InvalidKeyException, SignatureException, NoSuchAlgorithmException {
        //排序， 生成签名string 根据算法签名
        List<String> keyList = signParam.keySet().stream().sorted().toList();
        StringBuilder sb = new StringBuilder();
        for (String key : keyList) {
            Object raw = signParam.get(key);
            sb.append("&");
            sb.append(key);
            sb.append("=");
            if (raw == null){

            } else if (raw instanceof String strValue) {
                sb.append(strValue);
            } else if (raw instanceof String[] strArrayValue) {
                sb.append(String.join(",", strArrayValue));
            }
        }
        sb.deleteCharAt(0);
        String signString = sb.toString();
        Signature signature = Signature.getInstance(sign.getAlgorithm());
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("rsa");
        keyPairGenerator.initialize(2048);
        signature.initSign(keyPairGenerator.generateKeyPair().getPrivate());
        signature.update(signString.getBytes());
        return signature.verify(signString.getBytes(StandardCharsets.UTF_8));
    }


    /**
     * 比较签名
     *
     * @param request 请求
     * @return 比较签名是否相同
     */
    @Override
    public boolean compare(HttpServletRequest request) throws IOException, SignatureException, NoSuchAlgorithmException, InvalidKeyException {
        Map<String, Object> signMap = getSignParam(request);
        String requestSignStr = switch (sign.getSign().getParameterPosition()) {
            case HEAD-> Optional.ofNullable(request.getHeader(sign.getSign().getName())).orElseThrow(()->new IllegalArgumentException("sign" + sign.getSign().getName() + " is miss"));
            case QUERY,BODY-> {
                String signStr = (String) signMap.get(sign.getSign().getName());
                signMap.remove(sign.getSign().getName());
                yield signStr;
            }
            case ALL -> Optional.ofNullable(request.getHeader(sign.getSign().getName())).orElseGet(()->{
                String signStr = (String) signMap.get(sign.getSign().getName());
                signMap.remove(sign.getSign().getName());
                return signStr;
//              TODO 测试  yield signStr;
            });
        };
        if (requestSignStr == null || requestSignStr.isBlank()) {
            throw new IllegalArgumentException("requestSignStr not select RequestHead、RequestQuery and RequestBody, signName: " + sign.getSign().getName());
        }
        return verify(signMap);
    }

    private void customParameter(HttpServletRequest request, InterceptorsAutoConfigurationProperties.SignProperties.CustomParam customParam, Map<String, Object> signMap){
        if (customParam.isEnable()) {
            if (customParam.getName() == null || customParam.getName().isBlank()) {
                throw new IllegalArgumentException("nonce is blank, please review moli.interceptor.sign.nonce.name");
            }
            InterceptorsAutoConfigurationProperties.SignProperties.ParameterPosition parameterPosition = customParam.getParameterPosition();
            switch (parameterPosition){
                case HEAD,ALL-> signMap.put(customParam.getName(), request.getHeader(customParam.getName()));
                case QUERY,BODY-> Optional.ofNullable(signMap.get(customParam.getName())).orElseThrow(()->new IllegalArgumentException("get " + customParam.getName() + " is null"));
            }
        }
    }

    private static void jsonLeaf(JsonNode node, HashMap<String, Object> jsonBodyMap)
    {
        if (node.isValueNode()) {
            log.warn("node is ValueNode: {}", node);
        }

        if (node.isObject())
        {
            Iterator<Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext())
            {
                Map.Entry<String, JsonNode> entry = it.next();
                jsonBodyMap.put(entry.getKey().toLowerCase(), entry.getValue().asText().toLowerCase());
            }
        }

        if (node.isArray())
        {
            Iterator<JsonNode> it = node.iterator();
            while (it.hasNext())
            {
                jsonLeaf(it.next(), jsonBodyMap);
            }
        }
    }
}
