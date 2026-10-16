package com.bingchunmoli.codegen;

import com.bingchunmoli.codegen.jdbc.*;
import com.bingchunmoli.codegen.model.*;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Opt-in integration tests. Uses real databases and always drops its uniquely named test tables. */
class DatabaseGenerationIT {
    @TempDir Path directory;
    @Test void postgresSchemaWildcardsCannotSelectAnotherNamespace() throws Exception {
        String url = System.getenv("CODEGEN_TEST_POSTGRESQL_URL"); assertThat(url).isNotBlank();
        var config = new GenerationConfig(); config.datasource.url = url;
        config.datasource.username = Objects.requireNonNullElse(System.getenv("CODEGEN_TEST_POSTGRESQL_USERNAME"), "postgres");
        config.datasource.password = Objects.requireNonNullElse(System.getenv("CODEGEN_TEST_POSTGRESQL_PASSWORD"), "");
        String token = "cg" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String selected = token + "_%", neighbor = token + "AZ";
        config.datasource.schema = selected;
        var provider = JdbcConnections.of(config.datasource);
        var dialect = DatabaseDialect.forProduct("PostgreSQL");
        boolean createdSelected = false, createdNeighbor = false;
        try (var connection = provider.open(); var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + dialect.quote(selected)); createdSelected = true;
            statement.execute("CREATE SCHEMA " + dialect.quote(neighbor)); createdNeighbor = true;
            statement.execute("CREATE TABLE " + dialect.quote(selected) + ".orders (id BIGINT PRIMARY KEY, selected_value VARCHAR(20))");
            statement.execute("CREATE TABLE " + dialect.quote(neighbor) + ".orders (id BIGINT PRIMARY KEY, neighbor_value VARCHAR(20))");
            var generator = new CodeGenerator();
            assertThat(generator.tables(config, provider)).hasSize(1).allMatch(t -> t.schema().equals(selected));
            var table = generator.columns(config, provider, List.of("orders")).get(0);
            assertThat(table.columns()).extracting(ColumnMetadata::name).containsExactly("id", "selected_value");
        } finally {
            try (var connection = provider.open(); var statement = connection.createStatement()) {
                if (createdSelected) statement.execute("DROP SCHEMA " + dialect.quote(selected) + " CASCADE");
                if (createdNeighbor) statement.execute("DROP SCHEMA " + dialect.quote(neighbor) + " CASCADE");
            }
        }
    }
    @ParameterizedTest @CsvSource({"MYSQL,mybatis", "MYSQL,mybatis-plus", "POSTGRESQL,mybatis", "POSTGRESQL,mybatis-plus"})
    void generatedCrudAndPaginationWorkAgainstRealDatabase(String database, String orm) throws Exception {
        String prefix = "CODEGEN_TEST_" + database + "_";
        String url = System.getenv(prefix + "URL");
        assertThat(url).as("Set %sURL to run database-it", prefix).isNotBlank();
        var config = new GenerationConfig();
        config.datasource.url = url;
        config.datasource.username = Objects.requireNonNullElse(System.getenv(prefix + "USERNAME"), database.equals("MYSQL") ? "root" : "postgres");
        config.datasource.password = Objects.requireNonNullElse(System.getenv(prefix + "PASSWORD"), "");
        config.datasource.schema = database.equals("POSTGRESQL") ? Objects.requireNonNullElse(System.getenv(prefix + "SCHEMA"), "public") : null;
        ConnectionProvider provider = JdbcConnections.of(config.datasource);
        DatabaseDialect dialect = DatabaseDialect.forProduct(database.equals("MYSQL") ? "MySQL" : "PostgreSQL");
        String tableName = "codegen_it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        config.orm = orm; config.basePackage = "example.generated";
        var selected = new GenerationConfig.Table(tableName); selected.className = "Sample";
        var label = new GenerationConfig.Column(); label.property = "label"; label.queryType = "LIKE"; selected.columns.put("order", label);
        config.tables.add(selected);
        String namespace;
        try (var connection = provider.open()) { namespace = database.equals("MYSQL") ? connection.getCatalog() : config.datasource.schema; }
        String qualified = dialect.quote(namespace) + "." + dialect.quote(tableName);
        String identity = database.equals("MYSQL") ? "BIGINT AUTO_INCREMENT" : "BIGINT GENERATED BY DEFAULT AS IDENTITY";
        String ddl = "CREATE TABLE " + qualified + " (id " + identity + " PRIMARY KEY, " + dialect.quote("order") + " VARCHAR(100), amount DECIMAL(18,2), quantity INT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, doubled INT GENERATED ALWAYS AS (quantity * 2) STORED)";
        try (var connection = provider.open(); var statement = connection.createStatement()) { statement.execute(ddl); }
        try {
            CodeGenerator generator = new CodeGenerator();
            var metadata = generator.columns(config, provider, List.of(tableName)).get(0);
            assertThat(metadata.columns()).anyMatch(c -> c.primaryKey() && c.autoIncrement());
            assertThat(metadata.columns()).anyMatch(c -> c.name().equals("doubled") && c.generated());
            var files = generator.preview(config, directory, provider);
            executeCrud(config, files, tableName);
        } finally { try (var connection = provider.open(); var statement = connection.createStatement()) { statement.execute("DROP TABLE " + qualified); } }
    }

    private void executeCrud(GenerationConfig config, List<GeneratedFile> files, String table) throws Exception {
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try (var loader = GeneratedCodeSupport.compile(files, directory)) {
            Thread.currentThread().setContextClassLoader(loader);
            Configuration configuration = config.orm.equals("mybatis-plus") ? new MybatisConfiguration() : new Configuration();
            var source = new UnpooledDataSource(config.datasource.url.startsWith("jdbc:mysql:") ? "com.mysql.cj.jdbc.Driver" : "org.postgresql.Driver", config.datasource.url, config.datasource.username, config.datasource.password);
            configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), source));
            if (files.stream().anyMatch(f -> f.relativePath().endsWith("UuidTypeHandler.java")))
                configuration.getTypeHandlerRegistry().register(loader.loadClass("example.generated.model.UuidTypeHandler"));
            if (config.orm.equals("mybatis-plus")) {
                var interceptor = new MybatisPlusInterceptor(); interceptor.addInnerInterceptor(new PaginationInnerInterceptor()); configuration.addInterceptor(interceptor);
            }
            for (GeneratedFile file : files) if (file.relativePath().endsWith(".xml"))
                new XMLMapperBuilder(new StringReader(file.content()), configuration, file.relativePath(), configuration.getSqlFragments()).parse();
            SqlSessionFactory factory = config.orm.equals("mybatis-plus") ? new MybatisSqlSessionFactoryBuilder().build(configuration) : new SqlSessionFactoryBuilder().build(configuration);
            try (SqlSession session = factory.openSession(true)) {
                Class<?> entityType = loader.loadClass("example.generated.entity.Sample");
                Class<?> mapperType = loader.loadClass("example.generated.mapper.SampleMapper");
                Class<?> serviceType = loader.loadClass("example.generated.service.impl.SampleServiceImpl");
                Object mapper = session.getMapper(mapperType);
                Object service;
                if (config.orm.equals("mybatis")) service = serviceType.getConstructor(mapperType).newInstance(mapper);
                else {
                    service = serviceType.getConstructor().newInstance();
                    Class<?> owner = serviceType;
                    while (owner != Object.class) {
                        try { Field field = owner.getDeclaredField("baseMapper"); field.setAccessible(true); field.set(service, mapper); break; }
                        catch (NoSuchFieldException e) { owner = owner.getSuperclass(); }
                    }
                }
                Object entity = entityType.getConstructor().newInstance();
                Class<?> keyType = entityType.getMethod("getId").getReturnType();
                if (keyType == UUID.class) entityType.getMethod("setId", keyType).invoke(entity, UUID.randomUUID());
                if (keyType == String.class) entityType.getMethod("setId", keyType).invoke(entity, "key-001");
                entityType.getMethod("setLabel", String.class).invoke(entity, "初始订单");
                entityType.getMethod("setAmount", BigDecimal.class).invoke(entity, new BigDecimal("123.45"));
                entityType.getMethod("setQuantity", Integer.class).invoke(entity, 2);
                assertThat(serviceType.getMethod("create", entityType).invoke(service, entity)).isEqualTo(true);
                Object id = entityType.getMethod("getId").invoke(entity); assertThat(id).isNotNull();
                Object found = serviceType.getMethod("findById", keyType).invoke(service, id);
                assertThat(entityType.getMethod("getAmount").invoke(found)).isEqualTo(new BigDecimal("123.45"));
                assertThat(entityType.getMethod("getDoubled").invoke(found)).isEqualTo(4);
                Object filter = entityType.getConstructor().newInstance(); entityType.getMethod("setLabel", String.class).invoke(filter, "订单");
                Object page = serviceType.getMethod("findPage", entityType, long.class, long.class).invoke(service, filter, 1L, 10L);
                assertThat(page.getClass().getMethod("total").invoke(page)).isEqualTo(1L);
                assertThat((List<?>)page.getClass().getMethod("records").invoke(page)).hasSize(1);
                Object patch = entityType.getConstructor().newInstance(); entityType.getMethod("setId", keyType).invoke(patch, id);
                assertThat(serviceType.getMethod("update", entityType).invoke(service, patch)).isEqualTo(false);
                entityType.getMethod("setLabel", String.class).invoke(patch, "修改订单");
                assertThat(serviceType.getMethod("update", entityType).invoke(service, patch)).isEqualTo(true);
                assertThat(serviceType.getMethod("delete", keyType).invoke(service, id)).isEqualTo(true);
                assertThat(serviceType.getMethod("findById", keyType).invoke(service, id)).isNull();
            }
        } finally { Thread.currentThread().setContextClassLoader(original); }
    }
    @ParameterizedTest @CsvSource({"MYSQL,mybatis,string", "MYSQL,mybatis-plus,string", "POSTGRESQL,mybatis,string", "POSTGRESQL,mybatis-plus,string", "MYSQL,mybatis,uuid", "MYSQL,mybatis-plus,uuid", "POSTGRESQL,mybatis,uuid", "POSTGRESQL,mybatis-plus,uuid"})
    void stringAndUuidKeysRoundTrip(String database, String orm, String key) throws Exception {
        String prefix = "CODEGEN_TEST_" + database + "_";
        var config = new GenerationConfig(); config.orm = orm; config.basePackage = "example.generated";
        config.datasource.url = System.getenv(prefix + "URL");
        assertThat(config.datasource.url).as("Set %sURL", prefix).isNotBlank();
        config.datasource.username = Objects.requireNonNullElse(System.getenv(prefix + "USERNAME"), database.equals("MYSQL") ? "root" : "postgres");
        config.datasource.password = Objects.requireNonNullElse(System.getenv(prefix + "PASSWORD"), "");
        config.datasource.schema = database.equals("POSTGRESQL") ? Objects.requireNonNullElse(System.getenv(prefix + "SCHEMA"), "public") : null;
        var provider = JdbcConnections.of(config.datasource);
        var dialect = DatabaseDialect.forProduct(database.equals("MYSQL") ? "MySQL" : "PostgreSQL");
        String tableName = "codegen_key_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        var selected = new GenerationConfig.Table(tableName); selected.className = "Sample";
        var label = new GenerationConfig.Column(); label.property = "label"; label.queryType = "LIKE"; selected.columns.put("order", label);
        if (key.equals("uuid") && database.equals("MYSQL")) { var id = new GenerationConfig.Column(); id.javaType = "java.util.UUID"; selected.columns.put("id", id); }
        config.tables.add(selected);
        String namespace;
        try (var connection = provider.open()) { namespace = database.equals("MYSQL") ? connection.getCatalog() : config.datasource.schema; }
        String qualified = dialect.quote(namespace) + "." + dialect.quote(tableName);
        String idType = key.equals("uuid") && database.equals("POSTGRESQL") ? "UUID" : "VARCHAR(36)";
        String ddl = "CREATE TABLE " + qualified + " (id " + idType + " PRIMARY KEY, " + dialect.quote("order") + " VARCHAR(100), amount DECIMAL(18,2), quantity INT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, doubled INT GENERATED ALWAYS AS (quantity * 2) STORED)";
        try (var connection = provider.open(); var statement = connection.createStatement()) { statement.execute(ddl); }
        try { executeCrud(config, new CodeGenerator().preview(config, directory, provider), tableName); }
        finally { try (var connection = provider.open(); var statement = connection.createStatement()) { statement.execute("DROP TABLE " + qualified); } }
    }
}
