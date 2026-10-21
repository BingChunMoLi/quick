# quick-interceptors-demo

Quick 请求签名的 Vue 3 / TypeScript / Vite 演示，使用 Axios 和 jsrsasign 展示客户端待签名文本构造。它是前端子项目，不是 Maven 模块。

[签名后端模块](../../../../README.md) · [项目总览](../../../../../README.md)

## 运行

本目录提供 yarn.lock，按现有锁文件安装并运行：

```shell
yarn install
yarn dev
yarn build
```

脚本还提供 yarn type-check、yarn preview；实际脚本见 [package.json](package.json)。修改服务地址可创建 .env.local：

```dotenv
VITE_API_URL=http://127.0.0.1:8080
```

对应后端可使用仓库 common-test 演示应用，需先配置 Redis、RSA 公钥和相同的字段协议。

## 协议代码

- [request.ts](src/util/request.ts)：构造参数、时间戳、nonce，添加请求头与签名。
- [signed-request.mjs](src/util/signed-request.mjs)：在 Axios 完成 `transformRequest` 后，按实际 URL、查询／表单字段及序列化正文计算签名，并固定 URL 和正文供传输使用。
- [sign.ts](src/util/sign.ts)：jsrsasign RSA 签名示例。

后端使用 SHA256WithRSA 时，前端算法名与排序、路径、JSON 字符串序列化等约定也必须匹配。修改字段配置后应同步修改此演示，不能只替换某一个请求头。

`baseURL` 可带路径前缀，URL 内查询串和 `params` 会一同签入；使用 Axios 的最终序列化结果，支持自定义 `paramsSerializer` 和正文转换，签名后不会再次执行查询序列化。JSON 对象、已序列化 JSON 字符串、数组及 `false/0/null` 使用同一份实际发送的文本签名，不会给 JSON 字符串额外包一层引号。POST 表单按实际 URL 编码字段签名，重复字段或与签名元数据冲突的字段在发送前拒绝；其他正文类型不支持。签名输入只对实际 JSON 换行做与后端一致的移除，线上正文不改写。

示例使用头字段 `sign/t/nonce`、空 `Authorization` 和后端默认的 `ignore-parameters-list: [file]`。这些接入配置若变化，须同步调整辅助方法。可以运行 `npm test`：除 Java 共用向量外，还会启动本地 HTTP 服务，用临时 RSA 密钥验证 Axios 实际发出的请求，覆盖路径前缀、内嵌查询、JSON 字符串、表单、参数冲突和自定义转换。

声明 JSON 但未提供正文时，统一发送空字符串并保留 JSON Content-Type，使 Node HTTP 和浏览器 XHR 均按 `body=` 验签。复用请求配置时保留空正文含义，不将它再次序列化为 JSON 字符串，也不叠加签名转换。测试同时运行实际 Axios XHR 适配器，由 XMLHttpRequest 接口替身把最终头和正文转发给本地 HTTP 服务；该测试覆盖适配器行为，不依赖 Chrome。

## 演示边界

示例包含浏览器端私钥用法，仅用于理解协议；浏览器打包产物中的私钥不保密，不能据此建立生产身份认证边界。不要替换为真实服务端私钥。该页面不是 OAuth 登录 UI，也不参与统一生成器 UI 构建。
