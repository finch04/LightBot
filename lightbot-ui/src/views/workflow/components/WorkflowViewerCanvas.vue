<template>
  <div class="workflow-viewer-canvas">
    <WorkflowCanvasCore
      :flow-id="flowId"
      :nodes="displayNodes"
      :edges="displayEdges"
      readonly
      :nodes-draggable="false"
      :edges-selectable="false"
      :edges-updatable="false"
      :nodes-connectable="false"
      :elements-selectable="true"
      :default-edge-options="defaultEdgeOptions"
      :get-node-color="getNodeColor"
      :show-minimap="showMinimap"
      :fit-view-on-init="fitViewOnInit"
      @node-click="onNodeClick"
      @pane-click="onPaneClick"
    />
    <div v-if="!displayNodes.length" class="viewer-empty">
      <slot name="empty">暂无工作流图数据</slot>
    </div>
  </div>
</template>

<script setup>
import { computed, watch, nextTick } from 'vue'
import { useVueFlow } from '@vue-flow/core'
import WorkflowCanvasCore from './WorkflowCanvasCore.vue'
import { mergeNodeStates, highlightExecutedEdges } from '../workflowViewerAdapter.js'

const props = defineProps({
  flowId: { type: String, default: 'lightbot-workflow-viewer' },
  nodes: { type: Array, default: () => [] },
  edges: { type: Array, default: () => [] },
  /** @type {Record<string, { debugStatus?: string, durationMs?: number }>} */
  nodeStates: { type: Object, default: () => ({}) },
  highlightedEdgeIds: { type: [Set, Array], default: () => new Set() },
  selectedNodeId: { type: String, default: null },
  showMinimap: { type: Boolean, default: true },
  fitViewOnInit: { type: Boolean, default: true },
})

const emit = defineEmits(['node-click', 'pane-click'])

const defaultEdgeOptions = {
  type: 'workflow-bezier',
  selectable: false,
  updatable: false,
  style: { strokeWidth: 2, stroke: '#94a3b8' },
}

const displayNodes = computed(() =>
  mergeNodeStates(props.nodes, props.nodeStates, props.selectedNodeId),
)

const highlightedSet = computed(() => {
  if (props.highlightedEdgeIds instanceof Set) return props.highlightedEdgeIds
  return new Set(props.highlightedEdgeIds || [])
})

const displayEdges = computed({
  get() {
    return highlightExecutedEdges(props.edges, highlightedSet.value)
  },
  set() {
    /* 只读，忽略写入 */
  },
})

const { fitView } = useVueFlow({ id: props.flowId })

function getNodeColor(node) {
  const status = node?.data?.debugStatus
  if (status === 'success') return '#22c55e'
  if (status === 'fail') return '#ef4444'
  if (status === 'executing') return '#6366f1'
  return '#94a3b8'
}

function onNodeClick(payload) {
  const nodeId = payload?.node?.id
  if (nodeId) emit('node-click', nodeId)
}

function onPaneClick() {
  emit('pane-click')
}

watch(
  () => [props.nodes.length, props.flowId],
  () => {
    if (!props.fitViewOnInit || !props.nodes.length) return
    nextTick(() => {
      setTimeout(() => {
        try {
          fitView({ padding: 0.2, includeHiddenNodes: true, duration: 200 })
        } catch (_) { /* VueFlow 未就绪 */ }
      }, 120)
    })
  },
  { flush: 'post' },
)

defineExpose({
  fitView: (opts) => fitView(opts ?? { padding: 0.2, includeHiddenNodes: true, duration: 300 }),
})
</script>

<style scoped>
.workflow-viewer-canvas {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 320px;
  background: var(--color-canvas);
}
.workflow-viewer-canvas :deep(.vue-flow) {
  width: 100%;
  height: 100%;
}
.viewer-empty {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-mute);
  font-size: 14px;
  pointer-events: none;
}
</style>

<style>
@import '@vue-flow/core/dist/style.css';
@import '@vue-flow/core/dist/theme-default.css';
@import '@vue-flow/controls/dist/style.css';
@import '@vue-flow/minimap/dist/style.css';

.workflow-viewer-canvas .workflow-minimap.vue-flow__minimap {
  border-radius: 8px;
  overflow: hidden;
}
</style>
