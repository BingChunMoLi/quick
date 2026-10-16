package ${basePackage}.model;

import org.apache.ibatis.type.*;
import java.sql.*;
import java.util.UUID;

/** Register this package with MyBatis type-handlers-package when using UUID keys. */
@MappedTypes(UUID.class)
@MappedJdbcTypes(value = {JdbcType.OTHER, JdbcType.CHAR, JdbcType.VARCHAR}, includeNullJdbcType = true)
public class UuidTypeHandler extends BaseTypeHandler<UUID> {
    @Override public void setNonNullParameter(PreparedStatement statement, int index, UUID value, JdbcType type) throws SQLException {
        if (type == JdbcType.CHAR || type == JdbcType.VARCHAR || type == null && "MySQL".equals(statement.getConnection().getMetaData().getDatabaseProductName())) statement.setString(index, value.toString());
        else statement.setObject(index, value);
    }
    private UUID uuid(Object value) { return value == null ? null : value instanceof UUID id ? id : UUID.fromString(value.toString()); }
    @Override public UUID getNullableResult(ResultSet result, String column) throws SQLException { return uuid(result.getObject(column)); }
    @Override public UUID getNullableResult(ResultSet result, int column) throws SQLException { return uuid(result.getObject(column)); }
    @Override public UUID getNullableResult(CallableStatement result, int column) throws SQLException { return uuid(result.getObject(column)); }
}
