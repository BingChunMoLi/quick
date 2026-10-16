package com.bingchunmoli.codegen;

import com.bingchunmoli.codegen.jdbc.MetadataReader;
import com.bingchunmoli.codegen.model.GenerationConfig;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class MetadataReaderTest {
    @Test void schemaAndTableNamesAreLiteralMetadataPatterns() throws Exception {
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(metadata);
        when(connection.getCatalog()).thenReturn("demo");
        when(metadata.getDatabaseProductName()).thenReturn("PostgreSQL");
        when(metadata.getSearchStringEscape()).thenReturn("\\");
        ResultSet tables = mock(ResultSet.class), keys = mock(ResultSet.class), columns = mock(ResultSet.class);
        when(metadata.getTables(any(), any(), any(), any())).thenReturn(tables);
        when(tables.next()).thenReturn(true, false);
        when(tables.getString("TABLE_NAME")).thenReturn("order_line");
        when(tables.getString("TABLE_CAT")).thenReturn("demo");
        when(tables.getString("TABLE_SCHEM")).thenReturn("tenant_one%");
        when(metadata.getPrimaryKeys(any(), any(), any())).thenReturn(keys);
        when(metadata.getColumns(any(), any(), any(), any())).thenReturn(columns);
        var config = new GenerationConfig.Database(); config.schema = "tenant_one%";
        var result = new MetadataReader().read(() -> connection, config, List.of("order_line"));
        assertThat(result.tables()).hasSize(1);
        verify(metadata).getTables(eq("demo"), eq("tenant\\_one\\%"), eq("%"), any());
        verify(metadata).getColumns("demo", "tenant\\_one\\%", "order\\_line", "%");
        verify(metadata).getPrimaryKeys("demo", "tenant_one%", "order_line");
        verify(connection).close();
    }
    @Test void postgresWithoutCurrentSchemaRequiresAnExplicitSchema() throws Exception {
        Connection connection = mock(Connection.class); DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(metadata); when(metadata.getDatabaseProductName()).thenReturn("PostgreSQL");
        assertThatThrownBy(() -> new MetadataReader().read(() -> connection, new GenerationConfig.Database(), List.of())).isInstanceOf(CodegenException.class).hasMessageContaining("schema is required");
        verify(metadata, never()).getTables(any(), any(), any(), any());
    }
}
