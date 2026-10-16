package com.bingchunmoli.codegen;

import com.bingchunmoli.codegen.model.ColumnMetadata;
import com.bingchunmoli.codegen.model.GenerationConfig;
import java.sql.JDBCType;
import java.sql.Types;
import java.util.Locale;
import java.util.Map;

final class TypeMappings {
    private TypeMappings() { }
    static Map<String, String> resolve(ColumnMetadata column, GenerationConfig.Column override, String location) {
        String typeName = column.typeName().toLowerCase(Locale.ROOT);
        String javaType;
        if (typeName.equals("uuid")) javaType = "java.util.UUID";
        else javaType = switch (column.jdbcType()) {
            case Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR, Types.NCHAR, Types.NVARCHAR, Types.LONGNVARCHAR, Types.CLOB, Types.NCLOB -> "String";
            case Types.BOOLEAN, Types.BIT -> column.size() > 1 && column.jdbcType() == Types.BIT ? null : "Boolean";
            case Types.TINYINT, Types.SMALLINT, Types.INTEGER -> typeName.contains("unsigned") && column.jdbcType() == Types.INTEGER ? "Long" : "Integer";
            case Types.BIGINT -> typeName.contains("unsigned") ? "java.math.BigInteger" : "Long";
            case Types.FLOAT, Types.REAL -> "Float";
            case Types.DOUBLE -> "Double";
            case Types.NUMERIC, Types.DECIMAL -> "java.math.BigDecimal";
            case Types.DATE -> "java.time.LocalDate";
            case Types.TIME -> "java.time.LocalTime";
            case Types.TIME_WITH_TIMEZONE -> "java.time.OffsetTime";
            case Types.TIMESTAMP -> "java.time.LocalDateTime";
            case Types.TIMESTAMP_WITH_TIMEZONE -> "java.time.OffsetDateTime";
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> "byte[]";
            default -> null;
        };
        // PostgreSQL reports timestamptz as TIMESTAMP in some driver versions.
        if (typeName.equals("timestamptz")) javaType = "java.time.OffsetDateTime";
        boolean custom = javaType == null || typeName.contains("json") || typeName.endsWith("[]");
        if (override.javaType != null) javaType = override.javaType;
        if (javaType == null || custom && (override.javaType == null || override.typeHandler == null))
            throw new CodegenException("Unsupported type at " + location + " (" + column.typeName() + "); configure javaType and typeHandler");
        if (java.util.Set.of("String", "Boolean", "Byte", "Short", "Integer", "Long", "Float", "Double", "Character").contains(javaType)) javaType = "java.lang." + javaType;
        Names.javaType(javaType, location);
        if (java.util.Set.of("int", "long", "boolean", "double", "float", "short", "char", "byte").contains(javaType))
            throw new CodegenException("Use a nullable boxed Java type at " + location);
        String jdbc;
        try { jdbc = override.jdbcType == null ? JDBCType.valueOf(column.jdbcType()).getName() : JDBCType.valueOf(override.jdbcType).getName(); }
        catch (IllegalArgumentException e) { throw new CodegenException("Invalid JDBC type at " + location); }
        if (override.typeHandler != null) Names.javaType(override.typeHandler, location + " typeHandler");
        return Map.of("javaType", javaType, "jdbcType", jdbc, "typeHandler", override.typeHandler == null ? "" : override.typeHandler);
    }
}
