package com.bingchunmoli.codegen.web;

import com.bingchunmoli.codegen.CodeGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;

class CodegenAutoConfigurationTest {
    private final WebApplicationContextRunner runner = new WebApplicationContextRunner().withConfiguration(AutoConfigurations.of(CodegenAutoConfiguration.class));
    @Test void disabledByDefaultRegistersNeitherControllerNorResources() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(CodegenController.class);
            assertThat(context).doesNotHaveBean("codegenResources");
            assertThat(context).doesNotHaveBean(CodeGenerator.class);
        });
    }
    @Test void explicitlyEnabledRegistersApiAndResources() {
        runner.withPropertyValues("moli.codegen.enabled=true").run(context -> {
            assertThat(context).hasSingleBean(CodegenController.class);
            assertThat(context).hasBean("codegenResources");
            assertThat(context).hasSingleBean(CodeGenerator.class);
        });
    }
    @Test void hostCanReplaceGenerator() {
        var custom = new CodeGenerator();
        runner.withPropertyValues("moli.codegen.enabled=true").withBean(CodeGenerator.class, () -> custom)
                .run(context -> assertThat(context.getBean(CodeGenerator.class)).isSameAs(custom));
    }
}
