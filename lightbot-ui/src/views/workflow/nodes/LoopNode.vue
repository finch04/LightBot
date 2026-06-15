<template>
  <div class="group-shell loop-shell" :class="nodeClass" @dblclick="$emit('edit')">
    <div class="group-header">
      <div class="group-icon"><NodeTypeIcon type="loop" /></div>
      <span class="group-title">{{ data.label || '循环' }}</span>
      <span class="group-tag">循环</span>
    </div>
    <div class="group-inner" />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import NodeTypeIcon from '../components/NodeTypeIcon.vue'

const props = defineProps({
  data: Object,
  selected: Boolean,
})

defineEmits(['edit'])

const nodeClass = computed(() => ({
  selected: props.selected,
  [`debug-${props.data?.debugStatus}`]: !!props.data?.debugStatus,
}))
</script>

<style scoped>
.group-shell {
  width: 100%;
  height: 100%;
  min-width: 480px;
  min-height: 280px;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  border: 2px dashed #f97316;
  border-radius: 12px;
  background: rgba(255, 247, 237, 0.55);
  pointer-events: auto;
  cursor: grab;
}
/* 容器壳层不显示句柄，连线通过内置 start/end 节点 */
.group-shell :deep(.vue-flow__handle) {
  display: none !important;
}
.group-shell:active {
  cursor: grabbing;
}
.group-shell.selected {
  border-color: #ea580c;
  box-shadow: 0 0 0 2px rgba(249, 115, 22, 0.25);
}
.group-header {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  height: 44px;
  padding: 0 12px;
  border-bottom: 1px dashed #fdba74;
  background: rgba(255, 237, 213, 0.85);
  border-radius: 10px 10px 0 0;
  pointer-events: auto;
  cursor: grab;
}
.group-header:active {
  cursor: grabbing;
}
.group-icon {
  width: 26px;
  height: 26px;
  border-radius: 6px;
  background: rgba(249, 115, 22, 0.2);
  color: #ea580c;
  display: flex;
  align-items: center;
  justify-content: center;
}
.group-title {
  flex: 1;
  font-size: 13px;
  font-weight: 600;
  color: #9a3412;
}
.group-tag {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 999px;
  background: #ffedd5;
  color: #c2410c;
}
.group-inner {
  flex: 1;
  margin: 8px;
  border-radius: 8px;
  border: 1px dashed rgba(249, 115, 22, 0.35);
  background: rgba(255, 255, 255, 0.25);
  min-height: 200px;
  pointer-events: auto;
}
</style>
