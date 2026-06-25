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
  background: var(--color-canvas);
  border: 2px solid #4f46e5;
  border-radius: 12px;
  min-width: 180px;
  transition: all 0.2s ease;
}

.retrieval-node:hover {
  box-shadow: 0 4px 12px rgba(79, 70, 229, 0.15);
}

.retrieval-node.selected {
  border-color: var(--color-link);
  box-shadow: 0 0 0 3px rgba(79, 70, 229, 0.2);
}

.node-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  background: var(--color-purple-bg);
  border-bottom: 1px solid var(--color-hairline);
  border-radius: 10px 10px 0 0;
}

.node-icon {
  color: #4f46e5;
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