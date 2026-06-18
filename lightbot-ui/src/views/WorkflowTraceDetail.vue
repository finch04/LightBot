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
      <!-- 图模式 -->
      <div v-if="viewMode === 'graph' && graphNodes.length" class="graph-container">
        <!-- 缩放控制 -->
        <div class="zoom-controls">
          <a-button size="small" @click="zoomIn"><PlusOutlined /></a-button>
          <span class="zoom-label">{{ Math.round(scale * 100) }}%</span>
          <a-button size="small" @click="zoomOut"><MinusOutlined /></a-button>
          <a-button size="small" @click="resetView">重置</a-button>
          <a-divider type="vertical" />
          <a-button size="small" @click="reLayout"><ApartmentOutlined /> 重新布局</a-button>
        </div>

        <svg
          ref="svgRef"
          class="dag-svg"
          @mousedown="onSvgMouseDown"
          @mousemove="onSvgMouseMove"
          @mouseup="onSvgMouseUp"
          @mouseleave="onSvgMouseUp"
          @wheel.prevent="onWheel"
          @click="onSvgClick"
        >
          <g :transform="`translate(${panX}, ${panY}) scale(${scale})`">
            <!-- 边 -->
            <g v-for="edge in graphEdges" :key="edge.id" class="edge-group">
              <path :d="edge.path" fill="none" :stroke="edge.highlighted ? '#1890ff' : '#d9d9d9'"
                    :stroke-width="edge.highlighted ? 2.5 : 1.5" :stroke-dasharray="edge.highlighted ? 'none' : '4 2'" />
              <polygon v-if="edge.highlighted" :points="arrowPoints(edge)" fill="#1890ff" />
              <text v-if="edge.label" :x="edge.labelX" :y="edge.labelY"
                    font-size="10" fill="#8c8c8c" text-anchor="middle">{{ edge.label }}</text>
            </g>
            <!-- 容器节点（batch/loop） -->
            <template v-for="node in graphNodes" :key="node.id">
              <g v-if="node.isContainer"
                 class="dag-node dag-container"
                 :class="{ 'node-selected': selectedNodeId === node.id, 'node-dragging': dragState.id === node.id }"
                 :transform="`translate(${node.x + (dragState.id === node.id ? dragState.dx : 0)}, ${node.y + (dragState.id === node.id ? dragState.dy : 0)})`"
                 @mousedown.stop="onNodeMouseDown($event, node)"
                 @click.stop="selectNode(node.id)">
                <rect :x="-node.w / 2" :y="-node.h / 2"
                      :width="node.w" :height="node.h" rx="12"
                      fill="#fafbfc" :stroke="node.stroke" stroke-width="2" stroke-dasharray="6 3" />
                <text :x="-node.w / 2 + 16" :y="-node.h / 2 + 20"
                      font-size="12" font-weight="600" :fill="node.textColor">
                  {{ node.icon }} {{ node.label }}
                </text>
                <!-- 容器内子节点 -->
                <g v-for="child in node.children" :key="child.id"
                   class="dag-node"
                   :class="{ 'node-selected': selectedNodeId === child.id }"
                   :transform="`translate(${child.x}, ${child.y})`"
                   @click.stop="selectNode(child.id)">
                  <rect :x="-child.w / 2" :y="-child.h / 2"
                        :width="child.w" :height="child.h" rx="8"
                        :fill="child.fill" :stroke="child.stroke" stroke-width="1.5" />
                  <text x="0" y="-4" text-anchor="middle"
                        font-size="11" font-weight="500" :fill="child.textColor">
                    {{ child.icon }} {{ child.label }}
                  </text>
                  <text x="0" y="12" text-anchor="middle"
                        font-size="10" fill="#8c8c8c">
                    {{ child.statusText }}
                  </text>
                  <text v-if="child.durationText" x="0" y="24" text-anchor="middle"
                        font-size="9" fill="#bfbfbf">
                    {{ child.durationText }}
                  </text>
                </g>
              </g>
              <!-- 普通节点 -->
              <g v-else
                 class="dag-node"
                 :class="{
                   'node-selected': selectedNodeId === node.id,
                   'node-dragging': dragState.id === node.id,
                   'node-transition': dragState.id !== node.id
                 }"
                 :transform="`translate(${node.x + (dragState.id === node.id ? dragState.dx : 0)}, ${node.y + (dragState.id === node.id ? dragState.dy : 0)})`"
                 @mousedown.stop="onNodeMouseDown($event, node)"
                 @click.stop="selectNode(node.id)">
                <rect :x="-node.w / 2" :y="-node.h / 2"
                      :width="node.w" :height="node.h" rx="8"
                      :fill="node.fill" :stroke="node.stroke" stroke-width="2" />
                <text x="0" y="-6" text-anchor="middle"
                      font-size="12" font-weight="600" :fill="node.textColor">
                  {{ node.icon }} {{ node.label }}
                </text>
                <text x="0" y="10" text-anchor="middle"
                      font-size="10" fill="#8c8c8c">
                  {{ node.statusText }}
                </text>
                <text v-if="node.durationText" x="0" y="24" text-anchor="middle"
                      font-size="10" fill="#bfbfbf">
                  {{ node.durationText }}
                </text>
              </g>
            </template>
          </g>
        </svg>

        <!-- 节点详情面板 -->
        <div v-if="selectedNodeSpan" class="node-detail-panel">
          <div class="panel-header">
            <span class="panel-title">{{ getNodeIcon(selectedNodeSpan.attributes?.nodeType) }} {{ selectedNodeSpan.attributes?.nodeLabel || selectedNodeSpan.spanId }}</span>
            <a-button type="text" size="small" @click="selectedNodeId = null"><CloseOutlined /></a-button>
          </div>
          <div class="panel-body">
            <div class="panel-grid">
              <div class="pg-item"><span class="pg-key">节点类型</span><span class="pg-val">{{ getNodeTitle(selectedNodeSpan.attributes?.nodeType) }}</span></div>
              <div class="pg-item"><span class="pg-key">状态</span><span class="pg-val"><a-tag :color="selectedNodeSpan.status === 'completed' ? 'success' : 'error'" size="small">{{ selectedNodeSpan.status === 'completed' ? '成功' : '失败' }}</a-tag></span></div>
              <div class="pg-item"><span class="pg-key">耗时</span><span class="pg-val">{{ formatDuration(selectedNodeSpan.durationMs) }}</span></div>
              <div v-if="selectedNodeSpan.attributes?.message" class="pg-item" style="grid-column: 1 / -1;">
                <span class="pg-key">消息</span><span class="pg-val">{{ selectedNodeSpan.attributes.message }}</span>
              </div>
            </div>
            <div v-if="selectedNodeSpan.attributes?.config && Object.keys(selectedNodeSpan.attributes.config).length" class="panel-section">
              <div class="ps-title">节点配置</div>
              <pre class="ps-json">{{ JSON.stringify(selectedNodeSpan.attributes.config, null, 2) }}</pre>
            </div>
            <div v-if="selectedNodeSpan.attributes?.input && Object.keys(selectedNodeSpan.attributes.input).length" class="panel-section">
              <div class="ps-title">输入参数</div>
              <pre class="ps-json">{{ JSON.stringify(selectedNodeSpan.attributes.input, null, 2) }}</pre>
            </div>
            <div v-if="selectedNodeSpan.attributes?.outputs && Object.keys(selectedNodeSpan.attributes.outputs).length" class="panel-section">
              <div class="ps-title">输出结果</div>
              <pre class="ps-json">{{ JSON.stringify(filterOutputs(selectedNodeSpan.attributes.outputs), null, 2) }}</pre>
            </div>
            <div v-if="llmMessages(selectedNodeSpan)" class="panel-section">
              <div class="ps-title">LLM 上下文（完整消息列表）</div>
              <div class="llm-messages-list">
                <div v-for="(msg, mi) in llmMessages(selectedNodeSpan)" :key="mi" class="llm-msg-item" :class="'llm-msg-' + msg.role">
                  <span class="llm-msg-role">{{ msg.role }}</span>
                  <pre class="llm-msg-content">{{ msg.content }}</pre>
                </div>
              </div>
            </div>
            <div v-if="selectedNodeSpan.attributes?.detail" class="panel-section">
              <div class="ps-title">执行详情</div>
              <div class="ps-text">{{ selectedNodeSpan.attributes.detail }}</div>
            </div>
            <div v-if="llmChildSpan(selectedNodeSpan)" class="panel-section">
              <div class="ps-title">LLM 调用</div>
              <pre class="ps-json">{{ formatLlmAttrs(llmChildSpan(selectedNodeSpan).attributes) }}</pre>
            </div>
          </div>
        </div>
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
import { ref, reactive, computed, onMounted, onUnmounted, shallowRef } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowLeftOutlined,
  ApartmentOutlined,
  UnorderedListOutlined,
  CloseOutlined,
  PlusOutlined,
  MinusOutlined,
} from '@ant-design/icons-vue'
import { getTraceDetail } from '../api/observability'
import MarkdownPreview from '../components/MarkdownPreview.vue'
import { getNodeIconText as getNodeIcon, getNodeColors } from '../utils/nodeStyleUtils'
import { getNodeTitle } from '../views/workflow/nodeMeta'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const trace = ref(null)
const viewMode = ref('graph')
const selectedNodeId = ref(null)
const textExpanded = ref(new Set())
const svgRef = ref(null)

// Pan & Zoom
const panX = ref(40)
const panY = ref(20)
const scale = ref(1)
let isPanning = false
let panMoved = false
let panStart = { x: 0, y: 0 }

// Node drag — 用 offset 代替直接改 x/y，避免触发大量响应式更新
const dragState = reactive({ id: null, dx: 0, dy: 0, startX: 0, startY: 0, mouseStartX: 0, mouseStartY: 0 })
let rafId = null
let pendingMouse = null

// Graph data — shallowRef 避免深层响应式追踪
const graphNodes = shallowRef([])
const graphEdges = shallowRef([])
const rawWfEdges = shallowRef([])

// --- Data loading ---

async function loadTrace() {
  loading.value = true
  try {
    const res = await getTraceDetail(route.params.id)
    trace.value = res.data
  } catch { /* ignore */ } finally {
    loading.value = false
  }
}

// --- Span parsing ---

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

// --- Graph init ---

const CONTAINER_TYPES = new Set(['batch', 'loop'])
const CONTAINER_W = 420, CONTAINER_H = 260
const NODE_W = 160, NODE_H = 56
const CHILD_PAD_X = 30, CHILD_PAD_TOP = 50, CHILD_GAP_Y = 80

function initGraph(forceLayout = false) {
  const root = rootSpan.value
  const spans = nodeSpans.value
  if (!root || !spans.length) { graphNodes.value = []; graphEdges.value = []; return }

  const rootAttrs = root.attributes || {}
  const wfEdges = rootAttrs.edges || []
  const wfNodes = rootAttrs.nodes || []
  rawWfEdges.value = wfEdges

  const spanMap = new Map()
  for (const s of spans) {
    const nid = s.spanId.replace('node:', '')
    spanMap.set(nid, s)
  }

  const wfNodeMap = new Map(wfNodes.map(n => [n.id, n]))
  const childIds = new Set(wfNodes.filter(n => n.parentNode).map(n => n.id))
  const topNodes = wfNodes.filter(n => !n.parentNode)

  // 构建节点列表：容器 + 顶层普通节点
  const nodes = []
  for (const wfNode of topNodes) {
    const nid = wfNode.id
    const isContainer = CONTAINER_TYPES.has(wfNode.type)
    if (isContainer) {
      const children = wfNodes.filter(n => n.parentNode === nid)
      const childNodes = children.map(child => buildGraphNode(child, spanMap))
      // 容器内子节点按相对坐标排列后转为绝对坐标
      layoutChildrenInside(childNodes)
      const span = spanMap.get(nid)
      const status = computeContainerStatus(children, spanMap)
      nodes.push({
        id: `node:${nid}`, nodeId: nid, nodeType: wfNode.type,
        label: truncate(wfNode.data?.label || wfNode.label || nid, 14),
        icon: getNodeIcon(wfNode.type), status,
        statusText: status === 'completed' ? '成功' : status === 'failed' ? '失败' : '未执行',
        durationText: '', w: CONTAINER_W, h: CONTAINER_H,
        x: 0, y: 0, ...getNodeColors(wfNode.type, status), span,
        isContainer: true, children: childNodes,
      })
    } else {
      nodes.push(buildGraphNode(wfNode, spanMap))
    }
  }

  // 布局：容器和普通节点一起参与顶层布局
  const hasPositions = !forceLayout && topNodes.some(n => n.position && typeof n.position.x === 'number')
  if (!hasPositions) {
    autoLayout(nodes, wfEdges)
  } else {
    const padX = 60, padY = 40
    const minX = Math.min(...nodes.map(n => n.x - n.w / 2))
    const minY = Math.min(...nodes.map(n => n.y - n.h / 2))
    nodes.forEach(n => {
      n.x = n.x - minX + padX
      n.y = n.y - minY + padY
      if (n.isContainer && n.children) {
        n.children.forEach(c => { c.x += n.x - (n.w / 2 - CHILD_PAD_X); c.y += n.y - (n.h / 2 - CHILD_PAD_TOP) })
      }
    })
  }

  // 构建边：容器子节点的边也渲染
  const allNodeMap = new Map()
  for (const n of nodes) {
    allNodeMap.set(n.nodeId, n)
    if (n.isContainer && n.children) {
      for (const c of n.children) allNodeMap.set(c.nodeId, c)
    }
  }
  const executedIds = new Set(spanMap.keys())
  const edges = wfEdges.map(e => {
    const srcNode = allNodeMap.get(e.source)
    const tgtNode = allNodeMap.get(e.target)
    if (!srcNode || !tgtNode) return null
    const highlighted = executedIds.has(e.source) && executedIds.has(e.target)
    return {
      id: e.id || `${e.source}->${e.target}`,
      source: e.source, target: e.target,
      label: e.label || '', sourceHandle: e.sourceHandle || '',
      highlighted, ...buildEdgePath(srcNode, tgtNode),
    }
  }).filter(Boolean)

  graphNodes.value = nodes
  graphEdges.value = edges
}

function buildGraphNode(wfNode, spanMap) {
  const nid = wfNode.id
  const span = spanMap.get(nid)
  const attrs = span?.attributes || {}
  const nodeType = wfNode.type || attrs.nodeType || nid
  const label = wfNode.data?.label || wfNode.label || attrs.nodeLabel || nid
  const status = span?.status || 'SKIPPED'
  const pos = wfNode.position || {}
  const x = (typeof pos.x === 'number' ? pos.x : 0) + NODE_W / 2
  const y = (typeof pos.y === 'number' ? pos.y : 0) + NODE_H / 2
  return {
    id: `node:${nid}`, nodeId: nid, nodeType,
    label: truncate(label, 14), icon: getNodeIcon(nodeType), status,
    statusText: status === 'completed' ? '成功' : status === 'failed' ? '失败' : '未执行',
    durationText: span?.durationMs != null ? formatDuration(span.durationMs) : '',
    w: NODE_W, h: NODE_H, x, y, ...getNodeColors(nodeType, status), span,
  }
}

function layoutChildrenInside(children) {
  if (!children.length) return
  // 按原始 position.y 排序，再依次垂直排列
  children.sort((a, b) => a.y - b.y || a.x - b.x)
  for (let i = 0; i < children.length; i++) {
    children[i].x = CHILD_PAD_X + NODE_W / 2
    children[i].y = CHILD_PAD_TOP + i * (NODE_H + CHILD_GAP_Y) + NODE_H / 2
  }
}

function computeContainerStatus(children, spanMap) {
  const statuses = children.map(c => spanMap.get(c.id)?.status || 'SKIPPED')
  if (statuses.some(s => s === 'failed')) return 'failed'
  if (statuses.some(s => s === 'completed')) return 'completed'
  return 'SKIPPED'
}

function buildEdgePath(srcNode, tgtNode) {
  const sx = srcNode.x + srcNode.w / 2, sy = srcNode.y
  const tx = tgtNode.x - tgtNode.w / 2, ty = tgtNode.y
  const mx = (sx + tx) / 2
  return {
    path: `M${sx},${sy} C${mx},${sy} ${mx},${ty} ${tx},${ty}`,
    labelX: mx, labelY: (sy + ty) / 2 - 8,
  }
}

function autoLayout(nodes, wfEdges) {
  if (!nodes.length) return
  const nodeGapX = 220, nodeGapY = 100, startX = 80, startY = 60
  const adj = new Map(), inDeg = new Map()
  for (const n of nodes) { adj.set(n.nodeId, []); inDeg.set(n.nodeId, 0) }
  for (const e of wfEdges) {
    if (adj.has(e.source) && adj.has(e.target)) {
      adj.get(e.source).push(e.target)
      inDeg.set(e.target, (inDeg.get(e.target) || 0) + 1)
    }
  }
  const queue = nodes.filter(n => (inDeg.get(n.nodeId) || 0) === 0).map(n => n.nodeId)
  const topoOrder = []
  while (queue.length) {
    const id = queue.shift(); topoOrder.push(id)
    for (const next of (adj.get(id) || [])) {
      inDeg.set(next, inDeg.get(next) - 1)
      if (inDeg.get(next) === 0) queue.push(next)
    }
  }
  for (const n of nodes) { if (!topoOrder.includes(n.nodeId)) topoOrder.push(n.nodeId) }
  const depthMap = new Map()
  for (const id of topoOrder) {
    const preds = wfEdges.filter(e => e.target === id).map(e => e.source)
    const predDepths = preds.map(p => depthMap.get(p) ?? 0)
    depthMap.set(id, predDepths.length ? Math.max(...predDepths) + 1 : 0)
  }
  const depthGroups = new Map()
  for (const n of nodes) {
    const d = depthMap.get(n.nodeId) ?? 0
    if (!depthGroups.has(d)) depthGroups.set(d, [])
    depthGroups.get(d).push(n)
  }
  for (const [depth, group] of depthGroups) {
    const x = startX + depth * nodeGapX
    const totalHeight = group.length * nodeGapY
    const offsetY = startY + Math.max(0, (400 - totalHeight) / 2)
    group.forEach((n, i) => {
      n.x = x
      n.y = offsetY + i * nodeGapY
      // 容器内子节点跟随容器移动
      if (n.isContainer && n.children) {
        n.children.forEach(c => {
          c.x += n.x - (n.w / 2 - CHILD_PAD_X)
          c.y += n.y - (n.h / 2 - CHILD_PAD_TOP)
        })
      }
    })
  }
}

function reLayout() {
  initGraph(true)
}

// --- Zoom ---

function zoomIn() { scale.value = Math.min(scale.value * 1.2, 3) }
function zoomOut() { scale.value = Math.max(scale.value / 1.2, 0.2) }
function resetView() { scale.value = 1; panX.value = 40; panY.value = 20 }

function onWheel(e) {
  const delta = e.deltaY > 0 ? 0.9 : 1.1
  const newScale = Math.min(Math.max(scale.value * delta, 0.2), 3)
  const rect = svgRef.value.getBoundingClientRect()
  const mx = e.clientX - rect.left
  const my = e.clientY - rect.top
  panX.value = mx - (mx - panX.value) * (newScale / scale.value)
  panY.value = my - (my - panY.value) * (newScale / scale.value)
  scale.value = newScale
}

// --- SVG panning ---

function onSvgMouseDown(e) {
  isPanning = true
  panMoved = false
  panStart = { x: e.clientX - panX.value, y: e.clientY - panY.value }
}

function onSvgMouseMove(e) {
  // 节点拖拽
  if (dragState.id && pendingMouse) {
    pendingMouse.x = e.clientX
    pendingMouse.y = e.clientY
    if (!rafId) {
      rafId = requestAnimationFrame(flushDrag)
    }
    return
  }
  // 画布平移
  if (!isPanning) return
  panMoved = true
  panX.value = e.clientX - panStart.x
  panY.value = e.clientY - panStart.y
}

function onSvgMouseUp() {
  if (dragState.id) {
    commitDrag()
  }
  isPanning = false
}

function onSvgClick() {
  // 点击空白处（非拖拽、非平移）关闭节点详情
  if (!dragMoved && !panMoved) {
    selectedNodeId.value = null
  }
}

// --- Node drag (CSS transform + rAF) ---

let dragMoved = false

function onNodeMouseDown(e, node) {
  dragState.id = node.id
  dragState.dx = 0
  dragState.dy = 0
  dragState.startX = node.x
  dragState.startY = node.y
  dragState.mouseStartX = e.clientX
  dragState.mouseStartY = e.clientY
  pendingMouse = { x: e.clientX, y: e.clientY }
  dragMoved = false
}

function flushDrag() {
  rafId = null
  if (!dragState.id || !pendingMouse) return
  dragState.dx = (pendingMouse.x - dragState.mouseStartX) / scale.value
  dragState.dy = (pendingMouse.y - dragState.mouseStartY) / scale.value
  if (dragState.dx !== 0 || dragState.dy !== 0) dragMoved = true
  rebuildEdges()
}

function commitDrag() {
  if (rafId) { cancelAnimationFrame(rafId); rafId = null }
  if (!dragState.id) return

  const finalDx = dragState.dx
  const finalDy = dragState.dy

  // 写入实际坐标
  const nodes = graphNodes.value
  const node = nodes.find(n => n.id === dragState.id)
  if (node) {
    node.x = dragState.startX + finalDx
    node.y = dragState.startY + finalDy
  }

  // 重置 offset
  dragState.dx = 0
  dragState.dy = 0
  dragState.id = null
  pendingMouse = null

  // 重建受影响的边
  rebuildEdges()
}

function rebuildEdges() {
  const isDragging = dragState.id != null
  const nodeMap = new Map(graphNodes.value.map(n => {
    if (isDragging && n.id === dragState.id) {
      // 拖拽中：用偏移后的位置计算边
      return [n.nodeId, { ...n, x: n.x + dragState.dx, y: n.y + dragState.dy }]
    }
    return [n.nodeId, n]
  }))
  graphEdges.value = graphEdges.value.map(edge => {
    const srcNode = nodeMap.get(edge.source)
    const tgtNode = nodeMap.get(edge.target)
    if (!srcNode || !tgtNode) return edge
    return { ...edge, ...buildEdgePath(srcNode, tgtNode) }
  })
}

// --- Node selection ---

const selectedNodeSpan = computed(() => {
  if (!selectedNodeId.value) return null
  return nodeSpans.value.find(s => s.spanId === selectedNodeId.value)
})

function selectNode(id) {
  if (dragState.id || dragMoved) return
  selectedNodeId.value = selectedNodeId.value === id ? null : id
}

// --- Text mode ---

function toggleTextExpand(spanId) {
  const s = new Set(textExpanded.value)
  if (s.has(spanId)) s.delete(spanId)
  else s.add(spanId)
  textExpanded.value = s
}

// --- Helpers ---

function arrowPoints(edge) {
  const path = edge.path
  if (!path) return ''
  const match = path.match(/(\d+\.?\d*),(\d+\.?\d*)$/)
  if (!match) return ''
  const tx = parseFloat(match[1])
  const ty = parseFloat(match[2])
  return `${tx},${ty} ${tx - 8},${ty - 4} ${tx - 8},${ty + 4}`
}

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

function truncate(s, max) {
  if (!s) return ''
  return s.length > max ? s.substring(0, max) + '...' : s
}

// --- Watch trace changes to rebuild graph ---

import { watch } from 'vue'
watch(() => trace.value, () => initGraph(), { flush: 'post' })

// --- Init ---

onMounted(loadTrace)
onUnmounted(() => { if (rafId) cancelAnimationFrame(rafId) })
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
  background: #fafafa;
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
  color: #8c8c8c;
}
.info-val {
  font-size: 13px;
}
.rid-text {
  font-family: 'Geist Mono', Menlo, monospace;
  font-size: 11px;
  word-break: break-all;
  color: #595959;
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
  color: #334155;
  margin-bottom: 8px;
}
.reply-content-box {
  background: #fff;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  padding: 16px;
  line-height: 1.8;
  font-size: 13px;
}
.user-question-section {
  border-color: #bae7ff;
  background: #f0faff;
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
.reply-content-box :deep(pre) { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 6px; padding: 12px; overflow-x: auto; font-size: 12px; }
.reply-content-box :deep(code) { background: #f1f5f9; padding: 1px 4px; border-radius: 3px; font-size: 12px; }
.reply-content-box :deep(pre code) { background: none; padding: 0; }
.reply-content-box :deep(table) { border-collapse: collapse; margin: 8px 0; width: 100%; }
.reply-content-box :deep(th),
.reply-content-box :deep(td) { border: 1px solid #e2e8f0; padding: 6px 10px; font-size: 12px; }
.reply-content-box :deep(th) { background: #f8fafc; font-weight: 600; }
.reply-content-box :deep(ul),
.reply-content-box :deep(ol) { padding-left: 20px; margin: 6px 0; }
.reply-content-box :deep(blockquote) { border-left: 3px solid #d1d5db; padding-left: 12px; color: #6b7280; margin: 8px 0; }
.reply-content-box :deep(a) { color: #1890ff; text-decoration: none; }
.reply-content-box :deep(a:hover) { text-decoration: underline; }

/* Graph container */
.graph-container {
  flex: 1;
  display: flex;
  gap: 0;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  overflow: hidden;
  height: calc(100vh - 260px);
  min-height: 400px;
  background: #fff;
  position: relative;
}
.dag-svg {
  flex: 1;
  cursor: grab;
  min-width: 0;
  user-select: none;
}
.dag-svg:active { cursor: grabbing; }

.dag-node { cursor: pointer; }
.dag-node:hover rect { filter: brightness(0.95); }
.node-selected rect { stroke-width: 3 !important; filter: drop-shadow(0 2px 8px rgba(24, 144, 255, 0.3)); }

/* 非拖拽节点平滑过渡 */
.node-transition {
  transition: transform 0.25s cubic-bezier(0.25, 0.1, 0.25, 1);
}

/* 拖拽中的节点：无过渡 + 阴影 + 微放大 */
.node-dragging {
  cursor: grabbing;
}
.node-dragging rect {
  filter: drop-shadow(0 6px 16px rgba(0, 0, 0, 0.18));
  stroke-width: 2.5;
}

/* 边平滑过渡 */
.edge-group {
  transition: opacity 0.2s;
}

/* Zoom controls */
.zoom-controls {
  position: absolute;
  top: 12px;
  left: 12px;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: 6px;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(4px);
  border: 1px solid #e8e8e8;
  border-radius: 6px;
  padding: 4px 8px;
}
.zoom-label {
  font-size: 11px;
  color: #8c8c8c;
  min-width: 36px;
  text-align: center;
}

/* Node detail panel */
.node-detail-panel {
  width: 380px;
  min-width: 380px;
  border-left: 1px solid #e8e8e8;
  background: #fafafa;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  max-height: 100%;
}
.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #e8e8e8;
  background: #fff;
}
.panel-title {
  font-size: 15px;
  font-weight: 600;
}
.panel-body {
  padding: 12px 16px;
  flex: 1;
  overflow-y: auto;
}
.panel-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  margin-bottom: 12px;
}
.pg-item {
  display: flex;
  align-items: center;
  gap: 6px;
}
.pg-key {
  font-size: 12px;
  color: #8c8c8c;
  min-width: 50px;
}
.pg-val {
  font-size: 12px;
}
.panel-section {
  margin-bottom: 12px;
}
.ps-title {
  font-size: 12px;
  font-weight: 600;
  color: #595959;
  margin-bottom: 6px;
}
.ps-json {
  background: #fff;
  border: 1px solid #e8e8e8;
  border-radius: 6px;
  padding: 10px 12px;
  font-size: 11px;
  line-height: 1.5;
  max-height: 240px;
  overflow: auto;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
}
.ps-text {
  background: #fff;
  border: 1px solid #e8e8e8;
  border-radius: 6px;
  padding: 10px 12px;
  font-size: 12px;
  line-height: 1.6;
  max-height: 200px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-all;
}

/* Text mode */
.text-container {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.text-node-card {
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  overflow: hidden;
  background: #fff;
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
.tn-header:hover { background: #fafafa; }
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
  color: #8c8c8c;
  margin-left: auto;
}
.tn-expand {
  font-size: 10px;
  color: #bfbfbf;
  margin-left: 8px;
}
.tn-body {
  padding: 0 16px 12px 50px;
  border-top: 1px solid #f0f0f0;
}
.tn-field {
  display: flex;
  gap: 8px;
  padding: 6px 0;
  font-size: 13px;
}
.tn-key {
  color: #8c8c8c;
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
  color: #595959;
  margin-bottom: 6px;
}
.tn-json {
  background: #fafafa;
  border: 1px solid #e8e8e8;
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
  background: #fafafa;
  border: 1px solid #e8e8e8;
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
  border: 1px solid #e8e8e8;
}
.llm-msg-system { background: #fff7e6; border-color: #ffd591; }
.llm-msg-user { background: #e6f7ff; border-color: #91d5ff; }
.llm-msg-assistant { background: #f6ffed; border-color: #b7eb8f; }
.llm-msg-role {
  display: inline-block;
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  color: #595959;
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
