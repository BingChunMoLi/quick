package com.bingchunmoli.oauth.server.authorization.jdbc;
import com.bingchunmoli.oauth.server.authorization.*;
import java.time.Duration;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.dao.DataAccessException;
public final class JdbcGrantCoordinator implements GrantCoordinator {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final Duration wait;
    private static final class Busy extends RuntimeException { }
    public JdbcGrantCoordinator(DataSource source,Duration wait) {
        if(wait==null || wait.isNegative() || wait.isZero()) throw new IllegalArgumentException("Positive grant wait required");
        jdbc=new JdbcTemplate(source);
        this.wait=wait;
        transactions=new TransactionTemplate(new DataSourceTransactionManager(source));
        transactions.setTimeout(Math.max(1,(int)wait.toSeconds()));
    }
    @Override public <T> T execute(String id,Supplier<T> work) {
        long deadline=System.nanoTime()+wait.toNanos();
        while(true) {
            try { return attempt(id,work); }
            catch(Busy busy) {
                if(System.nanoTime()>=deadline) throw LocalGrantCoordinator.unavailable();
                try { Thread.sleep(20); } catch(InterruptedException interrupted) {Thread.currentThread().interrupt();throw LocalGrantCoordinator.unavailable();}
            }
        }
    }
    private <T> T attempt(String id,Supplier<T> work) {
        AtomicReference<OAuth2AuthenticationException> protocolFailure=new AtomicReference<>();
        try {
            T result=transactions.execute(status -> {
                try { jdbc.query("select id from oauth2_authorization where id = ? for update nowait",rs -> {},id); }
                catch(DataAccessException busy) {throw new Busy();}
                try { return work.get(); }
                catch(OAuth2AuthenticationException failure) {
                    // Native replay detection may revoke prior tokens before raising invalid_grant; commit that revocation.
                    protocolFailure.set(failure);return null;
                }
            });
            if(protocolFailure.get()!=null) throw protocolFailure.get();
            return result;
        } catch(DataAccessException failure) { throw LocalGrantCoordinator.unavailable(); }
    }
}
