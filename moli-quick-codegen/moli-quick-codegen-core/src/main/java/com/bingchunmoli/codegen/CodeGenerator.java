package com.bingchunmoli.codegen;

import com.bingchunmoli.codegen.jdbc.*;
import com.bingchunmoli.codegen.model.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Single generation pipeline used by UI, CLI and Maven. No files are written by preview. */
public class CodeGenerator {
    public static final List<String> ARTIFACTS = List.of("entity", "controller", "service", "serviceImpl", "mapper", "xml");
    private final MetadataReader metadata;
    private final TemplateRenderer renderer;
    public CodeGenerator() { this(new MetadataReader(), new TemplateRenderer()); }
    public CodeGenerator(MetadataReader metadata, TemplateRenderer renderer) { this.metadata = metadata; this.renderer = renderer; }

    public List<TableMetadata> tables(GenerationConfig config, ConnectionProvider provider) {
        return metadata.read(provider, config.datasource, List.of()).tables();
    }
    public List<TableMetadata> columns(GenerationConfig config, ConnectionProvider provider, List<String> tables) {
        if (tables == null || tables.isEmpty() || tables.stream().anyMatch(name -> name == null || name.isBlank())) throw new CodegenException("Select at least one valid table");
        return metadata.read(provider, config.datasource, tables).tables();
    }
    public List<GeneratedFile> preview(GenerationConfig config, Path configDirectory, ConnectionProvider provider) {
        validate(config);
        var snapshot = metadata.read(provider, config.datasource, config.tables.stream().map(t -> t.name).toList());
        return preview(config, configDirectory, snapshot.tables(), snapshot.dialect());
    }
    public List<GeneratedFile> preview(GenerationConfig config, Path configDirectory, List<TableMetadata> tables, DatabaseDialect dialect) {
        validate(config);
        Map<String, GeneratedFile> files = new TreeMap<>();
        Map<String, TableMetadata> byName = new HashMap<>();
        for (TableMetadata table : tables) if (byName.putIfAbsent(table.name(), table) != null)
            throw new CodegenException("Ambiguous table metadata: " + table.name());
        Map<String, Object> firstContext = null;
        for (GenerationConfig.Table selected : config.tables) {
            TableMetadata table = byName.get(selected.name);
            if (table == null) throw new CodegenException("Table metadata missing: " + selected.name);
            Map<String, Object> context = context(config, selected, table, dialect);
            if (firstContext == null) firstContext = context;
            Set<String> artifacts = new LinkedHashSet<>(config.artifacts);
            config.templates.keySet().stream().filter(a -> !ARTIFACTS.contains(a) && !a.equals("pageResult") && !a.equals("uuidTypeHandler")).forEach(artifacts::add);
            for (String artifact : artifacts) add(files, config, configDirectory, artifact, context);
        }
        if (config.artifacts.stream().anyMatch(a -> Set.of("controller", "service", "serviceImpl").contains(a)))
            add(files, config, configDirectory, "pageResult", firstContext);
        if (tables.stream().flatMap(t -> t.columns().stream()).anyMatch(c -> c.typeName().equalsIgnoreCase("uuid")) ||
                config.tables.stream().flatMap(t -> t.columns.values().stream()).anyMatch(c -> "java.util.UUID".equals(c.javaType)))
            add(files, config, configDirectory, "uuidTypeHandler", firstContext);
        return FileOutputs.validate(List.copyOf(files.values()));
    }
    public Map<String, String> templates(String orm) {
        if (orm == null || !Set.of("mybatis", "mybatis-plus").contains(orm)) throw new CodegenException("Unknown ORM style");
        Map<String, String> templates = new LinkedHashMap<>();
        for (String artifact : ARTIFACTS) templates.put(artifact, renderer.builtin(orm, artifact));
        templates.put("pageResult", renderer.builtin(orm, "pageResult"));
        return templates;
    }

    private void add(Map<String, GeneratedFile> files, GenerationConfig config, Path base, String artifact, Map<String, Object> context) {
        GenerationConfig.Template override = config.templates.get(artifact);
        String source;
        if (override != null && override.content != null) source = override.content;
        else if (override != null && override.file != null) {
            try { source = Files.readString(base.resolve(override.file).normalize()); }
            catch (IOException e) { throw new CodegenException("Cannot read template file for " + artifact); }
        } else source = renderer.builtin(config.orm, artifact);
        String path = override != null && override.outputPath != null ? renderer.render(artifact + " outputPath", override.outputPath, context) : defaultPath(config, artifact, context);
        path = FileOutputs.relative(path);
        String name = context.get("className") + "/" + artifact;
        GeneratedFile file = new GeneratedFile(path, renderer.render(name, source, context));
        if (files.putIfAbsent(path, file) != null) throw new CodegenException("Duplicate generated path: " + path);
    }
    private String defaultPath(GenerationConfig config, String artifact, Map<String, Object> context) {
        String name = context.get("className").toString();
        String directory = config.output.directories.getOrDefault(artifact, artifact.equals("xml") ? config.output.xmlDirectory : config.output.javaDirectory);
        if (artifact.equals("xml")) return directory + "/" + name + "Mapper.xml";
        String suffix = switch (artifact) {
            case "entity" -> "entity/" + name;
            case "controller" -> "controller/" + name + "Controller";
            case "service" -> "service/" + name + "Service";
            case "serviceImpl" -> "service/impl/" + name + "ServiceImpl";
            case "mapper" -> "mapper/" + name + "Mapper";
            case "pageResult" -> "model/PageResult";
            case "uuidTypeHandler" -> "model/UuidTypeHandler";
            default -> throw new CodegenException("Custom template requires outputPath: " + artifact);
        };
        return directory + "/" + config.basePackage.replace('.', '/') + "/" + suffix + ".java";
    }

    private Map<String, Object> context(GenerationConfig config, GenerationConfig.Table selected, TableMetadata table, DatabaseDialect dialect) {
        String raw = table.name().startsWith(config.tablePrefix) ? table.name().substring(config.tablePrefix.length()) : table.name();
        String className = selected.className == null ? Names.camel(raw, true) : Names.identifier(selected.className, table.name());
        List<Map<String, Object>> columns = new ArrayList<>();
        Set<String> properties = new HashSet<>();
        Set<String> accessors = new HashSet<>();
        for (String column : selected.columns.keySet())
            if (table.columns().stream().noneMatch(c -> c.name().equals(column))) throw new CodegenException("Unknown column override: " + table.name() + "." + column);
        for (ColumnMetadata column : table.columns()) {
            String location = table.name() + "." + column.name();
            GenerationConfig.Column override = selected.columns.getOrDefault(column.name(), new GenerationConfig.Column());
            String property = override.property == null ? Names.camel(column.name(), false) : Names.identifier(override.property, location);
            if (config.orm.equals("mybatis") && config.artifacts.contains("xml")) Names.ognlProperty(property, location);
            if (!properties.add(property)) throw new CodegenException("Duplicate property at " + location);
            if (!accessors.add(Names.beanSuffix(property))) throw new CodegenException("Duplicate JavaBean accessor at " + location);
            if (Names.beanSuffix(property).equals("Class")) throw new CodegenException("Property conflicts with Object.getClass at " + location);
            if (override.queryType == null || !Set.of("EQ", "LIKE").contains(override.queryType)) throw new CodegenException("queryType must be EQ or LIKE at " + location);
            Map<String, String> type = new HashMap<>(TypeMappings.resolve(column, override, location));
            if (type.get("javaType").equals("java.util.UUID") && type.get("typeHandler").isEmpty()) type.put("typeHandler", config.basePackage + ".model.UuidTypeHandler");
            if (column.primaryKey() && !column.autoIncrement() && !override.insert) throw new CodegenException("Non-auto primary key must be insertable: " + location);
            if (override.queryType.equals("LIKE") && !type.get("javaType").equals("java.lang.String")) throw new CodegenException("LIKE requires String at " + location);
            Map<String, Object> item = new LinkedHashMap<>(type);
            item.put("name", column.name()); item.put("property", property);
            item.put("getter", "get" + Names.beanSuffix(property));
            item.put("setter", "set" + Names.beanSuffix(property));
            item.put("manualAccessors", !config.lombok || !Names.beanSuffix(property).equals(Character.toUpperCase(property.charAt(0)) + property.substring(1)));
            item.put("comment", Names.comment(column.comment())); item.put("commentLiteral", Names.literal(Names.comment(column.comment())));
            item.put("sql", dialect.quote(column.name())); item.put("sqlLiteral", Names.literal(dialect.quote(column.name())));
            item.put("pk", column.primaryKey()); item.put("auto", column.autoIncrement()); item.put("generated", column.generated());
            item.put("nullable", column.nullable()); item.put("insert", override.insert && !column.autoIncrement() && !column.generated());
            item.put("update", override.update && !column.primaryKey() && !column.autoIncrement() && !column.generated());
            item.put("query", override.query); item.put("queryType", override.queryType);
            String handler = type.get("typeHandler").isEmpty() ? "" : ",typeHandler=" + type.get("typeHandler");
            item.put("bind", "#{" + property + ",jdbcType=" + type.get("jdbcType") + handler + "}");
            item.put("filterBind", "#{filter." + property + ",jdbcType=" + type.get("jdbcType") + handler + "}");
            item.put("keyBind", "#{id,jdbcType=" + type.get("jdbcType") + handler + "}");
            columns.add(item);
        }
        if (columns.isEmpty()) throw new CodegenException("No columns found: " + table.name());
        List<Map<String, Object>> keys = columns.stream().filter(c -> (boolean)c.get("pk")).toList();
        if (config.artifacts.stream().anyMatch(a -> !a.equals("entity")) && keys.size() != 1)
            throw new CodegenException("CRUD requires exactly one primary key: " + table.name());
        List<Map<String, Object>> updates = columns.stream().filter(c -> (boolean)c.get("update")).toList();
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("basePackage", config.basePackage); context.put("packagePath", config.basePackage.replace('.', '/'));
        context.put("className", className); context.put("entityVariable", Character.toLowerCase(className.charAt(0)) + className.substring(1));
        context.put("entityType", config.basePackage + ".entity." + className);
        context.put("route", Character.toLowerCase(className.charAt(0)) + className.substring(1)); context.put("author", Names.comment(config.author));
        context.put("comment", Names.comment(table.comment())); context.put("tableName", table.name());
        context.put("qualifiedTable", dialect.qualifiedName(table)); context.put("tableLiteral", Names.literal(dialect.quote(table.name())));
        String namespace = dialect.id().equals("mysql") ? table.catalog() : table.schema();
        context.put("schemaLiteral", namespace == null ? "" : Names.literal(dialect.quote(namespace)));
        context.put("dialect", dialect.id()); context.put("orm", config.orm); context.put("columns", columns);
        context.put("insertColumns", columns.stream().filter(c -> (boolean)c.get("insert")).toList());
        context.put("updateColumns", updates); context.put("queryColumns", columns.stream().filter(c -> (boolean)c.get("query")).toList());
        context.put("pk", keys.isEmpty() ? Map.of() : keys.get(0));
        context.put("singleKey", keys.size() == 1);
        context.put("lombok", config.lombok); context.put("openApi", config.openApi); context.put("quick", config.responseStyle.equals("quick"));
        // Deliberately omit datasource, output paths and configuration objects.
        context.put("variables", config.variables);
        return context;
    }
    private void validate(GenerationConfig config) {
        if (config == null || config.schemaVersion != 1) throw new CodegenException("schemaVersion must be 1");
        if (config.orm == null || !Set.of("mybatis", "mybatis-plus").contains(config.orm)) throw new CodegenException("orm must be mybatis or mybatis-plus");
        if (config.responseStyle == null || !Set.of("generic", "quick").contains(config.responseStyle)) throw new CodegenException("responseStyle must be generic or quick");
        if (config.basePackage == null || !javax.lang.model.SourceVersion.isName(config.basePackage)) throw new CodegenException("Invalid basePackage");
        if (config.tables == null || config.tables.isEmpty()) throw new CodegenException("Select at least one table");
        if (config.artifacts == null || config.artifacts.isEmpty() || !ARTIFACTS.containsAll(config.artifacts)) throw new CodegenException("Invalid or empty artifacts list");
        if (config.datasource == null || config.output == null || config.templates == null || config.variables == null || config.tablePrefix == null)
            throw new CodegenException("Configuration sections cannot be null");
        if (config.output.directory == null || config.output.directories == null || config.output.javaDirectory == null || config.output.xmlDirectory == null)
            throw new CodegenException("Output settings cannot be null");
        if (config.output.directories.values().stream().anyMatch(Objects::isNull)) throw new CodegenException("Output directory overrides cannot be null");
        Set<String> names = new HashSet<>();
        for (GenerationConfig.Table table : config.tables) {
            if (table == null || table.name == null || table.name.isBlank() || table.columns == null || !names.add(table.name)) throw new CodegenException("Invalid or duplicate table selection");
            if (table.columns.values().stream().anyMatch(Objects::isNull)) throw new CodegenException("Column overrides cannot be null: " + table.name);
        }
        for (var entry : config.templates.entrySet()) {
            var template = entry.getValue();
            if (!entry.getKey().matches("[A-Za-z][A-Za-z0-9]*") || template == null) throw new CodegenException("Invalid template entry");
            if (template.content != null && template.file != null) throw new CodegenException("Template must use either content or file: " + entry.getKey());
        }
    }
}
