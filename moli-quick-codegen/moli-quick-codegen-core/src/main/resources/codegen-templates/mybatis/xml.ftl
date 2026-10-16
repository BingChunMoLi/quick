<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="${basePackage}.mapper.${className}Mapper">
  <resultMap id="BaseResultMap" type="${basePackage}.entity.${className}">
<#list columns as c>
    <#if c.pk><id<#else><result</#if> column="${c.name?xml}" property="${c.property}" jdbcType="${c.jdbcType}"<#if c.typeHandler?has_content> typeHandler="${c.typeHandler}"</#if>/>
</#list>
  </resultMap>
  <sql id="BaseColumns"><#list columns as c>${c.sql?xml}<#sep>, </#list></sql>
  <sql id="Filter">
    <where>
<#list queryColumns as c>
      <if test="filter != null and filter.${c.property} != null">
        AND ${c.sql?xml} <#if c.queryType == "LIKE">LIKE CONCAT('%', ${c.filterBind}, '%')<#else>= ${c.filterBind}</#if>
      </if>
</#list>
    </where>
  </sql>
  <select id="selectById" resultMap="BaseResultMap">
    SELECT <include refid="BaseColumns"/> FROM ${qualifiedTable?xml} WHERE ${pk.sql?xml} = ${pk.keyBind}
  </select>
  <select id="selectPage" resultMap="BaseResultMap">
    SELECT <include refid="BaseColumns"/> FROM ${qualifiedTable?xml}
    <include refid="Filter"/> ORDER BY ${pk.sql?xml} LIMIT ${r'#{limit}'} OFFSET ${r'#{offset}'}
  </select>
  <select id="count" resultType="long">
    SELECT COUNT(*) FROM ${qualifiedTable?xml} <include refid="Filter"/>
  </select>
  <insert id="insert"<#if pk.auto> useGeneratedKeys="true" keyProperty="${pk.property}" keyColumn="${pk.name?xml}"</#if>>
    <choose>
<#if insertColumns?has_content>
      <when test="<#list insertColumns as c>${c.property} != null<#sep> or </#list>">
        INSERT INTO ${qualifiedTable?xml}
        <trim prefix="(" suffix=")" suffixOverrides=",">
<#list insertColumns as c>
          <if test="${c.property} != null">${c.sql?xml},</if>
</#list>
        </trim>
        VALUES
        <trim prefix="(" suffix=")" suffixOverrides=",">
<#list insertColumns as c>
          <if test="${c.property} != null">${c.bind},</if>
</#list>
        </trim>
      </when>
</#if>
      <otherwise>INSERT INTO ${qualifiedTable?xml} <#if dialect == "mysql">() VALUES ()<#else>DEFAULT VALUES</#if></otherwise>
    </choose>
  </insert>
<#if updateColumns?has_content>
  <update id="updateById">
    <choose>
      <when test="<#list updateColumns as c>${c.property} != null<#sep> or </#list>">
        UPDATE ${qualifiedTable?xml}
        <set>
<#list updateColumns as c>
          <if test="${c.property} != null">${c.sql?xml} = ${c.bind},</if>
</#list>
        </set>
        WHERE ${pk.sql?xml} = ${pk.bind}
      </when>
      <otherwise>UPDATE ${qualifiedTable?xml} SET ${updateColumns[0].sql?xml} = ${updateColumns[0].sql?xml} WHERE 1 = 0</otherwise>
    </choose>
  </update>
</#if>
  <delete id="deleteById">DELETE FROM ${qualifiedTable?xml} WHERE ${pk.sql?xml} = ${pk.keyBind}</delete>
</mapper>
