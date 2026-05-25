<template>
  <Handle
    v-bind="$attrs"
    :type="type"
    :position="position"
    :id="handleId"
    :connectable="connectable"
    class="wf-handle"
    :class="handleClass"
    :title="handleTitle"
  />
</template>

<script setup>
import { computed } from 'vue'
import { Handle } from '@vue-flow/core'
import { HANDLE_IN, HANDLE_OUT } from '../workflowConnection'

const props = defineProps({
  type: { type: String, required: true },
  position: { type: String, required: true },
  id: { type: String, default: undefined },
  connectable: { type: [Boolean, Number, Function], default: true },
})

const handleId = computed(() => {
  if (props.id) return props.id
  return props.type === 'target' ? HANDLE_IN : HANDLE_OUT
})

const handleClass = computed(() => ({
  'wf-handle-in': props.type === 'target',
  'wf-handle-out': props.type === 'source',
}))

const handleTitle = computed(() =>
  props.type === 'target' ? '入：连接上游节点' : '出：连接下游节点'
)
</script>

<style scoped>
:deep(.wf-handle) {
  width: 14px !important;
  height: 14px !important;
  border: 2px solid #6366f1 !important;
  background: #fff !important;
  border-radius: 50% !important;
  transition: width 0.15s, height 0.15s, background 0.15s, border-color 0.15s;
}
:deep(.wf-handle-in) {
  border-color: #0ea5e9 !important;
}
:deep(.wf-handle-out) {
  border-color: #6366f1 !important;
}
:deep(.wf-handle:hover) {
  width: 20px !important;
  height: 20px !important;
  background: #6366f1 !important;
}
:deep(.wf-handle-in:hover) {
  background: #0ea5e9 !important;
}
:deep(.vue-flow__handle-connecting.wf-handle-in),
:deep(.vue-flow__handle-valid.wf-handle-in) {
  background: #0ea5e9 !important;
}
</style>
