package com.bingchunmoli.util;

import com.bingchunmoli.autoconfigure.redis.util.RedisUtil;
import com.bingchunmoli.bean.SignParamDTO;
import com.bingchunmoli.properties.InterceptorsAutoConfigurationProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * MD5签名接口 未实现
 *
 * @author MoLi
 */
@Slf4j
public class MD5SignUtil extends AbstractSignUtil {

    public MD5SignUtil(ObjectMapper om, InterceptorsAutoConfigurationProperties.SignProperties sign, RedisUtil redisUtil) {
        super(om, sign, redisUtil);
    }

    @Override
    public boolean verify(HttpServletRequest request) {
        SignParamDTO signParam = getSignParam(request);
        return doVerify(signParam);
    }

    private boolean doVerify(SignParamDTO signParam) {
        return Objects.equals(DigestUtils.md5DigestAsHex(signParam.getUnsignedStr().getBytes(StandardCharsets.UTF_8)), signParam.getSignatureStr());
    }
}
