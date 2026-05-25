<template>
  <div class="group-builtin-node" :class="[variant, nodeClass]">
    <WorkflowHandle v-if="showTarget" type="target" position="left" />
    <div class="builtin-content">
      <span class="builtin-label">{{ data.label || defaultLabel }}</span>
    </div>
    <WorkflowHandle v-if="showSource" type="source" position="right" />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import WorkflowHandle from '../components/WorkflowHandle.vue'

const props = defineProps({
  data: Object,
  selected: Boolean,
  /** loop_start | loop_end | batch_start | batch_end */
  nodeType: { type: String, required: true },
})

const VARIANT_META = {
  loop_start: { label: '迭代开始', variant: 'loop-start' },
  loop_end: { label: '迭代结束', variant: 'loop-end' },
  batch_start: { label: '并行处理', variant: 'batch-start' },
  batch_end: { label: '并行结束', variant: 'batch-end' },
}

const meta = computed(() => VARIANT_META[props.nodeType] || { label: props.nodeType, variant: 'loop-start' })
const defaultLabel = computed(() => meta.value.label)
const variant = computed(() => meta.value.variant)
const showSource = computed(() => props.nodeType === 'loop_start' || props.nodeType === 'batch_start')
const showTarget = computed(() => props.nodeType === 'loop_end' || props.nodeType === 'batch_end')

const nodeClass = computed(() => ({
  selected: props.selected,
  [`debug-${props.data?.debugStatus}`]: !!props.data?.debugStatus,
}))
</script>

<style scoped>
.group-builtin-node {
  display: flex;
  align-items: center;
  min-width: 112px;
  height: 40px;
  padding: 0 8px;
  border-radius: 20px;
  border: 1px solid transparent;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  transition: box-shadow 0.2s ease;
}
.group-builtin-node.selected {
  box-shadow: 0 0 0 2px rgba(99, 102, 241, 0.35);
}
.builtin-content {
  flex: 1;
  text-align: center;
  font-size: 12px;
  font-weight: 600;
  user-select: none;
}
.loop-start {
  background: linear-gradient(135deg, #a78bfa, #8b5cf6);
  color: #fff;
  border-color: #7c3aed;
}
.loop-end {
  background: linear-gradient(135deg, #c4b5fd, #a78bfa);
  color: #4c1d95;
  border-color: #8b5cf6;
}
.batch-start {
  background: linear-gradient(135deg, #2dd4bf, #14b8a6);
  color: #fff;
  border-color: #0d9488;
}
.batch-end {
  background: linear-gradient(135deg, #99f6e4, #5eead4);
  color: #115e59;
  border-color: #14b8a6;
}
</style>
