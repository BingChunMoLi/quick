package com.bingchunmoli.codegen;

import com.bingchunmoli.codegen.model.*;
import java.io.StringWriter;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.sql.Types;
import java.util.*;
import javax.tools.*;

final class GeneratedCodeSupport {
    static GenerationConfig config(String orm) {
        GenerationConfig config = new GenerationConfig();
        config.orm = orm; config.basePackage = "example.generated"; config.author = "测试作者";
        config.tables.add(new GenerationConfig.Table("order"));
        return config;
    }
    static TableMetadata table(String dialect) {
        return new TableMetadata(dialect.equals("mysql") ? "demo" : null, dialect.equals("postgresql") ? "public" : null, "order", "订单记录", List.of(
                new ColumnMetadata("id", "主键", 1, Types.BIGINT, "BIGINT", 19, 0, false, null, true, true, false),
                new ColumnMetadata("display_name", "名称 */ \"注释\"", 2, Types.VARCHAR, "VARCHAR", 100, 0, true, null, false, false, false),
                new ColumnMetadata("balance", "金额", 3, Types.DECIMAL, "DECIMAL", 18, 2, true, null, false, false, false),
                new ColumnMetadata("created_at", "时间", 4, Types.TIMESTAMP, "TIMESTAMP", 0, 0, true, null, false, false, false),
                new ColumnMetadata("generated_value", "生成列", 5, Types.INTEGER, "INTEGER", 10, 0, true, null, false, false, true)));
    }
    static URLClassLoader compile(List<GeneratedFile> files, Path root) throws Exception {
        return compile(files, root, false);
    }
    static URLClassLoader compile(List<GeneratedFile> files, Path root, boolean lombok) throws Exception {
        FileOutputs.write(files, root, false);
        Path classes = Files.createDirectories(root.resolve("classes"));
        List<Path> sources;
        try (var walk = Files.walk(root)) { sources = walk.filter(p -> p.toString().endsWith(".java")).toList(); }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new AssertionError("Tests require a JDK");
        StringWriter diagnostic = new StringWriter();
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(null, null, java.nio.charset.StandardCharsets.UTF_8)) {
            List<String> options = new ArrayList<>(List.of("--release", "17", "-encoding", "UTF-8", "-classpath", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")), "-d", classes.toString()));
            if (lombok) options.addAll(List.of("-processor", "lombok.launch.AnnotationProcessorHider$AnnotationProcessor"));
            else options.add("-proc:none");
            boolean success = compiler.getTask(diagnostic, manager, null, options, null, manager.getJavaFileObjectsFromPaths(sources)).call();
            if (!success) throw new AssertionError("Generated code failed compilation:\n" + diagnostic);
        }
        return new URLClassLoader(new java.net.URL[]{classes.toUri().toURL()}, GeneratedCodeSupport.class.getClassLoader());
    }
}
