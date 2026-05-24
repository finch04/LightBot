<template>
  <div class="workflow-node start-node">
    <Handle type="source" position="right" />
    <div class="node-content">
      <div class="node-icon">
        <PlayCircleOutlined />
      </div>
      <div class="node-label">开始</div>
    </div>
  </div>
</template>

<script setup>
import { Handle } from '@vue-flow/core'
import { PlayCircleOutlined } from '@ant-design/icons-vue'

defineProps({
  data: Object,
  selected: Boolean
})
</script>

<style scoped>
.start-node {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: linear-gradient(135deg, #22c55e, #16a34a);
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  box-shadow: 0 2px 8px rgba(34, 197, 94, 0.3);
  transition: all 0.2s ease;
}

.start-node:hover {
  box-shadow: 0 4px 12px rgba(34, 197, 94, 0.4);
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