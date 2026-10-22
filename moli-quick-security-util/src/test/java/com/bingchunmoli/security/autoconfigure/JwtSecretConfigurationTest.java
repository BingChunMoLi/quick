package com.bingchunmoli.security.autoconfigure;

import com.bingchunmoli.security.jwt.JwtTokenProperties;
import com.bingchunmoli.security.jwt.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class JwtSecretConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SecurityUtilAutoConfiguration.class));

    @Test
    void rejectsMissingSecretAtStartup(CapturedOutput output) {
        contextRunner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class)
                    .rootCause().hasMessageContaining("moli.security.jwt.secret");
        });
        assertThat(output.getAll()).doesNotContain(JwtTokenProperties.DEFAULT_SECRET);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "short", JwtTokenProperties.DEFAULT_SECRET})
    void rejectsBlankWeakAndLegacySecrets(String secret) {
        contextRunner.withPropertyValues("moli.security.jwt.secret=" + secret).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class);
        });
    }

    @Test
    void acceptsAnExplicitSecretWithoutPrintingIt(CapturedOutput output) {
        String secret = "configured-secret-for-jwt-warning-regression-test";
        contextRunner.withPropertyValues("moli.security.jwt.secret=" + secret)
                .run(context -> assertThat(context).hasSingleBean(JwtTokenService.class));
        assertThat(output.getAll()).doesNotContain(secret);
    }

    @Test
    void doesNotWarnWhenJwtIsDisabled(CapturedOutput output) {
        contextRunner.withPropertyValues("moli.security.jwt.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(JwtTokenService.class));
        assertThat(output.getAll()).doesNotContain("default JWT secret");
    }

    @Test
    void doesNotWarnWhenSecurityIsDisabled(CapturedOutput output) {
        contextRunner.withPropertyValues("moli.security.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(JwtTokenService.class));
        assertThat(output.getAll()).doesNotContain("default JWT secret");
    }
}
