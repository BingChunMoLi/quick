package com.bingchunmoli.autoconfigure.config;

import com.baomidou.mybatisplus.annotation.DbType;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Defaults for the Quick MyBatis-Plus plugin combination. */
@ConfigurationProperties("moli.mybatis-plus")
public class MybatisPlusProperties {
    private boolean enabled = true;
    private DbType dbType = DbType.MYSQL;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public DbType getDbType() { return dbType; }
    public void setDbType(DbType dbType) { this.dbType = dbType; }
}
