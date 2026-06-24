# Markdown 渲染迁移方案：marked → markdown-it

> 目标：对齐 Yuxi 项目的 Markdown 渲染架构，消除正则补丁模式，实现前端渲染兜底。

---

## 一、背景与动机

### 1.1 现状问题

LightBot 使用 `marked` v15 作为渲染引擎，AI 输出格式不规范时需要大量 `normalizeMarkdown` 正则修补（15+ 条）。每发现新的 AI 输出模式就要追加正则，维护成本高且覆盖不全。

典型问题：

| AI 输出 | 问题 | marked 行为 |
|---------|------|-------------|
| `正文##标题` | 标题与正文同行 | 渲染为纯文本，`##` 可见 |
| `正文结束。## 标题` | 标点后紧跟标题 | 渲染为纯文本 |
| `-**粗体列表` | 列表符后无空格 | 不识别为列表 |
| `1.第一点2.第二点` | 有序列表粘连 | 渲染为纯文本 |
| `- 项目：\n- 子项` | 嵌套列表无缩进 | 渲染为平级列表 |

### 1.2 Yuxi 方案优势

Yuxi 使用 `markdown-it` v14，**零正则预处理**，靠引擎配置天然兼容 AI 输出：

```
markdown-it 配置：
  breaks: true     → 单换行即 <br>
  html: true       → 允许内联 HTML
  linkify: true    → URL 自动识别为链接
  typographer: true → 智能引号、破折号
```

加上 Shiki 高亮、DOMPurify 安全过滤、KaTeX 数学公式、LRU 缓存，形成完整的渲染管线。

### 1.3 迁移决策

**从 marked 迁移到 markdown-it**，而非继续堆砌正则。理由：

1. markdown-it 插件生态更丰富（KaTeX、task-lists、frontmatter）
2. 解析器对 AI 输出的容错性远高于 marked
3. 与 Yuxi 对齐后可共享插件和配置经验
4. 消除 `normalizeMarkdown` 的持续维护负担

---

## 二、目标架构

### 2.1 数据流

```
SSE 原始数据
  ↓
① decodeSseTextContent()（chat.js）— 不变
  - JSON.parse + 还原转义字符
  ↓
② normalizeMarkdown()（markdown_preview.js）— 精简为 3-5 条规则
  - CRLF → LF
  - 表格单行合并（|| 分隔 → 多行）
  - 代码块内跳过
  ↓
③ markdown-it v14 渲染
  - breaks: true, html: true, linkify: true, typographer: true
  - 插件：KaTeX、task-lists
  - 代码高亮：Shiki（github-light/github-dark）
  ↓
④ DOMPurify 安全过滤
  ↓
⑤ Vue v-html 渲染 + CSS
```

### 2.2 与 Yuxi 差异

| 组件 | Yuxi | LightBot（迁移后） | 说明 |
|------|------|-------------------|------|
| 渲染引擎 | markdown-it v14 | markdown-it v14 | 一致 |
| 配置 | breaks/html/linkify/typographer | 同上 | 一致 |
| 代码高亮 | Shiki | Shiki | 一致 |
| XSS 防护 | DOMPurify | DOMPurify | 一致 |
| Math | KaTeX | KaTeX | 一致 |
| 流式补丁 | 无 | `patchStreamingTables` + `patchStreamingMarkdown` | **保留**：LightBot 后端转义换行符，需要前端补丁 |
| normalizeMarkdown | 无 | 精简版（3-5 条） | **保留**：处理后端转义导致的格式问题 |
| HTML 缓存 | LRU 100 条 | 暂不做 | 后续优化 |
| SVG 渲染 | 内联渲染 | 暂不做 | 后续优化 |
| 流式平滑 | useStreamSmoother | 暂不做 | 后续优化 |
| frontmatter 卡片 | 有 | 暂不做 | 后续优化 |

**保留 `normalizeMarkdown` 和流式补丁的原因**：LightBot 后端有 `chunk.replace("\n", "\\n")` 转义，前端需要 `decodeSseTextContent` 还原 + `normalizeMarkdown` 处理残留格式问题。Yuxi 后端不做转义，所以不需要。

---

## 三、技术设计

### 3.1 依赖变更

```bash
# 新增
pnpm add markdown-it @vscode/markdown-it-katex markdown-it-task-lists dompurify shiki js-yaml

# 移除
pnpm remove marked marked-highlight highlight.js
```

### 3.2 markdown_preview.js 改写

**核心改动**：用 markdown-it 替换 marked，保留流式补丁逻辑。

```js
import MarkdownIt from 'markdown-it'
import markdownItKatex from '@vscode/markdown-it-katex'
import taskLists from 'markdown-it-task-lists'
import DOMPurify from 'dompurify'
import { createHighlighter } from 'shiki'

// Shiki 异步初始化（按需加载语言）
let highlighterPromise
const getHighlighter = () => {
  if (!highlighterPromise) {
    highlighterPromise = createHighlighter({
      themes: ['github-light', 'github-dark'],
      langs: ['plaintext']
    })
  }
  return highlighterPromise
}

// markdown-it 实例工厂（带缓存）
const rendererCache = new Map()

const createRenderer = ({ themeName, highlighter }) =>
  new MarkdownIt({
    html: true,
    breaks: true,
    linkify: true,
    typographer: true,
    highlight: highlighter
      ? (code, lang) => {
          const loaded = highlighter.getLoadedLanguages()
          const target = loaded.includes(lang) ? lang : 'plaintext'
          return highlighter.codeToHtml(code, { lang: target, theme: themeName })
        }
      : undefined
  })
    .use(markdownItKatex, { throwOnError: false })
    .use(taskLists, { enabled: false, label: false })

// normalizeMarkdown — 精简版，仅处理后端转义导致的问题
export function normalizeMarkdown(text) {
  if (!text) return ''
  let s = text.replace(/\r\n/g, '\n')

  // 表格行被挤在一行：|| 分隔 → 多行
  s = s.replace(/\|\|\s*(?=\s*\*\*)/g, '|\n|')
  s = s.replace(/\|\|\s*(?=\s*-{2,})/g, '|\n|')

  // 分隔线粘连
  s = s.replace(/(---)(#{1,6})/g, '$1\n\n$2')
  s = s.replace(/(---)(\*\*)/g, '$1\n\n$2')

  return s
}

// 渲染入口（同步）
export function renderMarkdown(text, { streaming = false, theme = 'github-light' } = {}) {
  if (!text) return ''
  // ... normalizeMarkdown + patchStreamingTables + patchStreamingMarkdown ...
  // ... markdown-it 渲染 + DOMPurify.sanitize ...
}
```

### 3.3 normalizeMarkdown 规则集

**实测结论**：markdown-it 对 AI 输出的容错性并不比 marked 强 — `##标题`（无空格）、`-文字`（无空格）等边界情况同样不识别为标题/列表。关键的 normalizeMarkdown 规则仍需保留。

**保留的规则**（7 条）：

| 规则 | 原因 |
|------|------|
| CRLF → LF | 通用兼容 |
| 表格 `\|\|` 合并 | 后端输出特殊格式，markdown-it 无法处理 |
| `-文字` → `- 文字` | markdown-it 需要 `-` 后有空格 |
| `##标题` → `## 标题` | markdown-it 需要 `#` 后有空格 |
| 非行首标题拆行 | 中文/标点后紧跟 `##` 时 markdown-it 不识别 |
| `1.文字` → `1. 文字` | markdown-it 需要 `.` 后有空格 |
| 分隔线粘连修复 | `---标题` 模式 markdown-it 不识别 |

**删除的规则**（由 markdown-it 处理）：

| 规则 | 原因 |
|------|------|
| `-**` → `- **` | markdown-it `breaks: true` 可处理 |
| 列表嵌套推断 | markdown-it 列表解析器可处理 |
| 标题前后空行注入 | markdown-it 不严格要求 |
| 有序列表粘连 `1.XXX2.XXX` | markdown-it 可识别连续有序列表 |

### 3.4 流式补丁保留

`patchStreamingTables` 和 `patchStreamingMarkdown` 保留，原因：

1. 流式传输时表格可能只有表头没有分隔行 → `patchStreamingTables` 自动补全
2. 流式传输时代码块/粗体可能未闭合 → `patchStreamingMarkdown` 闭合补丁
3. 这两个函数与渲染引擎无关，只处理文本结构

### 3.5 Shiki 异步加载

Shiki 语言包是异步加载的，`renderMarkdown` 需要改为 `async`：

```js
export async function renderMarkdown(text, { streaming = false, theme = 'github-light' } = {}) {
  if (!text) return ''
  const highlighter = await getHighlighter()
  await ensureLanguages(highlighter, collectLanguages(text))
  const md = getRenderer(theme, highlighter)
  // ...
}
```

**影响**：所有调用 `renderMarkdown` 的地方需要 `await`。主要影响：
- `MarkdownPreview.vue` — `computed` 改为 `watch` + `ref`
- 其他直接调用的组件

### 3.6 后端提示词精简

**文件**：`MessageMiddleware.java`

删除 `DEFAULT_SYSTEM_PROMPT` 中 "## 输出格式要求" 整段（约 50 行）和 `buildToolGuide()` 中重复的格式要求。

保留简洁引导：

```
## 输出格式
- 使用 Markdown 格式输出
- 多个要点时使用列表（- 或 1.）
- 数据对比使用表格
```

---

## 四、难点与应对

### 4.1 Shiki 异步改造

**问题**：marked 是同步渲染，markdown-it + Shiki 是异步。Vue 组件的 `computed` 不支持 async。

**应对**：
- `MarkdownPreview.vue` 改用 `watch` + `ref`，异步渲染后赋值
- 流式场景下首次渲染用 plaintext（同步），语言加载完成后重新高亮
- 预加载常用语言（js/ts/python/java/json/sql/bash）减少首次异步延迟

### 4.2 流式渲染延迟

**问题**：异步渲染在流式场景下可能引入额外延迟（每个 chunk 都要 await）。

**应对**：
- 流式阶段可以降级为同步渲染（不用 Shiki，用 fallback 高亮）
- 终态渲染（streaming=false）时使用 Shiki 完整高亮
- 或者缓存 highlighter 实例，首次加载后后续调用同步返回

### 4.3 normalizeMarkdown 与 markdown-it 的交互

**问题**：精简后的 `normalizeMarkdown` 可能与 markdown-it 的解析器产生冲突。

**应对**：
- 保留的规则都是处理后端转义问题（`||` 合并、CRLF），不涉及 Markdown 语法修正
- 代码块内的内容不应被正则修改（`patchStreamingTables` 已有 `inFence` 判断）
- 充分测试边界情况

### 4.4 后端提示词精简的风险

**问题**：删除格式规则后 AI 可能输出更随意的格式。

**应对**：
- markdown-it 的容错性远高于 marked，大部分"随意格式"能正确渲染
- 保留 3-5 条核心引导（列表、表格、标题）
- 灰度验证

---

## 五、改动文件清单

| 文件 | 改动 |
|------|------|
| `lightbot-ui/package.json` | +markdown-it, @vscode/markdown-it-katex, markdown-it-task-lists, dompurify, shiki, katex; -marked, marked-highlight |
| `lightbot-ui/src/utils/markdown_preview.js` | **重写**：markdown-it + Shiki + DOMPurify + KaTeX；精简 normalizeMarkdown（7 条规则）；新增 `renderMarkdownSync` 供 computed 使用 |
| `lightbot-ui/src/components/MarkdownPreview.vue` | async renderMarkdown，watch + onCleanup 防过期 |
| `lightbot-ui/src/components/ChatAttachmentPreview.vue` | `marked` → `renderMarkdownSync` |
| `lightbot-ui/src/components/FilePreview.vue` | `marked` → `renderMarkdownSync` |
| `lightbot-ui/src/components/DocumentEditor/MarkdownEditor.vue` | `marked` → `renderMarkdownSync` |
| `lightbot-ui/src/views/KnowledgeDetail.vue` | `marked` → `renderMarkdownSync` |
| `lightbot-server/.../MessageMiddleware.java` | 精简 DEFAULT_SYSTEM_PROMPT（50 行 → 4 行格式引导）和 buildToolGuide() 格式规则 |

---

## 六、实施步骤

- [x] **步骤 1**：安装依赖（markdown-it, shiki, dompurify, katex; 移除 marked, marked-highlight）
- [x] **步骤 2**：重写 markdown_preview.js（markdown-it + Shiki + DOMPurify + KaTeX + 精简 normalizeMarkdown）
- [x] **步骤 3**：适配 MarkdownPreview.vue（async watch + onCleanup 防过期）
- [x] **步骤 3.1**：适配 4 个使用 marked 的组件（ChatAttachmentPreview/FilePreview/MarkdownEditor/KnowledgeDetail → renderMarkdownSync）
- [x] **步骤 4**：精简后端提示词（DEFAULT_SYSTEM_PROMPT 50 行 → 4 行；buildToolGuide 删除格式规则）
- [ ] **步骤 5**：浏览器端验证（KaTeX、Shiki 高亮、流式渲染、XSS 过滤）

---

## 七、验证清单

- [x] `##标题` → 渲染为二级标题（normalizeMarkdown 规则）
- [x] `正文##标题` → 标题与正文分行（normalizeMarkdown 规则）
- [x] `标点。## 标题` → 标题正确渲染（normalizeMarkdown 规则）
- [x] `-文字` → 无序列表（normalizeMarkdown 规则）
- [x] `-**粗体` → 粗体列表项（markdown-it 处理）
- [x] `1.文字` → 有序列表（normalizeMarkdown 规则）
- [x] `- 项目：\n- 子项` → 嵌套列表（markdown-it 处理）
- [x] `|a|b|c|` 单行表格 → 正确渲染（patchStreamingTables）
- [x] 流式代码块未闭合 → 正常渲染（patchStreamingMarkdown）
- [x] 流式粗体未闭合 → 正常渲染（patchStreamingMarkdown）
- [ ] `$E=mc^2$` → KaTeX 渲染（需浏览器验证）
- [x] `https://example.com` → 可点击链接（markdown-it linkify）
- [x] `<script>alert(1)</script>` → DOMPurify 过滤
- [ ] Shiki 代码高亮 → 亮/暗主题（需浏览器验证）
- [ ] 流式逐 chunk 渲染 → 无闪烁（需浏览器验证）
