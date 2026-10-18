# moli-quick-codegen-maven-plugin

提供 tables、preview、generate 三个 Maven goal，适合在当前业务模块手动生成代码。

[项目总览](../../README.md) · [所属模块](../README.md)

依赖版本通过项目总览中的 Quick BOM 对齐为当前源码版本 `1.0.13-bate`，Java 17 / Spring Boot 4.1.0。以下示例需与功能族的完整接入说明配合使用。

## 使用

插件应放入 build/plugins；依赖 BOM 不负责插件版本管理：

```xml
<plugin>
  <groupId>com.bingchunmoli</groupId>
  <artifactId>moli-quick-codegen-maven-plugin</artifactId>
  <version>1.0.13-bate</version>
  <configuration>
    <config>codegen.yml</config>
  </configuration>
</plugin>
```

也可直接执行：

```shell
mvn -N com.bingchunmoli:moli-quick-codegen-maven-plugin:1.0.13-bate:tables -Dcodegen.config=codegen.yml
mvn -N com.bingchunmoli:moli-quick-codegen-maven-plugin:1.0.13-bate:preview -Dcodegen.config=codegen.yml
mvn -N com.bingchunmoli:moli-quick-codegen-maven-plugin:1.0.13-bate:generate -Dcodegen.config=codegen.yml
```

PowerShell 中含点的 -D 参数可整体加引号。首次本地使用先按 [上级文档](../README.md) 安装父 POM 和生成器模块。

## 配置

| 用户属性 | 用途 |
| --- | --- |
| `codegen.config` | 配置路径，相对当前 Maven 模块 |
| `codegen.output` | 输出目录 |
| `codegen.zip` | ZIP 输出位置 |
| `codegen.overwrite` | 显式允许覆盖 |

YAML 内路径相对 YAML 所在目录，不相对整个 reactor 根。目录和 ZIP 输出不能同时指定。生成不自动绑定生命周期；建议先 preview 再 generate，避免在普通编译时意外写业务源码。其余字段覆盖、模板和主键规则与 Core 相同。
