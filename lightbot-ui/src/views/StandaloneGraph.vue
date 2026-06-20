<template>
  <div class="standalone-graph-page">
    <!-- 页面头部 -->
    <div class="page-header">
      <div>
        <h1 class="page-title">知识图谱</h1>
        <p class="page-desc">全局独立知识图谱，支持 JSONL 导入、语义搜索、节点/边编辑</p>
      </div>
      <div class="page-header-actions">
        <span class="neo4j-status" :class="neo4jAvailable ? 'connected' : 'disconnected'">
          <span class="neo4j-dot"></span>
          {{ neo4jAvailable ? 'Neo4j 已连接' : 'Neo4j 未连接' }}
        </span>
        <span v-if="stats.nodeCount > 0" class="header-stats">
          {{ stats.nodeCount }} 节点 / {{ stats.edgeCount }} 边
        </span>
      </div>
    </div>

    <!-- 工具栏 -->
    <div class="sg-toolbar">
      <div class="sg-toolbar-row">
        <div class="sg-toolbar-left">
          <a-input
            v-model:value="searchText"
            placeholder="搜索节点名称..."
            allow-clear
            size="middle"
            class="sg-search"
            @press-enter="handleSearch"
          >
            <template #prefix><SearchOutlined /></template>
          </a-input>
          <a-button size="middle" @click="handleSearch" :disabled="!searchText">搜索</a-button>
          <a-tooltip v-if="searchKeywords.length > 0" title="清除高亮">
            <a-button size="middle" @click="handleClearSearch">
              <template #icon><ClearOutlined /></template>
            </a-button>
          </a-tooltip>

          <a-input
            v-model:value="semanticQuery"
            placeholder="语义搜索..."
            allow-clear
            size="middle"
            class="sg-search"
            @press-enter="handleSemanticSearch"
          >
            <template #prefix><ThunderboltOutlined /></template>
          </a-input>
          <a-button size="middle" @click="handleSemanticSearch" :loading="semanticSearching" :disabled="!semanticQuery">
            语义搜索
          </a-button>
          <a-tooltip>
            <template #title>
              <div style="max-width: 260px">
                <div style="font-weight: 600; margin-bottom: 4px">什么是语义搜索？</div>
                <div>基于向量相似度匹配节点含义，而非精确匹配文字。</div>
                <div style="margin-top: 6px; color: #bbb">示例：搜索"数据库技术"可以找到"MySQL"、"PostgreSQL"等节点</div>
              </div>
            </template>
            <QuestionCircleOutlined style="color: #888; cursor: help; font-size: 14px" />
          </a-tooltip>
        </div>
      </div>
      <div class="sg-toolbar-row sg-toolbar-actions">
        <a-button size="middle" type="primary" @click="showNodeCreateModal = true" :disabled="!neo4jAvailable">
          <template #icon><PlusOutlined /></template> 新建节点
        </a-button>
        <a-button size="middle" @click="showEdgeCreateModal = true" :disabled="!neo4jAvailable">
          <template #icon><LinkOutlined /></template> 新建关系
        </a-button>
        <a-button size="middle" :disabled="!neo4jAvailable" @click="showImportModal = true">
          <template #icon><UploadOutlined /></template> 导入 JSONL
        </a-button>
        <a-button v-if="stats.nodeCount > 0" size="middle" danger :loading="deleting" @click="confirmDeleteGraph">
          <template #icon><DeleteOutlined /></template> 清空图谱
        </a-button>
        <a-tooltip title="为节点创建向量索引" placement="bottom">
          <a-button size="middle" :loading="rebuildingIndex" :disabled="!neo4jAvailable" @click="handleRebuildIndex">
            <template #icon><ThunderboltOutlined /></template> 重建索引
          </a-button>
        </a-tooltip>
      </div>
    </div>

    <!-- 图谱画布 -->
    <div class="sg-canvas-wrapper" ref="canvasWrapperRef">
      <div v-if="graphReady" class="sg-canvas" ref="canvasRef"></div>
      <div v-if="graphReady" class="sg-fit-btn">
        <a-tooltip title="适应画布" placement="left">
          <a-button shape="circle" size="small" @click="handleFitView">
            <template #icon><CompressOutlined /></template>
          </a-button>
        </a-tooltip>
      </div>
      <div v-else class="sg-empty">
        <a-spin v-if="loading" tip="加载图谱中..." />
        <template v-else>
          <p>暂无知识图谱数据</p>
          <p class="sg-empty-hint">上传 JSONL 文件或手动创建节点来构建图谱</p>
        </template>
      </div>
    </div>

    <!-- 节点/边详情抽屉 -->
    <a-drawer
      v-model:open="detailVisible"
      :title="detailTitle"
      :width="400"
      :bodyStyle="{ padding: '16px' }"
    >
      <template v-if="detailType === 'node' && selectedNode">
        <a-descriptions :column="1" size="small" bordered>
          <a-descriptions-item label="名称">{{ selectedNode.name }}</a-descriptions-item>
          <a-descriptions-item label="类型">{{ selectedNode.entityType || '-' }}</a-descriptions-item>
          <a-descriptions-item label="描述">{{ selectedNode.description || '-' }}</a-descriptions-item>
          <a-descriptions-item label="来源">{{ selectedNode.source || '-' }}</a-descriptions-item>
          <a-descriptions-item v-if="selectedNode.score" label="相似度">
            {{ (selectedNode.score * 100).toFixed(1) }}%
          </a-descriptions-item>
        </a-descriptions>
        <div class="sg-detail-actions">
          <a-button size="small" @click="openEditNode">编辑</a-button>
          <a-button size="small" danger @click="confirmDeleteNode">删除节点</a-button>
        </div>
      </template>
      <template v-else-if="detailType === 'edge' && selectedEdge">
        <a-descriptions :column="1" size="small" bordered>
          <a-descriptions-item label="关系类型">{{ selectedEdge.relationType }}</a-descriptions-item>
          <a-descriptions-item label="描述">{{ selectedEdge.description || '-' }}</a-descriptions-item>
          <a-descriptions-item label="权重">{{ selectedEdge.weight ?? '-' }}</a-descriptions-item>
          <a-descriptions-item label="来源">{{ selectedEdge.source || '-' }}</a-descriptions-item>
        </a-descriptions>
        <div class="sg-detail-actions">
          <a-button size="small" @click="openEditEdge">编辑</a-button>
          <a-button size="small" danger @click="confirmDeleteEdge">删除关系</a-button>
        </div>
      </template>
    </a-drawer>

    <!-- 新建/编辑节点弹窗 -->
    <a-modal
      v-model:open="showNodeCreateModal"
      :title="editingNode ? '编辑节点' : '新建节点'"
      :width="480"
      :maskClosable="false"
      @ok="handleNodeSubmit"
      @cancel="resetNodeForm"
      :confirm-loading="nodeSubmitting"
    >
      <a-form :model="nodeForm" :label-col="{ span: 4 }">
        <a-form-item label="名称">
          <a-input v-model:value="nodeForm.name" placeholder="实体名称（必填）" :maxlength="30" show-count />
        </a-form-item>
        <a-form-item label="类型">
          <a-select v-model:value="nodeForm.entityType" placeholder="选择实体类型">
            <a-select-option v-for="t in entityTypes" :key="t" :value="t">{{ t }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="nodeForm.description" :rows="3" placeholder="实体描述（可选）" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 新建/编辑关系弹窗 -->
    <a-modal
      v-model:open="showEdgeCreateModal"
      :title="editingEdge ? '编辑关系' : '新建关系'"
      :width="480"
      :maskClosable="false"
      @ok="handleEdgeSubmit"
      @cancel="resetEdgeForm"
      :confirm-loading="edgeSubmitting"
    >
      <a-form :model="edgeForm" :label-col="{ span: 4 }">
        <a-form-item label="起始节点">
          <a-select
            v-model:value="edgeForm.headName"
            placeholder="选择起始节点（必填）"
            show-search
            :filter-option="(input, option) => option.value.toLowerCase().includes(input.toLowerCase())"
            :disabled="!!editingEdge"
          >
            <a-select-option v-for="name in allNodeNames" :key="name" :value="name">{{ name }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="关系类型">
          <a-input v-model:value="edgeForm.relationType" placeholder="如：担任、隶属于、使用（必填）" />
        </a-form-item>
        <a-form-item label="目标节点">
          <a-select
            v-model:value="edgeForm.tailName"
            placeholder="选择目标节点（必填）"
            show-search
            :filter-option="(input, option) => option.value.toLowerCase().includes(input.toLowerCase())"
            :disabled="!!editingEdge"
          >
            <a-select-option v-for="name in allNodeNames" :key="name" :value="name">{{ name }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="edgeForm.description" :rows="2" placeholder="关系描述（可选）" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 导入 JSONL 弹窗 -->
    <a-modal
      v-model:open="showImportModal"
      title="导入知识图谱"
      :maskClosable="false"
      :footer="null"
      :width="560"
    >
      <a-alert type="info" show-icon style="margin-bottom: 16px">
        <template #message>通过 JSONL 文件批量导入节点和关系，每行一个三元组（头实体 → 关系 → 尾实体）。</template>
      </a-alert>

      <div class="import-section">
        <div class="import-section-title">文件格式说明</div>
        <pre class="import-format-pre">{"head": "张三", "headType": "人物", "headDesc": "软件工程师",
 "relation": "就职于", "relationDesc": "任职关系",
 "tail": "腾讯", "tailType": "组织", "tailDesc": "互联网公司"}</pre>
        <div class="import-field-desc">
          <p><b>必填字段：</b><code>head</code>（头实体）、<code>relation</code>（关系）、<code>tail</code>（尾实体）</p>
          <p><b>可选字段：</b><code>headType</code>、<code>headDesc</code>、<code>relationDesc</code>、<code>tailType</code>、<code>tailDesc</code></p>
        </div>
      </div>

      <a-divider style="margin: 12px 0" />

      <div class="import-section">
        <div class="import-section-title">上传要求</div>
        <ul class="import-rules">
          <li>文件格式：<code>.jsonl</code>（UTF-8 编码）</li>
          <li>文件大小：不超过 <b>5MB</b></li>
          <li>每行一个 JSON 对象，不可跨行</li>
          <li>已存在的节点和关系会自动合并，不会重复创建</li>
        </ul>
      </div>

      <a-divider style="margin: 12px 0" />

      <div class="import-actions">
        <a-upload
          :before-upload="handleJsonlUpload"
          :show-upload-list="false"
          accept=".jsonl"
        >
          <a-button type="primary" :loading="importing">
            <template #icon><UploadOutlined /></template>
            {{ importing ? '导入中...' : '选择文件' }}
          </a-button>
        </a-upload>
        <a-button @click="downloadSample">
          <template #icon><DownloadOutlined /></template> 下载示例文件
        </a-button>
      </div>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { Graph } from '@antv/g6'
import {
  SearchOutlined, DeleteOutlined, CompressOutlined, UploadOutlined, DownloadOutlined,
  PlusOutlined, LinkOutlined, ThunderboltOutlined, ClearOutlined, QuestionCircleOutlined
} from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import {
  getStandaloneSubgraph, getStandaloneStats, deleteStandaloneGraph,
  importGraphFromJsonl, semanticSearchGraph,
  createStandaloneNode, updateStandaloneNode, deleteStandaloneNode,
  createStandaloneEdge, updateStandaloneEdge, deleteStandaloneEdge,
  getStandaloneNodeNames, rebuildVectorIndex
} from '../api/graph'

// ---- state ----
const loading = ref(false)
const importing = ref(false)
const deleting = ref(false)
const semanticSearching = ref(false)
const rebuildingIndex = ref(false)
const graphReady = ref(false)
const searchText = ref('')
const searchKeywords = ref([])
const semanticQuery = ref('')

const neo4jAvailable = ref(false)
const allNodeNames = ref([])
const stats = reactive({ nodeCount: 0, edgeCount: 0 })

const detailVisible = ref(false)
const detailType = ref('')
const selectedNode = ref(null)
const selectedEdge = ref(null)

const canvasRef = ref(null)
const canvasWrapperRef = ref(null)

// 节点表单
const showNodeCreateModal = ref(false)
const editingNode = ref(null)
const nodeSubmitting = ref(false)
const nodeForm = reactive({ name: '', entityType: '其他', description: '' })

// 导入弹窗
const showImportModal = ref(false)

// 边表单
const showEdgeCreateModal = ref(false)
const editingEdge = ref(null)
const edgeSubmitting = ref(false)
const edgeForm = reactive({ headName: undefined, relationType: '', tailName: undefined, description: '' })

const entityTypes = ['人物', '组织', '职位', '项目', '产品', '地点', '技术', '概念', '事件', '其他']

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

  // 平行边偏移：同 source-target 的边交替向两侧弯曲
  const edgeGroups = {}
  edges.forEach(e => {
    const key = [e.source, e.target].sort().join('->')
    if (!edgeGroups[key]) edgeGroups[key] = []
    edgeGroups[key].push(e)
  })
  const BASE_OFFSET = 30
  Object.values(edgeGroups).forEach(group => {
    if (group.length <= 1) return
    group.forEach((edge, i) => {
      const sign = i % 2 === 0 ? 1 : -1
      const magnitude = Math.ceil(i / 2)
      edge.data.curveOffset = sign * magnitude * BASE_OFFSET
    })
  })

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
        controlPoints: (d) => {
          const offset = d.data?.curveOffset
          if (!offset) return undefined
          const sx = d.sourceNode?.style?.x ?? d.sourceNode?.x ?? 0
          const sy = d.sourceNode?.style?.y ?? d.sourceNode?.y ?? 0
          const tx = d.targetNode?.style?.x ?? d.targetNode?.x ?? 0
          const ty = d.targetNode?.style?.y ?? d.targetNode?.y ?? 0
          const mx = (sx + tx) / 2
          const my = (sy + ty) / 2
          const dx = tx - sx
          const dy = ty - sy
          const len = Math.sqrt(dx * dx + dy * dy) || 1
          return [{ x: mx + (-dy / len) * offset, y: my + (dx / len) * offset }]
        },
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
      { type: 'drag-element', key: 'drag-element', disable: true },
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

  graphInstance.on('canvas:click', () => {
    detailVisible.value = false
  })
  // 布局动画结束后启用节点拖拽
  graphInstance.on('afterlayout', () => {
    graphInstance.updateBehavior({ key: 'drag-element', disable: false })
  })
}

// ---- 加载图谱数据 ----
async function loadGraphData() {
  loading.value = true
  try {
    // stats 独立请求，失败不影响图谱加载
    const statsRes = await getStandaloneStats().catch(() => null)
    neo4jAvailable.value = statsRes?.data?.available ?? false

    if (!neo4jAvailable.value) {
      graphReady.value = false
      loading.value = false
      return
    }

    // 加载节点名称列表（用于关系创建下拉选择）
    getStandaloneNodeNames().then(res => {
      allNodeNames.value = res.data || []
    }).catch(() => {})

    const subgraphRes = await getStandaloneSubgraph({})
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

    graphReady.value = true
    await nextTick()

    initGraph()
    if (graphInstance) {
      const data = formatGraphData(subgraph)
      graphInstance.setData(data)
      graphInstance.render()
    }
  } catch (e) {
    console.error('[独立图谱] 加载失败', e)
    message.error('加载知识图谱失败')
  } finally {
    loading.value = false
  }
}

// ---- JSONL 上传 ----
function handleJsonlUpload(file) {
  const isJsonl = file.name.endsWith('.jsonl')
  if (!isJsonl) {
    message.error('请上传 .jsonl 格式的文件')
    return false
  }
  if (file.size > 5 * 1024 * 1024) {
    message.error('文件大小不能超过 5MB')
    return false
  }

  importing.value = true
  importGraphFromJsonl(file).then(res => {
    const s = res.data
    message.success(`导入完成：${s.nodeCount} 个节点，${s.edgeCount} 条边`)
    showImportModal.value = false
    loadGraphData()
  }).catch(() => {
    // interceptor 已处理错误提示
  }).finally(() => {
    importing.value = false
  })

  return false // 阻止 ant-upload 自动上传
}

function downloadSample() {
  const content = [
    '{"head": "张三", "headType": "人物", "headDesc": "软件工程师", "relation": "就职于", "relationDesc": "任职关系", "tail": "腾讯", "tailType": "组织", "tailDesc": "互联网公司"}',
    '{"head": "张三", "headType": "人物", "headDesc": "软件工程师", "relation": "精通", "relationDesc": "技术能力", "tail": "Java", "tailType": "技术", "tailDesc": "编程语言"}',
    '{"head": "腾讯", "tailType": "组织", "tailDesc": "互联网公司", "relation": "开发", "relationDesc": "产品开发", "tail": "微信", "headType": "产品", "headDesc": "社交应用"}',
  ].join('\n')
  const blob = new Blob([content], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = 'graph-sample.jsonl'
  a.click()
  URL.revokeObjectURL(url)
}

// ---- 语义搜索 ----
async function handleSemanticSearch() {
  if (!semanticQuery.value?.trim()) return
  semanticSearching.value = true
  try {
    const res = await semanticSearchGraph(semanticQuery.value.trim(), 10)
    const nodes = res.data || []
    if (nodes.length === 0) {
      message.info('未找到匹配的节点')
      return
    }

    // 高亮语义搜索结果
    if (graphInstance) {
      const matchedNames = new Set(nodes.map(n => n.name))
      const { nodes: graphNodes, edges } = graphInstance.getData()
      const updates = {}

      graphNodes.forEach(node => {
        const label = node.data.label || ''
        if (matchedNames.has(label)) {
          updates[node.id] = ['highlighted']
        } else {
          updates[node.id] = ['inactive']
        }
      })

      // 匹配节点的邻居也高亮
      edges.forEach(e => {
        if (updates[e.source]?.[0] === 'highlighted' || updates[e.target]?.[0] === 'highlighted') {
          updates[e.source] = ['highlighted']
          updates[e.target] = ['highlighted']
        }
      })

      graphInstance.setElementState(updates)
      graphInstance.draw()
      searchKeywords.value = [semanticQuery.value.trim()]
    }
  } catch (e) {
    console.error('[独立图谱] 语义搜索失败', e)
  } finally {
    semanticSearching.value = false
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

  edges.forEach(e => {
    if (matchedIds.has(e.source) || matchedIds.has(e.target)) {
      updates[e.source] = ['highlighted']
      updates[e.target] = ['highlighted']
    }
  })

  graphInstance.setElementState(updates)
  graphInstance.draw()
}

function handleClearSearch() {
  searchKeywords.value = []
  searchText.value = ''
  semanticQuery.value = ''
  if (!graphInstance) return

  const { nodes, edges } = graphInstance.getData()
  const updates = {}
  nodes.forEach(n => (updates[n.id] = []))
  edges.forEach(e => (updates[e.id] = []))
  graphInstance.setElementState(updates)
  graphInstance.draw()
}

// ---- 适应画布 ----
function handleFitView() {
  if (graphInstance) {
    try { graphInstance.fitView() } catch { /* ignore */ }
  }
}

// ---- 清空图谱 ----
function confirmDeleteGraph() {
  Modal.confirm({
    title: '确定清空整个知识图谱？',
    content: '此操作不可恢复，所有节点和关系将被永久删除。',
    okText: '清空',
    okType: 'danger',
    cancelText: '取消',
    onOk: handleDeleteGraph,
  })
}

async function handleDeleteGraph() {
  deleting.value = true
  try {
    await deleteStandaloneGraph()
    message.success('图谱已清空')
    graphReady.value = false
    Object.assign(stats, { nodeCount: 0, edgeCount: 0 })
    if (graphInstance) {
      try { graphInstance.destroy() } catch { /* ignore */ }
      graphInstance = null
    }
  } catch (e) {
    console.error('[独立图谱] 清空失败', e)
  } finally {
    deleting.value = false
  }
}

async function handleRebuildIndex() {
  rebuildingIndex.value = true
  try {
    const res = await rebuildVectorIndex()
    const count = res.data || 0
    if (count > 0) {
      message.success(`索引重建完成，补生成 ${count} 个节点的向量`)
    } else {
      message.info('所有节点均已包含向量，无需重建')
    }
  } catch (e) {
    console.error('[独立图谱] 重建索引失败', e)
  } finally {
    rebuildingIndex.value = false
  }
}

// ---- 节点 CRUD ----
function openEditNode() {
  if (!selectedNode.value) return
  editingNode.value = selectedNode.value
  nodeForm.name = selectedNode.value.name || ''
  nodeForm.entityType = selectedNode.value.entityType || '其他'
  nodeForm.description = selectedNode.value.description || ''
  showNodeCreateModal.value = true
  detailVisible.value = false
}

async function handleNodeSubmit() {
  if (!nodeForm.name.trim()) {
    message.warning('请输入节点名称')
    return
  }
  nodeSubmitting.value = true
  try {
    if (editingNode.value) {
      await updateStandaloneNode(editingNode.value.elementId, {
        name: nodeForm.name,
        entityType: nodeForm.entityType,
        description: nodeForm.description
      })
      message.success('节点已更新')
    } else {
      await createStandaloneNode({
        name: nodeForm.name,
        entityType: nodeForm.entityType,
        description: nodeForm.description
      })
      message.success('节点已创建')
    }
    showNodeCreateModal.value = false
    resetNodeForm()
    loadGraphData()
  } catch (e) {
    console.error('[独立图谱] 节点操作失败', e)
  } finally {
    nodeSubmitting.value = false
  }
}

async function handleDeleteNode() {
  if (!selectedNode.value) return
  try {
    await deleteStandaloneNode(selectedNode.value.elementId)
    message.success('节点已删除')
    detailVisible.value = false
    loadGraphData()
  } catch (e) {
    console.error('[独立图谱] 删除节点失败', e)
  }
}

function confirmDeleteNode() {
  Modal.confirm({
    title: '确定删除该节点及其关联的边？',
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: handleDeleteNode
  })
}

function confirmDeleteEdge() {
  Modal.confirm({
    title: '确定删除该关系？',
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: handleDeleteEdge
  })
}

function resetNodeForm() {
  editingNode.value = null
  nodeForm.name = ''
  nodeForm.entityType = '其他'
  nodeForm.description = ''
}

// ---- 边 CRUD ----
function openEditEdge() {
  if (!selectedEdge.value) return
  editingEdge.value = selectedEdge.value
  edgeForm.relationType = selectedEdge.value.relationType || ''
  edgeForm.description = selectedEdge.value.description || ''
  edgeForm.headName = undefined
  edgeForm.tailName = undefined
  showEdgeCreateModal.value = true
  detailVisible.value = false
}

async function handleEdgeSubmit() {
  if (editingEdge.value) {
    // 编辑模式：只更新关系类型和描述
    if (!edgeForm.relationType.trim()) {
      message.warning('请输入关系类型')
      return
    }
    edgeSubmitting.value = true
    try {
      await updateStandaloneEdge(editingEdge.value.elementId, {
        relationType: edgeForm.relationType,
        description: edgeForm.description
      })
      message.success('关系已更新')
      showEdgeCreateModal.value = false
      resetEdgeForm()
      loadGraphData()
    } catch (e) {
      console.error('[独立图谱] 关系更新失败', e)
    } finally {
      edgeSubmitting.value = false
    }
  } else {
    // 创建模式
    if (!edgeForm.headName || !edgeForm.relationType.trim() || !edgeForm.tailName) {
      message.warning('请填写起始节点、关系类型和目标节点')
      return
    }
    edgeSubmitting.value = true
    try {
      await createStandaloneEdge({
        headName: edgeForm.headName,
        relationType: edgeForm.relationType,
        tailName: edgeForm.tailName,
        description: edgeForm.description
      })
      message.success('关系已创建')
      showEdgeCreateModal.value = false
      resetEdgeForm()
      loadGraphData()
    } catch (e) {
      console.error('[独立图谱] 关系创建失败', e)
    } finally {
      edgeSubmitting.value = false
    }
  }
}

async function handleDeleteEdge() {
  if (!selectedEdge.value) return
  try {
    await deleteStandaloneEdge(selectedEdge.value.elementId)
    message.success('关系已删除')
    detailVisible.value = false
    loadGraphData()
  } catch (e) {
    console.error('[独立图谱] 删除关系失败', e)
  }
}

function resetEdgeForm() {
  editingEdge.value = null
  edgeForm.headName = undefined
  edgeForm.relationType = ''
  edgeForm.tailName = undefined
  edgeForm.description = ''
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
</script>

<style scoped>
.standalone-graph-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  overflow: hidden;
  background: #fafafa;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 24px 32px 0;
  flex-shrink: 0;
}

.page-title {
  font-size: 24px;
  font-weight: 600;
  color: #171717;
  margin-bottom: 4px;
}

.page-desc {
  font-size: 14px;
  color: #71717a;
}

.page-header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-stats {
  font-size: 14px;
  color: #71717a;
  padding: 6px 14px;
  background: #fff;
  border: 1px solid #ebebeb;
  border-radius: 8px;
}

.neo4j-status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  padding: 4px 12px;
  border-radius: 8px;
  background: #fff;
  border: 1px solid #ebebeb;
}

.neo4j-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
}

.neo4j-status.connected {
  color: #16a34a;
  border-color: #bbf7d0;
  background: #f0fdf4;
}

.neo4j-status.connected .neo4j-dot {
  background: #22c55e;
  box-shadow: 0 0 4px #22c55e;
}

.neo4j-status.disconnected {
  color: #dc2626;
  border-color: #fecaca;
  background: #fef2f2;
}

.neo4j-status.disconnected .neo4j-dot {
  background: #ef4444;
}

.sg-toolbar {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px 32px;
  flex-shrink: 0;
}

.sg-toolbar-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.sg-toolbar-left {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.sg-toolbar-actions {
  justify-content: flex-end;
}

.sg-search {
  width: 200px;
}

.sg-canvas-wrapper {
  flex: 1;
  margin: 0 32px 24px;
  border: 1px solid #ebebeb;
  border-radius: 12px;
  overflow: hidden;
  position: relative;
  min-height: 400px;
  background: #fff;
}

.sg-canvas {
  width: 100%;
  height: 100%;
}

.sg-fit-btn {
  position: absolute;
  right: 12px;
  bottom: 12px;
  z-index: 10;
}

.sg-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #a1a1aa;
}

.sg-empty p {
  margin-bottom: 8px;
}

.sg-empty-hint {
  font-size: 13px;
  color: #bfbfbf;
}

.sg-detail-actions {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.import-section {
  margin-bottom: 4px;
}
.import-section-title {
  font-weight: 600;
  font-size: 14px;
  margin-bottom: 8px;
}
.import-format-pre {
  background: #f5f5f5;
  border-radius: 6px;
  padding: 10px 12px;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0 0 8px 0;
}
.import-field-desc {
  font-size: 13px;
  color: #555;
}
.import-field-desc p {
  margin-bottom: 4px;
}
.import-field-desc code {
  background: #f0f0f0;
  padding: 1px 4px;
  border-radius: 3px;
  font-size: 12px;
}
.import-rules {
  padding-left: 20px;
  margin: 0;
  font-size: 13px;
  color: #555;
}
.import-rules li {
  margin-bottom: 4px;
}
.import-rules code {
  background: #f0f0f0;
  padding: 1px 4px;
  border-radius: 3px;
  font-size: 12px;
}
.import-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
</style>
