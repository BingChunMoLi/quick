package com.bingchunmoli.codegen.jdbc;

import com.bingchunmoli.codegen.CodegenException;
import com.bingchunmoli.codegen.model.GenerationConfig;
import java.sql.DriverManager;
import java.util.Properties;
import java.util.regex.Pattern;

public final class JdbcConnections {
    private static final Pattern ENV = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)}");
    private JdbcConnections() { }

    public static ConnectionProvider of(GenerationConfig.Database config) {
        if (config == null) throw new CodegenException("datasource is required");
        if (config.beanName != null && !config.beanName.isBlank())
            throw new CodegenException("Named DataSource is only available in the embedded Web application");
        String url = resolve(config.url);
        if (!url.startsWith("jdbc:mysql:") && !url.startsWith("jdbc:postgresql:"))
            throw new CodegenException("Only MySQL and PostgreSQL JDBC URLs are supported");
        Properties properties = new Properties();
        properties.setProperty("user", resolve(config.username));
        properties.setProperty("password", resolve(config.password));
        if (url.startsWith("jdbc:mysql:")) properties.setProperty("useInformationSchema", "true");
        return () -> DriverManager.getConnection(url, properties);
    }

    private static String resolve(String value) {
        if (value == null) return "";
        var matcher = ENV.matcher(value);
        return matcher.replaceAll(result -> {
            String replacement = System.getenv(result.group(1));
            if (replacement == null) throw new CodegenException("Missing environment variable: " + result.group(1));
            return java.util.regex.Matcher.quoteReplacement(replacement);
        });
    }
}
