# LightBot 前端代码优化方案

> 日期：2026-06-21
> 范围：lightbot-ui/src 全部 Vue 文件
> 目标：组件复用、枚举统一、API 模式抽象、样式治理、状态管理优化

---

## 一、需求分析

### 1.1 项目现状

| 维度 | 现状 |
|------|------|
| 技术栈 | Vue 3.5 (Composition API + `<script setup>`) + Ant Design Vue 4.2.6 + Pinia 2.3 + Vue Router 4.5 |
| 源码规模 | 约 60+ 视图文件、30+ 组件文件、28 API 文件、3 Store 文件 |
| TypeScript | 无 |
| Lint/Format | 无 ESLint/Prettier |
| CSS 变量 | App.vue 定义了完善的设计系统变量，但几乎未被组件实际使用 |

### 1.2 核心问题分类

| 问题类型 | 严重程度 | 影响范围 | 说明 |
|---------|---------|---------|------|
| 页面骨架重复 | 高 | 15+ 页面 | `.page` / `.page-header` / `.page-title` 样式在每个管理页面重复定义 |
| 按钮样式重复 | 高 | 15+ 文件 | `.btn-primary` / `.btn-outline` 等按钮类在每个文件完整重复，约 2000 行 |
| 卡片列表重复 | 高 | 10+ 页面 | `.card-grid` / `.card-item` / `.empty-state` 在每个管理页面重复 |
| 枚举硬编码 | 中 | 20+ 文件 | 状态标签、类型映射、颜色映射散落在各页面，且存在不一致 |
| 工具函数重复 | 中 | 8+ 文件 | `formatTime` / `formatJson` 等函数在每个文件独立实现 |
| API 调用重复 | 中 | 12+ 页面 | 分页查询、CRUD 操作、启用/禁用切换模式完全相同 |
| SSE 连接分散 | 中 | 4 处 | 每处独立实现重连逻辑，不统一 |
| CSS 变量未落地 | 中 | 全局 | App.vue 定义了变量但 60+ 文件全部硬编码颜色值 |
| Store 利用率低 | 低 | 全局 | 仅 3 个 Store，跨页面共享数据通过重复 API 调用实现 |

---

## 二、技术设计

### 2.1 提取基础组件层

#### 2.1.1 PageLayout.vue — 页面骨架组件

**消除重复**：约 1500 行 CSS，涉及 15+ 文件

```vue
<!-- components/PageLayout.vue -->
<template>
  <div class="page">
    <div class="page-header">
      <div class="page-header-left">
        <h1 class="page-title">{{ title }}</h1>
        <p v-if="description" class="page-desc">{{ description }}</p>
      </div>
      <div class="page-header-actions">
        <slot name="actions" />
      </div>
    </div>
    <div class="page-body">
      <slot />
    </div>
  </div>
</template>

<script setup>
defineProps({
  title: { type: String, required: true },
  description: { type: String, default: '' }
})
</script>
```

**使用方式**：
```vue
<PageLayout title="Prompt 管理" description="管理系统提示词模板">
  <template #actions>
    <button class="btn-primary" @click="openCreate">新建 Prompt</button>
  </template>
  <!-- 内容 -->
</PageLayout>
```

#### 2.1.2 BaseButton.vue — 统一按钮组件

**消除重复**：约 2000 行 CSS，涉及 15+ 文件

```vue
<!-- components/BaseButton.vue -->
<template>
  <button :class="['btn', variantClass, sizeClass]" :disabled="disabled" @click="$emit('click')">
    <slot />
  </button>
</template>

<script setup>
const props = defineProps({
  variant: { type: String, default: 'primary' }, // primary | outline | cancel | link | icon
  size: { type: String, default: 'md' },         // sm | md
  disabled: { type: Boolean, default: false },
  danger: { type: Boolean, default: false }
})
defineEmits(['click'])

const variantClass = computed(() => `btn-${props.variant}`)
const sizeClass = computed(() => props.size === 'sm' ? `btn-${props.variant}-sm` : '')
</script>
```

#### 2.1.3 EntityCard.vue + EntityCardGrid.vue — 卡片列表组件

**消除重复**：约 1200 行 CSS + template，涉及 10+ 文件

```vue
<!-- components/EntityCardGrid.vue -->
<template>
  <div v-if="items.length" class="card-grid">
    <EntityCard v-for="item in items" :key="item.id" :item="item" @click="$emit('select', item)">
      <template #top-right><slot name="card-badge" :item="item" /></template>
      <template #actions><slot name="card-actions" :item="item" /></template>
    </EntityCard>
  </div>
  <div v-else class="empty-state">
    <slot name="empty">
      <p>{{ emptyText }}</p>
    </slot>
  </div>
</template>
```

#### 2.1.4 DetailModal.vue — 详情弹窗组件

**消除重复**：约 500 行 CSS，涉及 6+ 文件

```vue
<!-- components/DetailModal.vue -->
<template>
  <a-modal :open="open" :title="title" @cancel="$emit('close')" :footer="null" width="640px">
    <div class="detail-section">
      <div v-for="field in fields" :key="field.key" class="detail-row">
        <span class="detail-label">{{ field.label }}</span>
        <span class="detail-value">
          <slot :name="field.key" :value="record?.[field.key]">
            {{ record?.[field.key] ?? '-' }}
          </slot>
        </span>
      </div>
    </div>
  </a-modal>
</template>
```

#### 2.1.5 FormDialog.vue — 表单弹窗容器

**消除重复**：约 300 行 CSS，涉及 10+ 文件

```vue
<!-- components/FormDialog.vue -->
<template>
  <a-modal :open="open" :title="title" :confirm-loading="loading" @ok="$emit('submit')" @cancel="$emit('close')">
    <slot />
  </a-modal>
</template>
```

### 2.2 提取组合式函数

#### 2.2.1 useEntityList — 列表查询

**消除重复**：约 600 行 JS，涉及 12+ 文件

```javascript
// composables/useEntityList.js
export function useEntityList(fetchApi, options = {}) {
  const list = ref([])
  const loading = ref(false)
  const searchText = ref('')
  const pagination = reactive({ pageNum: 1, pageSize: options.pageSize || 100 })

  async function loadList() {
    loading.value = true
    try {
      const res = await fetchApi({ ...pagination, keyword: searchText.value })
      list.value = res.data?.records || res.data || []
    } finally {
      loading.value = false
    }
  }

  // 防抖搜索
  const debouncedSearch = useDebounceFn(loadList, 300)
  watch(searchText, debouncedSearch)

  onMounted(loadList)

  return { list, loading, searchText, pagination, loadList }
}
```

#### 2.2.2 useEntityCrud — CRUD 操作

**消除重复**：约 800 行 JS，涉及 10+ 文件

```javascript
// composables/useEntityCrud.js
export function useEntityCrud({ createApi, updateApi, deleteApi, loadList, formRef }) {
  const dialogVisible = ref(false)
  const editingId = ref(null)
  const submitting = ref(false)

  function openCreate() {
    editingId.value = null
    dialogVisible.value = true
  }

  function openEdit(row) {
    editingId.value = row.id
    dialogVisible.value = true
  }

  async function handleSubmit(formData) {
    submitting.value = true
    try {
      if (editingId.value) {
        await updateApi(editingId.value, formData)
      } else {
        await createApi(formData)
      }
      dialogVisible.value = false
      await loadList()
    } finally {
      submitting.value = false
    }
  }

  function handleDelete(id, name) {
    Modal.confirm({
      title: '确认删除',
      content: `确定要删除「${name}」吗？`,
      onOk: async () => {
        await deleteApi(id)
        await loadList()
      }
    })
  }

  return { dialogVisible, editingId, submitting, openCreate, openEdit, handleSubmit, handleDelete }
}
```

#### 2.2.3 useSSE — SSE 连接管理

**消除重复**：约 300 行 JS，涉及 4 处

```javascript
// composables/useSSE.js
export function useSSE(url, handlers = {}, options = {}) {
  const { maxRetries = 5, baseDelay = 1000 } = options
  let eventSource = null
  let retryCount = 0

  function connect() {
    eventSource = new EventSource(url)

    eventSource.onmessage = (e) => handlers.onMessage?.(JSON.parse(e.data))
    eventSource.onerror = () => {
      eventSource.close()
      if (retryCount < maxRetries) {
        const delay = baseDelay * Math.pow(2, retryCount++)
        setTimeout(connect, delay)
      } else {
        handlers.onError?.(new Error('max retries reached'))
      }
    }
  }

  function close() {
    eventSource?.close()
    retryCount = maxRetries // 阻止重连
  }

  onMounted(connect)
  onUnmounted(close)

  return { close }
}
```

### 2.3 统一工具函数

#### 2.3.1 utils/format.js 扩展

当前文件仅有 `truncateText` 和 `needTruncate`，需补充：

```javascript
// utils/format.js — 新增

/** 格式化时间（兼容数组、ISO、时间戳） */
export function formatTime(t) {
  if (!t) return '-'
  if (Array.isArray(t)) {
    const [y, m, d, h = 0, min = 0, s = 0] = t
    return new Date(y, m - 1, d, h, min, s).toLocaleString()
  }
  const date = new Date(t)
  return isNaN(date) ? String(t) : date.toLocaleString()
}

/** 格式化持续时间（毫秒 → 可读字符串） */
export function formatDuration(ms) {
  if (ms == null) return '-'
  if (ms < 1000) return `${ms}ms`
  if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`
  return `${Math.floor(ms / 60000)}m ${Math.round((ms % 60000) / 1000)}s`
}

/** 格式化 JSON（安全解析 + 缩进） */
export function formatJson(str) {
  if (!str) return ''
  try {
    const obj = typeof str === 'string' ? JSON.parse(str) : str
    return JSON.stringify(obj, null, 2)
  } catch {
    return String(str)
  }
}

/** JSON 预览（截断 + 单行） */
export function formatJsonPreview(str, maxLen = 120) {
  const s = typeof str === 'string' ? str : JSON.stringify(str)
  return s.length > maxLen ? s.slice(0, maxLen) + '...' : s
}
```

#### 2.3.2 utils/statusMap.js — 状态映射统一

```javascript
// utils/statusMap.js

/** 实验/任务状态 */
export const TASK_STATUS = {
  created:   { label: '已创建', color: '#a1a1aa' },
  pending:   { label: '等待中', color: '#f59e0b' },
  running:   { label: '运行中', color: '#3b82f6' },
  completed: { label: '已完成', color: '#22c55e' },
  failed:    { label: '失败',   color: '#ef4444' },
  stopped:   { label: '已停止', color: '#71717a' }
}

/** 文档状态 */
export const DOC_STATUS = {
  pending:    { label: '待处理', color: '#f59e0b' },
  processing: { label: '处理中', color: '#3b82f6' },
  completed:  { label: '已完成', color: '#22c55e' },
  failed:     { label: '失败',   color: '#ef4444' }
}

/** 工具类型 */
export const TOOL_TYPE_LABEL = {
  builtin:    '内置',
  knowledge:  '知识库',
  custom:     '自定义',
  api:        'API调用',
  mcp:        'MCP协议',
  http:       'HTTP',
  script:     '脚本'
}

export function getStatusColor(status) {
  return TASK_STATUS[status]?.color ?? '#a1a1aa'
}

export function getStatusLabel(status) {
  return TASK_STATUS[status]?.label ?? status
}
```

#### 2.3.3 utils/iconMap.js — 图标映射统一

从 `Landing.vue` 和 `SettingsView.vue` 中提取重复的图标映射表：

```javascript
// utils/iconMap.js
import { Bot, BookOpen, Database, Globe, ... } from 'lucide-vue-next'

export const ICON_OPTIONS = [
  { name: 'Bot', icon: Bot },
  { name: 'BookOpen', icon: BookOpen },
  // ... 完整列表
]

export const ICON_MAP = Object.fromEntries(ICON_OPTIONS.map(o => [o.name, o.icon]))
```

### 2.4 补充 Pinia Store

#### 2.4.1 stores/modelProvider.js — 模型提供商缓存

当前 `ModelSelect.vue`、`AgentDetail.vue`、`SubAgentManage.vue`、`Playground.vue`、`PromptDetail.vue` 等 5+ 页面各自独立调用 `getProvidersWithModels`。

```javascript
// stores/modelProvider.js
import { defineStore } from 'pinia'
import { getProvidersWithModels } from '@/api/model'

export const useModelProviderStore = defineStore('modelProvider', {
  state: () => ({
    providers: [],
    loaded: false,
    loading: false
  }),
  actions: {
    async ensureLoaded() {
      if (this.loaded || this.loading) return
      this.loading = true
      try {
        const res = await getProvidersWithModels()
        this.providers = res.data || []
        this.loaded = true
      } finally {
        this.loading = false
      }
    }
  }
})
```

#### 2.4.2 stores/tool.js — 工具列表缓存

`AgentDetail.vue`、`SubAgentManage.vue`、`WorkflowEdit.vue` 各自独立调用 `getTools`。

#### 2.4.3 stores/knowledge.js — 知识库列表缓存

`AgentDetail.vue`、`WorkflowEdit.vue` 各自独立调用 `getKnowledgeList`。

#### 2.4.4 stores/task.js 改造

当前使用 `reactive()` 导出而非 `defineStore`，需改造为标准 Pinia store 以支持 DevTools 调试。

### 2.5 CSS 设计系统落地

#### 2.5.1 补充 CSS 变量

`MarkdownPreview.vue` 引用了 `--gray-25`、`--gray-1000`、`--main-700` 等变量，但 App.vue 中未定义。需在 App.vue 的 `:root` 中补充：

```css
:root {
  /* 已有变量保持不变 */
  --color-primary: #171717;
  --color-body: #71717a;
  --color-link: #0070f3;
  --color-hairline: #ebebeb;
  --color-canvas-soft: #fafafa;

  /* 补充 MarkdownPreview 需要的变量 */
  --gray-25: #fafafa;
  --gray-1000: #171717;
  --main-700: #0070f3;
}
```

#### 2.5.2 硬编码颜色替换

全项目硬编码颜色值统计：

| 颜色值 | 含义 | 出现次数 | 对应 CSS 变量 |
|--------|------|---------|--------------|
| `#171717` | 主色/标题色 | 100+ | `var(--color-primary)` |
| `#71717a` | 次要文字色 | 200+ | `var(--color-body)` |
| `#0070f3` | 链接/强调色 | 80+ | `var(--color-link)` |
| `#ebebeb` | 边框色 | 50+ | `var(--color-hairline)` |
| `#fafafa` | 背景色 | 40+ | `var(--color-canvas-soft)` |

**执行策略**：可编写脚本批量搜索替换，按文件逐个验证无视觉变化后提交。

---

## 三、枚举主题色统一方案

### 3.1 问题现状

项目中不同枚举有各自的主题色（Ant Design Tag 颜色），但颜色映射散落在各前端页面，且存在不一致。

#### 3.1.1 典型案例：TaskCenter

后端 `TaskType` 枚举：
```java
public enum TaskType {
    DOCUMENT_UPLOAD("document_upload", "文档上传", "documentUploadExecutor"),
    DOCUMENT_INGEST("document_ingest", "文档入库", "documentIngestExecutor"),
    DOCUMENT_OCR("document_ocr", "文档OCR", "documentOcrExecutor"),
    EXPERIMENT_RUN("experiment_run", "实验执行", "experimentRunExecutor"),
    BENCHMARK_GENERATE("benchmark_generate", "基准生成", "benchmarkGenerateExecutor"),
    BENCHMARK_IMPORT("benchmark_import", "基准导入", "benchmarkImportExecutor"),
    RAG_EVALUATION("rag_evaluation", "RAG评估", "ragEvaluationExecutor"),
    GRAPH_EXTRACTION("graph_extraction", "图谱抽取", "graphExtractionExecutor"),
    QA_PAIR_GENERATE("qa_pair_generate", "问答对生成", "qaPairGenerateExecutor");
}
```

前端 `TaskCenter.vue` 硬编码颜色：
```javascript
const typeColor = {
  '文档上传': 'blue',      // 用中文 desc 当 key，脆弱
  '文档入库': 'green',
  '文档OCR': 'orange',
  '实验执行': 'cyan',
  '基准生成': 'purple',
  '基准导入': 'volcano',
  'RAG评估': 'magenta',
  '图谱抽取': 'geekblue',
  '问答对生成': 'gold',
}

const statusMap = {
  pending: '等待中',
  running: '执行中',
  success: '已完成',
  failed: '失败',
  cancelled: '已取消',
}
```

**问题**：
1. `TaskType` 的 `@JsonValue` 在 `getDesc()` 上，序列化返回中文，前端用中文当 key 做映射——后端改 desc 文案就崩
2. `EnumController` **没有暴露 TaskType 和 TaskStatus**
3. `statusMap` / `statusBadge` 与后端 `TaskStatus` 枚举的 desc 重复维护
4. 类型颜色只在 TaskCenter 定义，其他页面如果需要展示任务类型颜色得重新写一份

#### 3.1.2 全局散落的颜色映射

| 页面 | 映射对象 | 硬编码位置 |
|------|---------|-----------|
| TaskCenter.vue | 任务类型 → Tag 颜色 | `typeColor` 对象（9 项） |
| TaskCenter.vue | 任务状态 → 中文标签 + Badge 状态 | `statusMap` + `statusBadge`（5 项） |
| Eval.vue | 实验状态 → Tag 颜色 + 中文标签 | `statusColor` + `statusLabel`（5 项） |
| DashboardView.vue | 任务状态 → 颜色 | 独立映射 |
| Observability.vue | 状态 → 标签 | 独立映射 |
| KnowledgeDetail.vue | 文档状态 → 颜色 + 标签 | `statusText` + `statusColor` + `docStatusColor` |
| LogMonitor.vue | 日志级别 | 硬编码 `[{ value: 'INFO', label: 'INFO' }]` |
| bindingTheme.js | 工具类型 → 标签 | `TOOL_TYPE_LABEL_MAP`（5 项） |
| WorkflowEdit.vue | 工具类型 → 标签 | `getToolTypeLabel`（4 项，与 bindingTheme 不一致） |
| ToolManage.vue | 工具类型 → 标签 + 颜色 | `toolTypeLabels` + `typeColors` |

### 3.2 设计思路

**核心原则**：颜色是枚举的展示属性，跟随枚举值走，不应由前端单独维护。

**不做的事**：
- 不在 Java 枚举实体上加 `color` 字段（枚举是业务概念，颜色是 UI 概念，不应耦合）
- 不搞动态主题配置系统（过度设计）

**做的事**：
- `EnumController` 的 `EnumVO` 扩展为 `code + label + color` 三元组
- 颜色定义在 Controller 层（靠近枚举定义，一处维护）
- 前端通过 Pinia store 缓存，页面直接消费，不再自己写映射

### 3.3 后端改造

#### 3.3.1 EnumVO 扩展

```java
public static class EnumVO {
    private String value;   // code: "document_upload"
    private String label;   // 中文: "文档上传"
    private String color;   // Ant Design Tag 颜色: "blue"

    public EnumVO(String value, String label, String color) {
        this.value = value;
        this.label = label;
        this.color = color;
    }
    // getter/setter
}
```

#### 3.3.2 EnumController 扩展

```java
@Operation(summary = "获取任务类型枚举")
@GetMapping("/task-types")
public Result<List<EnumVO>> getTaskTypes() {
    return Result.ok(List.of(
        new EnumVO("document_upload",    "文档上传",  "blue"),
        new EnumVO("document_ingest",    "文档入库",  "green"),
        new EnumVO("document_ocr",       "文档OCR",   "orange"),
        new EnumVO("experiment_run",     "实验执行",  "cyan"),
        new EnumVO("benchmark_generate", "基准生成",  "purple"),
        new EnumVO("benchmark_import",   "基准导入",  "volcano"),
        new EnumVO("rag_evaluation",     "RAG评估",   "magenta"),
        new EnumVO("graph_extraction",   "图谱抽取",  "geekblue"),
        new EnumVO("qa_pair_generate",   "问答对生成", "gold")
    ));
}

@Operation(summary = "获取任务状态枚举")
@GetMapping("/task-statuses")
public Result<List<EnumVO>> getTaskStatuses() {
    return Result.ok(List.of(
        new EnumVO("pending",   "等待中",  "processing"),  // Badge status
        new EnumVO("running",   "执行中",  "processing"),
        new EnumVO("success",   "已完成",  "success"),
        new EnumVO("failed",    "失败",    "error"),
        new EnumVO("cancelled", "已取消",  "default")
    ));
}

@Operation(summary = "获取文档状态枚举")
@GetMapping("/document-statuses")
public Result<List<EnumVO>> getDocumentStatuses() {
    return Result.ok(List.of(
        new EnumVO("pending",    "待处理", "default"),
        new EnumVO("processing", "处理中", "processing"),
        new EnumVO("completed",  "已完成", "success"),
        new EnumVO("failed",     "失败",   "error")
    ));
}
```

**注意**：颜色值区分两种语义——
- Tag 颜色：`blue` / `green` / `orange` / `cyan` / `purple` / `volcano` / `magenta` / `geekblue` / `gold`（用于 `<a-tag :color>`)
- Badge 状态：`processing` / `success` / `error` / `default` / `warning`（用于 `<a-badge :status>`）

两种语义不同，需要分别返回或在 VO 中加一个 `badgeStatus` 字段。简单起见，可以统一用 `color` 字段存 Tag 颜色，Badge 状态由前端根据 `value` 映射（因为 Badge 状态只有 5 种固定值，不会扩展）。

#### 3.3.3 已有枚举接口改造

当前 `toEnumVOList` 用反射取 `code`/`desc` 字段，需改为显式传入颜色：

```java
// 方案：每个枚举接口独立构建 EnumVO 列表，不再用通用反射方法
// 优点：颜色可控，不需要在枚举实体上加 UI 属性
// 缺点：每新增一个枚举值需要同步修改 EnumController

@Operation(summary = "获取工具类型枚举")
@GetMapping("/tool-types")
public Result<List<EnumVO>> getToolTypes() {
    return Result.ok(List.of(
        new EnumVO("builtin",   "内置",    "blue"),
        new EnumVO("knowledge", "知识库",  "green"),
        new EnumVO("custom",    "自定义",  "orange"),
        new EnumVO("api",       "API调用", "cyan"),
        new EnumVO("mcp",       "MCP协议", "purple"),
        new EnumVO("http",      "HTTP",    "geekblue"),
        new EnumVO("script",    "脚本",    "gold")
    ));
}
```

### 3.4 前端改造

#### 3.4.1 枚举 Store

```javascript
// stores/enum.js
import { defineStore } from 'pinia'
import { getEnumList } from '@/api/enum'

export const useEnumStore = defineStore('enum', {
  state: () => ({
    // 各枚举列表：[{ value, label, color }]
    taskTypes: [],
    taskStatuses: [],
    documentStatuses: [],
    toolTypes: [],
    modelProviderTypes: [],
    agentStatuses: [],
    modelTypes: [],
    loaded: false
  }),

  getters: {
    // 通用查找：根据枚举类型和 value 返回完整对象
    find: (state) => (enumKey, value) => {
      return state[enumKey]?.find(item => item.value === value)
    },

    // 快捷 getter：获取颜色
    taskTypeColor: (state) => (value) => {
      return state.taskTypes.find(t => t.value === value)?.color ?? 'default'
    },

    taskStatusLabel: (state) => (value) => {
      return state.taskStatuses.find(t => t.value === value)?.label ?? value
    },

    toolTypeLabel: (state) => (value) => {
      return state.toolTypes.find(t => t.value === value)?.label ?? value
    }
  },

  actions: {
    async ensureLoaded() {
      if (this.loaded) return
      const [taskTypes, taskStatuses, documentStatuses, toolTypes,
             modelProviderTypes, agentStatuses, modelTypes] = await Promise.all([
        getEnumList('task-types'),
        getEnumList('task-statuses'),
        getEnumList('document-statuses'),
        getEnumList('tool-types'),
        getEnumList('model-provider-types'),
        getEnumList('agent-statuses'),
        getEnumList('model-types')
      ])
      this.taskTypes = taskTypes
      this.taskStatuses = taskStatuses
      this.documentStatuses = documentStatuses
      this.toolTypes = toolTypes
      this.modelProviderTypes = modelProviderTypes
      this.agentStatuses = agentStatuses
      this.modelTypes = modelTypes
      this.loaded = true
    }
  }
})
```

#### 3.4.2 TaskCenter 改造对比

**改造前**（前端硬编码）：
```javascript
// TaskCenter.vue
const typeColor = {
  '文档上传': 'blue',
  '文档入库': 'green',
  // ... 9 项
}
const statusMap = {
  pending: '等待中',
  running: '执行中',
  // ... 5 项
}
// 模板中
<a-tag :color="typeColor[record.type] || 'default'">{{ record.type }}</a-tag>
<a-badge :status="statusBadge[record.status]" :text="statusMap[record.status]" />
```

**改造后**（消费 store）：
```javascript
// TaskCenter.vue
import { useEnumStore } from '@/stores/enum'
const enumStore = useEnumStore()
onMounted(() => enumStore.ensureLoaded())

// 模板中
<a-tag :color="enumStore.taskTypeColor(record.typeCode)">{{ record.typeLabel }}</a-tag>
<a-badge :status="enumStore.taskStatusBadge(record.status)" :text="enumStore.taskStatusLabel(record.status)" />
```

前端不再维护任何颜色/标签映射，全部从 store 获取。

#### 3.4.3 通用枚举渲染组件

```vue
<!-- components/EnumTag.vue -->
<template>
  <a-tag :color="item?.color ?? 'default'">{{ item?.label ?? value }}</a-tag>
</template>

<script setup>
import { computed } from 'vue'
import { useEnumStore } from '@/stores/enum'

const props = defineProps({
  enumKey: { type: String, required: true },  // 'taskTypes' | 'toolTypes' | ...
  value:   { type: String, required: true }
})

const enumStore = useEnumStore()
const item = computed(() => enumStore.find(props.enumKey, props.value))
</script>
```

使用：
```vue
<EnumTag enum-key="taskTypes" :value="record.typeCode" />
<EnumTag enum-key="toolTypes" :value="tool.type" />
```

### 3.5 遗留问题处理

#### 3.5.1 TaskType 的 @JsonValue 问题

当前 `TaskType` 的 `@JsonValue` 在 `getDesc()` 上，序列化返回中文（"文档上传"）。这导致前端用中文当 key，后端改 desc 文案就崩。

**建议**：`@JsonValue` 改到 `getCode()` 上，序列化返回 code（"document_upload"）。前端统一用 code 做 key。

```java
// TaskType.java — 修改
@JsonValue
public String getCode() {   // 原来是 getDesc()
    return code;
}
```

**影响范围**：需要检查前端所有依赖 `record.type === '文档上传'` 的地方改为 `record.type === 'document_upload'`。由于 TaskType 序列化值变更，这是一次**不兼容变更**，需要前后端同步发布。

#### 3.5.2 TaskStatus 同理

`TaskStatus` 的 `@JsonValue` 已经在 `getCode()` 上（返回 "pending"/"running" 等），这是正确的，无需修改。

#### 3.5.3 不走后端的枚举

以下枚举不需要后端接口，统一到前端常量文件：

| 枚举 | 位置 | 理由 |
|------|------|------|
| 日志级别 | `utils/constants.js` | 固定不变（INFO/WARN/ERROR/DEBUG） |
| 图标列表 | `utils/iconMap.js` | 纯前端展示，不涉及业务 |
| 实体类型 | `utils/constants.js` | 稳定，但可后续改为后端接口 |

### 3.6 工作量估算

| 改进项 | 预估工时 | 依赖 |
|--------|---------|------|
| EnumVO 扩展（code + label + color） | 0.5 天 | 无 |
| EnumController 补充 task-types / task-statuses / document-statuses / tool-types 等接口 | 0.5 天 | EnumVO |
| 前端 stores/enum.js + api/enum.js | 0.5 天 | 后端接口 |
| EnumTag 通用组件 | 0.5 小时 | stores/enum.js |
| TaskCenter.vue 改造（删除硬编码映射，改用 store） | 0.5 天 | stores/enum.js |
| Eval.vue / Observability.vue / KnowledgeDetail.vue 改造 | 1 天 | stores/enum.js |
| bindingTheme.js / WorkflowEdit.vue / ToolManage.vue 工具类型统一 | 0.5 天 | stores/enum.js |
| TaskType @JsonValue 改为 getCode() + 前端适配 | 0.5 天 | 需前后端同步 |

**总计：约 4 人天**

---

## 四、难点与风险

### 4.1 WorkflowEdit.vue 重构

- 文件 3475 行，其中 `<style scoped>` 超过 1000 行
- 包含 VueFlow 画布、节点拖拽、边管理、版本管理、自动保存等复杂逻辑
- 大量使用 `:deep()` 选择器，样式迁移需逐个验证
- **建议**：分批提取子组件（如将 toolbar 样式迁移到 WorkflowEditToolbar.vue），而非一次性重构

### 4.2 Chat.vue 流式处理

- 约 2500 行，包含 SSE 流式对话、工具事件、工作流事件、敏感词阻断
- 流式处理的回调链复杂，提取 `useSSE` 时需要保持回调接口兼容
- **建议**：先提取 SSE 基础连接层，Chat.vue 的业务逻辑暂不动

### 4.3 CSS 变量迁移兼容

- `MarkdownPreview.vue` 使用的变量名（`--gray-25`、`--gray-1000`）与 App.vue 定义的（`--color-canvas-soft`、`--color-ink`）命名体系不一致
- **建议**：保留两套命名（别名映射），逐步迁移到新命名体系

### 4.4 无 TypeScript 的类型安全

- 所有 API 返回值和 props 都没有类型约束
- 提取通用组件/函数时容易遗漏边界情况
- **建议**：先补 JSDoc 注释，后续渐进引入 TypeScript

---

## 五、工作量估算

| 序号 | 优化项 | 预估工时 | 优先级 | 依赖 |
|------|--------|---------|--------|------|
| 1 | 提取 `utils/format.js` (formatTime, formatDuration, formatJson) | 0.5 天 | P0 | 无 |
| 2 | 后端 EnumVO 扩展（code+label+color）+ EnumController 补充 task-types/task-statuses/document-statuses/tool-types 接口 | 1 天 | P0 | 无 |
| 3 | 前端 `stores/enum.js` + `api/enum.js` + `EnumTag.vue` 通用组件 | 0.5 天 | P0 | #2 |
| 4 | TaskCenter.vue 改造（删除硬编码 typeColor/statusMap/statusBadge，改用 enumStore） | 0.5 天 | P0 | #3 |
| 5 | 提取 `BaseButton.vue` 全局按钮组件 | 1 天 | P1 | 无 |
| 6 | 提取 `PageLayout.vue` 页面骨架组件 | 1 天 | P1 | 无 |
| 7 | 提取 `EntityCard.vue` + `EntityCardGrid.vue` | 1.5 天 | P1 | #6 |
| 8 | 提取 `useEntityList` 组合式函数 | 1 天 | P1 | 无 |
| 9 | 提取 `useEntityCrud` 组合式函数 | 1.5 天 | P1 | #8 |
| 10 | Eval.vue / Observability.vue / KnowledgeDetail.vue 枚举改造（删除硬编码映射，改用 enumStore） | 1 天 | P1 | #3 |
| 11 | bindingTheme.js / WorkflowEdit.vue / ToolManage.vue 工具类型统一（删除重复定义） | 0.5 天 | P1 | #3 |
| 12 | TaskType @JsonValue 改为 getCode() + 前端适配（不兼容变更，需前后端同步） | 0.5 天 | P1 | #3 |
| 13 | 提取 `DetailModal.vue` + `FormDialog.vue` | 1 天 | P2 | 无 |
| 14 | 补充 CSS 变量并批量替换硬编码颜色 | 2 天 | P2 | 无 |
| 15 | 创建 `stores/modelProvider.js` 等共享 store | 1 天 | P2 | 无 |
| 16 | 将 `task.js` 改造为标准 Pinia store | 0.5 天 | P2 | 无 |
| 17 | 提取 `useSSE` 组合式函数 | 1 天 | P2 | 无 |
| 18 | WorkflowEdit.vue 样式迁移至子组件 | 3 天 | P3 | 其他组件提取完成 |
| 19 | Chat.vue 流式逻辑重构 | 2 天 | P3 | #17 |
| 20 | 清理死代码 | 0.5 天 | P3 | 无 |

**总预估：约 22 人天**（其中枚举统一约 4 人天）

**实施节奏**：
- P0（2.5 天）：format 工具函数 + 枚举主题色统一（消除 TaskCenter 等页面的硬编码映射）
- P1（6 天）：组件抽取 + 枚举全面改造 + @JsonValue 修正
- P2（5.5 天）：架构级改进（CSS 变量、共享 store、SSE 封装）
- P3（5.5 天）：大型文件深度重构，风险较高

---

*文档生成时间: 2026-06-21*
