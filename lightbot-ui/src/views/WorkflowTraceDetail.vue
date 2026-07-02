<template>
  <div class="workflow-trace-detail">
    <!-- 顶部栏 -->
    <div class="trace-header">
      <div class="header-left">
        <a-button type="text" @click="router.push('/app/observability')"><ArrowLeftOutlined /> 返回</a-button>
        <span class="header-title">工作流链路详情</span>
        <a-tag v-if="trace" :color="trace.status === 'completed' ? 'success' : trace.status === 'failed' ? 'error' : 'processing'">
          {{ trace.status === 'completed' ? '成功' : trace.status === 'failed' ? '失败' : '运行中' }}
        </a-tag>
      </div>
      <div class="header-right">
        <a-radio-group v-model:value="viewMode" button-style="solid" size="small">
          <a-radio-button value="graph"><ApartmentOutlined /> 图</a-radio-button>
          <a-radio-button value="text"><UnorderedListOutlined /> 文</a-radio-button>
        </a-radio-group>
      </div>
    </div>

    <!-- 基本信息栏 -->
    <div v-if="trace" class="trace-info-bar">
      <div class="info-item">
        <span class="info-label">Agent</span>
        <span class="info-val">{{ trace.agentName || '-' }}</span>
      </div>
      <div class="info-item">
        <span class="info-label">耗时</span>
        <span class="info-val">{{ formatDuration(trace.totalDurationMs) }}</span>
      </div>
      <div class="info-item">
        <span class="info-label">Token</span>
        <span class="info-val">
          <span class="token-input">入 {{ trace.inputTokens ?? 0 }}</span>
          <span class="token-sep">/</span>
          <span class="token-output">出 {{ trace.outputTokens ?? 0 }}</span>
        </span>
      </div>
      <div v-if="trace.requestId" class="info-item">
        <span class="info-label">Request ID</span>
        <span class="info-val rid-text">{{ trace.requestId }}</span>
      </div>
    </div>

    <!-- 用户问题 -->
    <div v-if="userInput" class="reply-section user-question-section">
      <div class="reply-section-title">用户问题</div>
      <div class="user-question-text">{{ userInput }}</div>
    </div>

    <!-- 回复摘要（独立区域，Markdown 渲染） -->
    <div v-if="trace?.replyContent" class="reply-section">
      <div class="reply-section-title">回复摘要</div>
      <div class="reply-content-box">
        <MarkdownPreview :content="trace.replyContent" :finalized="true" />
      </div>
    </div>

    <a-spin :spinning="loading">
      <!-- 图模式：与编排页统一的 Vue Flow 只读画板 -->
      <div v-if="viewMode === 'graph'" class="graph-container">
        <div v-if="viewerNodes.length" class="graph-viewer-wrap">
          <WorkflowViewerCanvas
            flow-id="workflow-trace-viewer"
            :nodes="viewerNodes"
            :edges="viewerEdges"
            :node-states="viewerNodeStates"
            :highlighted-edge-ids="highlightedEdgeIds"
            :selected-node-id="selectedCanvasNodeId"
            @node-click="onViewerNodeClick"
            @pane-click="selectedNodeId = null"
          />
        </div>
        <div v-else class="graph-empty-hint">暂无工作流图快照，请切换到文本模式查看节点详情</div>

        <!-- 节点详情：与编排页一致的只读配置面板 + 本次执行结果 -->
        <WorkflowNodeDetailPanel
          v-if="selectedWorkflowNode"
          class="trace-node-detail-panel"
          compact
          :node="selectedWorkflowNode"
          :edges="viewerEdges"
          force-readonly
          :show-header-actions="false"
          :show-footer-delete="false"
          :execution-span="selectedNodeSpan"
          :llm-child-span="selectedNodeSpan ? llmChildSpan(selectedNodeSpan) : null"
          :config-incomplete-hint="configSnapshotIncomplete"
          :node-errors="[]"
          :knowledge-list="knowledgeList"
          :tools="tools"
          :target-nodes="traceTargetNodes"
          :filter-knowledge-option="filterKnowledgeOption"
          :filter-tool-option="filterToolOption"
          :get-tool-type-label="getToolTypeLabel"
          :get-node-color="getNodeColor"
          :get-node-title="getNodeTitle"
          :is-group-builtin-node="isGroupBuiltinNodeFn"
          @close="selectedNodeId = null"
        />
      </div>

      <!-- 文本模式 -->
      <div v-if="viewMode === 'text' && nodeSpans.length" class="text-container">
        <div v-for="(span, idx) in nodeSpans" :key="span.spanId"
             class="text-node-card" :class="{ 'node-success': span.status === 'completed', 'node-error': span.status === 'failed' }">
          <div class="tn-header" @click="toggleTextExpand(span.spanId)">
            <span class="tn-index">{{ idx + 1 }}</span>
            <span class="tn-icon">{{ getNodeIcon(span.attributes?.nodeType) }}</span>
            <span class="tn-label">{{ span.attributes?.nodeLabel || span.spanId }}</span>
            <a-tag :color="span.status === 'completed' ? 'success' : 'error'" size="small">{{ span.status === 'completed' ? '成功' : '失败' }}</a-tag>
            <span class="tn-duration">{{ formatDuration(span.durationMs) }}</span>
            <span class="tn-expand">{{ textExpanded.has(span.spanId) ? '▲' : '▼' }}</span>
          </div>
          <div v-if="textExpanded.has(span.spanId)" class="tn-body">
            <div v-if="span.attributes?.message" class="tn-field">
              <span class="tn-key">消息</span>
              <span class="tn-val">{{ span.attributes.message }}</span>
            </div>
            <div v-if="span.attributes?.config && Object.keys(span.attributes.config).length" class="tn-section">
              <div class="tn-section-title">节点配置</div>
              <pre class="tn-json">{{ JSON.stringify(span.attributes.config, null, 2) }}</pre>
            </div>
            <div v-if="span.attributes?.input && Object.keys(span.attributes.input).length" class="tn-section">
              <div class="tn-section-title">输入参数</div>
              <pre class="tn-json">{{ JSON.stringify(span.attributes.input, null, 2) }}</pre>
            </div>
            <div v-if="span.attributes?.outputs && Object.keys(span.attributes.outputs).length" class="tn-section">
              <div class="tn-section-title">输出结果</div>
              <pre class="tn-json">{{ JSON.stringify(filterOutputs(span.attributes.outputs), null, 2) }}</pre>
            </div>
            <div v-if="llmMessages(span)" class="tn-section">
              <div class="tn-section-title">LLM 上下文（完整消息列表）</div>
              <div class="llm-messages-list">
                <div v-for="(msg, mi) in llmMessages(span)" :key="mi" class="llm-msg-item" :class="'llm-msg-' + msg.role">
                  <span class="llm-msg-role">{{ msg.role }}</span>
                  <pre class="llm-msg-content">{{ msg.content }}</pre>
                </div>
              </div>
            </div>
            <div v-if="span.attributes?.detail" class="tn-section">
              <div class="tn-section-title">执行详情</div>
              <div class="tn-text-block">{{ span.attributes.detail }}</div>
            </div>
            <div v-if="llmChildSpan(span)" class="tn-section">
              <div class="tn-section-title">LLM 调用</div>
              <pre class="tn-json">{{ formatLlmAttrs(llmChildSpan(span).attributes) }}</pre>
            </div>
          </div>
        </div>
      </div>
    </a-spin>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowLeftOutlined,
  ApartmentOutlined,
  UnorderedListOutlined,
} from '@ant-design/icons-vue'
import { getTraceDetail } from '../api/observability'
import { getKnowledgeList } from '../api/knowledge'
import { getTools } from '../api/tool'
import MarkdownPreview from '../components/MarkdownPreview.vue'
import WorkflowViewerCanvas from './workflow/components/WorkflowViewerCanvas.vue'
import WorkflowNodeDetailPanel from './workflow/components/edit/WorkflowNodeDetailPanel.vue'
import { workflowGraphToVueFlow, mergeTraceNodeData } from './workflow/workflowGraphToVueFlow.js'
import { spansToNodeStates, buildExecutedEdgeIds } from './workflow/workflowViewerAdapter.js'
import { getNodeIconText as getNodeIcon } from '../utils/nodeStyleUtils'
import { getNodeTitle, getNodeColor } from '../views/workflow/nodeMeta'
import { isGroupBuiltinType } from './workflow/workflowGroup.js'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const trace = ref(null)
const viewMode = ref('graph')
const selectedNodeId = ref(null)
const textExpanded = ref(new Set())
const knowledgeList = ref([])
const tools = ref([])

// --- Data loading ---

async function loadResources() {
  try {
    const [knowledgeRes, toolRes] = await Promise.all([
      getKnowledgeList({ pageNum: 1, pageSize: 200 }),
      getTools({ pageNum: 1, pageSize: 200 }),
    ])
    knowledgeList.value = knowledgeRes.data?.records || knowledgeRes.data || []
    tools.value = toolRes.data?.records || toolRes.data || []
  } catch {
    knowledgeList.value = []
    tools.value = []
  }
}

async function loadTrace() {
  loading.value = true
  try {
    const res = await getTraceDetail(route.params.id)
    trace.value = res.data
  } catch { /* ignore */ } finally {
    loading.value = false
  }
}

// --- Span parsing（与改造前一致，trace 业务逻辑不变）---

function parseSpans() {
  if (!trace.value?.spans) return []
  try {
    const raw = trace.value.spans
    return Array.isArray(raw) ? raw : (typeof raw === 'string' ? JSON.parse(raw) : [])
  } catch { return [] }
}

const rootSpan = computed(() => {
  return parseSpans().find(s => s.name === 'workflow_run' && !s.parentSpanId)
})

const userInput = computed(() => {
  return rootSpan.value?.attributes?.userInput || ''
})

const nodeSpans = computed(() => {
  return parseSpans()
    .filter(s => s.spanId?.startsWith('node:'))
    .sort((a, b) => (a.startTime || 0) - (b.startTime || 0))
})

const llmSpans = computed(() => {
  const map = {}
  for (const s of parseSpans()) {
    if (s.spanId?.startsWith('llm:')) {
      const nodeId = s.spanId.replace('llm:', 'node:')
      map[nodeId] = s
    }
  }
  return map
})

function llmChildSpan(nodeSpan) {
  return llmSpans.value[nodeSpan.spanId] || null
}

// --- 只读画板数据（由 trace span 适配，不改变 span 解析逻辑）---

const viewerGraph = computed(() => {
  const root = rootSpan.value
  if (!root?.attributes?.nodes?.length) {
    return { nodes: [], edges: [] }
  }
  return workflowGraphToVueFlow({
    nodes: root.attributes.nodes || [],
    edges: root.attributes.edges || [],
  })
})

const viewerNodes = computed(() => viewerGraph.value.nodes)
const viewerEdges = computed(() => viewerGraph.value.edges)

const viewerNodeStates = computed(() => spansToNodeStates(nodeSpans.value))

const highlightedEdgeIds = computed(() => {
  const executedIds = nodeSpans.value
    .map(s => s.spanId?.replace(/^node:/, ''))
    .filter(Boolean)
  return buildExecutedEdgeIds(rootSpan.value?.attributes?.edges || [], executedIds)
})

const selectedCanvasNodeId = computed(() => {
  if (!selectedNodeId.value) return null
  return selectedNodeId.value.replace(/^node:/, '')
})

/** 来自 trace 图快照 + span 配置合并（执行时刻真实配置） */
const selectedWorkflowNode = computed(() => {
  const id = selectedCanvasNodeId.value
  if (!id) return null
  const n = viewerNodes.value.find(x => x.id === id)
  if (!n) return null
  return mergeTraceNodeData(n, selectedNodeSpan.value)
})

/** 旧 trace 仅有 label、无完整 data 时提示 */
const configSnapshotIncomplete = computed(() => {
  if (!selectedWorkflowNode.value || !selectedNodeSpan.value) return false
  const data = selectedWorkflowNode.value.data || {}
  const keys = Object.keys(data).filter(k => k !== 'label')
  const spanCfg = selectedNodeSpan.value.attributes?.config
  const spanKeys = spanCfg && typeof spanCfg === 'object' ? Object.keys(spanCfg) : []
  return keys.length === 0 && spanKeys.length > 0
})

const traceTargetNodes = computed(() =>
  viewerNodes.value.map(n => ({ id: n.id, label: n.data?.label || n.id, type: n.type })),
)

function filterKnowledgeOption(input, option) {
  const k = knowledgeList.value.find(item => String(item.id) === String(option.value))
  if (!k) return false
  const keyword = (input || '').toLowerCase()
  const text = `${k.name} ${k.description || ''} ${k.embeddingModel || ''}`.toLowerCase()
  return text.includes(keyword)
}

function filterToolOption(input, option) {
  const t = tools.value.find(item => String(item.id) === String(option.value))
  if (!t) return false
  const keyword = (input || '').toLowerCase()
  const text = `${t.displayName || ''} ${t.name || ''} ${t.description || ''}`.toLowerCase()
  return text.includes(keyword)
}

function getToolTypeLabel(toolType) {
  const code = toolType?.code || toolType
  const labels = {
    builtin: '内置',
    http: 'HTTP',
    script: '脚本',
    knowledge: '知识',
    mcp: 'MCP',
  }
  return labels[code] || code || ''
}

function isGroupBuiltinNodeFn(node) {
  return node && isGroupBuiltinType(node.type)
}

function onViewerNodeClick(nodeId) {
  const spanId = `node:${nodeId}`
  selectedNodeId.value = selectedNodeId.value === spanId ? null : spanId
}

// --- Node selection（详情面板仍绑定 span）---

const selectedNodeSpan = computed(() => {
  if (!selectedNodeId.value) return null
  return nodeSpans.value.find(s => s.spanId === selectedNodeId.value)
})

// --- Text mode ---

function toggleTextExpand(spanId) {
  const s = new Set(textExpanded.value)
  if (s.has(spanId)) s.delete(spanId)
  else s.add(spanId)
  textExpanded.value = s
}

// --- Helpers（文本模式仍使用）---

function llmMessages(span) {
  const msgs = span.attributes?.outputs?.llmMessages
  return Array.isArray(msgs) && msgs.length ? msgs : null
}

function filterOutputs(outputs) {
  if (!outputs) return {}
  const filtered = { ...outputs }
  delete filtered.llmMessages
  return filtered
}

function formatLlmAttrs(attrs) {
  if (!attrs) return '{}'
  const filtered = { ...attrs }
  for (const k of Object.keys(filtered)) {
    if (filtered[k] === 0 || filtered[k] === '' || filtered[k] === null || filtered[k] === undefined) {
      delete filtered[k]
    }
  }
  return JSON.stringify(filtered, null, 2)
}

function formatDuration(ms) {
  if (!ms && ms !== 0) return '-'
  if (ms < 1000) return ms + 'ms'
  return (ms / 1000).toFixed(1) + 's'
}

// --- Init ---

onMounted(async () => {
  await Promise.all([loadTrace(), loadResources()])
})
</script>

<style scoped>
.workflow-trace-detail {
  padding: 20px 24px;
  height: 100%;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}

/* Header */
.trace-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.header-title {
  font-size: 17px;
  font-weight: 600;
}

/* Info bar */
.trace-info-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 24px;
  padding: 12px 16px;
  background: var(--color-canvas-soft);
  border-radius: 8px;
  margin-bottom: 16px;
  align-items: center;
}
.info-item {
  display: flex;
  align-items: center;
  gap: 6px;
}
.info-label {
  font-size: 12px;
  color: var(--color-mute);
}
.info-val {
  font-size: 13px;
}
.rid-text {
  font-family: 'Geist Mono', Menlo, monospace;
  font-size: 11px;
  word-break: break-all;
  color: var(--color-body);
}
.token-input { color: #1890ff; }
.token-sep { color: #d9d9d9; margin: 0 3px; }
.token-output { color: #52c41a; }

/* Reply section */
.reply-section {
  margin-bottom: 16px;
}
.reply-section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-dark);
  margin-bottom: 8px;
}
.reply-content-box {
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
  border-radius: 8px;
  padding: 16px;
  line-height: 1.8;
  font-size: 13px;
}
.user-question-section {
  border-color: var(--color-border-blue);
  background: var(--color-info-bg);
}
.user-question-text {
  font-size: 14px;
  line-height: 1.8;
  color: #333;
  white-space: pre-wrap;
  word-break: break-word;
}
.reply-content-box :deep(h1),
.reply-content-box :deep(h2),
.reply-content-box :deep(h3) { margin-top: 12px; margin-bottom: 8px; font-weight: 600; }
.reply-content-box :deep(pre) { background: var(--color-canvas-soft); border: 1px solid var(--color-border-slate); border-radius: 6px; padding: 12px; overflow-x: auto; font-size: 12px; }
.reply-content-box :deep(code) { background: var(--color-canvas-soft); padding: 1px 4px; border-radius: 3px; font-size: 12px; }
.reply-content-box :deep(pre code) { background: none; padding: 0; }
.reply-content-box :deep(table) { border-collapse: collapse; margin: 8px 0; width: 100%; }
.reply-content-box :deep(th),
.reply-content-box :deep(td) { border: 1px solid var(--color-border-slate); padding: 6px 10px; font-size: 12px; }
.reply-content-box :deep(th) { background: var(--color-canvas-soft); font-weight: 600; }
.reply-content-box :deep(ul),
.reply-content-box :deep(ol) { padding-left: 20px; margin: 6px 0; }
.reply-content-box :deep(blockquote) { border-left: 3px solid #d1d5db; padding-left: 12px; color: var(--color-mute); margin: 8px 0; }
.reply-content-box :deep(a) { color: #1890ff; text-decoration: none; }
.reply-content-box :deep(a:hover) { text-decoration: underline; }

/* Graph container */
.graph-container {
  flex: 1;
  display: flex;
  gap: 0;
  border: 1px solid var(--color-hairline);
  border-radius: 8px;
  overflow: hidden;
  height: calc(100vh - 260px);
  min-height: 400px;
  background: var(--color-canvas);
  position: relative;
}
.graph-viewer-wrap {
  flex: 1;
  min-width: 0;
  min-height: 0;
}
.trace-node-detail-panel.config-panel {
  height: 100%;
  max-height: 100%;
}
.graph-empty-hint {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-mute);
  font-size: 14px;
  padding: 24px;
}

/* Text mode */
.text-container {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.text-node-card {
  border: 1px solid var(--color-hairline);
  border-radius: 8px;
  overflow: hidden;
  background: var(--color-canvas);
}
.text-node-card.node-success { border-left: 4px solid #52c41a; }
.text-node-card.node-error { border-left: 4px solid #ff4d4f; }
.tn-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
  cursor: pointer;
  transition: background 0.15s;
}
.tn-header:hover { background: var(--color-canvas-soft); }
.tn-index {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: #1890ff;
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}
.tn-icon { font-size: 16px; }
.tn-label {
  font-size: 14px;
  font-weight: 500;
  flex: 1;
}
.tn-duration {
  font-size: 12px;
  color: var(--color-mute);
  margin-left: auto;
}
.tn-expand {
  font-size: 10px;
  color: var(--color-mute);
  margin-left: 8px;
}
.tn-body {
  padding: 0 16px 12px 50px;
  border-top: 1px solid var(--color-hairline);
}
.tn-field {
  display: flex;
  gap: 8px;
  padding: 6px 0;
  font-size: 13px;
}
.tn-key {
  color: var(--color-mute);
  min-width: 60px;
}
.tn-val {
  flex: 1;
  word-break: break-all;
}
.tn-section {
  margin-top: 10px;
}
.tn-section-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-body);
  margin-bottom: 6px;
}
.tn-json {
  background: var(--color-canvas-soft);
  border: 1px solid var(--color-hairline);
  border-radius: 6px;
  padding: 10px 12px;
  font-size: 11px;
  line-height: 1.5;
  max-height: 200px;
  overflow: auto;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
}
.tn-text-block {
  background: var(--color-canvas-soft);
  border: 1px solid var(--color-hairline);
  border-radius: 6px;
  padding: 10px 12px;
  font-size: 12px;
  line-height: 1.6;
  max-height: 200px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-all;
}

/* LLM Messages */
.llm-messages-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.llm-msg-item {
  border-radius: 6px;
  padding: 8px 12px;
  border: 1px solid var(--color-hairline);
}
.llm-msg-system { background: var(--color-warn-bg); border-color: var(--color-warn-bg-deep); }
.llm-msg-user { background: #e6f7ff; border-color: #91d5ff; }
.llm-msg-assistant { background: var(--color-success-bg); border-color: #b7eb8f; }
.llm-msg-role {
  display: inline-block;
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  color: var(--color-body);
  margin-bottom: 4px;
  padding: 1px 6px;
  border-radius: 3px;
  background: rgba(0, 0, 0, 0.06);
}
.llm-msg-content {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 300px;
  overflow: auto;
}
</style>
