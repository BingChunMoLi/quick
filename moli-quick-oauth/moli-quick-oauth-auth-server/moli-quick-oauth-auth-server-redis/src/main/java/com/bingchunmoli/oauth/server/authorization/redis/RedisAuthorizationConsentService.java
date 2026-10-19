package com.bingchunmoli.oauth.server.authorization.redis;
import com.bingchunmoli.oauth.server.authorization.AuthorizationSnapshotCodec;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.server.authorization.*;
public final class RedisAuthorizationConsentService implements OAuth2AuthorizationConsentService {
    private final StringRedisTemplate redis;private final String prefix;private final AuthorizationSnapshotCodec codec;
    public RedisAuthorizationConsentService(StringRedisTemplate redis,String prefix,AuthorizationSnapshotCodec codec) {this.redis=redis;this.prefix=prefix;this.codec=codec;}
    private String key(String id,String principal) {return prefix+":consent:"+AuthorizationSnapshotCodec.digest(id+"\u0000"+principal);}
    public void save(OAuth2AuthorizationConsent consent) {redis.opsForValue().set(key(consent.getRegisteredClientId(),consent.getPrincipalName()),codec.consent(consent));}
    public void remove(OAuth2AuthorizationConsent consent) {redis.delete(key(consent.getRegisteredClientId(),consent.getPrincipalName()));}
    public OAuth2AuthorizationConsent findById(String id,String principal) {String data=redis.opsForValue().get(key(id,principal));return data==null?null:codec.consent(data);}
}
