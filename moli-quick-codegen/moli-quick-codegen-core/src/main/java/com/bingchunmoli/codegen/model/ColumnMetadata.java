package com.bingchunmoli.codegen.model;

/** JDBC column facts; generation overrides are kept separately in GenerationConfig. */
public record ColumnMetadata(String name, String comment, int ordinal, int jdbcType, String typeName,
                             int size, int scale, boolean nullable, String defaultValue,
                             boolean primaryKey, boolean autoIncrement, boolean generated) { }
