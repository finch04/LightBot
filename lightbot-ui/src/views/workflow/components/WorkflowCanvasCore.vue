<template>
  <VueFlow
    :id="flowId"
    v-if="nodes.length > 0"
    :nodes="nodes"
    v-model:edges="edgesModel"
    :edge-types="resolvedEdgeTypes"
    connection-mode="strict"
    :connection-radius="28"
    :nodes-draggable="nodesDraggable"
    :edges-selectable="edgesSelectable"
    :edges-updatable="edgesUpdatable"
    :nodes-connectable="nodesConnectable"
    :elements-selectable="elementsSelectable"
    :selection-key-code="readonly ? null : 'Shift'"
    :multi-selection-key-code="readonly ? null : 'Control'"
    :default-edge-options="defaultEdgeOptions"
    :is-valid-connection="isValidConnection"
    :delete-key-code="null"
    :fit-view-on-init="fitViewOnInit"
    :min-zoom="0.1"
    :max-zoom="4"
    :pan-on-drag="panOnDrag"
    :zoom-on-scroll="zoomOnScroll"
    :class="{ 'wf-canvas-readonly': readonly }"
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
    <Controls v-if="showControls" position="bottom-right" show-zoom show-fit-view />
    <MiniMap
      v-if="showMinimap"
      position="bottom-left"
      class="workflow-minimap"
      :offset-scale="4"
      pannable
      zoomable
      :node-color="getNodeColor"
      :node-stroke-width="3"
    />

    <template #node-start="slotProps"><StartNode v-bind="slotProps" /></template>
    <template #node-end="slotProps"><EndNode v-bind="slotProps" /></template>
    <template #node-llm="slotProps"><LlmNode v-bind="slotProps" /></template>
    <template #node-condition="slotProps"><ConditionNode v-bind="slotProps" /></template>
    <template #node-retrieval="slotProps"><RetrievalNode v-bind="slotProps" /></template>
    <template #node-tool="slotProps"><ToolNode v-bind="slotProps" /></template>
    <template #node-classifier="slotProps"><ClassifierNode v-bind="slotProps" /></template>
    <template #node-api="slotProps"><GenericWorkflowNode v-bind="slotProps" node-type="api" summary-key="url" /></template>
    <template #node-loop="slotProps"><LoopNode v-bind="slotProps" /></template>
    <template #node-loop_start="slotProps"><GroupBuiltinNode v-bind="slotProps" node-type="loop_start" /></template>
    <template #node-loop_end="slotProps"><GroupBuiltinNode v-bind="slotProps" node-type="loop_end" /></template>
    <template #node-variable="slotProps"><GenericWorkflowNode v-bind="slotProps" node-type="variable" summary-key="variableName" /></template>
    <template #node-batch="slotProps"><BatchNode v-bind="slotProps" /></template>
    <template #node-batch_start="slotProps"><GroupBuiltinNode v-bind="slotProps" node-type="batch_start" /></template>
    <template #node-batch_end="slotProps"><GroupBuiltinNode v-bind="slotProps" node-type="batch_end" /></template>
    <template #node-script="slotProps"><GenericWorkflowNode v-bind="slotProps" node-type="script" /></template>
    <template #node-mcp="slotProps"><GenericWorkflowNode v-bind="slotProps" node-type="mcp" summary-key="mcpServerName" /></template>
    <template #node-input="slotProps"><GenericWorkflowNode v-bind="slotProps" node-type="input" /></template>
    <template #node-confirm="slotProps"><GenericWorkflowNode v-bind="slotProps" node-type="confirm" summary-key="message" /></template>
    <template #node-output="slotProps"><GenericWorkflowNode v-bind="slotProps" node-type="output" summary-key="output" /></template>
    <template #node-variable_handle="slotProps"><GenericWorkflowNode v-bind="slotProps" node-type="variable_handle" /></template>
    <template #node-parameter_extractor="slotProps"><GenericWorkflowNode v-bind="slotProps" node-type="parameter_extractor" /></template>
    <template #node-app_component="slotProps"><GenericWorkflowNode v-bind="slotProps" node-type="app_component" summary-key="componentName" /></template>

    <template v-if="showConnectionLine" #connection-line="lineProps">
      <WorkflowConnectionLine v-bind="lineProps" />
    </template>

    <slot name="overlay" />
  </VueFlow>
</template>

<script setup>
import { markRaw, computed } from 'vue'
import { VueFlow } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import { MiniMap } from '@vue-flow/minimap'
import StartNode from '../nodes/StartNode.vue'
import EndNode from '../nodes/EndNode.vue'
import LlmNode from '../nodes/LlmNode.vue'
import ConditionNode from '../nodes/ConditionNode.vue'
import RetrievalNode from '../nodes/RetrievalNode.vue'
import ToolNode from '../nodes/ToolNode.vue'
import ClassifierNode from '../nodes/ClassifierNode.vue'
import GenericWorkflowNode from '../nodes/GenericWorkflowNode.vue'
import LoopNode from '../nodes/LoopNode.vue'
import BatchNode from '../nodes/BatchNode.vue'
import GroupBuiltinNode from '../nodes/GroupBuiltinNode.vue'
import WorkflowConnectionLine from '../components/WorkflowConnectionLine.vue'
import WorkflowBezierEdge from '../edges/WorkflowBezierEdge.vue'

const edgesModel = defineModel('edges', { type: Array, default: () => [] })

const props = defineProps({
  flowId: { type: String, required: true },
  nodes: { type: Array, default: () => [] },
  edgeTypes: {
    type: Object,
    default: null,
  },
  readonly: { type: Boolean, default: false },
  nodesDraggable: { type: Boolean, default: true },
  edgesSelectable: { type: Boolean, default: true },
  edgesUpdatable: { type: Boolean, default: true },
  nodesConnectable: { type: Boolean, default: true },
  elementsSelectable: { type: Boolean, default: true },
  defaultEdgeOptions: {
    type: Object,
    default: () => ({
      type: 'workflow-bezier',
      selectable: true,
      updatable: true,
      style: { strokeWidth: 2, stroke: '#94a3b8' },
    }),
  },
  isValidConnection: {
    type: Function,
    default: () => true,
  },
  getNodeColor: {
    type: Function,
    default: () => '#6366f1',
  },
  showControls: { type: Boolean, default: true },
  showMinimap: { type: Boolean, default: true },
  showConnectionLine: { type: Boolean, default: false },
  fitViewOnInit: { type: Boolean, default: true },
  panOnDrag: { type: Boolean, default: true },
  zoomOnScroll: { type: Boolean, default: true },
})

defineEmits([
  'edges-change', 'connect', 'edge-update', 'nodes-change',
  'node-drag-start', 'node-drag', 'node-drag-stop', 'drop',
  'node-click', 'edge-click', 'edge-mouse-enter', 'edge-mouse-move', 'edge-mouse-leave', 'pane-click',
])

const edgeTypes = markRaw({
  'workflow-bezier': markRaw(WorkflowBezierEdge),
  default: markRaw(WorkflowBezierEdge),
})

const resolvedEdgeTypes = computed(() => props.edgeTypes || edgeTypes)
</script>

<style scoped>
:deep(.wf-canvas-readonly .vue-flow__handle) {
  opacity: 0;
  pointer-events: none;
}
</style>

<!-- 与 WorkflowEditCanvas 一致的节点深色模式 -->
<style>
[data-theme="dark"] .generic-node { border-color: #3f3f46; }
[data-theme="dark"] .generic-node .node-header { border-color: #2e2e33; }
[data-theme="dark"] .llm-node .node-header { background: #2e1065; }
[data-theme="dark"] .tool-node .node-header { background: #052e16; }
[data-theme="dark"] .classifier-node .node-header { background: #422006; border-color: #3f3f46; }
[data-theme="dark"] .batch-shell { background: rgba(5, 46, 22, 0.25); border-color: #0d9488; }
[data-theme="dark"] .batch-shell .group-header { background: rgba(5, 46, 22, 0.65); border-color: #115e59; }
[data-theme="dark"] .batch-shell .group-title { color: #5eead4; }
[data-theme="dark"] .batch-shell .group-tag { background: #115e59; color: #99f6e4; }
[data-theme="dark"] .batch-shell .group-icon { background: rgba(20, 184, 166, 0.25); color: #5eead4; }
[data-theme="dark"] .batch-shell .group-inner { border-color: rgba(20, 184, 166, 0.3); background: rgba(0, 0, 0, 0.15); }
[data-theme="dark"] .loop-shell { background: rgba(124, 45, 18, 0.2); border-color: #ea580c; }
[data-theme="dark"] .loop-shell .group-header { background: rgba(124, 45, 18, 0.5); border-color: #9a3412; }
[data-theme="dark"] .loop-shell .group-title { color: #fdba74; }
[data-theme="dark"] .loop-shell .group-tag { background: #7c2d12; color: #fed7aa; }
[data-theme="dark"] .loop-shell .group-icon { background: rgba(249, 115, 22, 0.25); color: #fb923c; }
[data-theme="dark"] .loop-shell .group-inner { border-color: rgba(249, 115, 22, 0.3); background: rgba(0, 0, 0, 0.15); }
[data-theme="dark"] .group-builtin-node { box-shadow: 0 1px 4px rgba(0, 0, 0, 0.3); }
[data-theme="dark"] .group-builtin-node.loop-end { background: linear-gradient(135deg, #4c1d95, #5b21b6); color: #e9d5ff; border-color: #6d28d9; }
[data-theme="dark"] .group-builtin-node.batch-end { background: linear-gradient(135deg, #134e4a, #115e59); color: #99f6e4; border-color: #0f766e; }
</style>
