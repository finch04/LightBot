<template>
  <div v-if="workflowEvents && workflowEvents.length > 0" class="workflow-nodes-group">
    <button type="button" class="workflow-summary" :class="{ 'is-expanded': isExpanded }" @click="isExpanded = !isExpanded">
      <span class="summary-icon">
        <CheckCircleOutlined v-if="isDone" class="icon-success" />
        <LoadingOutlined v-else class="icon-spinning" />
      </span>
      <span class="summary-content">
        <span class="summary-title">工作流执行 {{ nodeCount }} 个节点</span>
        <span v-if="nodeLabels.length" class="summary-meta">{{ nodeLabels.join(' → ') }}</span>
      </span>
      <span class="summary-trailing">
        <RightOutlined :class="{ expanded: isExpanded }" class="expand-icon" />
      </span>
    </button>

    <div v-show="isExpanded" class="workflow-panel">
      <div v-for="(evt, i) in displayEvents" :key="i" class="workflow-event-item">
        <div v-if="evt.type === 'workflow_node_start'" class="event-row event-start">
          <PlayCircleOutlined class="event-icon start" />
          <span class="event-label">
            执行 <strong>{{ evt.nodeLabel || getNodeTypeName(evt.nodeType) }}</strong>
          </span>
          <span class="event-id">{{ evt.nodeId }}</span>
        </div>
        <div v-else-if="evt.type === 'workflow_node_complete'" class="event-row" :class="evt.success === false ? 'event-fail' : 'event-done'">
          <CheckCircleOutlined v-if="evt.success !== false" class="event-icon icon-success" />
          <CloseCircleOutlined v-else class="event-icon icon-fail" />
          <span class="event-label">
            <strong>{{ evt.nodeLabel || getNodeTypeName(evt.nodeType) }}</strong>
            {{ evt.message || (evt.success === false ? '执行失败' : '执行完成') }}
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import {
  CheckCircleOutlined, LoadingOutlined, RightOutlined,
  PlayCircleOutlined, CloseCircleOutlined
} from '@ant-design/icons-vue'

const props = defineProps({
  workflowEvents: { type: Array, default: () => [] },
  isDone: { type: Boolean, default: true },
  defaultExpanded: { type: Boolean, default: false }
})

const isExpanded = ref(props.defaultExpanded)

watch(
  () => props.isDone,
  (done, prev) => {
    if (prev === false && done === true) {
      isExpanded.value = false
    }
  }
)

watch(
  () => props.defaultExpanded,
  (val) => { isExpanded.value = val },
  { immediate: true }
)

const displayEvents = computed(() =>
  props.workflowEvents.filter(e =>
    e.type === 'workflow_node_start' || e.type === 'workflow_node_complete'
  )
)

const nodeCount = computed(() =>
  props.workflowEvents.filter(e => e.type === 'workflow_node_start').length
)

const nodeLabels = computed(() => {
  const labels = []
  props.workflowEvents.forEach(e => {
    if (e.type === 'workflow_node_start' && e.nodeLabel) {
      labels.push(e.nodeLabel)
    }
  })
  return labels
})

function getNodeTypeName(type) {
  const map = {
    start: '开始',
    end: '结束',
    llm: '大模型',
    condition: '条件判断',
    retrieval: '知识检索',
    tool: '工具调用'
  }
  return map[type] || type || '节点'
}
</script>

<style scoped>
.workflow-nodes-group {
  margin-bottom: 8px;
  padding: 10px 12px;
  background: #f5f3ff;
  border: 1px solid #ddd6fe;
  border-radius: 8px;
}

.workflow-summary {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 0;
  border: none;
  background: transparent;
  cursor: pointer;
  text-align: left;
}

.summary-icon {
  flex-shrink: 0;
  font-size: 16px;
}

.icon-success { color: #22c55e; }
.icon-spinning { color: #7c3aed; }

.summary-content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.summary-title {
  font-size: 13px;
  font-weight: 600;
  color: #5b21b6;
}

.summary-meta {
  font-size: 12px;
  color: #7c3aed;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.summary-trailing {
  flex-shrink: 0;
}

.expand-icon {
  font-size: 10px;
  color: #9ca3af;
  transition: transform 0.2s;
}

.expand-icon.expanded {
  transform: rotate(90deg);
}

.workflow-panel {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid #e9d5ff;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.workflow-event-item {
  font-size: 13px;
}

.event-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 6px;
  background: #fff;
}

.event-start {
  border: 1px solid #e9d5ff;
}

.event-done {
  border: 1px solid #dcfce7;
}

.event-fail {
  border: 1px solid #fecaca;
  background: #fef2f2;
}

.event-icon {
  flex-shrink: 0;
  margin-top: 2px;
  font-size: 14px;
}

.event-icon.start { color: #7c3aed; }
.event-icon.icon-fail { color: #dc2626; }

.event-label {
  flex: 1;
  color: #374151;
  line-height: 1.5;
}

.event-id {
  flex-shrink: 0;
  font-size: 11px;
  font-family: ui-monospace, monospace;
  color: #9ca3af;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
