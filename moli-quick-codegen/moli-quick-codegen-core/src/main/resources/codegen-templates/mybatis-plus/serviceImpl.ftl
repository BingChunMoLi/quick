package ${basePackage}.service.impl;

import ${basePackage}.mapper.${className}Mapper;
import ${basePackage}.model.PageResult;
import ${basePackage}.service.${className}Service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;

@Service
public class ${className}ServiceImpl extends ServiceImpl<${className}Mapper, ${entityType}> implements ${className}Service {
    @Override
    public PageResult<${entityType}> findPage(${entityType} filter, long page, long size) {
        if (page < 1 || size < 1 || size > 1000) throw new IllegalArgumentException("Invalid page or size");
        Math.multiplyExact(page - 1, size);
        LambdaQueryWrapper<${entityType}> query = new LambdaQueryWrapper<>();
        if (filter != null) {
<#list queryColumns as c>
            if (filter.${c.getter}() != null) query.<#if c.queryType == "LIKE">like<#else>eq</#if>(${entityType}::${c.getter}, filter.${c.getter}());
</#list>
        }
        query.orderByAsc(${entityType}::${pk.getter});
        Page<${entityType}> result = page(new Page<>(page, size), query);
        return new PageResult<>(result.getRecords(), result.getTotal(), page, size);
    }
    @Override public ${entityType} findById(${pk.javaType} id) { return getById(Objects.requireNonNull(id, "id")); }
    @Override @Transactional public boolean create(${entityType} entity) {
        Objects.requireNonNull(entity, "entity");
<#if pk.auto>
        entity.${pk.setter}(null);
<#else>
        Objects.requireNonNull(entity.${pk.getter}(), "primary key");
</#if>
        return save(entity);
    }
    @Override @Transactional public boolean update(${entityType} entity) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(entity.${pk.getter}(), "primary key");
<#if updateColumns?has_content>
        if (<#list updateColumns as c>entity.${c.getter}() == null<#sep> && </#list>) return false;
        return updateById(entity);
<#else>
        return false;
</#if>
    }
    @Override @Transactional public boolean delete(${pk.javaType} id) { return removeById(Objects.requireNonNull(id, "id")); }
}
