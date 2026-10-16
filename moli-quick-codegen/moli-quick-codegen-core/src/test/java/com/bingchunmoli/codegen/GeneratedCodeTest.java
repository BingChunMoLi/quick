package com.bingchunmoli.codegen;

import com.bingchunmoli.codegen.jdbc.DatabaseDialect;
import com.bingchunmoli.codegen.model.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.io.StringReader;
import java.nio.file.Path;
import java.sql.Types;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class GeneratedCodeTest {
    @TempDir Path directory;
    @ParameterizedTest @CsvSource({"mybatis,mysql", "mybatis,postgresql", "mybatis-plus,mysql", "mybatis-plus,postgresql"})
    void compilesAllGeneratedJavaAndLoadsXml(String orm, String dialect) throws Exception {
        GenerationConfig config = GeneratedCodeSupport.config(orm);
        GenerationConfig.Column override = new GenerationConfig.Column(); override.queryType = "LIKE";
        config.tables.get(0).columns.put("display_name", override);
        var files = new CodeGenerator().preview(config, directory, List.of(GeneratedCodeSupport.table(dialect)), DatabaseDialect.forProduct(dialect));
        assertThat(files).hasSize(7);
        assertThat(files).isEqualTo(new CodeGenerator().preview(config, directory, List.of(GeneratedCodeSupport.table(dialect)), DatabaseDialect.forProduct(dialect)));
        load(files, directory);
        String service = files.stream().filter(f -> f.relativePath().endsWith("ServiceImpl.java")).findFirst().orElseThrow().content();
        assertThat(service).doesNotContain("entity.getGeneratedValue() == null");
        assertThat(service).contains("Math.multiplyExact");
    }
    @Test void uuidKeyHasAUsableHandlerAndActualParameterType() throws Exception {
        var config = GeneratedCodeSupport.config("mybatis");
        var table = new TableMetadata(null, "public", "order", "", List.of(new ColumnMetadata("id", "", 1, Types.OTHER, "uuid", 0, 0, false, null, true, false, false),
                new ColumnMetadata("name", "", 2, Types.VARCHAR, "varchar", 50, 0, true, null, false, false, false)));
        var files = new CodeGenerator().preview(config, directory, List.of(table), DatabaseDialect.forProduct("PostgreSQL"));
        assertThat(files).anySatisfy(f -> assertThat(f.relativePath()).endsWith("UuidTypeHandler.java"));
        assertThat(files.stream().filter(f -> f.relativePath().endsWith("Controller.java")).findFirst().orElseThrow().content()).contains("java.util.UUID id");
        load(files, directory);
    }
    @Test void identityOnlyTableHasValidMapperXml() throws Exception {
        var config = GeneratedCodeSupport.config("mybatis");
        var table = GeneratedCodeSupport.table("mysql");
        var identityOnly = new TableMetadata(table.catalog(), null, table.name(), "", List.of(table.columns().get(0)));
        var files = new CodeGenerator().preview(config, directory, List.of(identityOnly), DatabaseDialect.forProduct("MySQL"));
        load(files, directory);
    }
    @Test void batchGeneratesOneSharedPageTypeAndSupportsReservedSqlTableNames() throws Exception {
        var config = GeneratedCodeSupport.config("mybatis");
        config.tables.add(new GenerationConfig.Table("class"));
        var first = GeneratedCodeSupport.table("mysql");
        var second = new TableMetadata(first.catalog(), first.schema(), "class", "", first.columns());
        var files = new CodeGenerator().preview(config, directory, List.of(first, second), DatabaseDialect.forProduct("MySQL"));
        assertThat(files).hasSize(13);
        assertThat(files.stream().filter(f -> f.relativePath().endsWith("PageResult.java"))).hasSize(1);
        load(files, directory);
    }
    private void load(List<GeneratedFile> files, Path root) throws Exception {
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try (var loader = GeneratedCodeSupport.compile(files, root)) {
            Thread.currentThread().setContextClassLoader(loader);
            Configuration configuration = new Configuration();
            for (GeneratedFile file : files) if (file.relativePath().endsWith(".xml"))
                new XMLMapperBuilder(new StringReader(file.content()), configuration, file.relativePath(), configuration.getSqlFragments()).parse();
        } finally { Thread.currentThread().setContextClassLoader(original); }
    }
    @ParameterizedTest @CsvSource({"mybatis", "mybatis-plus"})
    void entityNamesCannotShadowFrameworkAndJavaTypes(String orm) throws Exception {
        var config = GeneratedCodeSupport.config(orm);
        config.tables.clear();
        var sample = GeneratedCodeSupport.table("mysql");
        List<TableMetadata> tables = new ArrayList<>();
        for (String name : List.of("PageResult", "List", "String", "Long", "Objects", "TableName", "FieldStrategy")) {
            var selection = new GenerationConfig.Table(name.toLowerCase(Locale.ROOT)); selection.className = name; config.tables.add(selection);
            tables.add(new TableMetadata(sample.catalog(), sample.schema(), selection.name, "", sample.columns()));
        }
        var files = new CodeGenerator().preview(config, directory, tables, DatabaseDialect.forProduct("MySQL"));
        try (var loader = GeneratedCodeSupport.compile(files, directory)) {
            assertThat(loader.loadClass("example.generated.entity.String").getDeclaredField("displayName").getType()).isEqualTo(String.class);
            assertThat(loader.loadClass("example.generated.entity.Long").getDeclaredField("id").getType()).isEqualTo(Long.class);
        }
    }
    @ParameterizedTest @CsvSource({"mybatis", "mybatis-plus"})
    void lombokUsesTheSameJavaBeanAccessorNamesAsTemplates(String orm) throws Exception {
        var config = GeneratedCodeSupport.config(orm); config.lombok = true;
        var override = new GenerationConfig.Column(); override.property = "eMail";
        config.tables.get(0).columns.put("display_name", override);
        var files = new CodeGenerator().preview(config, directory, List.of(GeneratedCodeSupport.table("mysql")), DatabaseDialect.forProduct("MySQL"));
        try (var ignored = GeneratedCodeSupport.compile(files, directory, true)) { }
    }
}
