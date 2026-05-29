<template>
  <div class="knowledge-graph-tab">
    <!-- 顶部工具栏 -->
    <div class="kg-toolbar">
      <div class="kg-toolbar-left">
        <a-input
          v-model:value="searchText"
          placeholder="搜索节点..."
          allow-clear
          size="small"
          class="kg-search"
          @press-enter="handleSearch"
        >
          <template #prefix><SearchOutlined /></template>
        </a-input>
        <a-button size="small" @click="handleSearch" :disabled="!searchText">搜索</a-button>
        <a-button size="small" @click="handleClearSearch" v-if="searchKeywords.length > 0">清除高亮</a-button>
      </div>
      <div class="kg-toolbar-right">
        <a-button size="small" @click="handleFitView">
          <template #icon><CompressOutlined /></template> 适应画布
        </a-button>
        <a-button size="small" :loading="extracting" @click="handleExtract">
          <template #icon><RobotOutlined /></template> AI 抽取图谱
        </a-button>
        <a-popconfirm
          v-if="stats.nodeCount > 0"
          title="确定清空整个知识图谱？此操作不可恢复。"
          ok-text="清空"
          cancel-text="取消"
          @confirm="handleDeleteGraph"
        >
          <a-button size="small" danger :loading="deleting">
            <template #icon><DeleteOutlined /></template> 清空图谱
          </a-button>
        </a-popconfirm>
      </div>
    </div>

    <!-- 统计信息 -->
    <div v-if="stats.nodeCount > 0" class="kg-stats">
      <span class="kg-stat-item">
        <span class="kg-stat-label">节点</span>
        <span class="kg-stat-value">{{ displayNodeCount }}</span>
        <span v-if="displayNodeCount !== stats.nodeCount" class="kg-stat-total">/ {{ stats.nodeCount }}</span>
      </span>
      <span class="kg-stat-item">
        <span class="kg-stat-label">边</span>
        <span class="kg-stat-value">{{ displayEdgeCount }}</span>
        <span v-if="displayEdgeCount !== stats.edgeCount" class="kg-stat-total">/ {{ stats.edgeCount }}</span>
      </span>
    </div>

    <!-- 图谱画布 -->
    <div class="kg-canvas-wrapper" ref="canvasWrapperRef">
      <div v-if="graphReady" class="kg-canvas" ref="canvasRef"></div>
      <div v-else class="kg-empty">
        <a-spin v-if="loading" tip="加载图谱中..." />
        <template v-else>
          <p>暂无知识图谱</p>
          <p class="kg-empty-hint">点击「AI 抽取图谱」从已有文档中自动抽取实体关系</p>
        </template>
      </div>
    </div>

    <!-- 节点/边详情面板 -->
    <a-drawer
      v-model:open="detailVisible"
      :title="detailTitle"
      :width="360"
      :bodyStyle="{ padding: '16px' }"
    >
      <template v-if="detailType === 'node' && selectedNode">
        <a-descriptions :column="1" size="small" bordered>
          <a-descriptions-item label="名称">{{ selectedNode.name }}</a-descriptions-item>
          <a-descriptions-item label="类型">{{ selectedNode.entityType || '-' }}</a-descriptions-item>
          <a-descriptions-item label="描述">{{ selectedNode.description || '-' }}</a-descriptions-item>
          <a-descriptions-item label="来源">{{ selectedNode.source || '-' }}</a-descriptions-item>
          <a-descriptions-item label="文档ID">{{ selectedNode.documentId || '-' }}</a-descriptions-item>
        </a-descriptions>
        <div class="kg-detail-actions">
          <a-popconfirm
            title="确定删除该节点及其关联的边？"
            ok-text="删除"
            cancel-text="取消"
            @confirm="handleDeleteNode"
          >
            <a-button size="small" danger>删除节点</a-button>
          </a-popconfirm>
        </div>
      </template>
      <template v-else-if="detailType === 'edge' && selectedEdge">
        <a-descriptions :column="1" size="small" bordered>
          <a-descriptions-item label="关系类型">{{ selectedEdge.relationType }}</a-descriptions-item>
          <a-descriptions-item label="描述">{{ selectedEdge.description || '-' }}</a-descriptions-item>
          <a-descriptions-item label="权重">{{ selectedEdge.weight ?? '-' }}</a-descriptions-item>
          <a-descriptions-item label="来源">{{ selectedEdge.source || '-' }}</a-descriptions-item>
        </a-descriptions>
        <div class="kg-detail-actions">
          <a-popconfirm
            title="确定删除该关系？"
            ok-text="删除"
            cancel-text="取消"
            @confirm="handleDeleteEdge"
          >
            <a-button size="small" danger>删除关系</a-button>
          </a-popconfirm>
        </div>
      </template>
    </a-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { Graph } from '@antv/g6'
import {
  SearchOutlined, RobotOutlined, DeleteOutlined, CompressOutlined
} from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import {
  getGraphSubgraph, getGraphStats, extractGraph, deleteGraph,
  deleteGraphNode, deleteGraphEdge
} from '../api/knowledge'

const props = defineProps({
  knowledgeId: { type: [String, Number], required: true }
})

// ---- state ----
const loading = ref(false)
const extracting = ref(false)
const deleting = ref(false)
const graphReady = ref(false)
const searchText = ref('')
const searchKeywords = ref([])

const stats = reactive({ nodeCount: 0, edgeCount: 0 })
const displayNodeCount = ref(0)
const displayEdgeCount = ref(0)

const detailVisible = ref(false)
const detailType = ref('') // 'node' | 'edge'
const selectedNode = ref(null)
const selectedEdge = ref(null)

const canvasRef = ref(null)
const canvasWrapperRef = ref(null)

let graphInstance = null
let resizeObserver = null

// ---- computed ----
const detailTitle = computed(() => {
  if (detailType.value === 'node') return '节点详情'
  if (detailType.value === 'edge') return '关系详情'
  return '详情'
})

// ---- 颜色调色板 ----
const COLOR_PALETTE = [
  '#60a5fa', '#34d399', '#f59e0b', '#f472b6', '#22d3ee',
  '#a78bfa', '#f97316', '#4ade80', '#f43f5e', '#2dd4bf'
]

// ---- 图谱布局配置 ----
const LAYOUT_CONFIG = {
  type: 'd3-force',
  preventOverlap: true,
  alphaDecay: 0.1,
  alphaMin: 0.01,
  velocityDecay: 0.6,
  iterations: 150,
  force: {
    center: { x: 0.5, y: 0.5, strength: 0.1 },
    charge: { strength: -400, distanceMax: 600 },
    link: { distance: 100, strength: 0.8 }
  },
  collide: { radius: 40, strength: 0.8, iterations: 3 }
}

// ---- 数据格式化 ----
function formatGraphData(data) {
  if (!data) return { nodes: [], edges: [] }

  const degrees = new Map()
  for (const n of data.nodes) {
    degrees.set(String(n.elementId), 0)
  }
  for (const e of data.edges) {
    const s = String(e.startNodeElementId)
    const t = String(e.endNodeElementId)
    degrees.set(s, (degrees.get(s) || 0) + 1)
    degrees.set(t, (degrees.get(t) || 0) + 1)
  }

  const nodes = (data.nodes || []).map(n => ({
    id: String(n.elementId),
    data: {
      label: n.name || String(n.elementId),
      degree: degrees.get(String(n.elementId)) || 0,
      original: n
    }
  }))

  const edges = (data.edges || []).map((e, idx) => ({
    id: e.elementId ? String(e.elementId) : `edge-${idx}`,
    source: String(e.startNodeElementId),
    target: String(e.endNodeElementId),
    data: {
      label: e.relationType || '',
      original: e
    }
  }))

  return { nodes, edges }
}

// ---- 初始化图谱 ----
function initGraph() {
  if (!canvasRef.value) return

  const width = canvasRef.value.offsetWidth
  const height = canvasRef.value.offsetHeight
  if (width === 0 || height === 0) return

  canvasRef.value.innerHTML = ''
  if (graphInstance) {
    try { graphInstance.destroy() } catch { /* ignore */ }
    graphInstance = null
  }

  graphInstance = new Graph({
    container: canvasRef.value,
    width,
    height,
    autoFit: 'view',
    autoResize: true,
    layout: LAYOUT_CONFIG,
    node: {
      type: 'circle',
      style: {
        labelText: (d) => d.data.label,
        labelFill: '#374151',
        labelWordWrap: true,
        labelMaxWidth: '300%',
        size: (d) => {
          const deg = d.data.degree || 0
          return Math.min(15 + deg * 5, 50)
        },
        opacity: 0.9,
        stroke: '#ffffff',
        lineWidth: 1.5,
        shadowColor: '#d1d5db',
        shadowBlur: 4
      },
      palette: {
        field: 'label',
        color: COLOR_PALETTE
      }
    },
    edge: {
      type: 'quadratic',
      style: {
        labelText: (d) => d.data.label,
        labelFill: '#1f2937',
        labelBackground: true,
        labelBackgroundFill: '#f3f4f6',
        stroke: '#d1d5db',
        opacity: 0.8,
        lineWidth: 1.2,
        endArrow: true
      }
    },
    behaviors: [
      'drag-element',
      'zoom-canvas',
      'drag-canvas',
      'hover-activate',
      {
        type: 'click-select',
        degree: 1,
        state: 'selected',
        neighborState: 'active',
        unselectedState: 'inactive',
        multiple: true,
        trigger: ['shift'],
        disableDefault: false
      }
    ]
  })

  // 节点点击
  graphInstance.on('node:click', (evt) => {
    const nodeId = evt.target.id
    const nodeData = graphInstance.getNodeData(nodeId)
    const original = nodeData?.data?.original
    if (original) {
      selectedNode.value = { ...original }
      detailType.value = 'node'
      detailVisible.value = true
    }
  })

  // 边点击
  graphInstance.on('edge:click', (evt) => {
    const edgeId = evt.target.id
    const edgeData = graphInstance.getEdgeData(edgeId)
    const original = edgeData?.data?.original
    if (original) {
      selectedEdge.value = { ...original }
      detailType.value = 'edge'
      detailVisible.value = true
    }
  })

  // 点击空白关闭详情
  graphInstance.on('canvas:click', () => {
    detailVisible.value = false
  })
}

// ---- 加载图谱数据 ----
async function loadGraphData() {
  loading.value = true
  try {
    const [subgraphRes, statsRes] = await Promise.all([
      getGraphSubgraph(props.knowledgeId, {}),
      getGraphStats(props.knowledgeId)
    ])

    const subgraph = subgraphRes.data
    Object.assign(stats, {
      nodeCount: subgraph.nodeCount || 0,
      edgeCount: subgraph.edgeCount || 0
    })

    if (stats.nodeCount === 0) {
      graphReady.value = false
      loading.value = false
      return
    }

    // 设置显示数量（可能被搜索过滤）
    displayNodeCount.value = subgraph.nodes?.length || 0
    displayEdgeCount.value = subgraph.edges?.length || 0

    graphReady.value = true
    await nextTick()

    initGraph()
    if (graphInstance) {
      const data = formatGraphData(subgraph)
      graphInstance.setData(data)
      graphInstance.render()
    }
  } catch (e) {
    console.error('[知识图谱] 加载失败', e)
    message.error('加载知识图谱失败')
  } finally {
    loading.value = false
  }
}

// ---- AI 抽取 ----
async function handleExtract() {
  Modal.confirm({
    title: 'AI 抽取知识图谱',
    content: '将从所有已完成文档中自动抽取实体关系，耗时取决于文档数量。确认继续？',
    okText: '开始抽取',
    cancelText: '取消',
    async onOk() {
      extracting.value = true
      try {
        await extractGraph(props.knowledgeId)
        message.success('图谱抽取任务已提交，正在后台处理中，请前往任务中心查看进度')
      } catch (e) {
        console.error('[知识图谱] 抽取失败', e)
      } finally {
        extracting.value = false
      }
    }
  })
}

// ---- 清空图谱 ----
async function handleDeleteGraph() {
  deleting.value = true
  try {
    await deleteGraph(props.knowledgeId)
    message.success('图谱已清空')
    graphReady.value = false
    Object.assign(stats, { nodeCount: 0, edgeCount: 0 })
    displayNodeCount.value = 0
    displayEdgeCount.value = 0
    if (graphInstance) {
      try { graphInstance.destroy() } catch { /* ignore */ }
      graphInstance = null
    }
  } catch (e) {
    console.error('[知识图谱] 清空失败', e)
  } finally {
    deleting.value = false
  }
}

// ---- 搜索 ----
function handleSearch() {
  if (!searchText.value?.trim()) return
  const keyword = searchText.value.trim()
  searchKeywords.value = [keyword]

  if (!graphInstance) return

  const { nodes, edges } = graphInstance.getData()
  const updates = {}
  const matchedIds = new Set()

  nodes.forEach(node => {
    const label = node.data.label || ''
    const match = label.toLowerCase().includes(keyword.toLowerCase())
    if (match) {
      matchedIds.add(node.id)
      updates[node.id] = ['highlighted']
    } else {
      updates[node.id] = ['inactive']
    }
  })

  // 匹配的节点的邻居也高亮
  edges.forEach(e => {
    if (matchedIds.has(e.source) || matchedIds.has(e.target)) {
      updates[e.source] = ['highlighted']
      updates[e.target] = ['highlighted']
    }
  })

  graphInstance.setElementState(updates)
  graphInstance.draw()

  // 更新显示数量
  displayNodeCount.value = Object.values(updates).filter(v => v[0] === 'highlighted').length
}

function handleClearSearch() {
  searchKeywords.value = []
  searchText.value = ''
  if (!graphInstance) return

  const { nodes, edges } = graphInstance.getData()
  const updates = {}
  nodes.forEach(n => (updates[n.id] = []))
  edges.forEach(e => (updates[e.id] = []))
  graphInstance.setElementState(updates)
  graphInstance.draw()

  displayNodeCount.value = stats.nodeCount
  displayEdgeCount.value = stats.edgeCount
}

// ---- 适应画布 ----
function handleFitView() {
  if (graphInstance) {
    try { graphInstance.fitView() } catch { /* ignore */ }
  }
}

// ---- 删除节点 ----
async function handleDeleteNode() {
  if (!selectedNode.value) return
  try {
    await deleteGraphNode(props.knowledgeId, selectedNode.value.elementId)
    message.success('节点已删除')
    detailVisible.value = false
    loadGraphData()
  } catch (e) {
    console.error('[知识图谱] 删除节点失败', e)
  }
}

// ---- 删除边 ----
async function handleDeleteEdge() {
  if (!selectedEdge.value) return
  try {
    await deleteGraphEdge(props.knowledgeId, selectedEdge.value.elementId)
    message.success('关系已删除')
    detailVisible.value = false
    loadGraphData()
  } catch (e) {
    console.error('[知识图谱] 删除边失败', e)
  }
}

// ---- ResizeObserver ----
function setupResizeObserver() {
  if (!window.ResizeObserver || !canvasRef.value) return
  resizeObserver = new ResizeObserver(() => {
    if (!canvasRef.value || !graphInstance) return
    const w = canvasRef.value.offsetWidth
    const h = canvasRef.value.offsetHeight
    graphInstance.changeSize(w, h)
  })
  resizeObserver.observe(canvasRef.value)
}

// ---- 生命周期 ----
onMounted(() => {
  loadGraphData()
  // 延迟设置 ResizeObserver，等 DOM 渲染
  nextTick(() => setupResizeObserver())
})

onUnmounted(() => {
  if (resizeObserver && canvasRef.value) {
    resizeObserver.unobserve(canvasRef.value)
  }
  if (graphInstance) {
    try { graphInstance.destroy() } catch { /* ignore */ }
    graphInstance = null
  }
})

// 切换 tab 时重新加载
watch(() => props.knowledgeId, () => {
  loadGraphData()
})
</script>

<style scoped>
.knowledge-graph-tab {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 500px;
}

.kg-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}

.kg-toolbar-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.kg-toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.kg-search {
  width: 200px;
}

.kg-stats {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 4px 12px;
  margin-bottom: 8px;
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  font-size: 13px;
}

.kg-stat-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.kg-stat-label {
  color: #8c8c8c;
  font-weight: 500;
}

.kg-stat-value {
  color: #262626;
  font-weight: 600;
}

.kg-stat-total {
  color: #bfbfbf;
  font-size: 11px;
}

.kg-canvas-wrapper {
  flex: 1;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  overflow: hidden;
  position: relative;
  min-height: 400px;
}

.kg-canvas {
  width: 100%;
  height: 100%;
}

.kg-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #a1a1aa;
}

.kg-empty p {
  margin-bottom: 8px;
}

.kg-empty-hint {
  font-size: 13px;
  color: #bfbfbf;
}

.kg-detail-actions {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
