package com.bingchunmoli.codegen;

import com.bingchunmoli.codegen.jdbc.DatabaseDialect;
import com.bingchunmoli.codegen.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.sql.Types;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ConfigurationAndTemplatesTest {
    @TempDir Path directory;
    private final CodeGenerator generator = new CodeGenerator();
    @Test void yamlRoundTripPreservesTemplatesAndRedactsOnlyPlaintextPassword() {
        var config = GeneratedCodeSupport.config("mybatis"); config.datasource.password = "do-not-export";
        var custom = new GenerationConfig.Template(); custom.content = "public class ${className}Dto {}\n"; custom.outputPath = "dto/${className}Dto.java";
        config.templates.put("dto", custom);
        var codec = new ConfigCodec();
        String text = codec.write(config, true);
        assertThat(text).doesNotContain("do-not-export");
        var roundTrip = codec.read(text);
        assertThat(roundTrip.templates.get("dto").content).isEqualTo(custom.content);
        assertThat(render(roundTrip)).isEqualTo(render(config));
        config.datasource.password = "${DB_PASSWORD}";
        assertThat(codec.write(config, true)).contains("${DB_PASSWORD}");
    }
    @Test void rejectsUnknownConfigPropertiesDuplicateKeysAndYamlTags() {
        var codec = new ConfigCodec();
        for (String yaml : List.of("schemaVersion: 2", "unknown: true", "schemaVersion: 1\nschemaVersion: 1", "!!java.lang.ProcessBuilder {}", "tables: null", "templates: {entity: null}", "tables: [null]", "tables: [{name: orders, columns: {id: null}}]"))
            assertThatThrownBy(() -> codec.read(yaml)).isInstanceOf(CodegenException.class);
    }
    @Test void overridesFileTemplateAndAddsAnExtraOutput() throws Exception {
        var config = GeneratedCodeSupport.config("mybatis");
        Files.writeString(directory.resolve("entity.ftl"), "entity ${className} ${variables.marker}");
        var override = new GenerationConfig.Template(); override.file = "entity.ftl"; config.templates.put("entity", override);
        var custom = new GenerationConfig.Template(); custom.content = "dto ${className}"; custom.outputPath = "extra/${className}.txt";
        config.templates.put("dto", custom); config.variables.put("marker", "ok");
        assertThat(render(config)).anySatisfy(file -> assertThat(file.content()).isEqualTo("entity Order ok"));
        assertThat(render(config)).anySatisfy(file -> assertThat(file.relativePath()).isEqualTo("extra/Order.txt"));
        config.templates.put("duplicate", custom);
        assertThatThrownBy(() -> render(config)).hasMessageContaining("Duplicate generated path");
    }
    @Test void templatesCannotSeeCredentialsOrInstantiateJavaClasses() {
        var config = GeneratedCodeSupport.config("mybatis"); config.datasource.password = "secret-value";
        var override = new GenerationConfig.Template(); config.templates.put("entity", override);
        for (String source : List.of("${datasource.password}", "${\"freemarker.template.utility.Execute\"?new()(\"cmd\")}", "${columns?api}")) {
            override.content = source;
            assertThatThrownBy(() -> render(config)).isInstanceOf(CodegenException.class).hasMessageNotContaining("secret-value");
        }
    }
    @Test void onlyEntityCanBeGeneratedWithoutSingleColumnKey() {
        var config = GeneratedCodeSupport.config("mybatis");
        var noKey = new TableMetadata("demo", null, "order", "", List.of(new ColumnMetadata("name", "", 1, Types.VARCHAR, "VARCHAR", 20, 0, true, null, false, false, false)));
        assertThatThrownBy(() -> generator.preview(config, directory, List.of(noKey), DatabaseDialect.forProduct("MySQL"))).hasMessageContaining("primary key");
        config.artifacts = List.of("entity");
        assertThat(generator.preview(config, directory, List.of(noKey), DatabaseDialect.forProduct("MySQL"))).hasSize(1);
    }
    @Test void unknownDatabaseTypeRequiresExplicitHandler() {
        var config = GeneratedCodeSupport.config("mybatis"); config.artifacts = List.of("entity");
        var table = new TableMetadata(null, "public", "order", "", List.of(new ColumnMetadata("payload", "", 1, Types.OTHER, "jsonb", 0, 0, true, null, false, false, false)));
        assertThatThrownBy(() -> generator.preview(config, directory, List.of(table), DatabaseDialect.forProduct("PostgreSQL"))).hasMessageContaining("typeHandler");
        var override = new GenerationConfig.Column(); override.javaType = "String"; override.typeHandler = "example.JsonHandler"; config.tables.get(0).columns.put("payload", override);
        assertThat(generator.preview(config, directory, List.of(table), DatabaseDialect.forProduct("PostgreSQL"))).hasSize(1);
    }
    private List<GeneratedFile> render(GenerationConfig config) { return generator.preview(config, directory, List.of(GeneratedCodeSupport.table("mysql")), DatabaseDialect.forProduct("MySQL")); }
    @Test void embeddedJdbcPasswordsAreRedactedOnExport() {
        var config = GeneratedCodeSupport.config("mybatis"); var codec = new ConfigCodec();
        for (String url : List.of("jdbc:mysql://localhost/db?password=embedded-secret&useSSL=true", "jdbc:postgresql://localhost/db?%70assword=embedded-secret", "jdbc:mysql://user:embedded-secret@localhost/db", "jdbc:mysql://address=(host=localhost)(password=embedded-secret)/db")) {
            config.datasource.url = url;
            assertThat(codec.write(config, true)).doesNotContain("embedded-secret");
            assertThat(codec.write(config, false)).contains("embedded-secret");
        }
        config.datasource.url = "jdbc:mysql://localhost/db?password=${DB_PASSWORD}&useSSL=true";
        assertThat(codec.write(config, true)).contains("${DB_PASSWORD}").contains("useSSL=true");
    }
    @Test void fullyQualifiedStringOverridesSupportLikeQueries() {
        var config = GeneratedCodeSupport.config("mybatis");
        var column = new GenerationConfig.Column(); column.javaType = "java.lang.String"; column.queryType = "LIKE";
        config.tables.get(0).columns.put("display_name", column);
        assertThat(render(config)).hasSize(7);
    }
    @Test void invalidJavaTypesAndObjectAccessorsFailBeforeRendering() {
        var config = GeneratedCodeSupport.config("mybatis"); var column = new GenerationConfig.Column();
        config.tables.get(0).columns.put("display_name", column);
        for (String type : List.of("void", "com.class.Payload")) {
            column.javaType = type;
            assertThatThrownBy(() -> render(config)).isInstanceOf(CodegenException.class).hasMessageContaining("Java type");
        }
        column.javaType = "String"; column.property = "Class";
        assertThatThrownBy(() -> render(config)).isInstanceOf(CodegenException.class).hasMessageContaining("getClass");
    }
}
