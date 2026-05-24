<template>
  <div class="workflow-node tool-node" :class="{ selected }" @dblclick="$emit('edit')">
    <Handle type="target" position="left" />
    <Handle type="source" position="right" />
    <div class="node-header">
      <div class="node-icon">
        <ToolOutlined />
      </div>
      <div class="node-title">{{ data.label || '工具调用' }}</div>
    </div>
    <div class="node-body">
      <div v-if="data.toolName" class="node-config">
        <span class="config-label">工具:</span>
        <span class="config-value">{{ data.toolName }}</span>
      </div>
      <div v-if="!data.toolId" class="node-placeholder">点击配置工具</div>
    </div>
  </div>
</template>

<script setup>
import { Handle } from '@vue-flow/core'
import { ToolOutlined } from '@ant-design/icons-vue'

defineProps({
  data: Object,
  selected: Boolean
})

defineEmits(['edit'])
</script>

<style scoped>
.tool-node {
  background: #fff;
  border: 2px solid #059669;
  border-radius: 12px;
  min-width: 180px;
  transition: all 0.2s ease;
}

.tool-node:hover {
  box-shadow: 0 4px 12px rgba(5, 150, 105, 0.15);
}

.tool-node.selected {
  border-color: #10b981;
  box-shadow: 0 0 0 3px rgba(5, 150, 105, 0.2);
}

.node-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  background: #ecfdf5;
  border-bottom: 1px solid #e5e7eb;
  border-radius: 10px 10px 0 0;
}

.node-icon {
  color: #059669;
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