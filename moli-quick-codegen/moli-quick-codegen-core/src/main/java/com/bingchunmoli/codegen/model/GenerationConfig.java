package com.bingchunmoli.codegen.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Portable configuration shared by every entry point. Paths are relative to the config file. */
public class GenerationConfig {
    public int schemaVersion = 1;
    public Database datasource = new Database();
    public String orm = "mybatis";
    public String basePackage = "com.example.generated";
    public String author = "";
    public String tablePrefix = "";
    public String responseStyle = "generic";
    public boolean lombok;
    public boolean openApi;
    public List<Table> tables = new ArrayList<>();
    public List<String> artifacts = new ArrayList<>(List.of("entity", "controller", "service", "serviceImpl", "mapper", "xml"));
    public Map<String, Template> templates = new LinkedHashMap<>();
    public Map<String, Object> variables = new LinkedHashMap<>();
    public Output output = new Output();

    public static class Database {
        public String url = "";
        public String username = "";
        public String password = "";
        public String catalog;
        public String schema;
        public String beanName;
    }

    public static class Table {
        public String name;
        public String className;
        public Map<String, Column> columns = new LinkedHashMap<>();
        public Table() { }
        public Table(String name) { this.name = name; }
    }

    public static class Column {
        public String property;
        public String javaType;
        public String jdbcType;
        public String typeHandler;
        public boolean insert = true;
        public boolean update = true;
        public boolean query = true;
        public String queryType = "EQ";
    }

    public static class Template {
        public String content;
        public String file;
        public String outputPath;
    }

    public static class Output {
        public String directory = ".";
        public String javaDirectory = "src/main/java";
        public String xmlDirectory = "src/main/resources/mapper";
        public Map<String, String> directories = new LinkedHashMap<>();
        public boolean overwrite;
    }
}
