# Starter 接入调整

这些调整针对当前 `1.0.13-bate` 源码，重点是安全默认值、Boot 4 接入和宿主扩展。依赖仍统一使用 Quick BOM 和 Spring Boot 4.1.0。

## JWT 必须显式配置密钥

JWT 默认开启，但不再提供签名密钥默认值。缺失、空白、少于 32 个 UTF-8 字节，或使用旧公开默认值时，创建 JwtTokenService 会失败；自动配置在启动阶段报告该错误。直接 new JwtTokenService 同样适用。

```yaml
moli:
  security:
    jwt:
      secret: ${JWT_SECRET}
```

例如在 PowerShell 中生成 32 个随机字节的 Base64 值供当前进程使用：

```powershell
$env:JWT_SECRET = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
```

部署时通过密钥管理或环境配置持久提供该值，多实例使用相同的有效密钥。更换密钥会使旧密钥签发的令牌失效；旧公开默认密钥不再接受。

不使用 JWT 的宿主可设置 `moli.security.jwt.enabled=false`。该开关现在同时关闭自动配置的 JWT 属性 Bean、存储工厂、存储、服务、解析器和过滤器，不会因保留原 Redis 存储配置而创建它。宿主自行声明的 Bean 仍由宿主管理，Session 工具和宿主认证不受此开关控制。

## Redis 的模板选择明确化

RedisUtil 固定使用名为 `redisTemplate` 的模板，兼容 Boot 的 Object/Object 泛型，不再因标准 Boot 模板而启动失败。多个模板时，这个名称是明确的选择契约。

已有 Boot 或宿主模板保持原序列化方式，Boot 默认值仍为 JDK 序列化。仅当缺少此模板、且存在唯一连接工厂候选时，Quick 才创建使用字符串 key / Jackson 2 JSON value 的模板。更换已有缓存的序列化格式需要宿主自行迁移数据。

JWT 会话和用户对象支持 JDK 序列化，可与 Boot 默认模板组合使用；自定义用户属性的整个对象图也需要可序列化。使用原有 JSON 模板时，不改变 JWT 会话格式或 Redis Key。

需要显式使用 Quick JSON 序列化器时，可声明：

```java
@Bean("redisTemplate")
RedisTemplate<String, Object> redisTemplate(
        RedisConnectionFactory factory,
        @Qualifier("redisSerializer") RedisSerializer<Object> serializer) {
    var template = new RedisTemplate<String, Object>();
    template.setConnectionFactory(factory);
    template.setKeySerializer(new StringRedisSerializer());
    template.setHashKeySerializer(new StringRedisSerializer());
    template.setValueSerializer(serializer);
    template.setHashValueSerializer(serializer);
    return template;
}
```

以上类型来自 Spring Context、Spring Beans 和 Spring Data Redis。`redisSerializer` 是 Quick 提供的默认 Bean，可用同名 RedisSerializer 替换。Quick 自己创建的模板现在也通过参数注入使用该 Bean。

Redis 模块提供同步工具，改用 Boot 同步 Redis Starter；若宿主使用 ReactiveRedisTemplate，另外添加 `spring-boot-starter-data-redis-reactive`。

## MyBatis-Plus 对齐 Boot 4

Quick MyBatis-Plus Starter 现在传递引入 `mybatis-plus-spring-boot4-starter`。移除宿主中为补齐旧入口而添加的 Boot 3 MP Starter，保留适合数据库的数据源配置和驱动。

```yaml
moli:
  mybatis-plus:
    enabled: true
    db-type: POSTGRE_SQL
```

默认方言仍为 MySQL。可使用属性或提供 PaginationInnerInterceptor Bean，默认插件组合会使用该实例；需要完全自定义时继续提供 MybatisPlusInterceptor。`enabled=false` 只关闭 Quick 插件和填充逻辑，不关闭原生 MP 集成。

默认组合也收集宿主声明的租户、数据权限等 InnerInterceptor Bean，并保留 Spring 排序；分页放在最后，使数据查询及总数统计经过相同的租户和权限过滤。乐观锁和全表保护插件支持同类型 Bean 替换。

## Windows Java 17 构建

根 POM 统一固定 UTF-8 源码及报告编码，子模块继承该设置。Windows Java 17 使用 GBK 默认字符集时，也可直接执行项目总览中的构建命令，无需额外指定源码编码。

## 宿主 Bean 替换

- 签名模块：SignUtil、SignInterceptor、InterceptorsRegistrar 和 CacheFilter 支持按类型覆盖，组合使用选定实例。
- 生成器：CodeGenerator、CodegenController 和 CodegenExceptionHandler 支持按类型覆盖；资源映射配置使用 `codegenResources` Bean 名覆盖。
- Boot 4 没有宿主 Jackson 2 Mapper 时，签名模块使用内部兼容 Mapper，不再要求手动补 Bean。签名协议保持原样。幂等摘要的回退 Mapper 支持 JavaTime；Jackson 3 的自定义序列化配置不会自动同步到 Jackson 2。

## 更早发现错误配置

- 默认请求 ID 为 32 位 UUID，max-length 小于 32 时启动失败；需要短 ID 时，同时提供符合约束的 RequestIdGenerator。
- 启用幂等时检查默认超时为正、key 前缀非空白。
- 启用限流时检查默认额度、窗口为正、key 前缀非空白。
- 关闭相应功能时不因这些未使用的配置阻止启动。
- 生成器对 Spring MVC 请求错误保留 HTTP 状态和协议头，例如不支持 Content-Type 返回 415；内部错误继续使用脱敏 500。

Security、MyBatis-Plus、旧生成器及 OAuth 渠道补充了配置元数据。仓库新增 Starter 回归工作流，覆盖全模块后端验证和两个前端的测试、类型检查与构建；已有 OAuth 真实存储集成工作流保留。

## 幂等和限流的重载方法

未显式设置 namespace 时，同一声明类中的真实重载方法会在默认 namespace 后追加参数类型，避免防重 Key 或限流桶互相影响。非重载方法和显式 namespace 的 Key 格式不变；编译器桥接方法不触发该调整。需要有意共享防重范围或配额时，使用相同的显式 namespace 和业务 key 维度。

使用 Redis 且依赖默认 namespace 的重载方法需要协调部署：升级前后这些方法的 Key 不同，混合版本会短暂使用独立的防重窗口或限流桶。稳定的显式 namespace 可用于明确管理跨版本的共享范围。

幂等释放失败时保留原业务异常，存储释放异常作为 suppressed 异常附加；未成功释放的 Key 仍由 TTL 兜底。标准 Authorization/Bearer 组合接受认证方案大小写变化，Token 内容及自定义 Header/前缀的匹配方式保持原样。
