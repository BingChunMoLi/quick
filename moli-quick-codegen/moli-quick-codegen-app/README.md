# moli-quick-codegen-app

复用 Starter 的独立代码生成器应用，默认仅监听本机 127.0.0.1:8088。

[项目总览](../../README.md) · [所属模块](../README.md)

依赖版本通过项目总览中的 Quick BOM 对齐为当前源码版本 `1.0.13-bate`，Java 17 / Spring Boot 4.1.0。以下示例需与功能族的完整接入说明配合使用。

## 构建与启动

```shell
mvn -pl :moli-quick-codegen-app -am -Dgpg.skip=true package
java -jar moli-quick-codegen/moli-quick-codegen-app/target/moli-quick-codegen-app-1.0.13-bate-exec.jar
```

从仓库根目录执行，浏览器打开 `http://127.0.0.1:8088/moli-codegen/`。可执行产物是带 `-exec` 的 Boot JAR，普通 jar 不包含全部运行依赖。

## 默认配置

```properties
server.address=127.0.0.1
server.port=8088
moli.codegen.enabled=true
```

可通过外部配置文件或 `--server.port=8089` 覆盖。默认没有额外的业务登录界面；若调整为其他网卡或对外开放，需由部署环境建立访问控制。

## 使用

页面支持 MySQL/PostgreSQL、MyBatis/MyBatis-Plus、字段与模板编辑、YAML 导入/导出、文件树预览和 ZIP 下载。下载后由用户检查并复制代码到目标项目，应用不直接写目标项目目录。

模板变量、数据库参数和默认不覆盖约定见 [生成器总指南](../README.md)。需要集成现有应用时选择 [Starter](../moli-quick-codegen-spring-boot-starter/README.md)；需要直接写入项目则选择 CLI/Maven。
