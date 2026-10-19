package com.bingchunmoli.oauth.server.authorization.jdbc;
import com.bingchunmoli.oauth.server.authorization.AuthorizationExpiration;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.oauth2.server.authorization.*;
public final class JdbcExpiringAuthorizationService implements OAuth2AuthorizationService,AutoCloseable {
    private final OAuth2AuthorizationService delegate;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ScheduledExecutorService cleanup=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"quick-oauth-jdbc-expiry");t.setDaemon(true);return t;});
    public JdbcExpiringAuthorizationService(DataSource source,OAuth2AuthorizationService delegate) {
        this.delegate=delegate;jdbc=new JdbcTemplate(source);transactions=new TransactionTemplate(new DataSourceTransactionManager(source));
        cleanup.scheduleWithFixedDelay(()->{try{cleanupExpired();}catch(RuntimeException unavailable){
            org.apache.commons.logging.LogFactory.getLog(getClass()).warn("OAuth JDBC authorization cleanup unavailable");
        }},60,60,TimeUnit.SECONDS);
    }
    public void save(OAuth2Authorization authorization) {
        transactions.executeWithoutResult(status->{
            delegate.save(authorization);Instant expiration=AuthorizationExpiration.latest(authorization);
            Timestamp expiry=expiration==null?null:Timestamp.from(expiration);
            if(jdbc.update("update quick_oauth_authorization_expiry set expires_at=? where authorization_id=?",expiry,authorization.getId())==0)
                jdbc.update("insert into quick_oauth_authorization_expiry (authorization_id,expires_at) values (?,?)",authorization.getId(),expiry);
        });
    }
    public void remove(OAuth2Authorization authorization) {
        transactions.executeWithoutResult(status->{delegate.remove(authorization);jdbc.update("delete from quick_oauth_authorization_expiry where authorization_id=?",authorization.getId());});
    }
    public OAuth2Authorization findById(String id){return delegate.findById(id);}
    public OAuth2Authorization findByToken(String token,OAuth2TokenType type){return delegate.findByToken(token,type);}
    public void cleanupExpired(){
        var ids=jdbc.queryForList("select authorization_id from quick_oauth_authorization_expiry where expires_at <= ? limit 100",String.class,Timestamp.from(Instant.now()));
        for(String id:ids) transactions.executeWithoutResult(status->{
            try {jdbc.query("select id from oauth2_authorization where id=? for update nowait",rs->{},id);}
            catch(DataAccessException locked){status.setRollbackOnly();return;}
            var expiry=jdbc.queryForList("select expires_at from quick_oauth_authorization_expiry where authorization_id=?",Timestamp.class,id);
            if(expiry.isEmpty() || expiry.get(0)==null || expiry.get(0).toInstant().isAfter(Instant.now()))return;
            // Expiry cleanup must not deserialize old/custom attributes or resolve a removed client.
            jdbc.update("delete from oauth2_authorization where id=?",id);
            jdbc.update("delete from quick_oauth_authorization_expiry where authorization_id=?",id);
        });
    }
    public void close(){cleanup.shutdownNow();}
}
