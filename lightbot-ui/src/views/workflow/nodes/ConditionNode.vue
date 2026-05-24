<template>
  <div class="workflow-node condition-node" :class="{ selected }" @dblclick="$emit('edit')">
    <Handle type="target" position="left" />
    <Handle type="source" position="top" id="a" />
    <Handle type="source" position="bottom" id="b" />
    <Handle type="source" position="right" id="c" />
    <div class="node-header">
      <div class="node-icon">
        <ForkOutlined />
      </div>
      <div class="node-title">{{ data.label || '条件判断' }}</div>
    </div>
    <div class="node-body">
      <div v-if="data.branches && data.branches.length > 0" class="node-config">
        <span class="config-label">分支数:</span>
        <span class="config-value">{{ data.branches.length }}</span>
      </div>
      <div v-else class="node-placeholder">点击配置分支条件</div>
    </div>
  </div>
</template>

<script setup>
import { Handle } from '@vue-flow/core'
import { ForkOutlined } from '@ant-design/icons-vue'

defineProps({
  data: Object,
  selected: Boolean
})

defineEmits(['edit'])
</script>

<style scoped>
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