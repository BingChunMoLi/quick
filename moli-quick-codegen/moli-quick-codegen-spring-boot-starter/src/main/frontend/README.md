# Quick Codegen UI

内嵌生成器的 Vue 3、TypeScript、Element Plus、Vite 前端，最终由 Maven 构建进 Starter JAR。

[Starter 接入](../../../README.md) · [生成器总指南](../../../../README.md)

## 本地开发

先启动独立 codegen-app（默认 127.0.0.1:8088），再在本目录执行：

```shell
npm ci
npm run dev
npm run build
```

开发服务器将 /moli-codegen/api 代理到 http://127.0.0.1:8088，见 [vite.config.ts](vite.config.ts)。更改 API 后端或代理地址时修改此配置；本项目使用 package-lock.json 保持构建可复现。

## 构建与交互

npm run build 执行类型检查和 Vite 构建，输出到 Starter 的 target/classes/codegen-ui；不要将 target 作为源码提交。Maven generate-resources 阶段使用固定 Node/npm 执行同样流程。干净发布构建不能跳过 UI。

页面支持连接、表选择、字段配置、模板编辑、预览和下载。模板编辑结果导出为 YAML 内联内容；导出默认去除明文密码，数据库密码不写浏览器持久存储。界面只请求预览/ZIP，不直接写服务端项目目录。

## 调试边界

宿主访问控制依然生效，前端不会绕过 Spring Security。代理只服务本地开发，不代替部署环境的路由与认证配置。API 路径和 JSON/YAML 契约见 Starter 与生成器总指南。
