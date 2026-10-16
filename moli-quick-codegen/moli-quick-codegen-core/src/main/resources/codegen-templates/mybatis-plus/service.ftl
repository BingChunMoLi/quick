package ${basePackage}.service;

import ${basePackage}.model.PageResult;
import com.baomidou.mybatisplus.spring.service.IService;

public interface ${className}Service extends IService<${entityType}> {
    PageResult<${entityType}> findPage(${entityType} filter, long page, long size);
    ${entityType} findById(${pk.javaType} id);
    boolean create(${entityType} entity);
    boolean update(${entityType} entity);
    boolean delete(${pk.javaType} id);
}
