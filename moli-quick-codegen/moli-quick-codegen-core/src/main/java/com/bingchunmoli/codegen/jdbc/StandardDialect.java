package com.bingchunmoli.codegen.jdbc;

import com.bingchunmoli.codegen.model.TableMetadata;

record StandardDialect(String id, String delimiter) implements DatabaseDialect {
    @Override public String quote(String identifier) {
        return delimiter + identifier.replace(delimiter, delimiter + delimiter) + delimiter;
    }
    @Override public String qualifiedName(TableMetadata table) {
        String namespace = "mysql".equals(id) ? table.catalog() : table.schema();
        return namespace == null || namespace.isBlank() ? quote(table.name()) : quote(namespace) + "." + quote(table.name());
    }
}
