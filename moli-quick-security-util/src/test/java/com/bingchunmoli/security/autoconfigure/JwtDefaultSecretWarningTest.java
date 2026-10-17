package com.bingchunmoli.security.autoconfigure;

import com.bingchunmoli.security.jwt.JwtTokenProperties;
import com.bingchunmoli.security.jwt.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class JwtDefaultSecretWarningTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SecurityUtilAutoConfiguration.class));

    @Test
    void warnsAtStartupWithoutPrintingTheDefaultSecret(CapturedOutput output) {
        contextRunner.run(context -> assertThat(context).hasSingleBean(JwtTokenService.class));
        assertThat(output.getAll()).contains("WARN", "default JWT secret", "production", "moli.security.jwt.secret")
                .doesNotContain(new JwtTokenProperties().getSecret());
    }

    @Test
    void doesNotWarnOrPrintACustomSecret(CapturedOutput output) {
        String secret = "configured-secret-for-jwt-warning-regression-test";
        contextRunner.withPropertyValues("moli.security.jwt.secret=" + secret)
                .run(context -> assertThat(context).hasSingleBean(JwtTokenService.class));
        assertThat(output.getAll()).doesNotContain("default JWT secret", secret);
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
