package ${package.Controller};

import ${package.Entity}.${entity};
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
<#if restControllerStyle>
    import org.springframework.web.bind.annotation.RestController;
<#else>
    import org.springframework.stereotype.Controller;
</#if>
<#if superControllerClassPackage??>
    import ${superControllerClassPackage};
</#if>

/**
* ${table.comment!}
* @author ${author}
* @since ${version}
*/
@Slf4j
<#if restControllerStyle>
    @RestController
<#else>
    @Controller
</#if>
@RequiredArgsConstructor
@RequestMapping("<#if package.ModuleName?? && package.ModuleName != "">/${package.ModuleName}</#if>/<#if controllerMappingHyphenStyle>${controllerMappingHyphen}<#else>${table.entityPath}</#if>")
<#if kotlin>
class ${table.controllerName}<#if superControllerClass??> : ${superControllerClass}()</#if>
<#else>
<#if superControllerClass??>
    public class ${table.controllerName} extends ${superControllerClass} {
<#else>
    public class ${table.controllerName} {
</#if>
private final ${table.serviceName} ${table.serviceName?uncap_first}};


@GetMapping
public IPage<${entity}> page${entity} (@RequestParam(required= false, defaultValue="1") Integer pageNum, @RequestParam(required= false, defaultValue="10") Integer pageSize, ${entity} ${entity?uncap_first}) {
IPage<${entity}> page = new Page<>(pageNum, pageSize);
return ResultVO.ok(${table.serviceName?uncap_first}.page(page, Wrappers.lambdaQuery(${entity?uncap_first})));
}

@GetMapping("/{id}")
public ResultVO<${entity}> get${entity} (@PathVariable Long id) {
return ResultVO.ok(${table.serviceName?uncap_first}.getById(id));
}

@PostMapping
public ResultVO
<Boolean> add${entity} (@RequestBody ${entity} ${entity?uncap_first}) {
    return ResultVO.ok(${table.serviceName?uncap_first}.save(${entity?uncap_first}));
    }

    @PutMapping
    public ResultVO
    <Boolean> edit${entity} (@RequestBody ${entity} ${entity?uncap_first}) {
        return ResultVO.ok(${table.serviceName?uncap_first}.updateById(${entity?uncap_first}) > 0);
        }

        @DeleteMapping("/{id}")
        public ResultVO
        <Boolean> delete${entity} (@PathVariable Long id) {
            return ResultVO.ok(${table.serviceName?uncap_first}.removeById(id) > 0);
            }
            }
            </#if>
