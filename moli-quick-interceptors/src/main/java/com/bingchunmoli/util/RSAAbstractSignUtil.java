package com.bingchunmoli.util;

import com.bingchunmoli.autoconfigure.redis.util.RedisUtil;
import com.bingchunmoli.bean.SignParamDTO;
import com.bingchunmoli.exception.SignConfigException;
import com.bingchunmoli.properties.InterceptorsAutoConfigurationProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.GeneralSecurityException;
import java.security.SignatureException;
import com.bingchunmoli.exception.SignParamIllegalArgumentException;
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
        return completeVerification(signParam, doVerify(signParam));
    }

    protected boolean doVerify(SignParamDTO signParam) {
        Signature signature;
        try {
            if (sign.getAlgorithm() == null) throw new IllegalArgumentException("Missing RSA signature algorithm");
            signature = Signature.getInstance(sign.getAlgorithm());
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            if (sign.getPublicKey() == null) throw new IllegalArgumentException("Missing RSA public key");
            PublicKey publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(sign.getPublicKey())));
            signature.initVerify(publicKey);
            signature.update(signParam.getUnsignedStr().getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException | IllegalArgumentException failure) {
            throw new SignConfigException("Invalid RSA signature algorithm or public key", failure);
        }
        if (log.isDebugEnabled()) {
            log.debug("doVerify, signParam: {}", signParam);
        }
        byte[] signatureBytes;
        try {
            signatureBytes = Base64.getDecoder().decode(signParam.getSignatureStr());
        } catch (IllegalArgumentException failure) {
            throw new SignParamIllegalArgumentException("Invalid RSA signature encoding");
        }
        try {
            return signature.verify(signatureBytes);
        } catch (SignatureException failure) {
            throw new SignParamIllegalArgumentException("Invalid RSA signature length or format");
        }
    }
}
