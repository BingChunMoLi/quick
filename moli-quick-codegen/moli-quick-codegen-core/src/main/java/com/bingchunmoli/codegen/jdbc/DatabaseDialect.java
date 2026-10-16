package com.bingchunmoli.codegen.jdbc;

import com.bingchunmoli.codegen.CodegenException;
import com.bingchunmoli.codegen.model.TableMetadata;

/** Extension point for identifier quoting and database-specific SQL. */
public interface DatabaseDialect {
    String id();
    String quote(String identifier);
    String qualifiedName(TableMetadata table);

    static DatabaseDialect forProduct(String product) {
        if ("MySQL".equalsIgnoreCase(product)) return new StandardDialect("mysql", "`");
        if ("PostgreSQL".equalsIgnoreCase(product)) return new StandardDialect("postgresql", "\"");
        throw new CodegenException("Unsupported database; expected MySQL or PostgreSQL");
    }
}
