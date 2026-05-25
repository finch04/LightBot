<template>
  <div class="workflow-node retrieval-node" :class="nodeClass" @dblclick="$emit('edit')">
    <WorkflowHandle type="target" position="left" />
    <WorkflowHandle type="source" position="right" />
    <div class="node-header">
      <div class="node-icon">
        <BookOutlined />
      </div>
      <div class="node-title">{{ data.label || '知识检索' }}</div>
    </div>
    <div class="node-body">
      <div v-if="data.knowledgeName" class="node-config">
        <span class="config-label">知识库:</span>
        <span class="config-value">{{ data.knowledgeName }}</span>
      </div>
      <div v-if="data.topK" class="node-config">
        <span class="config-label">TopK:</span>
        <span class="config-value">{{ data.topK }}</span>
      </div>
      <div v-if="!data.knowledgeId" class="node-placeholder">点击配置知识库</div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import WorkflowHandle from '../components/WorkflowHandle.vue'
import { BookOutlined } from '@ant-design/icons-vue'
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
}))
</script>

<style scoped>
.retrieval-node {
  background: #fff;
  border: 2px solid #4f46e5;
  border-radius: 12px;
  min-width: 180px;
  transition: all 0.2s ease;
}

.retrieval-node:hover {
  box-shadow: 0 4px 12px rgba(79, 70, 229, 0.15);
}

.retrieval-node.selected {
  border-color: #6366f1;
  box-shadow: 0 0 0 3px rgba(79, 70, 229, 0.2);
}

.node-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  background: #e0e7ff;
  border-bottom: 1px solid #e5e7eb;
  border-radius: 10px 10px 0 0;
}

.node-icon {
  color: #4f46e5;
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
  margin-bottom: 4px;
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