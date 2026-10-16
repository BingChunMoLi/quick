package ${basePackage}.service;

import ${basePackage}.model.PageResult;

public interface ${className}Service {
    PageResult<${entityType}> findPage(${entityType} filter, long page, long size);
    ${entityType} findById(${pk.javaType} id);
    boolean create(${entityType} entity);
    boolean update(${entityType} entity);
    boolean delete(${pk.javaType} id);
}
