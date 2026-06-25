package com.bingchunmoli.generate;


import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.config.ConstVal;
import com.baomidou.mybatisplus.generator.config.DataSourceConfig;
import com.baomidou.mybatisplus.generator.config.rules.DateType;
import com.baomidou.mybatisplus.generator.config.rules.NamingStrategy;
import com.baomidou.mybatisplus.generator.engine.VelocityTemplateEngine;
import com.baomidou.mybatisplus.generator.model.ClassAnnotationAttributes;
import com.bingchunmoli.generate.config.GeneratorConfigProperties;
import com.bingchunmoli.generate.enums.SwaggerEnums;
import lombok.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.HashMap;

/**
 * @author moli
 */
@Configuration
@EnableConfigurationProperties({DataSourceProperties.class, GeneratorConfigProperties.class})
@ConditionalOnBean(DataSourceProperties.class)
public class GenerateAutoConfiguration {

    public FastAutoGenerator buildGenerate(@NonNull DataSource dataSource, @NonNull GeneratorConfigProperties generatorConfigProperties) {
        return FastAutoGenerator.create(new DataSourceConfig.Builder(dataSource))
                .globalConfig(builder -> {
                    builder.author(generatorConfigProperties.getAuthor())
                            .outputDir(generatorConfigProperties.getOutputDir())
                            .commentDate(generatorConfigProperties.getCommentDate());
                    if (SwaggerEnums.V2.equals(generatorConfigProperties.getSwaggerVersion())) {
                        builder.enableSwagger();
                    }
                    if (SwaggerEnums.V3.equals(generatorConfigProperties.getSwaggerVersion())) {
                        builder.enableSpringdoc();
                    }
                    builder.dateType(DateType.TIME_PACK).build();
                })
                .packageConfig(v -> v
                        .parent(generatorConfigProperties.getBasePackage())
                        .build())
                .strategyConfig(builder -> builder
                        .entityBuilder()
                        .enableActiveRecord()
                        .enableLombok(new ClassAnnotationAttributes(Data.class),
                                new ClassAnnotationAttributes(Builder.class),
                                new ClassAnnotationAttributes(NoArgsConstructor.class),
                                new ClassAnnotationAttributes(AllArgsConstructor.class))
                        .javaTemplate(ConstVal.TEMPLATE_ENTITY_JAVA)
                        .columnNaming(NamingStrategy.underline_to_camel)
                        .controllerBuilder()
                        .template(ConstVal.TEMPLATE_CONTROLLER)
                        .serviceBuilder()
                        .serviceTemplate(ConstVal.TEMPLATE_SERVICE)
                        .serviceImplTemplate(ConstVal.TEMPLATE_SERVICE_IMPL)
                        .formatServiceFileName("%sService")
                        .mapperBuilder()
                        .enableBaseColumnList()
                        .enableBaseResultMap()
                        .mapperTemplate(ConstVal.TEMPLATE_MAPPER)
                )
                .injectionConfig(v -> {
                    HashMap<String, Object> map = new HashMap<>();
                    map.put("version", generatorConfigProperties.getVersion());
                    v.customMap(map);
                })
                .templateEngine(new VelocityTemplateEngine());
    }

}
