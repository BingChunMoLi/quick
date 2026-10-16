package com.bingchunmoli.codegen.model;

import java.util.List;

public record TableMetadata(String catalog, String schema, String name, String comment, List<ColumnMetadata> columns) {
    public TableMetadata { columns = List.copyOf(columns); }
}
