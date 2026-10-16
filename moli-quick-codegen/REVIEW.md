# 代码检查与回归记录

日期：2026-10-16。范围：新增 `moli-quick-codegen` 模块、生成模板、UI、命令入口及根模块注册。

## 发现并修复的问题

| 问题 | 原来的影响 | 修复 |
|---|---|---|
| 实体名与 Java/框架类型重名 | `String`、`Long`、`List`、`PageResult` 等实体可能导致类型误解析或生成源码无法编译 | 内置模板使用完整实体类名，标准字段类型和 MyBatis-Plus 注解使用完整类名 |
| Lombok 与访问器命名不一致 | 自定义属性 `eMail` 时，模板调用 `geteMail()`，Lombok 默认生成 `getEMail()`，导致编译失败 | 对这类字段生成明确的 JavaBean 访问器，并关闭相应 Lombok getter/setter |
| schema 被当作匹配模式 | PostgreSQL schema 名含 `_`、`%` 时，可能读取其他命名空间的同名表 | 对 schema/table 元数据模式进行转义，并拒绝歧义表元数据 |
| 输出路径预检不完整 | `a` 与 `a/b.txt` 同批生成时会在开始创建输出后失败；Windows 设备名也未提前拒绝 | 统一规范化路径，预检父子文件路径冲突、Windows 非法名称和大小写冲突；预览与 ZIP/目录输出复用校验 |
| UI 将原型属性误当字段配置 | 数据库列名为 `constructor`、`toString`、`__proto__` 时可能读取或修改对象原型 | 使用自有属性检查，并安全创建可枚举字段覆盖项 |
| JDBC URL 密码未脱敏 | 导出 YAML 只清除独立 password 字段，URL 中的密码仍可能保留 | 清除 URL 密码参数、编码后的参数名及 userinfo 密码；保留环境变量引用 |
| 输入与编码边界处理 | 不合法 JSON/空表项可能返回 500；宿主默认字符集可能损坏中文 YAML；部分 context path 会算错 API 地址 | 输入错误返回 400，拒绝空字段覆盖，明确 UTF-8，修正 API 根路径计算 |

同时修复完整 `java.lang.String` 类型覆盖不能使用 LIKE、非法 Java 类型/`getClass()` 访问器缺少提前诊断的问题。

## 验证结果

- 40 项 Java 单元、生成源码编译、XML 加载及 Web API 测试通过，失败/错误/跳过均为 0。
- 5 项 UI 回归测试通过；TypeScript 检查和 Vite 生产构建通过。
- MySQL 8.4.11、PostgreSQL 16.3 的真实 CRUD/分页回归，在 Spring Boot 3、4 依赖下各通过 13 个场景，共 26 次；包含字符串/UUID 主键和特殊 schema 隔离。
- Windows 符号链接权限不足时使用 Junction，链接及输出根目录祖先越界用例通过。
- 完整 Maven 构建与本地安装、根 reactor 校验通过。
- 同一份含特殊 schema 的 YAML 经 Web、CLI、Maven 生成的 ZIP 字节一致。

部分新增用例在修复前实际复现了失败。当前测试范围内未发现未解决的阻断问题。临时测试数据库在验证后停止并清理，生成器应用保留运行以供检查。

## 复测

常规测试和构建见 [README](README.md)。真实数据库回归需要设置文档列出的测试数据源环境变量，然后执行：

```shell
mvn -f moli-quick-codegen/moli-quick-codegen-core/pom.xml -Pdatabase-it -Dgpg.skip=true verify
mvn -f moli-quick-codegen/moli-quick-codegen-core/pom.xml -Pdatabase-it,generated-boot3 -Dgpg.skip=true verify
```

PowerShell 中将含逗号的 profile 参数和含点的 `-D` 参数整体加引号。
