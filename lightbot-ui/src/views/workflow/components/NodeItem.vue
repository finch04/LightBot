<template>
  <div
    class="node-item"
    :draggable="draggable"
    @dragstart="onDragStart"
  >
    <div class="node-icon" :style="{ background: color + '20', color: color }">
      <RobotOutlined v-if="type === 'llm'" />
      <ForkOutlined v-if="type === 'condition'" />
      <BookOutlined v-if="type === 'retrieval'" />
      <ToolOutlined v-if="type === 'tool'" />
      <PlayCircleOutlined v-if="type === 'start'" />
      <StopOutlined v-if="type === 'end'" />
    </div>
    <div class="node-info">
      <div class="node-title">{{ title }}</div>
      <div class="node-desc">{{ desc }}</div>
    </div>
  </div>
</template>

<script setup>
import { RobotOutlined, ForkOutlined, BookOutlined, ToolOutlined, PlayCircleOutlined, StopOutlined } from '@ant-design/icons-vue'

const props = defineProps({
  type: {
    type: String,
    required: true
  },
  title: {
    type: String,
    required: true
  },
  desc: {
    type: String,
    default: ''
  },
  color: {
    type: String,
    default: '#6b7280'
  },
  draggable: {
    type: Boolean,
    default: true
  }
})

const emit = defineEmits(['dragstart'])

function onDragStart(event) {
  emit('dragstart', event, props.type)
}
</script>

<style scoped>
.node-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  cursor: grab;
  transition: all 0.2s ease;
  margin-bottom: 8px;
}

.node-item:hover {
  border-color: #6366f1;
  box-shadow: 0 2px 8px rgba(99, 102, 241, 0.1);
  transform: translateY(-1px);
}

.node-item:active {
  cursor: grabbing;
}

.node-icon {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  font-size: 16px;
}

.node-info {
  flex: 1;
}

.node-title {
  font-size: 13px;
  font-weight: 600;
  color: #1f2937;
}

.node-desc {
  font-size: 11px;
  color: #6b7280;
  margin-top: 2px;
}
</style>