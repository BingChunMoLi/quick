package ${basePackage}.entity;

/** ${comment}
 * @author ${author}
 */
@com.baomidou.mybatisplus.annotation.TableName(value = "${tableLiteral}"<#if schemaLiteral?has_content>, schema = "${schemaLiteral}"</#if>, autoResultMap = true)
<#if lombok>
@lombok.Data
</#if>
public class ${className} {
<#list columns as c>
    /** ${c.comment} */
<#if c.pk && singleKey>
    @com.baomidou.mybatisplus.annotation.TableId(value = "${c.sqlLiteral}", type = com.baomidou.mybatisplus.annotation.IdType.<#if c.auto>AUTO<#else>INPUT</#if>)
<#else>
    @com.baomidou.mybatisplus.annotation.TableField(value = "${c.sqlLiteral}", insertStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.<#if c.insert>NOT_NULL<#else>NEVER</#if>, updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.<#if c.update>NOT_NULL<#else>NEVER</#if><#if c.typeHandler?has_content>, typeHandler = ${c.typeHandler}.class</#if>)
</#if>
<#if openApi>
    @io.swagger.v3.oas.annotations.media.Schema(description = "${c.commentLiteral}")
</#if>
<#if lombok && c.manualAccessors>
    @lombok.Getter(lombok.AccessLevel.NONE)
    @lombok.Setter(lombok.AccessLevel.NONE)
</#if>
    private ${c.javaType} ${c.property};

</#list>
<#list columns as c>
<#if c.manualAccessors>
    public ${c.javaType} ${c.getter}() { return ${c.property}; }
    public void ${c.setter}(${c.javaType} value) { this.${c.property} = value; }

</#if>
</#list>
}
