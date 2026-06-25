<template>
  <div class="workflow-node llm-node" :class="nodeClass" @dblclick="$emit('edit')">
    <WorkflowHandle type="target" position="left" />
    <WorkflowHandle type="source" position="right" />
    <div class="node-header">
      <div class="node-icon">
        <RobotOutlined />
      </div>
      <div class="node-title">{{ data.label || '大模型' }}</div>
    </div>
    <div class="node-body">
      <div v-if="data.modelName" class="node-config">
        <span class="config-label">模型:</span>
        <span class="config-value">{{ data.modelName }}</span>
      </div>
      <div v-if="data.promptTemplate" class="node-config">
        <span class="config-label">提示词:</span>
        <span class="config-value">{{ truncate(data.promptTemplate, 20) }}</span>
      </div>
      <div v-if="!data.modelId && !data.promptTemplate" class="node-placeholder">
        点击配置模型和提示词
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import WorkflowHandle from '../components/WorkflowHandle.vue'
import { RobotOutlined } from '@ant-design/icons-vue'
import { useGroupDragMask } from '../useGroupDragMask'

const props = defineProps({
  id: String,
  data: Object,
  selected: Boolean,
  parentNode: String,
})

defineEmits(['edit'])

const { isGroupChildDragMasked } = useGroupDragMask(props)

const nodeClass = computed(() => ({
  selected: props.selected,
  'wf-group-child-mask': isGroupChildDragMasked.value,
  [`debug-${props.data?.debugStatus}`]: !!props.data?.debugStatus
}))

function truncate(str, len) {
  if (!str) return ''
  return str.length > len ? str.slice(0, len) + '...' : str
}
</script>

<style scoped>
.llm-node {
  background: var(--color-canvas);
  border: 2px solid #7c3aed;
  border-radius: 12px;
  min-width: 180px;
  transition: all 0.2s ease;
}

.llm-node:hover {
  box-shadow: 0 4px 12px rgba(124, 58, 237, 0.15);
}

.llm-node.selected {
  border-color: #8b5cf6;
  box-shadow: 0 0 0 3px rgba(124, 58, 237, 0.2);
}

.llm-node.debug-executing { animation: wf-exec 1.2s linear infinite; border-color: var(--color-link); }
.llm-node.debug-success { border-color: #22c55e; box-shadow: 0 0 0 3px rgba(34,197,94,0.2); }
.llm-node.debug-fail { border-color: #ef4444; box-shadow: 0 0 0 3px rgba(239,68,68,0.2); }
@keyframes wf-exec {
  0% { box-shadow: 0 0 0 0 rgba(99,102,241,0.35); }
  50% { box-shadow: 0 0 0 8px rgba(99,102,241,0.12); }
  100% { box-shadow: 0 0 0 0 rgba(99,102,241,0.35); }
}

.node-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  background: #f3e8ff;
  border-bottom: 1px solid var(--color-hairline);
  border-radius: 10px 10px 0 0;
}

.node-icon {
  color: #7c3aed;
  font-size: 16px;
}

.node-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-ink);
}

.node-body {
  padding: 12px 14px;
}

.node-config {
  display: flex;
  gap: 4px;
  font-size: 12px;
  margin-bottom: 4px;
}

.config-label {
  color: var(--color-mute);
}

.config-value {
  color: var(--color-text-dark);
  font-weight: 500;
}

.node-placeholder {
  font-size: 12px;
  color: var(--color-mute);
}
</style>