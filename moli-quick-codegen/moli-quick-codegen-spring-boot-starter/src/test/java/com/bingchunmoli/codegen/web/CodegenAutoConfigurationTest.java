package com.bingchunmoli.codegen.web;

import com.bingchunmoli.codegen.CodeGenerator;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;
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
    @Test void hostControllerAndAdviceReplaceDefaultsWithoutDuplicateMvcMappings() {
        var controller = new CodegenController(new CodeGenerator(), new DefaultListableBeanFactory());
        var advice = new CodegenExceptionHandler();
        runner.withPropertyValues("moli.codegen.enabled=true")
                .withUserConfiguration(WebMvcConfigurationSupport.class)
                .withBean("hostController", CodegenController.class, () -> controller)
                .withBean("hostAdvice", CodegenExceptionHandler.class, () -> advice).run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(CodegenController.class).hasSingleBean(CodegenExceptionHandler.class);
                    assertThat(context.getBean(CodegenController.class)).isSameAs(controller);
                    assertThat(context.getBean(CodegenExceptionHandler.class)).isSameAs(advice);
                });
    }
}
