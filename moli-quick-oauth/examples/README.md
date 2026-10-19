# 独立 Authorization Server / Resource Server / Client 示例

[OAuth 导航](../README.md) · [授权端](../moli-quick-oauth-auth-server/README.md) · [资源端](../moli-quick-oauth-resource-server/README.md)

三个示例使用各自的 Spring Boot 4.1.0 parent 和 Quick BOM，不继承 Quick 根 POM，也不作为 reactor 模块。它们通过已安装的 JAR 自动配置，使用真实 HTTP 协议；无真实第三方账号、外部 OAuth 服务或默认数据库前置条件。

## 构建

先在 Quick 根目录安装当前依赖：

```powershell
mvn -pl ':moli-quick-dependencies,:moli-quick-oauth-auth-server-spring-boot-starter' -am install '-DskipTests' '-Dgpg.skip=true' '-Dmaven.javadoc.skip=true' '-Dmaven.compiler.proc=full'
```

在本 examples 目录分别构建：

```powershell
mvn -f authorization-server/pom.xml verify '-Dmaven.compiler.proc=full'
mvn -f resource-server/pom.xml verify '-Dmaven.compiler.proc=full'
mvn -f client/pom.xml verify '-Dmaven.compiler.proc=full'
python smoke.py
```

smoke.py 使用 9010—9013 端口，先确认均空闲，再启动四个独立 Java 进程：授权端、JWT API、opaque API、Client。脚本隔离 Spring/OAuth 应用环境变量、禁用本地请求代理并固定监听 127.0.0.1，避免全局配置影响本地验证。验证表单/同意/OIDC/宿主 Session/第三方 API、client_credentials、授权码 PKCE、刷新轮换、重放拒绝及两种撤销语义；最后只关闭脚本自己启动的进程。摘要/日志写入本目录 target，不输出令牌。端口已占用时失败，不接管已有服务。

## 手工启动

在各自终端执行：

```powershell
java -jar authorization-server/target/quick-oauth-authorization-server-example-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
java -jar resource-server/target/quick-oauth-resource-server-example-0.0.1-SNAPSHOT.jar
java -jar client/target/quick-oauth-client-example-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
```

浏览器访问 <http://127.0.0.1:9012/me>，使用用户 alice、开发密码 local-user-password，确认授权后可访问 `/me` 和 `/api`。授权端、Client 使用不同 Session Cookie 名，避免本机不同端口覆盖 JSESSIONID。

local profile 是显式本地演示：三个客户端 web、machine、reference 分别使用 local-web-secret、local-machine-secret、local-reference-secret，服务端保存不同的 BCrypt 哈希；授权端生成带警告的临时密钥。生产不启用 local；设置 OAUTH_JWK_SET_LOCATION / OAUTH_ACTIVE_KEY_ID、EXAMPLE_USER_PASSWORD_HASH 以及各自 OAUTH_WEB_CLIENT_SECRET_HASH、OAUTH_MACHINE_CLIENT_SECRET_HASH、OAUTH_REFERENCE_CLIENT_SECRET_HASH，并将示例用户仓库替换成业务认证。Client 通过 OAUTH_CLIENT_SECRET 提供 web 原始客户端密钥。生产默认无密钥/用户密码会启动失败。

## 三种授权存储

默认 memory，重启丢失授权。JDBC/Redis 的授权记录持久化不包含本地临时签名密钥；需要验证跨重启令牌时必须配置持久签名密钥。浏览器 Session 共享由宿主负责。

JDBC：先按数据库执行授权 JDBC 模块的四张表脚本，设置 OAUTH_JDBC_URL / OAUTH_JDBC_USERNAME / OAUTH_JDBC_PASSWORD，然后构建并运行：

```powershell
mvn -f authorization-server/pom.xml -Pjdbc package
java -jar authorization-server/target/quick-oauth-authorization-server-example-0.0.1-SNAPSHOT.jar --spring.profiles.active=jdbc
```

Redis：设置 OAUTH_REDIS_HOST / OAUTH_REDIS_PORT，使用 Maven profile redis 构建，Spring profile redis 运行；本地演示可显式组合 local,redis，但这种临时密钥不能用于多实例验签。

opaque API：设置 OAUTH_INTROSPECTION_SECRET 为 machine 原始密钥，运行资源端 JAR 时用 `--spring.profiles.active=opaque`，监听 9013；为授权端 reference 客户端申请 client_credentials 即得到 reference token。JWT 本地验签的撤销不立即生效，opaque 每次 introspection 可立即观察授权撤销。

## 复用第三方登录

授权端业务登录链也可复用现有 OAuth Client。宿主成功处理器按 provider + subject 查找/绑定本地账户，轮换 Session ID 并显式保存业务 Authentication，随后回到原授权请求。不能直接把第三方 Token 用作 Quick 授权服务器的登录态。Client 示例演示授权后的业务 Session；替换为 GitHub/QQ/微信/Google 时沿用同一宿主处理方式。
