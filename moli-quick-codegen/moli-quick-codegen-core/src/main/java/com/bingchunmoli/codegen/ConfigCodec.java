package com.bingchunmoli.codegen;

import com.bingchunmoli.codegen.model.GenerationConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class ConfigCodec {
    private final ObjectMapper mapper = new ObjectMapper();

    public GenerationConfig read(Path file) {
        try { return read(Files.readString(file)); }
        catch (IOException e) { throw new CodegenException("Cannot read configuration file"); }
    }

    public GenerationConfig read(String yaml) {
        try {
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);
            options.setMaxAliasesForCollections(20);
            Object data = new Yaml(new SafeConstructor(options)).load(yaml);
            if (!(data instanceof Map<?, ?>)) throw new IllegalArgumentException();
            GenerationConfig config = mapper.convertValue(data, GenerationConfig.class);
            if (config.schemaVersion != 1) throw new CodegenException("Unsupported schemaVersion; expected 1");
            if (config.datasource == null || config.tables == null || config.artifacts == null || config.templates == null || config.variables == null || config.output == null || config.output.directories == null)
                throw new CodegenException("Configuration sections cannot be null");
            if (config.tables.stream().anyMatch(table -> table == null || table.name == null || table.columns == null || table.columns.values().stream().anyMatch(java.util.Objects::isNull)) || config.templates.values().stream().anyMatch(java.util.Objects::isNull))
                throw new CodegenException("Invalid table or template entry");
            return config;
        } catch (CodegenException e) { throw e; }
        catch (RuntimeException e) { throw new CodegenException("Invalid YAML configuration or unknown property"); }
    }

    public String write(GenerationConfig config, boolean redactPassword) {
        Map<String, Object> data = mapper.convertValue(config, new TypeReference<>() { });
        if (redactPassword) {
            @SuppressWarnings("unchecked") Map<String, Object> database = (Map<String, Object>) data.get("datasource");
            String password = config.datasource.password;
            database.put("password", password != null && password.matches("\\$\\{[A-Za-z_][A-Za-z0-9_]*}") ? password : "");
            database.put("url", redactUrl(config.datasource.url));
        }
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        return new Yaml(options).dump(data);
    }
    private String redactUrl(String url) {
        if (url == null) return null;
        var properties = java.util.regex.Pattern.compile("([?&;(])([^?&;()=]+)=([^&;)]*)").matcher(url);
        String redacted = properties.replaceAll(match -> {
            String key;
            try { key = java.net.URLDecoder.decode(match.group(2), java.nio.charset.StandardCharsets.UTF_8); }
            catch (IllegalArgumentException e) { key = match.group(2); }
            String value = match.group(3);
            if (key.matches("(?i).*password[123]?") && !value.matches("\\$\\{[A-Za-z_][A-Za-z0-9_]*}")) value = "";
            return java.util.regex.Matcher.quoteReplacement(match.group(1) + match.group(2) + "=" + value);
        });
        return java.util.regex.Pattern.compile("(//[^/@:]+:)([^/@]*)(@)").matcher(redacted).replaceAll(match -> {
            String password = match.group(2);
            return java.util.regex.Matcher.quoteReplacement(match.group(1) + (password.matches("\\$\\{[A-Za-z_][A-Za-z0-9_]*}") ? password : "") + match.group(3));
        });
    }
}
