package ${basePackage}.service.impl;

import ${basePackage}.mapper.${className}Mapper;
import ${basePackage}.model.PageResult;
import ${basePackage}.service.${className}Service;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;

@Service
public class ${className}ServiceImpl implements ${className}Service {
    private final ${className}Mapper mapper;
    public ${className}ServiceImpl(${className}Mapper mapper) { this.mapper = mapper; }

    @Override
    public PageResult<${entityType}> findPage(${entityType} filter, long page, long size) {
        if (page < 1 || size < 1 || size > 1000) throw new IllegalArgumentException("Invalid page or size");
        long offset = Math.multiplyExact(page - 1, size);
        return new PageResult<>(mapper.selectPage(filter, offset, size), mapper.count(filter), page, size);
    }
    @Override public ${entityType} findById(${pk.javaType} id) { return mapper.selectById(Objects.requireNonNull(id, "id")); }
    @Override @Transactional public boolean create(${entityType} entity) {
        Objects.requireNonNull(entity, "entity");
<#if !pk.auto>
        Objects.requireNonNull(entity.${pk.getter}(), "primary key");
</#if>
        return mapper.insert(entity) > 0;
    }
    @Override @Transactional public boolean update(${entityType} entity) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(entity.${pk.getter}(), "primary key");
<#if updateColumns?has_content>
        if (<#list updateColumns as c>entity.${c.getter}() == null<#sep> && </#list>) return false;
        return mapper.updateById(entity) > 0;
<#else>
        return false;
</#if>
    }
    @Override @Transactional public boolean delete(${pk.javaType} id) { return mapper.deleteById(Objects.requireNonNull(id, "id")) > 0; }
}
