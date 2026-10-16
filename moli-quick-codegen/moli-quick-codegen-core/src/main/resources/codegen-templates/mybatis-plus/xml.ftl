<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="${basePackage}.mapper.${className}Mapper">
  <resultMap id="BaseResultMap" type="${basePackage}.entity.${className}">
<#list columns as c>
    <#if c.pk><id<#else><result</#if> column="${c.name?xml}" property="${c.property}" jdbcType="${c.jdbcType}"<#if c.typeHandler?has_content> typeHandler="${c.typeHandler}"</#if>/>
</#list>
  </resultMap>
  <sql id="BaseColumns"><#list columns as c>${c.sql?xml}<#sep>, </#list></sql>
</mapper>
