package com.bingchunmoli.generate;

import com.bingchunmoli.generate.config.GeneratorConfigProperties;
import com.bingchunmoli.generate.enums.SwaggerEnums;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GenerateAutoConfigurationIntegrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(GenerateAutoConfiguration.class))
            .withBean(DataSourceProperties.class)
            .withBean(DataSource.class, GenerateAutoConfigurationIntegrationTest::dataSource);

    @Test
    void shouldBindGeneratorPropertiesAndLoadConfigurationForBoot406() {
        contextRunner
                .withPropertyValues(
                        "moli.gen.base-package=com.example.demo",
                        "moli.gen.author=MoLi",
                        "moli.gen.output-dir=target/generated-test-sources",
                        "moli.gen.comment-date=yyyy/MM/dd",
                        "moli.gen.swagger-version=V2",
                        "moli.gen.version=1.0.13-bate",
                        "moli.gen.active-record=false"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(GenerateAutoConfiguration.class);
                    assertThat(context).hasSingleBean(GeneratorConfigProperties.class);

                    GeneratorConfigProperties properties = context.getBean(GeneratorConfigProperties.class);
                    assertThat(properties.getBasePackage()).isEqualTo("com.example.demo");
                    assertThat(properties.getAuthor()).isEqualTo("MoLi");
                    assertThat(properties.getOutputDir()).isEqualTo("target/generated-test-sources");
                    assertThat(properties.getCommentDate()).isEqualTo("yyyy/MM/dd");
                    assertThat(properties.getSwaggerVersion()).isEqualTo(SwaggerEnums.V2);
                    assertThat(properties.getVersion()).isEqualTo("1.0.13-bate");
                    assertThat(properties.getActiveRecord()).isFalse();
                });
    }

    @Test
    void shouldCreateFastAutoGeneratorWithConsumerConfiguration() {
        contextRunner.run(context -> assertThatCode(() -> context.getBean(GenerateAutoConfiguration.class)
                .buildGenerate(context.getBean(DataSource.class), context.getBean(GeneratorConfigProperties.class)))
                .doesNotThrowAnyException());
    }

    private static DataSource dataSource() {
        try {
            DataSource dataSource = mock(DataSource.class);
            Connection connection = mock(Connection.class);
            DatabaseMetaData metaData = mock(DatabaseMetaData.class);
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.getMetaData()).thenReturn(metaData);
            when(metaData.getURL()).thenReturn("jdbc:h2:mem:quick");
            when(metaData.getUserName()).thenReturn("sa");
            when(metaData.getDatabaseProductName()).thenReturn("H2");
            return dataSource;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
