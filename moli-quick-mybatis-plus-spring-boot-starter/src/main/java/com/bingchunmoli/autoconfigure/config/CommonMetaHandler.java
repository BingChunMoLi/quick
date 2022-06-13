package com.bingchunmoli.autoconfigure.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;

import java.time.LocalDateTime;


/**
 * 注入元对象字段填充，实现createTime和updateTime字段信息的自动填充
 *
 * @author BingChunMoLi
 */
//@Component
//@ConditionalOnClass(MetaObjectHandler.class)
//@ConditionalOnMissingBean(name = "metaObjectHandler")
public class CommonMetaHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}
