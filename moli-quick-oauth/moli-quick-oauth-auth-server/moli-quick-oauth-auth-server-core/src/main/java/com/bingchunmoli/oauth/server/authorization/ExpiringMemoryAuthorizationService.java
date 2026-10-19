package com.bingchunmoli.oauth.server.authorization;
import java.time.Instant;
import java.util.concurrent.*;
import org.springframework.security.oauth2.server.authorization.*;
public final class ExpiringMemoryAuthorizationService implements OAuth2AuthorizationService,AutoCloseable {
    private record Entry(OAuth2Authorization authorization,Instant expires) { }
    private final InMemoryOAuth2AuthorizationService delegate=new InMemoryOAuth2AuthorizationService();
    private final ConcurrentHashMap<String,Entry> entries=new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanup=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"quick-oauth-memory-expiry");t.setDaemon(true);return t;});
    public ExpiringMemoryAuthorizationService(){cleanup.scheduleWithFixedDelay(this::cleanupExpired,60,60,TimeUnit.SECONDS);}
    public void save(OAuth2Authorization authorization) {entries.compute(authorization.getId(),(id,old)->{delegate.save(authorization);return new Entry(authorization,AuthorizationExpiration.latest(authorization));});}
    public void remove(OAuth2Authorization authorization) {entries.compute(authorization.getId(),(id,old)->{delegate.remove(authorization);return null;});}
    public OAuth2Authorization findById(String id){expire(id);return delegate.findById(id);}
    public OAuth2Authorization findByToken(String token,OAuth2TokenType type){
        var authorization=delegate.findByToken(token,type);
        if(authorization!=null){expire(authorization.getId());return delegate.findByToken(token,type);}return null;
    }
    private void expire(String id){entries.computeIfPresent(id,(key,entry)->{
        if(entry.expires()!=null && !entry.expires().isAfter(Instant.now())){delegate.remove(entry.authorization());return null;}return entry;
    });}
    public void cleanupExpired(){entries.keySet().forEach(this::expire);}
    public void close(){cleanup.shutdownNow();}
}
