package com.bingchunmoli.codegen.jdbc;

import java.sql.Connection;
import java.sql.SQLException;

@FunctionalInterface
public interface ConnectionProvider {
    Connection open() throws SQLException;
}
