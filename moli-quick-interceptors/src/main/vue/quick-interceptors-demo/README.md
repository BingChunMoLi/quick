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
- [sign.ts](src/util/sign.ts)：jsrsasign RSA 签名示例。

后端使用 SHA256WithRSA 时，前端算法名与排序、路径、JSON 字符串序列化等约定也必须匹配。修改字段配置后应同步修改此演示，不能只替换某一个请求头。

## 演示边界

示例包含浏览器端私钥用法，仅用于理解协议；浏览器打包产物中的私钥不保密，不能据此建立生产身份认证边界。不要替换为真实服务端私钥。该页面不是 OAuth 登录 UI，也不参与统一生成器 UI 构建。
