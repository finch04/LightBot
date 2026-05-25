<template>
  <div v-if="workflowEvents && workflowEvents.length > 0" class="workflow-nodes-group">
    <button type="button" class="workflow-summary" :class="{ 'is-expanded': isExpanded }" @click="isExpanded = !isExpanded">
      <span class="summary-icon">
        <CheckCircleOutlined v-if="isDone" class="icon-success" />
        <LoadingOutlined v-else class="icon-spinning" />
      </span>
      <span class="summary-content">
        <span class="summary-title">
          {{ isDone ? `工作流已执行 ${nodeSteps.length} 个节点` : `工作流执行中 (${runningCount} 个进行中)` }}
        </span>
        <span v-if="nodeLabels.length" class="summary-meta">{{ nodeLabels.join(' → ') }}</span>
      </span>
      <span class="summary-trailing">
        <RightOutlined :class="{ expanded: isExpanded }" class="expand-icon" />
      </span>
    </button>

    <div v-show="isExpanded" class="workflow-panel">
      <div v-for="(step, i) in nodeSteps" :key="step.nodeId || i" class="workflow-step">
        <div class="event-row" :class="stepStatusClass(step)">
          <LoadingOutlined v-if="step.status === 'running'" class="event-icon icon-spinning" />
          <CheckCircleOutlined v-else-if="step.status === 'done'" class="event-icon icon-success" />
          <CloseCircleOutlined v-else-if="step.status === 'failed'" class="event-icon icon-fail" />
          <PlayCircleOutlined v-else class="event-icon start" />
          <div class="event-main">
            <div class="event-head">
              <span class="event-label">
                <strong>{{ step.nodeLabel || getNodeTypeName(step.nodeType) }}</strong>
                <span class="event-type-tag">{{ getNodeTypeName(step.nodeType) }}</span>
              </span>
              <span v-if="step.durationMs != null" class="event-duration">{{ step.durationMs }}ms</span>
              <span v-else-if="step.status === 'running'" class="event-duration running">执行中</span>
            </div>
            <div v-if="step.status === 'failed'" class="event-message fail">
              {{ step.message || '执行失败' }}
            </div>
            <div v-else-if="step.status === 'done' && step.message" class="event-message">
              {{ step.message }}
            </div>
            <div v-if="step.detail" class="event-detail">
              <pre>{{ step.detail }}</pre>
            </div>
            <div v-if="step.nodeId" class="event-node-id">节点 ID: {{ step.nodeId }}</div>
          </div>
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

/** 按 nodeId 合并 start/complete，展示运行中与详情 */
const nodeSteps = computed(() => {
  const map = new Map()
  for (const e of props.workflowEvents) {
    if (e.type === 'workflow_node_start' && e.nodeId) {
      const prev = map.get(e.nodeId) || {}
      map.set(e.nodeId, {
        ...prev,
        nodeId: e.nodeId,
        nodeType: e.nodeType,
        nodeLabel: e.nodeLabel,
        status: 'running',
      })
    } else if (e.type === 'workflow_node_complete' && e.nodeId) {
      const prev = map.get(e.nodeId) || {}
      map.set(e.nodeId, {
        ...prev,
        nodeId: e.nodeId,
        nodeType: e.nodeType ?? prev.nodeType,
        nodeLabel: e.nodeLabel ?? prev.nodeLabel,
        message: e.message,
        detail: e.detail,
        durationMs: e.durationMs,
        success: e.success,
        status: e.success === false ? 'failed' : 'done',
      })
    }
  }
  return Array.from(map.values())
})

const runningCount = computed(() =>
  nodeSteps.value.filter(s => s.status === 'running').length
)

const nodeLabels = computed(() =>
  nodeSteps.value.map(s => s.nodeLabel || getNodeTypeName(s.nodeType)).filter(Boolean)
)

function stepStatusClass(step) {
  if (step.status === 'running') return 'event-running'
  if (step.status === 'failed') return 'event-fail'
  if (step.status === 'done') return 'event-done'
  return 'event-start'
}

function getNodeTypeName(type) {
  const map = {
    start: '开始',
    end: '结束',
    llm: '大模型',
    condition: '条件判断',
    retrieval: '知识检索',
    tool: '工具调用',
    classifier: '意图分类',
    api: 'API',
    loop: '循环',
    variable: '变量',
    batch: '批处理',
    script: '脚本',
    mcp: 'MCP',
    input: '输入',
    output: '输出',
    variable_handle: '变量处理',
    parameter_extractor: '参数提取',
    app_component: '应用组件',
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
  gap: 8px;
}

.workflow-step {
  font-size: 13px;
}

.event-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 6px;
  background: #fff;
}

.event-running {
  border: 1px solid #c4b5fd;
  background: #faf5ff;
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

.event-main {
  flex: 1;
  min-width: 0;
}

.event-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.event-label {
  flex: 1;
  min-width: 0;
  color: #374151;
  line-height: 1.5;
}

.event-type-tag {
  margin-left: 6px;
  font-size: 11px;
  font-weight: normal;
  color: #9ca3af;
}

.event-duration {
  flex-shrink: 0;
  font-size: 11px;
  color: #6b7280;
  font-variant-numeric: tabular-nums;
}

.event-duration.running {
  color: #7c3aed;
}

.event-message {
  margin-top: 4px;
  font-size: 12px;
  color: #6b7280;
}

.event-message.fail {
  color: #dc2626;
}

.event-detail {
  margin-top: 6px;
  padding: 8px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  max-height: 160px;
  overflow: auto;
}

.event-detail pre {
  margin: 0;
  font-size: 12px;
  line-height: 1.45;
  color: #334155;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.event-node-id {
  margin-top: 4px;
  font-size: 11px;
  color: #9ca3af;
  font-family: ui-monospace, monospace;
}
</style>
