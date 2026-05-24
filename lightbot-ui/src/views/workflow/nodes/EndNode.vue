<template>
  <div class="workflow-node end-node">
    <Handle type="target" position="left" />
    <div class="node-content">
      <div class="node-icon">
        <StopOutlined />
      </div>
      <div class="node-label">结束</div>
    </div>
  </div>
</template>

<script setup>
import { Handle } from '@vue-flow/core'
import { StopOutlined } from '@ant-design/icons-vue'

defineProps({
  data: Object,
  selected: Boolean
})
</script>

<style scoped>
.end-node {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: linear-gradient(135deg, #ef4444, #dc2626);
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  box-shadow: 0 2px 8px rgba(239, 68, 68, 0.3);
  transition: all 0.2s ease;
}

.end-node:hover {
  box-shadow: 0 4px 12px rgba(239, 68, 68, 0.4);
}

.node-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  color: #fff;
}

.node-icon {
  font-size: 20px;
}

.node-label {
  font-size: 11px;
  margin-top: 2px;
  font-weight: 500;
}
</style>