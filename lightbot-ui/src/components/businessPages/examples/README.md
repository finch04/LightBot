# 业务页接入说明（H5）

本目录仅放接入文档，**不预装**任何业务 Vue 卡片（业务 UI 以能力中心登记的 `pageHtml` / `pageUrl` 为准）。

## 1. 在能力中心注册

二选一：

- **内嵌 HTML**：编写完整 HTML（含 CSS/JS）——推荐（可平台注身份 Header）
- **外链网页**：填写已部署地址（仅能 postMessage 收身份）

身份透传由平台默认开启（出站 `X-Zhiyuan-*` Header + `callerContext`），注册页无需额外配置。

## 2. 内嵌 HTML：页面可零感知

宿主会在 `srcdoc` 中**静默注入桥接**：

1. 拦截成功的 `fetch` / `XHR`（默认 `POST` / `PUT` / `PATCH`）
2. 把请求体（表单字段）作为结果回传对话；接口响开放 `extra`
3. 从页面 `<label>`（或 `data-label`）采集字段展示名，**平台不做字段中英词典**
4. 识别「取消」按钮（文案为取消，或 id/class 含 `cancel`，或 `data-lightbot-cancel`）
5. **默认证出站身份 Header**（与对话 `callerContext` 同源）

业务页只需写自己的校验与接口，**不必**写 `postMessage`，也**不必**手写拼身份 Header。

```js
const res = await fetch(API_URL || '/__lightbot_bp_demo__', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(form),
})
// 成功后无需通知父页面；宿主已自动回传
// 出站请求会自动带（若注册未关闭）：
//   X-Zhiyuan-External-User-Id / X-Zhiyuan-Region-Id / X-Zhiyuan-Enterprise-Id
```

### 读取调用方身份（可选）

```js
const ctx = window.LightBot?.getCallerContext?.()
// { externalUserId, regionId, enterpriseId, profile? }

const headers = window.LightBot?.getIdentityHeaders?.()
// { 'X-Zhiyuan-Region-Id': '510100', ... }

// 或监听 init
window.addEventListener('message', (e) => {
  const d = e.data
  if (d?.source === 'lightbot-business-page' && d.type === 'init') {
    console.log(d.payload.callerContext, d.payload.identityHeaders)
  }
})
```

### 字段标签（办结摘要）

请保证每个输入有可见 `<label>`，并与控件关联（包裹控件 / `for=id` / 紧邻上方）。摘要展示的是 label 原文，不会把 `name` 翻译成中文。

### 收窄自动捕获（可选）

通过工具 `options` 传入：

```json
{
  "autoCapture": true,
  "captureMethods": ["POST"],
  "captureUrlIncludes": ["/api/your/path"],
  "captureUrlExcludes": ["/api/log"]
}
```

### 兼容显式 API（可选）

```js
LightBot.submit({ orderId: 'x' })
LightBot.cancel()
LightBot.getCallerContext()
LightBot.getIdentityHeaders()
```

## 3. 外链网页

跨域无法注入桥接、**无法**强制注 Header。身份仅在 iframe `load` 后经 `postMessage` 下发（**不会**出现在 URL）。

可选：

- 页面内监听 `init`，自行把 `identityHeaders` 挂到业务请求
- 业务后端走自有登录态 / 网关

需要平台级强制 Header → 改用内嵌 `pageHtml`。

## 4. 业务后端约定

优先认平台标准 Header：

```http
X-Zhiyuan-External-User-Id: ...
X-Zhiyuan-Region-Id: ...
X-Zhiyuan-Enterprise-Id: ...
```

建议语义与问数一致：有企业 ID → 企业视角；无企业、有地区 → 行业视角。鉴权勿只信页面 Body 或 `profile`。

## 5. 优先级

宿主 `registerBusinessPageComponent` > **pageHtml（srcdoc + 静默桥接）** > pageUrl 外链。
