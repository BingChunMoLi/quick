package com.bingchunmoli.codegen.jdbc;

import com.bingchunmoli.codegen.CodegenException;
import com.bingchunmoli.codegen.model.*;
import java.sql.*;
import java.util.*;

public class MetadataReader {
    public record Snapshot(DatabaseDialect dialect, List<TableMetadata> tables) { }

    public Snapshot read(ConnectionProvider provider, GenerationConfig.Database config, Collection<String> selected) {
        try (Connection connection = provider.open()) {
            DatabaseMetaData meta = connection.getMetaData();
            DatabaseDialect dialect = DatabaseDialect.forProduct(meta.getDatabaseProductName());
            String catalog = config.catalog == null ? connection.getCatalog() : config.catalog;
            String schema = "mysql".equals(dialect.id()) ? null : config.schema == null ? connection.getSchema() : config.schema;
            if ("postgresql".equals(dialect.id()) && (schema == null || schema.isBlank())) throw new CodegenException("PostgreSQL schema is required when the connection has no current schema");
            List<TableMetadata> result = new ArrayList<>();
            Set<String> names = new HashSet<>(selected);
            try (ResultSet tables = meta.getTables(catalog, pattern(meta, schema), "%", new String[]{"TABLE"})) {
                while (tables.next()) {
                    String name = tables.getString("TABLE_NAME");
                    if (!names.isEmpty() && !names.contains(name)) continue;
                    String tableCatalog = tables.getString("TABLE_CAT");
                    String tableSchema = tables.getString("TABLE_SCHEM");
                    if (result.stream().anyMatch(table -> table.name().equals(name))) throw new CodegenException("Ambiguous table name across catalog/schema: " + name);
                    List<ColumnMetadata> columns = names.isEmpty() ? List.of() : columns(meta, tableCatalog, tableSchema, name);
                    result.add(new TableMetadata(tableCatalog, tableSchema, name, tables.getString("REMARKS"), columns));
                }
            }
            for (String name : names)
                if (result.stream().noneMatch(t -> t.name().equals(name))) throw new CodegenException("Table not found in selected catalog/schema: " + name);
            result.sort(Comparator.comparing(TableMetadata::name));
            return new Snapshot(dialect, List.copyOf(result));
        } catch (SQLException e) {
            throw new CodegenException("Database metadata read failed (SQLState " + e.getSQLState() + ")");
        }
    }

    private List<ColumnMetadata> columns(DatabaseMetaData meta, String catalog, String schema, String table) throws SQLException {
        Set<String> keys = new HashSet<>();
        try (ResultSet rs = meta.getPrimaryKeys(catalog, schema, table)) {
            while (rs.next()) keys.add(rs.getString("COLUMN_NAME"));
        }
        List<ColumnMetadata> columns = new ArrayList<>();
        try (ResultSet rs = meta.getColumns(catalog, pattern(meta, schema), pattern(meta, table), "%")) {
            while (rs.next()) {
                String name = rs.getString("COLUMN_NAME");
                columns.add(new ColumnMetadata(name, rs.getString("REMARKS"), rs.getInt("ORDINAL_POSITION"),
                        rs.getInt("DATA_TYPE"), rs.getString("TYPE_NAME"), rs.getInt("COLUMN_SIZE"), rs.getInt("DECIMAL_DIGITS"),
                        rs.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls, rs.getString("COLUMN_DEF"),
                        keys.contains(name), "YES".equals(rs.getString("IS_AUTOINCREMENT")), "YES".equals(rs.getString("IS_GENERATEDCOLUMN"))));
            }
        }
        columns.sort(Comparator.comparingInt(ColumnMetadata::ordinal));
        return columns;
    }
    private String pattern(DatabaseMetaData meta, String identifier) throws SQLException {
        if (identifier == null) return null;
        String escape = meta.getSearchStringEscape();
        if (escape == null || escape.isEmpty()) {
            if (identifier.contains("_") || identifier.contains("%")) throw new CodegenException("JDBC driver cannot escape metadata patterns");
            return identifier;
        }
        return identifier.replace(escape, escape + escape).replace("_", escape + "_").replace("%", escape + "%");
    }
}
