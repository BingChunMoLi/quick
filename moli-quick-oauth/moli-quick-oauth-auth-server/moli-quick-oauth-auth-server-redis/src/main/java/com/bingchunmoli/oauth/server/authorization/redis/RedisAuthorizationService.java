package com.bingchunmoli.oauth.server.authorization.redis;
import com.bingchunmoli.oauth.server.authorization.*;
import java.time.*;
import java.util.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import tools.jackson.databind.json.JsonMapper;
public final class RedisAuthorizationService implements OAuth2AuthorizationService {
    private static final DefaultRedisScript<Long> SAVE=new DefaultRedisScript<>(
        "if ARGV[1]~='' and redis.call('get',KEYS[3])~=ARGV[1] then return 0 end "+
        "local entries=cjson.decode(ARGV[5]); for _,entry in ipairs(entries) do local id=redis.call('get',entry.key); if id and id~=ARGV[2] then return -1 end end "+
        "for _,key in ipairs(redis.call('smembers',KEYS[2])) do if redis.call('get',key)==ARGV[2] then redis.call('del',key) end end redis.call('del',KEYS[2]); "+
        "redis.call('set',KEYS[1],ARGV[3]); if tonumber(ARGV[4])>0 then redis.call('pexpire',KEYS[1],ARGV[4]) end "+
        "for _,entry in ipairs(entries) do redis.call('set',entry.key,ARGV[2]); if entry.ttl>0 then redis.call('pexpire',entry.key,entry.ttl) end redis.call('sadd',KEYS[2],entry.key) end "+
        "if tonumber(ARGV[4])>0 then redis.call('pexpire',KEYS[2],ARGV[4]) end return 1",Long.class);
    private static final DefaultRedisScript<Long> REMOVE=new DefaultRedisScript<>(
        "if ARGV[1]~='' and redis.call('get',KEYS[3])~=ARGV[1] then return 0 end "+
        "for _,key in ipairs(redis.call('smembers',KEYS[2])) do if redis.call('get',key)==ARGV[2] then redis.call('del',key) end end redis.call('del',KEYS[1],KEYS[2]); return 1",Long.class);
    private final StringRedisTemplate redis;private final String prefix;private final AuthorizationSnapshotCodec codec;
    private final RegisteredClientRepository clients;private final RedisGrantCoordinator coordinator;
    private final JsonMapper json=JsonMapper.builder().build();
    public RedisAuthorizationService(StringRedisTemplate redis,String prefix,AuthorizationSnapshotCodec codec,RegisteredClientRepository clients,RedisGrantCoordinator coordinator) {
        this.redis=redis;this.prefix=prefix;this.codec=codec;this.clients=clients;this.coordinator=coordinator;
    }
    private String key(String id) {return prefix+":authorization:"+id;}
    private String index(String token) {return prefix+":token:"+AuthorizationSnapshotCodec.digest(token);}
    private static long ttl(Instant expiry) {return expiry==null?0:Math.max(1,Duration.between(Instant.now(),expiry).toMillis());}
    public void save(OAuth2Authorization authorization) {
        var tokens=AuthorizationSnapshotCodec.indexes(authorization);
        List<Map<String,Object>> entries=new ArrayList<>();
        Instant now=Instant.now();
        for(var token:tokens) {
            if(token.expiration()!=null && !token.expiration().isAfter(now))continue;
            entries.add(Map.of("key",index(token.value()),"ttl",ttl(token.expiration())));
        }
        long lifetime=ttl(AuthorizationExpiration.latest(authorization));String id=authorization.getId();
        Long result=redis.execute(SAVE,List.of(key(id),key(id)+":indexes",coordinator.lockKey(id)),coordinator.owner(id),
                id,codec.authorization(authorization),Long.toString(lifetime),json.writeValueAsString(entries));
        if(!Long.valueOf(1).equals(result)) throw LocalGrantCoordinator.unavailable();
    }
    public void remove(OAuth2Authorization authorization) {
        String id=authorization.getId();
        if(!Long.valueOf(1).equals(redis.execute(REMOVE,List.of(key(id),key(id)+":indexes",coordinator.lockKey(id)),coordinator.owner(id),id)))
            throw LocalGrantCoordinator.unavailable();
    }
    public OAuth2Authorization findById(String id) {String data=redis.opsForValue().get(key(id));return data==null?null:codec.authorization(data,clients);}
    public OAuth2Authorization findByToken(String token,OAuth2TokenType type) {
        String id=redis.opsForValue().get(index(token));if(id==null) return null;
        OAuth2Authorization authorization=findById(id);if(authorization==null) return null;
        if(type==null) return AuthorizationSnapshotCodec.indexes(authorization).stream().anyMatch(entry->entry.value().equals(token))?authorization:null;
        if("state".equals(type.getValue())) return token.equals(authorization.getAttribute("state"))?authorization:null;
        OAuth2Authorization.Token<?> saved=switch(type.getValue()) {
            case "code" -> authorization.getToken(OAuth2AuthorizationCode.class);
            case "access_token" -> authorization.getAccessToken();
            case "refresh_token" -> authorization.getRefreshToken();
            case "id_token" -> authorization.getToken(OidcIdToken.class);
            default -> null;
        };
        return saved!=null && token.equals(saved.getToken().getTokenValue())?authorization:null;
    }
}
