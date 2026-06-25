<template>
  <div class="workflow-canvas-container">
    <!-- 左侧节点面板 -->
    <WorkflowNodePalette @drag-node="onDragNode" />

    <!-- 中间画布区域 -->
    <div class="canvas-area">
      <VueFlow
        :nodes="nodes"
        :edges="edges"
        @connect="handleConnect"
        @node-click="handleNodeClick"
        @dragover="onDragOver"
        @drop="onDrop"
        :default-viewport="{ zoom: 1, x: 0, y: 0 }"
        :min-zoom="0.2"
        :max-zoom="4"
        fit-view-on-init
      >
        <Background pattern-color="#aaa" :gap="20" />
        <Controls />
        <MiniMap />

        <!-- 自定义节点模板 -->
        <template #node-start="nodeProps">
          <StartNode :data="nodeProps.data" :selected="nodeProps.selected" />
        </template>
        <template #node-end="nodeProps">
          <EndNode :data="nodeProps.data" :selected="nodeProps.selected" />
        </template>
        <template #node-llm="nodeProps">
          <LlmNode :data="nodeProps.data" :selected="nodeProps.selected" @edit="onEditNode(nodeProps)" />
        </template>
        <template #node-condition="nodeProps">
          <ConditionNode :data="nodeProps.data" :selected="nodeProps.selected" @edit="onEditNode(nodeProps)" />
        </template>
        <template #node-retrieval="nodeProps">
          <RetrievalNode :data="nodeProps.data" :selected="nodeProps.selected" @edit="onEditNode(nodeProps)" />
        </template>
        <template #node-tool="nodeProps">
          <ToolNode :data="nodeProps.data" :selected="nodeProps.selected" @edit="onEditNode(nodeProps)" />
        </template>
      </VueFlow>

      <!-- 操作按钮 -->
      <div class="canvas-actions">
        <a-button type="primary" @click="saveWorkflow">保存工作流</a-button>
        <a-button @click="clearWorkflow">清空</a-button>
      </div>
    </div>

    <!-- 右侧配置面板 -->
    <NodeConfigPanel
      v-if="selectedNode"
      :node="selectedNode"
      @close="selectedNode = null"
      @update="onUpdateNodeData"
    />
  </div>
</template>

<script setup>
import { ref, onMounted, shallowRef, triggerRef } from 'vue'
import { VueFlow, useVueFlow } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import { MiniMap } from '@vue-flow/minimap'
import { useWorkflowStore } from '../../stores/workflow'
import WorkflowNodePalette from './WorkflowNodePalette.vue'
import StartNode from './nodes/StartNode.vue'
import EndNode from './nodes/EndNode.vue'
import LlmNode from './nodes/LlmNode.vue'
import ConditionNode from './nodes/ConditionNode.vue'
import RetrievalNode from './nodes/RetrievalNode.vue'
import ToolNode from './nodes/ToolNode.vue'
import NodeConfigPanel from './NodeConfigPanel.vue'
import { message } from 'ant-design-vue'

const props = defineProps({
  agentId: {
    type: String,
    default: null
  },
  initialWorkflow: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['save'])

const workflowStore = useWorkflowStore()
const { onConnect, onNodeClick, fitView } = useVueFlow()

// 使用 shallowRef避免深度响应式导致的递归更新
const nodes = shallowRef([])
const edges = shallowRef([])
const selectedNode = ref(null)

// 初始化加载工作流
onMounted(() => {
  if (props.initialWorkflow) {
    workflowStore.loadWorkflow(props.initialWorkflow)
  } else {
    // 默认添加 start 和 end节点
    workflowStore.addNode('start', { x: 100, y: 100 })
    workflowStore.addNode('end', { x: 500, y: 100 })
  }
  // 同步到本地 shallowRef
  syncFromStore()
})

// 从 store同步到本地（仅在需要时手动调用）
function syncFromStore() {
  nodes.value = [...workflowStore.nodes]
  edges.value = [...workflowStore.edges]
  triggerRef(nodes)
  triggerRef(edges)
}

// 拖拽节点到画布
function onDragNode(event, nodeType) {
  event.dataTransfer.setData('nodeType', nodeType)
}

function onDragOver(event) {
  event.preventDefault()
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'move'
  }
}

function onDrop(event) {
  const nodeType = event.dataTransfer?.getData('nodeType')
  if (!nodeType) return

  const { left, top } = event.target.getBoundingClientRect()
  const position = {
    x: event.clientX - left,
    y: event.clientY - top
  }

  workflowStore.addNode(nodeType, position)
  syncFromStore()
}

// 处理连接事件
function handleConnect(params) {
  workflowStore.connectEdge(params.source, params.target)
  syncFromStore()
}

// 点击节点
function handleNodeClick(event) {
  selectedNode.value = event.node
}

// 编辑节点
function onEditNode(nodeProps) {
  selectedNode.value = nodeProps
}

// 更新节点数据
function onUpdateNodeData(nodeId, data) {
  workflowStore.updateNodeData(nodeId, data)
  syncFromStore()
}

// 保存工作流
function saveWorkflow() {
  const workflowData = workflowStore.saveWorkflow()
  emit('save', workflowData)
  message.success('工作流已保存')
}

// 清空工作流
function clearWorkflow() {
  workflowStore.clearWorkflow()
  workflowStore.addNode('start', { x: 100, y: 100 })
  workflowStore.addNode('end', { x: 500, y: 100 })
  syncFromStore()
}
</script>

<style scoped>
.workflow-canvas-container {
  display: flex;
  height: 100%;
  width: 100%;
}

.canvas-area {
  flex: 1;
  position: relative;
  background: var(--color-canvas-soft-2);
}

.canvas-actions {
  position: absolute;
  bottom: 20px;
  right: 20px;
  display: flex;
  gap: 10px;
  z-index: 10;
}
</style>

<style>
/* 全局 VueFlow 样式 */
@import '@vue-flow/core/dist/style.css';
@import '@vue-flow/core/dist/theme-default.css';
@import '@vue-flow/controls/dist/style.css';
@import '@vue-flow/minimap/dist/style.css';
</style>