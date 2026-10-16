package ${basePackage}.controller;

import ${basePackage}.service.${className}Service;
import ${basePackage}.model.PageResult;
import org.springframework.web.bind.annotation.*;
<#if quick>
import com.bingchunmoli.bean.ResultVO;
</#if>

@RestController
@RequestMapping("/${route}")
public class ${className}Controller {
    private final ${className}Service service;
    public ${className}Controller(${className}Service service) { this.service = service; }

    @GetMapping
    public <#if quick>ResultVO<</#if>PageResult<${entityType}><#if quick>></#if> page(@ModelAttribute ${entityType} filter,
            @RequestParam(name = "page", defaultValue = "1") long page,
            @RequestParam(name = "size", defaultValue = "10") long size) {
        return <#if quick>ResultVO.ok(</#if>service.findPage(filter, page, size)<#if quick>)</#if>;
    }
    @GetMapping("/{id}")
    public <#if quick>ResultVO<</#if>${entityType}<#if quick>></#if> get(@PathVariable("id") ${pk.javaType} id) {
        return <#if quick>ResultVO.ok(</#if>service.findById(id)<#if quick>)</#if>;
    }
    @PostMapping
    public <#if quick>ResultVO<Boolean><#else>boolean</#if> create(@RequestBody ${entityType} entity) {
        return <#if quick>ResultVO.ok(</#if>service.create(entity)<#if quick>)</#if>;
    }
    @PutMapping
    public <#if quick>ResultVO<Boolean><#else>boolean</#if> update(@RequestBody ${entityType} entity) {
        return <#if quick>ResultVO.ok(</#if>service.update(entity)<#if quick>)</#if>;
    }
    @DeleteMapping("/{id}")
    public <#if quick>ResultVO<Boolean><#else>boolean</#if> delete(@PathVariable("id") ${pk.javaType} id) {
        return <#if quick>ResultVO.ok(</#if>service.delete(id)<#if quick>)</#if>;
    }
}
