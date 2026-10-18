package com.bingchunmoli.oauth.client.jdbc;

import com.bingchunmoli.oauth.client.autoconfigure.QuickOAuth2Properties;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

@AutoConfiguration(afterName = "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration")
@ConditionalOnProperty(prefix = "moli.oauth.client", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(QuickOAuth2Properties.class)
public class JdbcOAuth2AutoConfiguration {
    @Bean @ConditionalOnMissingBean(OAuth2AuthorizedClientService.class)
    @ConditionalOnProperty(prefix = "moli.oauth.client.authorized-client", name = "store", havingValue = "jdbc")
    OAuth2AuthorizedClientService jdbcOAuth2AuthorizedClientService(DataSource dataSource, ClientRegistrationRepository registrations) {
        return new JdbcOAuth2AuthorizedClientService(new JdbcTemplate(dataSource), registrations);
    }
}
