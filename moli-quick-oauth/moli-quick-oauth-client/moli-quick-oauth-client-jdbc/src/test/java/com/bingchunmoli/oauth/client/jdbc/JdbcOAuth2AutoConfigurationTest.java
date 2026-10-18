package com.bingchunmoli.oauth.client.jdbc;

import com.bingchunmoli.oauth.client.autoconfigure.QuickOAuth2AutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.*;

class JdbcOAuth2AutoConfigurationTest {
    @Test void selectedJdbcUsesHostDataSourceWithoutOpeningOrInitializingIt() {
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(JdbcOAuth2AutoConfiguration.class, QuickOAuth2AutoConfiguration.class))
                .withBean(javax.sql.DataSource.class, () -> new DriverManagerDataSource())
                .withPropertyValues("moli.oauth.client.enabled=true", "moli.oauth.client.authorized-client.store=jdbc",
                        "spring.security.oauth2.client.registration.app.client-id=id",
                        "spring.security.oauth2.client.registration.app.authorization-grant-type=client_credentials",
                        "spring.security.oauth2.client.registration.app.provider=custom",
                        "spring.security.oauth2.client.provider.custom.token-uri=https://provider/token")
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(JdbcOAuth2AuthorizedClientService.class));
    }
}
