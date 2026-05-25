<template>
  <div class="workflow-node condition-node" :class="nodeClass" @dblclick="$emit('edit')">
    <WorkflowHandle type="target" position="left" />
    <WorkflowHandle type="source" position="top" id="out_a" />
    <WorkflowHandle type="source" position="bottom" id="out_b" />
    <WorkflowHandle type="source" position="right" id="out_c" />
    <div class="node-header">
      <div class="node-icon">
        <ForkOutlined />
      </div>
      <div class="node-title">{{ data.label || '条件判断' }}</div>
    </div>
    <div class="node-body">
      <div v-if="data.conditionGroups?.length || data.branches?.length" class="node-config">
        <span class="config-label">条件组:</span>
        <span class="config-value">{{ (data.conditionGroups || data.branches || []).length }}</span>
      </div>
      <div v-else class="node-placeholder">点击配置分支条件</div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import WorkflowHandle from '../components/WorkflowHandle.vue'
import { ForkOutlined } from '@ant-design/icons-vue'

const props = defineProps({
  data: Object,
  selected: Boolean
})

defineEmits(['edit'])

const nodeClass = computed(() => ({
  selected: props.selected,
  [`debug-${props.data?.debugStatus}`]: !!props.data?.debugStatus
}))
</script>

<style scoped>
.condition-node.debug-executing { animation: wf-exec 1.2s linear infinite; border-color: #6366f1; }
.condition-node.debug-success { border-color: #22c55e; }
.condition-node.debug-fail { border-color: #ef4444; }
@keyframes wf-exec {
  0% { box-shadow: 0 0 0 0 rgba(99,102,241,0.35); }
  50% { box-shadow: 0 0 0 8px rgba(99,102,241,0.12); }
  100% { box-shadow: 0 0 0 0 rgba(99,102,241,0.35); }
}

.condition-node {
  background: #fff;
  border: 2px solid #d97706;
  border-radius: 12px;
  min-width: 140px;
  transition: all 0.2s ease;
}

.condition-node:hover {
  box-shadow: 0 4px 12px rgba(217, 119, 6, 0.15);
}

.condition-node.selected {
  border-color: #f59e0b;
  box-shadow: 0 0 0 3px rgba(217, 119, 6, 0.2);
}

.node-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  background: #fef3c7;
  border-bottom: 1px solid #e5e7eb;
  border-radius: 10px 10px 0 0;
}

.node-icon {
  color: #d97706;
  font-size: 16px;
}

.node-title {
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
}

.node-body {
  padding: 12px 14px;
}

.node-config {
  display: flex;
  gap: 4px;
  font-size: 12px;
}

.config-label {
  color: #6b7280;
}

.config-value {
  color: #374151;
  font-weight: 500;
}

.node-placeholder {
  font-size: 12px;
  color: #9ca3af;
}
</style>