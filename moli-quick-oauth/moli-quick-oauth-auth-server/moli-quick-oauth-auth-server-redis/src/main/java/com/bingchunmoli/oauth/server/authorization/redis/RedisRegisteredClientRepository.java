package com.bingchunmoli.oauth.server.authorization.redis;
import com.bingchunmoli.oauth.server.authorization.AuthorizationSnapshotCodec;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.oauth2.server.authorization.client.*;
public final class RedisRegisteredClientRepository implements RegisteredClientRepository {
    private static final DefaultRedisScript<Long> SAVE=new DefaultRedisScript<>(
        "local id=redis.call('hget',KEYS[2],ARGV[2]); if id and id~=ARGV[1] then return 0 end "+
        "local previous=redis.call('hget',KEYS[1],ARGV[1]); if previous and cjson.decode(previous).clientId~=ARGV[2] then return -1 end "+
        "if ARGV[4]=='insert' and (previous or id) then return 2 end "+
        "redis.call('hset',KEYS[1],ARGV[1],ARGV[3]); redis.call('hset',KEYS[2],ARGV[2],ARGV[1]); return 1",Long.class);
    private final StringRedisTemplate redis;private final String prefix;private final AuthorizationSnapshotCodec codec;
    public RedisRegisteredClientRepository(StringRedisTemplate redis,String prefix,AuthorizationSnapshotCodec codec) {this.redis=redis;this.prefix=prefix;this.codec=codec;}
    public void save(RegisteredClient client) {
        persist(client,false);
    }
    /** Atomically initializes a missing client without overwriting another instance's record. */
    public boolean saveIfAbsent(RegisteredClient client) {
        return persist(client,true);
    }
    private boolean persist(RegisteredClient client,boolean onlyIfAbsent) {
        String secret=client.getClientSecret();
        if(secret!=null && (!secret.startsWith("{") || secret.startsWith("{noop}"))) throw new IllegalStateException("Persist an encoded OAuth client secret");
        RegisteredClient old=findById(client.getId());
        if(old!=null && !old.getClientId().equals(client.getClientId())) throw new IllegalStateException("Registered client ID is immutable");
        Long result=redis.execute(SAVE,List.of(prefix+":clients",prefix+":client-ids"),client.getId(),client.getClientId(),codec.client(client),onlyIfAbsent?"insert":"save");
        if(onlyIfAbsent && Long.valueOf(2).equals(result)) return false;
        if(!Long.valueOf(1).equals(result)) throw new IllegalStateException("Conflicting registered client ID");
        return true;
    }
    public RegisteredClient findById(String id) {
        String data=(String)redis.opsForHash().get(prefix+":clients",id);return data==null?null:codec.client(data);
    }
    public RegisteredClient findByClientId(String clientId) {
        String id=(String)redis.opsForHash().get(prefix+":client-ids",clientId);return id==null?null:findById(id);
    }
}
