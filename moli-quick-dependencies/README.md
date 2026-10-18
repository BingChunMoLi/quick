# moli-quick-dependencies

Quick 依赖 BOM，packaging 为 pom，用于对齐各业务组件版本，不注册 Bean，也不是可运行应用。

[项目总览](../README.md)

当前源码：Java 17，Spring Boot 依赖管理 4.1.0，Quick 版本 `1.0.13-bate`。依赖示例使用项目总览中的统一版本管理；工具和聚合模块的特殊接入方式见下文。

## 导入 BOM

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>com.bingchunmoli</groupId>
      <artifactId>moli-quick-dependencies</artifactId>
      <version>1.0.13-bate</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

随后选择实际需要的组件，省略该组件的版本：

```xml
<dependency>
  <groupId>com.bingchunmoli</groupId>
  <artifactId>moli-quick-web-spring-boot-starter</artifactId>
</dependency>
```

## 管理范围

覆盖 common-beans、AOP、Redis、MyBatis-Plus 运行时、签名拦截器、Security、幂等、限流、Web、代码生成器子模块、对象存储子模块和 OAuth Client 子模块（含 GitHub/QQ/微信/Google）。完整清单以 [pom.xml](pom.xml) 为准。

聚合父模块、演示应用 `moli-quick-spring-boot-starter-common-test` 和旧的 `moli-quick-mybatis-plus-generate` 不应被误认为都在此 BOM 的直接列表内。Maven 插件版本仍应在 plugin 声明中显式指定，BOM 不管理 pluginManagement。

## 宿主与本地开发

源码父工程管理 Spring Boot 4.1.0 和 Java 17。宿主使用相同 Boot 基线，已有依赖版本覆盖需自行验证兼容性。这里的版本是当前源码版本，不使用 Maven 的 `LATEST`。尚未安装/发布到所用仓库时，从根目录构建安装所需模块，命令见项目总览。
