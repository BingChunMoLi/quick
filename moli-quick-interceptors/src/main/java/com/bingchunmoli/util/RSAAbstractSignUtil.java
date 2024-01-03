package com.bingchunmoli.util;

import com.bingchunmoli.autoconfigure.redis.util.RedisUtil;
import com.bingchunmoli.bean.SignParamDTO;
import com.bingchunmoli.exception.SignConfigException;
import com.bingchunmoli.properties.InterceptorsAutoConfigurationProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * @author moli
 */
@Slf4j
public abstract class RSAAbstractSignUtil extends AbstractSignUtil {

    protected RSAAbstractSignUtil(ObjectMapper om, InterceptorsAutoConfigurationProperties.SignProperties sign, RedisUtil redisUtil) {
        super(om, sign, redisUtil);
    }

    @Override
    public boolean verify(HttpServletRequest request) {
        SignParamDTO signParam = getSignParam(request);
        return doVerify(signParam);
    }

    @SneakyThrows
    protected boolean doVerify(SignParamDTO signParam) {
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
}
