package ${basePackage}.entity;

/** ${comment}
 * @author ${author}
 */
<#if lombok>
@lombok.Data
</#if>
public class ${className} {
<#list columns as c>
    /** ${c.comment} */
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
