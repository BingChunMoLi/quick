# moli-quick-codegen-spring-boot-starter

将生成器 API 和内置 Vue 3 页面嵌入现有 Servlet Spring Boot 应用。

[项目总览](../../README.md) · [所属模块](../README.md)

依赖版本通过项目总览中的 Quick BOM 对齐为当前源码版本 `1.0.13-bate`，Java 17 / Spring Boot 4.1.0。以下示例需与功能族的完整接入说明配合使用。

## 接入

```xml
<dependency>
  <groupId>com.bingchunmoli</groupId>
  <artifactId>moli-quick-codegen-spring-boot-starter</artifactId>
</dependency>
```


```yaml
moli:
  codegen:
    enabled: true
```

默认关闭，关闭时不注册生成 API、UI 资源及生成器 Bean。启用后访问 `/moli-codegen/`，流程为连接、选表、字段/模板配置、预览、下载。宿主的安全链仍然生效，Quick 不自动放行生成器路径。

## API

基础路径 `/moli-codegen/api`，配置请求体使用 GenerationConfig JSON：

| 方法/路径 | 用途 |
| --- | --- |
| GET `/datasources` | 列出宿主 DataSource Bean 名 |
| POST `/connection` | 测试连接 |
| POST `/tables`、`/columns` | 元数据读取 |
| GET `/templates?orm=mybatis` | 内置模板 |
| POST `/preview` | 文件列表及诊断 |
| POST `/export` | ZIP 下载 |
| POST `/config/import`、`/config/export` | YAML 导入/导出 |

具名数据源使用 `datasource.beanName`，优先于 JDBC 凭证；只有 Web 入口支持宿主 Bean。UI 导出默认移除明文密码，并将编辑模板内联到 YAML，以便 CLI/Maven 复现。

宿主可按类型替换 CodeGenerator、CodegenController 和 CodegenExceptionHandler；UI 资源配置可通过名为 `codegenResources` 的 WebMvcConfigurer 替换。默认控制器会退让，避免重复路由。不支持的媒体类型等 Spring MVC 错误保留其 HTTP 状态及协议头，内部异常继续返回脱敏的 500。

## 开发和限制

前端开发指南位于 [src/main/frontend/README.md](src/main/frontend/README.md)，Maven 默认下载固定 Node/npm、执行 npm ci 和构建。干净发布构建需要前端产物；只验证后端可临时使用 `-Dcodegen.ui.skip=true`。

UI 不写服务器项目目录，不创建生成器业务表，不保存数据库密码到浏览器持久存储。生成器可访问配置的数据源及外部模板文件，因此应只向有权限的开发人员开放。完整模板、字段及输出约定见上级文档。
