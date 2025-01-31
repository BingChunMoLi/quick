package com.bingchunmoli.generate.config;

import com.bingchunmoli.generate.enums.SwaggerEnums;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author moli
 */
@Data
@ConfigurationProperties(prefix = "moli.gen")
public class GeneratorConfigProperties {

    /**
     * 基础包名
     */
    private String basePackage = "com.bingchunmoli";

    /**
     * 注释作者
     */
    private String author;

    /**
     * 输出文件目录
     */
    private String outputDir;

    /**
     * 注释日期格式
     */
    private String commentDate = "yyyy-MM-dd";

    /**
     * swagger版本
     */
    private SwaggerEnums swaggerVersion = SwaggerEnums.V3;

    /**
     * 当前注释的@since 版本
     */
    private String version;

    /**
     * 是否启用
     */
    private Boolean ActiveRecord = true;
}
