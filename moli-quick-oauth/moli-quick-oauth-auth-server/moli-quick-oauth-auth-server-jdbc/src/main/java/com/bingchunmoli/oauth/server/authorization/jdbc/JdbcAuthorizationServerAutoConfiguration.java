package com.bingchunmoli.oauth.server.authorization.jdbc;
import com.bingchunmoli.oauth.server.authorization.*;
import com.bingchunmoli.oauth.server.authorization.autoconfigure.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.*;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.client.*;
@AutoConfiguration(before=QuickAuthorizationServerAutoConfiguration.class,
    afterName="org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration")
@ConditionalOnProperty(prefix="moli.oauth.auth-server",name="enabled",havingValue="true")
@ConditionalOnProperty(prefix="moli.oauth.auth-server.storage",name="type",havingValue="jdbc")
@EnableConfigurationProperties({QuickAuthorizationServerProperties.class,OAuth2AuthorizationServerProperties.class})
public class JdbcAuthorizationServerAutoConfiguration {
    @Bean @ConditionalOnMissingBean(RegisteredClientRepository.class)
    RegisteredClientRepository jdbcRegisteredClients(DataSource source) { return new JdbcRegisteredClientRepository(new JdbcTemplate(source)); }
    @Bean @ConditionalOnMissingBean(OAuth2AuthorizationService.class)
    OAuth2AuthorizationService jdbcAuthorizations(DataSource source,RegisteredClientRepository clients,AuthorizationAttributeCodec attributes) {
        var service=new JdbcOAuth2AuthorizationService(new JdbcTemplate(source),clients);
        var json=AuthorizationAttributeJsonMapper.create(attributes);
        service.setAuthorizationParametersMapper(new JdbcOAuth2AuthorizationService.JsonMapperOAuth2AuthorizationParametersMapper(json));
        service.setAuthorizationRowMapper(new JdbcOAuth2AuthorizationService.JsonMapperOAuth2AuthorizationRowMapper(clients,json));
        return new JdbcExpiringAuthorizationService(source,service);
    }
    @Bean @ConditionalOnMissingBean(OAuth2AuthorizationConsentService.class)
    OAuth2AuthorizationConsentService jdbcConsents(DataSource source,RegisteredClientRepository clients) { return new JdbcOAuth2AuthorizationConsentService(new JdbcTemplate(source),clients); }
    @Bean @ConditionalOnMissingBean(GrantCoordinator.class)
    GrantCoordinator jdbcGrantCoordinator(DataSource source,QuickAuthorizationServerProperties quick) {
        return new JdbcGrantCoordinator(source,quick.getStorage().getWaitTimeout());
    }
    @Bean @ConditionalOnBean(name="jdbcRegisteredClients")
    SmartInitializingSingleton initializeJdbcOAuthClients(RegisteredClientRepository clients,
            OAuth2AuthorizationServerProperties nativeProperties,Environment environment,QuickAuthorizationServerProperties quick) {
        return () -> {
            if(nativeProperties.getClient().isEmpty()) return;
            for(var client:QuickAuthorizationServerAutoConfiguration.configuredClients(nativeProperties,environment)) {
                QuickAuthorizationServerAutoConfiguration.audience(quick,client);
                if(clients.findByClientId(client.getClientId())==null) try { clients.save(client); }
                catch(DuplicateKeyException conflict) { if(clients.findByClientId(client.getClientId())==null) throw conflict; }
            }
        };
    }
}
