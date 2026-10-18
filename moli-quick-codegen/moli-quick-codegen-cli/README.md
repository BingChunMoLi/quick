# moli-quick-codegen-cli

Picocli 命令行入口，复用 Core 配置、模板与输出行为，适用于本地脚本和自动化。

[项目总览](../../README.md) · [所属模块](../README.md)

依赖版本通过项目总览中的 Quick BOM 对齐为当前源码版本 `1.0.13-bate`，Java 17 / Spring Boot 4.1.0。以下示例需与功能族的完整接入说明配合使用。

## 构建与命令

从仓库根目录构建：

```shell
mvn -pl :moli-quick-codegen-cli -am -Dgpg.skip=true package
java -jar moli-quick-codegen/moli-quick-codegen-cli/target/moli-quick-codegen-cli-1.0.13-bate-exec.jar --help
```

打包附加 `-exec.jar` 才包含运行依赖；普通 jar 作为库发布。

| 子命令 | 用途 |
| --- | --- |
| `tables --config codegen.yml` | 列出可选表 |
| `preview --config codegen.yml` | 输出预览 |
| `generate --config codegen.yml` | 写入配置的目标目录 |

例如（将 codegen-cli.jar 替换为上面的 exec.jar）：

```shell
java -jar codegen-cli.jar generate --config codegen.yml --output ./generated
java -jar codegen-cli.jar generate --config codegen.yml --zip ./generated.zip
java -jar codegen-cli.jar generate --config codegen.yml --overwrite
```

`--output` 与 `--zip` 互斥；覆盖必须显式启用。模板和配置中的目录相对 YAML 所在目录解析。`generate` 失败退出码 1，命令参数错误为 2，成功为 0。

## 配置与边界

使用 [统一 YAML](../README.md) 及其数据库示例。密码可引用环境变量，CLI 无宿主具名 DataSource。默认不覆盖文件，不启动 Web 服务，也不持久化生成历史。相同配置与 UI/Maven 共享生成内容；只在最终输出方式上不同。
