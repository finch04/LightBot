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
    v-bind="selectionKeyBindings"
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
    <Background :gap="[20, 20]" :pattern-color="patternColor" />
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
    <template #node-sub_agent="slotProps"><GenericWorkflowNode v-bind="slotProps" node-type="sub_agent" summary-key="subAgentName" /></template>

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
import { useTheme } from '../../../composables/useTheme'
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

const { isDark } = useTheme()
const patternColor = computed(() => (isDark.value ? '#3f3f46' : '#e5e7eb'))

/** 可编辑时不传 keyCode，沿用 Vue Flow 默认 Shift/Control；只读时禁用框选快捷键 */
const selectionKeyBindings = computed(() => (
  props.readonly
    ? { selectionKeyCode: null, multiSelectionKeyCode: null }
    : {}
))

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

<style>
@import '@vue-flow/core/dist/style.css';
@import '@vue-flow/core/dist/theme-default.css';
@import '@vue-flow/controls/dist/style.css';
@import '@vue-flow/minimap/dist/style.css';
@import '../workflowCanvasShell.css';
@import '../workflowNodeTheme.css';
</style>
