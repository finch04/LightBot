<template>
  <div class="workflow-node generic-node" :class="nodeClass" @dblclick="$emit('edit')">
    <WorkflowHandle type="target" position="left" />
    <WorkflowHandle type="source" position="right" />
    <div class="node-header" :style="{ background: meta.color + '15' }">
      <div class="node-icon" :style="{ background: meta.color + '25', color: meta.color }">
        <NodeTypeIcon :type="nodeType" />
      </div>
      <div class="node-title">{{ data.label || meta.title }}</div>
    </div>
    <div class="node-body">
      <div v-if="summaryText" class="node-config"><span class="config-value">{{ summaryText }}</span></div>
      <div v-else class="node-placeholder">点击右侧配置</div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import WorkflowHandle from '../components/WorkflowHandle.vue'
import NodeTypeIcon from '../components/NodeTypeIcon.vue'
import { getNodeMeta } from '../nodeMeta'

const props = defineProps({
  nodeType: { type: String, required: true },
  data: Object,
  selected: Boolean,
  summaryKey: { type: String, default: '' }
})

defineEmits(['edit'])

const meta = computed(() => getNodeMeta(props.nodeType))

const summaryText = computed(() => {
  if (!props.summaryKey || !props.data) return ''
  const val = props.data[props.summaryKey]
  return val != null && val !== '' ? String(val) : ''
})

const nodeClass = computed(() => ({
  selected: props.selected,
  [`debug-${props.data?.debugStatus}`]: !!props.data?.debugStatus
}))
</script>

<style scoped>
.generic-node {
  width: 200px;
  min-width: 200px;
  max-width: 200px;
  box-sizing: border-box;
  background: #fff;
  border: 2px solid #e5e7eb;
  border-radius: 12px;
  position: relative;
}
.generic-node.selected { border-color: #6366f1; }
.generic-node.debug-executing { animation: wf-executing 1.2s linear infinite; border-color: #6366f1; }
.generic-node.debug-success { border-color: #22c55e; box-shadow: 0 0 0 3px rgba(34, 197, 94, 0.2); }
.generic-node.debug-fail { border-color: #ef4444; box-shadow: 0 0 0 3px rgba(239, 68, 68, 0.2); }
.node-header { display: flex; align-items: center; gap: 8px; padding: 10px 12px; border-bottom: 1px solid #f3f4f6; border-radius: 10px 10px 0 0; }
.node-icon { width: 28px; height: 28px; border-radius: 6px; display: flex; align-items: center; justify-content: center; font-size: 16px; }
.node-title { font-size: 13px; font-weight: 600; }
.node-body { padding: 8px 12px 10px; font-size: 12px; }
.node-placeholder { color: #9ca3af; font-style: italic; }
@keyframes wf-executing {
  0% { box-shadow: 0 0 0 0 rgba(99, 102, 241, 0.35); }
  50% { box-shadow: 0 0 0 8px rgba(99, 102, 241, 0.12); }
  100% { box-shadow: 0 0 0 0 rgba(99, 102, 241, 0.35); }
}
</style>
