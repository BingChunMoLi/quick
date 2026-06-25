package com.bingchunmoli.generate.config;

import com.bingchunmoli.generate.enums.SwaggerEnums;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeneratorConfigPropertiesTest {

    @Test
    void defaultsShouldBeStable() {
        GeneratorConfigProperties properties = new GeneratorConfigProperties();

        assertThat(properties.getBasePackage()).isEqualTo("com.bingchunmoli");
        assertThat(properties.getCommentDate()).isEqualTo("yyyy-MM-dd");
        assertThat(properties.getSwaggerVersion()).isEqualTo(SwaggerEnums.V3);
        assertThat(properties.getActiveRecord()).isTrue();
    }

    @Test
    void swaggerEnumsShouldExposeGeneratorLibraryNames() {
        assertThat(SwaggerEnums.V2.getVersion()).isEqualTo(2);
        assertThat(SwaggerEnums.V2.getLibraryName()).isEqualTo("swagger");
        assertThat(SwaggerEnums.V3.getVersion()).isEqualTo(3);
        assertThat(SwaggerEnums.V3.getLibraryName()).isEqualTo("springfox");
    }
}
