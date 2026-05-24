<template>
  <div class="workflow-edit-page">
    <!-- 顶部工具栏 -->
    <div class="workflow-toolbar">
      <button class="btn-back" @click="goBack">
        <ArrowLeftOutlined /> 返回
      </button>
      <h1 class="workflow-title">{{ agent?.name || '工作流配置' }}</h1>
      <div class="toolbar-status">
        <a-dropdown v-if="validationErrors.length > 0" :trigger="['click']">
          <span class="status-error clickable">
            <ExclamationCircleOutlined /> {{ validationErrors.length }} 个配置错误
            <DownOutlined style="margin-left: 4px; font-size: 10px;" />
          </span>
          <template #overlay>
            <div class="error-dropdown">
              <div class="error-header">配置错误详情</div>
              <div class="error-list">
                <div v-for="err in validationErrors" :key="err.nodeId + err.field" class="error-item">
                  <span class="error-node">{{ getNodeTitleById(err.nodeId) || '工作流全局' }}</span>
                  <span class="error-field">{{ err.field }}</span>
                  <span class="error-msg">{{ err.message }}</span>
                </div>
              </div>
            </div>
          </template>
        </a-dropdown>
        <span v-else-if="nodes.length > 2" class="status-valid">
          <CheckCircleOutlined /> 配置完整
        </span>
        <span v-else class="status-empty">
          请添加节点并配置
        </span>
      </div>
      <div class="toolbar-actions">
        <a-button v-if="canUndo" type="default" @click="undoAction">
          <UndoOutlined /> 撤回
        </a-button>
        <a-button type="default" @click="validateWorkflow">
          验证配置
        </a-button>
        <a-button type="primary" @click="saveWorkflow" :disabled="saving" :loading="saving">
          <SaveOutlined /> 保存工作流
        </a-button>
      </div>
    </div>

    <!-- 三栏布局 -->
    <div class="workflow-content">
      <!-- 左侧节点面板 -->
      <div class="node-panel" :class="{ collapsed: panelCollapsed }">
        <div class="panel-header">
          <span v-if="!panelCollapsed">节点库</span>
          <button class="btn-collapse" @click="panelCollapsed = !panelCollapsed">
            <LeftOutlined v-if="!panelCollapsed" />
            <RightOutlined v-else />
          </button>
        </div>
        <div class="panel-body" v-if="!panelCollapsed">
          <a-input
            v-model:value="nodeSearch"
            placeholder="搜索节点..."
            allow-clear
            size="small"
          >
            <template #prefix><SearchOutlined /></template>
          </a-input>

          <div class="node-group">
            <div class="group-title">基础节点</div>
            <NodeItem
              type="llm"
              title="大模型"
              desc="调用大模型生成内容"
              color="#7c3aed"
              draggable="true"
              @dragstart="onDragStart($event, 'llm')"
            />
            <NodeItem
              type="condition"
              title="条件判断"
              desc="根据条件选择分支"
              color="#d97706"
              draggable="true"
              @dragstart="onDragStart($event, 'condition')"
            />
          </div>

          <div class="node-group">
            <div class="group-title">扩展节点</div>
            <NodeItem
              type="retrieval"
              title="知识检索"
              desc="从知识库检索内容"
              color="#4f46e5"
              draggable="true"
              @dragstart="onDragStart($event, 'retrieval')"
            />
            <NodeItem
              type="tool"
              title="工具调用"
              desc="执行预设工具"
              color="#059669"
              draggable="true"
              @dragstart="onDragStart($event, 'tool')"
            />
          </div>
        </div>
      </div>

      <!-- 中间画布 -->
      <div class="canvas-area" @dragover.prevent @drop="onDrop">
        <VueFlow
          v-if="nodes.length > 0"
          :nodes="nodes"
          :edges="edges"
          @connect="onConnect"
          @node-click="onNodeClick"
          @pane-click="onPaneClick"
          :default-viewport="{ zoom: 0.8, x: 0, y: 0 }"
          :min-zoom="0.1"
          :max-zoom="4"
        >
          <Background :gap="[20, 20]" pattern-color="#e5e7eb" />
          <Controls position="bottom-right" show-zoom show-fit-view />
          <MiniMap
            position="bottom-left"
            :style="{ width: '350px', height: '200px', borderRadius: '8px', border: '1px solid #e5e7eb', background: '#fff' }"
            :node-color="getNodeColor"
            :node-stroke-width="3"
          />

          <!-- 自定义节点模板 -->
          <template #node-start="props"><StartNode v-bind="props" /></template>
          <template #node-end="props"><EndNode v-bind="props" /></template>
          <template #node-llm="props"><LlmNode v-bind="props" /></template>
          <template #node-condition="props"><ConditionNode v-bind="props" /></template>
          <template #node-retrieval="props"><RetrievalNode v-bind="props" /></template>
          <template #node-tool="props"><ToolNode v-bind="props" /></template>
        </VueFlow>

        <!-- 空状态提示 -->
        <div v-if="nodes.length === 0" class="canvas-empty">
          <p>从左侧拖拽节点到画布开始构建工作流</p>
        </div>
      </div>

      <!-- 右侧配置面板 -->
      <div class="config-panel" v-if="selectedNode">
        <div class="panel-header">
          <div class="node-type-badge">
            <div class="type-icon" :style="{ background: getNodeColor(selectedNode.type) + '20', color: getNodeColor(selectedNode.type) }">
              <RobotOutlined v-if="selectedNode.type === 'llm'" />
              <ForkOutlined v-if="selectedNode.type === 'condition'" />
              <BookOutlined v-if="selectedNode.type === 'retrieval'" />
              <ToolOutlined v-if="selectedNode.type === 'tool'" />
              <PlayCircleOutlined v-if="selectedNode.type === 'start'" />
              <StopOutlined v-if="selectedNode.type === 'end'" />
            </div>
            <span class="type-name">{{ getNodeTitle(selectedNode.type) }}</span>
          </div>
          <button class="btn-close" @click="selectedNode = null">
            <CloseOutlined />
          </button>
        </div>
        <div class="panel-body">
          <!-- 节点错误提示 -->
          <div v-if="getNodeErrors(selectedNode.id).length > 0" class="node-errors">
            <div v-for="err in getNodeErrors(selectedNode.id)" :key="err.field" class="error-item">
              <ExclamationCircleOutlined /> {{ err.message }}
            </div>
          </div>

          <a-form layout="vertical">
            <a-form-item label="节点名称">
              <a-input
                v-model:value="selectedNode.data.label"
                placeholder="输入节点名称"
                @change="syncNodes"
              />
            </a-form-item>

            <!-- LLM节点配置 -->
            <template v-if="selectedNode.type === 'llm'">
              <a-form-item label="选择模型" required>
                <a-select
                  v-model:value="selectedNode.data.modelId"
                  placeholder="选择模型提供商"
                  @change="onModelChange"
                >
                  <a-select-option v-for="p in providers" :key="p.id" :value="p.id">
                    {{ p.name }}
                  </a-select-option>
                </a-select>
              </a-form-item>
              <a-form-item label="提示词模板" required>
                <a-textarea
                  v-model:value="selectedNode.data.promptTemplate"
                  placeholder="使用 {{input}} 表示用户输入，{{retrieval}} 表示检索结果"
                  :rows="4"
                  @change="syncNodes"
                />
              </a-form-item>
              <a-form-item label="温度参数">
                <a-slider
                  v-model:value="selectedNode.data.temperature"
                  :min="0"
                  :max="2"
                  :step="0.1"
                  @change="syncNodes"
                />
                <span class="param-value">{{ selectedNode.data.temperature || 0.7 }}</span>
              </a-form-item>
            </template>

            <!-- 条件节点配置 -->
            <template v-if="selectedNode.type === 'condition'">
              <a-form-item label="条件分支配置">
                <div class="branches-config">
                  <div class="branch-list">
                    <div v-for="(branch, index) in selectedNode.data.branches" :key="index" class="branch-item">
                      <div class="branch-row">
                        <a-input
                          v-model:value="branch.condition"
                          placeholder="条件表达式，如: input === 'yes'"
                          size="small"
                          style="width: 100%"
                          @change="syncNodes"
                        />
                      </div>
                      <div class="branch-row">
                        <a-select
                          v-model:value="branch.targetNodeId"
                          placeholder="选择目标节点"
                          size="small"
                          style="width: 100%"
                          @change="syncNodes"
                        >
                          <a-select-option v-for="n in getTargetNodes()" :key="n.id" :value="n.id">
                            {{ n.data.label || getNodeTitle(n.type) }}
                          </a-select-option>
                        </a-select>
                        <a-button type="text" danger size="small" @click="removeBranch(index)">
                          <DeleteOutlined />
                        </a-button>
                      </div>
                    </div>
                  </div>
                  <a-button type="dashed" block size="small" @click="addBranch">
                    <PlusOutlined /> 添加分支
                  </a-button>
                </div>
              </a-form-item>
            </template>

            <!-- 知识检索配置 -->
            <template v-if="selectedNode.type === 'retrieval'">
              <a-form-item label="选择知识库" required>
                <a-select
                  v-model:value="selectedNode.data.knowledgeId"
                  placeholder="选择知识库"
                  @change="onKnowledgeChange"
                >
                  <a-select-option v-for="k in knowledgeList" :key="k.id" :value="k.id">
                    {{ k.name }}
                  </a-select-option>
                </a-select>
              </a-form-item>
              <a-form-item label="检索数量">
                <a-input-number
                  v-model:value="selectedNode.data.topK"
                  :min="1"
                  :max="10"
                  @change="syncNodes"
                />
              </a-form-item>
            </template>

            <!-- 工具调用配置 -->
            <template v-if="selectedNode.type === 'tool'">
              <a-form-item label="选择工具" required>
                <a-select
                  v-model:value="selectedNode.data.toolId"
                  placeholder="选择工具"
                  @change="onToolChange"
                >
                  <a-select-option v-for="t in tools" :key="t.id" :value="t.id">
                    {{ t.displayName || t.name }}
                  </a-select-option>
                </a-select>
              </a-form-item>
            </template>
          </a-form>

          <!-- 删除节点按钮 -->
          <div class="panel-footer" v-if="selectedNode.type !== 'start' && selectedNode.type !== 'end'">
            <a-button type="text" danger @click="deleteSelectedNode">
              <DeleteOutlined /> 删除节点
            </a-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed, shallowRef, triggerRef, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { VueFlow, useVueFlow } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import { MiniMap } from '@vue-flow/minimap'
import {
  ArrowLeftOutlined, SaveOutlined, CheckCircleOutlined, ExclamationCircleOutlined,
  SearchOutlined, LeftOutlined, RightOutlined, CloseOutlined, DeleteOutlined,
  RobotOutlined, ForkOutlined, BookOutlined, ToolOutlined, PlayCircleOutlined,
  StopOutlined, PlusOutlined, DownOutlined, UndoOutlined
} from '@ant-design/icons-vue'
import { message, notification } from 'ant-design-vue'
import { getAgentDetail, updateAgent } from '../api/agent'
import { getModelProviders } from '../api/modelProvider'
import { getKnowledgeList } from '../api/knowledge'
import { getTools } from '../api/tool'
import NodeItem from '../views/workflow/components/NodeItem.vue'
import StartNode from '../views/workflow/nodes/StartNode.vue'
import EndNode from '../views/workflow/nodes/EndNode.vue'
import LlmNode from '../views/workflow/nodes/LlmNode.vue'
import ConditionNode from '../views/workflow/nodes/ConditionNode.vue'
import RetrievalNode from '../views/workflow/nodes/RetrievalNode.vue'
import ToolNode from '../views/workflow/nodes/ToolNode.vue'

const route = useRoute()
const router = useRouter()
const agentId = route.params.agentId

// VueFlow hooks
const { fitView } = useVueFlow()

// 状态
const agent = ref(null)
const saving = ref(false)
const panelCollapsed = ref(false)
const nodeSearch = ref('')
const selectedNode = ref(null)
const validationErrors = ref([])

// 资源列表
const providers = ref([])
const knowledgeList = ref([])
const tools = ref([])

// 节点和边数据（使用 shallowRef 避免递归更新）
const nodes = shallowRef([])
const edges = shallowRef([])

// 操作历史记录（用于撤回）
const history = ref([])
const canUndo = computed(() => history.value.length > 0)

// 记录操作历史
function recordHistory() {
  history.value.push({
    nodes: JSON.parse(JSON.stringify(nodes.value)),
    edges: JSON.parse(JSON.stringify(edges.value))
  })
  // 限制历史记录数量
  if (history.value.length > 20) {
    history.value.shift()
  }
}

// 撤回操作
function undoAction() {
  if (history.value.length === 0) return
  const lastState = history.value.pop()
  nodes.value = lastState.nodes
  edges.value = lastState.edges
  triggerRef(nodes)
  triggerRef(edges)
  selectedNode.value = null
}

// 初始化加载
onMounted(async () => {
  try {
    // 加载 Agent 数据
    const res = await getAgentDetail(agentId)
    agent.value = res.data.agent

    // 解析工作流数据
    if (res.data.agent.config) {
      const config = JSON.parse(res.data.agent.config)
      if (config.workflow) {
        nodes.value = config.workflow.nodes || []
        edges.value = config.workflow.edges || []
        triggerRef(nodes)
        triggerRef(edges)
      }
    }

    // 如果没有节点，添加默认的 start 和 end
    if (nodes.value.length === 0) {
      nodes.value = [
        { id: 'start_1', type: 'start', position: { x: 100, y: 200 }, data: { label: '开始' } },
        { id: 'end_1', type: 'end', position: { x: 600, y: 200 }, data: { label: '结束' } }
      ]
      triggerRef(nodes)
    }

    // 加载资源列表
    const [providerRes, knowledgeRes, toolRes] = await Promise.all([
      getModelProviders({ pageNum: 1, pageSize: 100 }),
      getKnowledgeList({ pageNum: 1, pageSize: 100 }),
      getTools({ pageNum: 1, pageSize: 100 })
    ])
    providers.value = providerRes.data.records || []
    knowledgeList.value = knowledgeRes.data.records || []
    tools.value = toolRes.data.records || []

    // 等待节点渲染后自动适配视图
    await nextTick()
    // 使用 setTimeout 确保 DOM 完全渲染，适配所有节点
    setTimeout(() => {
      if (nodes.value.length > 0) {
        try {
          fitView({ padding: 0.5, includeHiddenNodes: true, duration: 300 })
        } catch (e) {
          console.warn('fitView error:', e)
        }
      }
    }, 150)
  } catch (e) {
    notification.error({ message: '加载失败', description: e.message })
  }
})

// 获取节点颜色
function getNodeColor(nodeOrType) {
  const type = typeof nodeOrType === 'string' ? nodeOrType : nodeOrType?.type
  const colors = {
    start: '#22c55e',
    end: '#ef4444',
    llm: '#7c3aed',
    condition: '#d97706',
    retrieval: '#4f46e5',
    tool: '#059669'
  }
  return colors[type] || '#6b7280'
}

// 获取节点标题
function getNodeTitle(type) {
  const titles = {
    start: '开始',
    end: '结束',
    llm: '大模型',
    condition: '条件判断',
    retrieval: '知识检索',
    tool: '工具调用'
  }
  return titles[type] || type
}

// 根据节点ID获取节点标题
function getNodeTitleById(nodeId) {
  if (!nodeId) return null
  const node = nodes.value.find(n => n.id === nodeId)
  if (!node) return null
  return node.data?.label || getNodeTitle(node.type)
}

// 获取节点错误
function getNodeErrors(nodeId) {
  return validationErrors.value.filter(e => e.nodeId === nodeId)
}

// 获取可选的目标节点
function getTargetNodes() {
  return nodes.value.filter(n => n.id !== selectedNode.value?.id)
}

// 拖拽开始
function onDragStart(event, nodeType) {
  event.dataTransfer.setData('nodeType', nodeType)
}

// 拖拽放置
function onDrop(event) {
  const nodeType = event.dataTransfer.getData('nodeType')
  if (!nodeType) return

  // 记录历史用于撤回
  recordHistory()

  const rect = event.currentTarget.getBoundingClientRect()
  const position = {
    x: event.clientX - rect.left,
    y: event.clientY - rect.top
  }

  const newNode = {
    id: `node_${Date.now()}`,
    type: nodeType,
    position,
    data: getDefaultNodeData(nodeType)
  }

  nodes.value = [...nodes.value, newNode]
  triggerRef(nodes)

  // 添加节点后重新适配视图（延迟确保渲染完成）
  setTimeout(() => {
    try {
      fitView({ padding: 0.5, includeHiddenNodes: true, duration: 300 })
    } catch (e) {
      console.warn('fitView error:', e)
    }
  }, 150)
}

// 获取节点默认数据
function getDefaultNodeData(type) {
  const defaults = {
    llm: { label: '大模型', modelId: null, modelName: '', promptTemplate: '{{input}}', temperature: 0.7 },
    condition: { label: '条件判断', branches: [] },
    retrieval: { label: '知识检索', knowledgeId: null, knowledgeName: '', topK: 5 },
    tool: { label: '工具调用', toolId: null, toolName: '' }
  }
  return defaults[type] || { label: getNodeTitle(type) }
}

// 连接节点
function onConnect(params) {
  const newEdge = {
    id: `edge_${params.source}_${params.target}`,
    source: params.source,
    target: params.target
  }
  edges.value = [...edges.value, newEdge]
  triggerRef(edges)
}

// 点击节点
function onNodeClick(event) {
  selectedNode.value = event.node
}

// 点击空白区域，收回节点详情
function onPaneClick() {
  selectedNode.value = null
}

// 同步节点数据
function syncNodes() {
  triggerRef(nodes)
}

// 模型选择变化
function onModelChange(value) {
  const provider = providers.value.find(p => p.id === value)
  selectedNode.value.data.modelName = provider?.name || ''
  syncNodes()
}

// 知识库选择变化
function onKnowledgeChange(value) {
  const knowledge = knowledgeList.value.find(k => k.id === value)
  selectedNode.value.data.knowledgeName = knowledge?.name || ''
  syncNodes()
}

// 工具选择变化
function onToolChange(value) {
  const tool = tools.value.find(t => t.id === value)
  selectedNode.value.data.toolName = tool?.displayName || tool?.name || ''
  syncNodes()
}

// 添加分支
function addBranch() {
  if (!selectedNode.value.data.branches) {
    selectedNode.value.data.branches = []
  }
  selectedNode.value.data.branches.push({ condition: '', targetNodeId: '' })
  syncNodes()
}

// 删除分支
function removeBranch(index) {
  selectedNode.value.data.branches.splice(index, 1)
  syncNodes()
}

// 删除选中节点
function deleteSelectedNode() {
  // 记录历史用于撤回
  recordHistory()

  nodes.value = nodes.value.filter(n => n.id !== selectedNode.value.id)
  edges.value = edges.value.filter(e => e.source !== selectedNode.value.id && e.target !== selectedNode.value.id)
  triggerRef(nodes)
  triggerRef(edges)
  selectedNode.value = null
}

// 验证工作流
function validateWorkflow() {
  const errors = []

  // 1. 检查 START 节点
  const startNodes = nodes.value.filter(n => n.type === 'start')
  if (startNodes.length === 0) {
    errors.push({ nodeId: null, field: 'start', message: '缺少开始节点' })
  } else if (startNodes.length > 1) {
    errors.push({ nodeId: null, field: 'start', message: '只能有一个开始节点' })
  }

  // 2. 检查 END 节点
  const endNodes = nodes.value.filter(n => n.type === 'end')
  if (endNodes.length === 0) {
    errors.push({ nodeId: null, field: 'end', message: '缺少结束节点' })
  }

  // 3. 检查节点连接
  const connectedIds = new Set()
  edges.value.forEach(e => {
    connectedIds.add(e.source)
    connectedIds.add(e.target)
  })
  nodes.value.forEach(n => {
    if (n.type !== 'start' && !connectedIds.has(n.id)) {
      errors.push({ nodeId: n.id, field: 'connection', message: '节点未连接到工作流' })
    }
  })

  // 4. 检查节点配置
  nodes.value.forEach(n => {
    if (n.type === 'llm') {
      if (!n.data.modelId) errors.push({ nodeId: n.id, field: 'modelId', message: '请选择模型' })
      if (!n.data.promptTemplate) errors.push({ nodeId: n.id, field: 'promptTemplate', message: '请填写提示词' })
    }
    if (n.type === 'retrieval') {
      if (!n.data.knowledgeId) errors.push({ nodeId: n.id, field: 'knowledgeId', message: '请选择知识库' })
    }
    if (n.type === 'tool') {
      if (!n.data.toolId) errors.push({ nodeId: n.id, field: 'toolId', message: '请选择工具' })
    }
  })

  validationErrors.value = errors

  if (errors.length === 0) {
    message.success('工作流配置验证通过')
  } else {
    notification.warning({
      message: '工作流配置不完整',
      description: `发现 ${errors.length} 个配置错误，请完善后保存`
    })
  }

  return errors
}

// 保存工作流
async function saveWorkflow() {
  // 先验证
  const errors = validateWorkflow()
  if (errors.length > 0) {
    return
  }

  saving.value = true
  try {
    const workflowData = {
      nodes: nodes.value,
      edges: edges.value
    }

    // 构建 config
    const config = agent.value.config ? JSON.parse(agent.value.config) : {}
    config.workflow = workflowData

    await updateAgent({
      ...agent.value,
      agentType: agent.value.agentType?.code || agent.value.agentType,
      config: JSON.stringify(config)
    })

    message.success('工作流保存成功')
  } catch (e) {
    notification.error({ message: '保存失败', description: e.message })
  } finally {
    saving.value = false
  }
}

// 返回
function goBack() {
  router.push(`/agents/${agentId}`)
}
</script>

<style scoped>
.workflow-edit-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f5f5f5;
}

.workflow-toolbar {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 24px;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
  flex-shrink: 0;
}

.btn-back {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px 16px;
  background: transparent;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
  color: #374151;
}

.btn-back:hover {
  background: #f9fafb;
}

.workflow-title {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
  margin: 0;
}

.toolbar-status {
  flex: 1;
  text-align: center;
}

.status-valid { color: #22c55e; font-size: 13px; }
.status-error { color: #ef4444; font-size: 13px; }
.status-error.clickable {
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 4px;
  transition: background 0.2s;
}
.status-error.clickable:hover {
  background: #fef2f2;
}
.status-empty { color: #9ca3af; font-size: 13px; }

/* 错误下拉面板 */
.error-dropdown {
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  min-width: 300px;
  max-width: 400px;
}

.error-header {
  padding: 12px 16px;
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
  border-bottom: 1px solid #e5e7eb;
}

.error-list {
  padding: 8px;
  max-height: 300px;
  overflow-y: auto;
}

.error-item {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 8px 12px;
  background: #fef2f2;
  border-radius: 6px;
  margin-bottom: 6px;
  font-size: 13px;
}

.error-node {
  color: #7c3aed;
  font-weight: 600;
}

.error-field {
  color: #6b7280;
  background: #e5e7eb;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 12px;
}

.error-msg {
  color: #dc2626;
  flex: 1;
}

.toolbar-actions {
  display: flex;
  gap: 8px;
}

.workflow-content {
  flex: 1;
  display: flex;
  overflow: hidden;
}

/* 左侧节点面板 */
.node-panel {
  width: 240px;
  background: #fff;
  border-right: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;
  transition: width 0.2s ease;
}

.node-panel.collapsed {
  width: 40px;
}

.node-panel .panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #e5e7eb;
}

.node-panel.collapsed .panel-header {
  padding: 12px 8px;
  justify-content: center;
}

.btn-collapse {
  background: transparent;
  border: none;
  cursor: pointer;
  padding: 4px;
  color: #6b7280;
}

.node-panel .panel-body {
  flex: 1;
  padding: 12px;
  overflow-y: auto;
}

.node-group {
  margin-top: 12px;
}

.group-title {
  font-size: 12px;
  font-weight: 600;
  color: #6b7280;
  padding: 4px 0;
}

/* 中间画布 */
.canvas-area {
  flex: 1;
  position: relative;
  background: #fff;
}

.canvas-empty {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  color: #9ca3af;
  font-size: 14px;
}

/* 右侧配置面板 */
.config-panel {
  width: 380px;
  background: #fff;
  border-left: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;
}

.config-panel .panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #e5e7eb;
}

.node-type-badge {
  display: flex;
  align-items: center;
  gap: 8px;
}

.type-icon {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.type-name {
  font-weight: 600;
  color: #1f2937;
}

.btn-close {
  background: transparent;
  border: none;
  cursor: pointer;
  padding: 4px;
  color: #6b7280;
}

.config-panel .panel-body {
  flex: 1;
  padding: 16px;
  overflow-y: auto;
}

.node-errors {
  margin-bottom: 12px;
}

.error-item {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px 12px;
  background: #fef2f2;
  border-radius: 4px;
  color: #dc2626;
  font-size: 12px;
  margin-bottom: 4px;
}

.config-panel .panel-footer {
  padding: 12px 16px;
  border-top: 1px solid #e5e7eb;
}

/* 分支配置 */
.branches-config {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.branch-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.branch-item {
  background: #f9fafb;
  border-radius: 6px;
  padding: 8px;
}

.branch-row {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 6px;
}

.branch-row:last-child {
  margin-bottom: 0;
}

.param-value {
  font-size: 12px;
  color: #6b7280;
  min-width: 40px;
}
</style>

<style>
/* 全局 VueFlow 样式 */
@import '@vue-flow/core/dist/style.css';
@import '@vue-flow/core/dist/theme-default.css';
@import '@vue-flow/controls/dist/style.css';
@import '@vue-flow/minimap/dist/style.css';
</style>