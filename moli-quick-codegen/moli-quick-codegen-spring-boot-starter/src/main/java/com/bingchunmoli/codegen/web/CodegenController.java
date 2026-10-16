package com.bingchunmoli.codegen.web;

import com.bingchunmoli.codegen.*;
import com.bingchunmoli.codegen.jdbc.*;
import com.bingchunmoli.codegen.model.*;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.*;

@RestController
@RequestMapping("/moli-codegen/api")
public class CodegenController {
    private final CodeGenerator generator;
    private final ListableBeanFactory beans;
    public CodegenController(CodeGenerator generator, ListableBeanFactory beans) { this.generator = generator; this.beans = beans; }
    public record PreviewResult(List<GeneratedFile> files, List<String> diagnostics) { }
    @GetMapping("/datasources") public List<String> datasources() { return Arrays.stream(beans.getBeanNamesForType(DataSource.class)).sorted().toList(); }
    @PostMapping("/connection") public Map<String, String> connection(@RequestBody GenerationConfig config) {
        try (var connection = provider(config).open()) {
            return Map.of("database", DatabaseDialect.forProduct(connection.getMetaData().getDatabaseProductName()).id());
        } catch (SQLException e) { throw new CodegenException("Connection failed (SQLState " + e.getSQLState() + ")"); }
    }
    @PostMapping("/tables") public List<TableMetadata> tables(@RequestBody GenerationConfig config) { return generator.tables(config, provider(config)); }
    @PostMapping("/columns") public List<TableMetadata> columns(@RequestBody GenerationConfig config) {
        if (config.tables == null || config.tables.stream().anyMatch(t -> t == null || t.name == null || t.name.isBlank())) throw new CodegenException("Select valid tables first");
        return generator.columns(config, provider(config), config.tables.stream().map(t -> t.name).toList());
    }
    @GetMapping("/templates") public Map<String, String> templates(@RequestParam(name = "orm", defaultValue = "mybatis") String orm) { return generator.templates(orm); }
    @PostMapping("/preview") public PreviewResult preview(@RequestBody GenerationConfig config) { return new PreviewResult(render(config), List.of()); }
    @PostMapping("/export") public ResponseEntity<byte[]> export(@RequestBody GenerationConfig config) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=codegen.zip").body(FileOutputs.zip(render(config)));
    }
    @PostMapping(value = "/config/import", consumes = {"text/plain", "application/yaml"}) public GenerationConfig importConfig(@RequestBody String yaml) { return new ConfigCodec().read(yaml); }
    @PostMapping(value = "/config/export", produces = "application/yaml;charset=UTF-8") public String exportConfig(@RequestBody GenerationConfig config) { return new ConfigCodec().write(config, true); }

    private List<GeneratedFile> render(GenerationConfig config) {
        if (config.templates != null && config.templates.values().stream().anyMatch(t -> t != null && t.file != null))
            throw new CodegenException("Web templates must use inline content; external files are available in CLI/Maven only");
        return generator.preview(config, Path.of("."), provider(config));
    }
    private ConnectionProvider provider(GenerationConfig config) {
        if (config == null || config.datasource == null) throw new CodegenException("datasource is required");
        String beanName = config.datasource.beanName;
        if (beanName == null || beanName.isBlank()) return JdbcConnections.of(config.datasource);
        if (!beans.containsBean(beanName) || !beans.isTypeMatch(beanName, DataSource.class)) throw new CodegenException("Named DataSource not found");
        return beans.getBean(beanName, DataSource.class)::getConnection;
    }
}
