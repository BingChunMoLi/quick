package ${basePackage}.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ${className}Mapper {
    ${entityType} selectById(@Param("id") ${pk.javaType} id);
    List<${entityType}> selectPage(@Param("filter") ${entityType} filter, @Param("offset") long offset, @Param("limit") long limit);
    long count(@Param("filter") ${entityType} filter);
    int insert(${entityType} entity);
<#if updateColumns?has_content>
    int updateById(${entityType} entity);
<#else>
    default int updateById(${entityType} entity) { return 0; }
</#if>
    int deleteById(@Param("id") ${pk.javaType} id);
}
