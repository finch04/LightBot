<template>
  <div class="canvas-area" ref="canvasAreaRef" @dragover.prevent>
    <div v-if="isVersionPreview" class="version-preview-banner">
      正在预览历史版本 v{{ selectedVersion }}（只读），点击右上角「回到当前版本」返回草稿
    </div>

    <div
      v-show="isNodeDragging"
      ref="trashRef"
      class="workflow-trash"
      :class="{ 'is-over': dragOverTrash, 'is-disabled': !canDeleteDraggedNode }"
    >
      <DeleteOutlined class="trash-icon" />
      <span class="trash-label">{{ canDeleteDraggedNode ? '拖到此处删除' : '开始/结束节点不可删除' }}</span>
    </div>

    <VueFlow
      :id="flowId"
      v-if="nodes.length > 0"
      :nodes="nodes"
      v-model:edges="edgesModel"
      :edge-types="edgeTypes"
      connection-mode="strict"
      :connection-radius="28"
      :nodes-draggable="!isVersionPreview"
      :edges-selectable="!isVersionPreview"
      :edges-updatable="!isVersionPreview"
      :nodes-connectable="!isVersionPreview"
      :elements-selectable="!isVersionPreview"
      selection-key-code="Shift"
      multi-selection-key-code="Control"
      :default-edge-options="defaultEdgeOptions"
      :is-valid-connection="isValidConnection"
      :delete-key-code="null"
      fit-view-on-init
      :min-zoom="0.1"
      :max-zoom="4"
      @edges-change="$emit('edges-change', $event)"
      @connect="$emit('connect', $event)"
      @edge-update="$emit('edge-update', $event)"
      @nodes-change="$emit('nodes-change', $event)"
      @node-drag-start="$emit('node-drag-start', $event)"
      @node-drag="$emit('node-drag', $event)"
      @node-drag-stop="$emit('node-drag-stop', $event)"
      @dragover.prevent
      @drop="$emit('drop', $event)"
      @node-click="$emit('node-click', $event)"
      @edge-click="$emit('edge-click', $event)"
      @edge-mouse-enter="$emit('edge-mouse-enter', $event)"
      @edge-mouse-move="$emit('edge-mouse-move', $event)"
      @edge-mouse-leave="$emit('edge-mouse-leave', $event)"
      @pane-click="$emit('pane-click', $event)"
    >
      <Background :gap="[20, 20]" pattern-color="#e5e7eb" />
      <Controls position="bottom-right" show-zoom show-fit-view />
      <MiniMap
        position="bottom-left"
        class="workflow-minimap"
        :offset-scale="4"
        pannable
        zoomable
        :node-color="getNodeColor"
        :node-stroke-width="3"
      />

      <template #node-start="props"><StartNode v-bind="props" /></template>
      <template #node-end="props"><EndNode v-bind="props" /></template>
      <template #node-llm="props"><LlmNode v-bind="props" /></template>
      <template #node-condition="props"><ConditionNode v-bind="props" /></template>
      <template #node-retrieval="props"><RetrievalNode v-bind="props" /></template>
      <template #node-tool="props"><ToolNode v-bind="props" /></template>
      <template #node-classifier="props"><ClassifierNode v-bind="props" /></template>
      <template #node-api="props"><GenericWorkflowNode v-bind="props" node-type="api" summary-key="url" /></template>
      <template #node-loop="props"><LoopNode v-bind="props" /></template>
      <template #node-loop_start="props"><GroupBuiltinNode v-bind="props" node-type="loop_start" /></template>
      <template #node-loop_end="props"><GroupBuiltinNode v-bind="props" node-type="loop_end" /></template>
      <template #node-variable="props"><GenericWorkflowNode v-bind="props" node-type="variable" summary-key="variableName" /></template>
      <template #node-batch="props"><BatchNode v-bind="props" /></template>
      <template #node-batch_start="props"><GroupBuiltinNode v-bind="props" node-type="batch_start" /></template>
      <template #node-batch_end="props"><GroupBuiltinNode v-bind="props" node-type="batch_end" /></template>
      <template #node-script="props"><GenericWorkflowNode v-bind="props" node-type="script" /></template>
      <template #node-mcp="props"><GenericWorkflowNode v-bind="props" node-type="mcp" summary-key="mcpServerName" /></template>
      <template #node-input="props"><GenericWorkflowNode v-bind="props" node-type="input" /></template>
      <template #node-output="props"><GenericWorkflowNode v-bind="props" node-type="output" summary-key="output" /></template>
      <template #node-variable_handle="props"><GenericWorkflowNode v-bind="props" node-type="variable_handle" /></template>
      <template #node-parameter_extractor="props"><GenericWorkflowNode v-bind="props" node-type="parameter_extractor" /></template>
      <template #node-app_component="props"><GenericWorkflowNode v-bind="props" node-type="app_component" summary-key="componentName" /></template>

      <template #connection-line="lineProps">
        <WorkflowConnectionLine v-bind="lineProps" />
      </template>

      <EdgeLabelRenderer>
        <div
          v-if="edgeInsertAnchorEdge && !isVersionPreview && edgeInsertLabelStyle"
          :style="edgeInsertLabelStyle"
          class="edge-insert-label-layer"
          @mouseenter="$emit('edge-insert-pointer-enter')"
          @mouseleave="$emit('edge-insert-pointer-leave')"
        >
          <WorkflowEdgeInsert
            :visible="true"
            @select="type => $emit('insert-node-on-edge', type)"
            @menu-open="$emit('edge-insert-menu-open')"
            @menu-close="$emit('edge-insert-menu-close')"
          />
        </div>
      </EdgeLabelRenderer>
    </VueFlow>

    <div v-if="nodes.length === 0" class="canvas-empty">
      <p>从左侧拖拽节点到画布开始构建工作流</p>
    </div>

    <div v-if="versionVisible" class="canvas-overlay-top">
      <div class="version-panel-float" :style="versionPanelStyle">
        <div class="version-panel-header">
          <span
            class="version-panel-drag-handle"
            title="按住拖动面板"
            @mousedown.prevent="$emit('version-panel-drag-start', $event)"
          >
            <HolderOutlined />
          </span>
          <span class="version-panel-title">历史版本</span>
          <button type="button" class="version-panel-close" @click="$emit('close-version-panel')">
            <CloseOutlined />
          </button>
        </div>
        <div class="version-panel-body">
          <div
            class="version-item draft"
            :class="{ active: selectedVersion === 'draft' }"
            @click="$emit('select-version', 'draft')"
          >
            <div class="version-item-title">当前草稿</div>
            <div class="version-item-desc">继续编辑未发布的修改</div>
          </div>
          <a-divider style="margin: 12px 0" />
          <a-spin :spinning="versionLoading">
            <a-timeline>
              <a-timeline-item
                v-for="(item, idx) in versionList"
                :key="item.version"
                :color="selectedVersion === item.version ? '#6366f1' : '#d1d5db'"
              >
                <div
                  class="version-item"
                  :class="{ active: selectedVersion === item.version }"
                  @click="$emit('select-version', item.version)"
                >
                  <div class="version-item-header">
                    <span class="version-item-title">{{ idx === 0 ? '线上版本' : `v${item.version}` }}</span>
                    <a-tag v-if="idx === 0" color="green" size="small">最新</a-tag>
                  </div>
                  <div v-if="item.description" class="version-item-note">{{ item.description }}</div>
                  <div class="version-item-desc">{{ formatVersionDesc(item) }}</div>
                </div>
              </a-timeline-item>
            </a-timeline>
            <a-empty v-if="!versionLoading && versionList.length === 0" description="暂无发布版本" />
          </a-spin>
        </div>
        <div v-if="selectedVersion !== 'draft'" class="version-panel-footer">
          <a-button type="primary" block @click="$emit('overwrite-draft')">覆盖当前草稿</a-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { VueFlow, EdgeLabelRenderer } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import { MiniMap } from '@vue-flow/minimap'
import { DeleteOutlined, CloseOutlined, HolderOutlined } from '@ant-design/icons-vue'
import StartNode from '../../nodes/StartNode.vue'
import EndNode from '../../nodes/EndNode.vue'
import LlmNode from '../../nodes/LlmNode.vue'
import ConditionNode from '../../nodes/ConditionNode.vue'
import RetrievalNode from '../../nodes/RetrievalNode.vue'
import ToolNode from '../../nodes/ToolNode.vue'
import ClassifierNode from '../../nodes/ClassifierNode.vue'
import GenericWorkflowNode from '../../nodes/GenericWorkflowNode.vue'
import LoopNode from '../../nodes/LoopNode.vue'
import BatchNode from '../../nodes/BatchNode.vue'
import GroupBuiltinNode from '../../nodes/GroupBuiltinNode.vue'
import WorkflowConnectionLine from '../WorkflowConnectionLine.vue'
import WorkflowEdgeInsert from '../WorkflowEdgeInsert.vue'

const edgesModel = defineModel('edges', { type: Array, default: () => [] })

defineProps({
  flowId: { type: String, default: 'lightbot-workflow-edit' },
  nodes: { type: Array, default: () => [] },
  edgeTypes: { type: Object, required: true },
  defaultEdgeOptions: { type: Object, required: true },
  isValidConnection: { type: Function, required: true },
  isVersionPreview: Boolean,
  isNodeDragging: Boolean,
  dragOverTrash: Boolean,
  canDeleteDraggedNode: Boolean,
  getNodeColor: { type: Function, required: true },
  edgeInsertAnchorEdge: { type: Object, default: null },
  edgeInsertLabelStyle: { type: Object, default: null },
  versionVisible: Boolean,
  versionPanelStyle: { type: Object, default: () => ({}) },
  versionList: { type: Array, default: () => [] },
  versionLoading: Boolean,
  selectedVersion: [String, Number],
  formatVersionDesc: { type: Function, required: true },
})

defineEmits([
  'edges-change', 'connect', 'edge-update', 'nodes-change',
  'node-drag-start', 'node-drag', 'node-drag-stop', 'drop',
  'node-click', 'edge-click', 'edge-mouse-enter', 'edge-mouse-move', 'edge-mouse-leave', 'pane-click',
  'edge-insert-pointer-enter', 'edge-insert-pointer-leave',
  'insert-node-on-edge', 'edge-insert-menu-open', 'edge-insert-menu-close',
  'version-panel-drag-start', 'close-version-panel', 'select-version', 'overwrite-draft',
])

const canvasAreaRef = ref(null)
const trashRef = ref(null)

defineExpose({
  canvasAreaEl: canvasAreaRef,
  trashEl: trashRef,
})
</script>

<style scoped>
.canvas-area {
  flex: 1;
  position: relative;
  background: #fff;
  isolation: isolate;
}
.canvas-area :deep(.vue-flow__controls),
.canvas-area :deep(.vue-flow__minimap) {
  z-index: 5 !important;
}
.canvas-area :deep(.vue-flow__handle) {
  width: 16px !important;
  height: 16px !important;
  border: 2px solid #6366f1 !important;
  background: #fff !important;
  border-radius: 50% !important;
  transition: width 0.15s, height 0.15s, background 0.15s;
}
.canvas-area :deep(.vue-flow__handle:hover) {
  width: 22px !important;
  height: 22px !important;
  background: #6366f1 !important;
}
.workflow-trash {
  position: absolute;
  left: 50%;
  bottom: 24px;
  transform: translateX(-50%);
  z-index: 30;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-width: 120px;
  padding: 14px 24px;
  border-radius: 12px;
  border: 2px dashed #d4d4d8;
  background: rgba(255, 255, 255, 0.95);
  color: #71717a;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  transition: border-color 0.15s, background 0.15s, color 0.15s, transform 0.15s;
  pointer-events: none;
}
.workflow-trash .trash-icon { font-size: 28px; }
.workflow-trash .trash-label { font-size: 12px; white-space: nowrap; }
.workflow-trash.is-over:not(.is-disabled) {
  border-color: #ef4444;
  border-style: solid;
  background: #fef2f2;
  color: #dc2626;
  transform: translateX(-50%) scale(1.05);
}
.workflow-trash.is-disabled { opacity: 0.65; }
.canvas-empty {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  color: #9ca3af;
  font-size: 14px;
}
.edge-insert-label-layer { pointer-events: none; }
.edge-insert-label-layer > * { pointer-events: auto; }
.canvas-overlay-top {
  position: absolute;
  inset: 0;
  z-index: 1000;
  pointer-events: none;
}
.version-panel-float {
  position: absolute;
  z-index: 1;
  display: flex;
  flex-direction: column;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.12);
  overflow: hidden;
  pointer-events: auto;
}
.version-panel-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-bottom: 1px solid #f1f5f9;
  flex-shrink: 0;
  cursor: default;
}
.version-panel-drag-handle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  color: #94a3b8;
  border-radius: 4px;
  cursor: grab;
  flex-shrink: 0;
  user-select: none;
}
.version-panel-drag-handle:hover { color: #64748b; background: #f1f5f9; }
.version-panel-drag-handle:active { cursor: grabbing; }
.version-panel-title {
  flex: 1;
  font-weight: 600;
  font-size: 14px;
  color: #1e293b;
}
.version-panel-close {
  border: none;
  background: transparent;
  cursor: pointer;
  color: #94a3b8;
  padding: 4px;
  line-height: 1;
}
.version-panel-close:hover { color: #475569; }
.version-panel-body {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}
.version-panel-footer {
  flex-shrink: 0;
  padding: 12px;
  border-top: 1px solid #f1f5f9;
}
.version-preview-banner {
  position: absolute;
  top: 12px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 999;
  padding: 8px 16px;
  background: #fffbeb;
  border: 1px solid #fcd34d;
  border-radius: 8px;
  font-size: 13px;
  color: #92400e;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
  pointer-events: none;
}
.version-item {
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s;
}
.version-item:hover { background: #f3f4f6; }
.version-item.active { background: #eef2ff; border: 1px solid #c7d2fe; }
.version-item.draft { margin-bottom: 4px; }
.version-item-header { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; }
.version-item-title { font-weight: 600; font-size: 14px; color: #1f2937; }
.version-item-note {
  font-size: 13px;
  color: #334155;
  line-height: 1.45;
  margin-bottom: 4px;
  word-break: break-word;
}
.version-item-desc { font-size: 12px; color: #9ca3af; }
</style>
