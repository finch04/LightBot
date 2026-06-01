<template>
  <div v-if="workflowEvents && workflowEvents.length > 0" class="workflow-nodes-group">
    <button type="button" class="workflow-summary" :class="{ 'is-expanded': isExpanded }" @click="toggleExpand">
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
      <div
        v-for="(step, i) in nodeSteps"
        :key="step.stepKey || i"
        v-show="i < visibleCount"
        class="workflow-step"
        :style="{ animationDelay: `${i * 80}ms` }"
      >
        <div class="event-row" :class="stepStatusClass(step)">
          <div class="event-icon-col">
            <LoadingOutlined v-if="step.status === 'running'" class="event-icon icon-spinning" />
            <CheckCircleOutlined v-else-if="step.status === 'done'" class="event-icon icon-success" />
            <CloseCircleOutlined v-else-if="step.status === 'failed'" class="event-icon icon-fail" />
            <PlayCircleOutlined v-else class="event-icon start" />
          </div>
          <div class="event-main">
            <div class="event-head" @click="toggleStep(i)">
              <span class="event-label">
                <strong>{{ step.nodeLabel || getNodeTypeName(step.nodeType) }}</strong>
                <span class="event-type-tag">{{ getNodeTypeName(step.nodeType) }}</span>
              </span>
              <span v-if="step.durationMs != null" class="event-duration">{{ step.durationMs }}ms</span>
              <span v-else-if="step.status === 'running'" class="event-duration running">
                <span class="running-dot"></span> 执行中
              </span>
              <RightOutlined v-if="hasExpandableContent(step)" :class="{ expanded: expandedSteps.has(i) }" class="step-toggle-icon" />
            </div>
            <div v-show="expandedSteps.has(i)" class="step-detail-body">
              <div v-if="step.status === 'failed'" class="event-message fail">
                {{ step.message || '执行失败' }}
              </div>
              <div v-else-if="step.status === 'done' && step.message" class="event-message">
                {{ step.message }}
              </div>
              <div v-if="step.detail" class="event-detail">
                <pre>{{ step.detail }}</pre>
              </div>
              <div v-if="hasKvData(step.input)" class="event-kv-block">
                <div class="event-kv-title">入参</div>
                <pre>{{ formatKv(step.input) }}</pre>
              </div>
              <div v-if="hasKvData(step.outputs)" class="event-kv-block">
                <div class="event-kv-title">出参</div>
                <pre>{{ formatKv(step.outputs) }}</pre>
              </div>
              <div v-if="step.nextNodeId" class="event-next-node">下一节点: {{ step.nextNodeId }}</div>
              <div v-if="step.nodeId" class="event-node-id">节点 ID: {{ step.nodeId }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onUnmounted, nextTick } from 'vue'
import {
  CheckCircleOutlined, LoadingOutlined, RightOutlined,
  PlayCircleOutlined, CloseCircleOutlined
} from '@ant-design/icons-vue'

const props = defineProps({
  workflowEvents: { type: Array, default: () => [] },
  isDone: { type: Boolean, default: true },
  defaultExpanded: { type: Boolean, default: false },
  isStreaming: { type: Boolean, default: false },
})

const isExpanded = ref(props.defaultExpanded)
const expandedSteps = ref(new Set())
const visibleCount = ref(0)
let revealTimer = null

watch(
  () => props.defaultExpanded,
  (val) => { isExpanded.value = val },
  { immediate: true }
)

function clearRevealTimer() {
  if (revealTimer) {
    clearInterval(revealTimer)
    revealTimer = null
  }
}

/** 展开面板时逐个显示节点 */
function toggleExpand() {
  isExpanded.value = !isExpanded.value
  if (isExpanded.value) {
    startRevealAnimation()
  } else {
    clearRevealTimer()
    visibleCount.value = 0
  }
}

/** 启动逐个显示动画：每 120ms 显示一个节点 */
function startRevealAnimation() {
  clearRevealTimer()
  const total = nodeSteps.value.length
  // 已完成的工作流（历史消息）：逐个显示
  // 流式中：显示所有已到达的节点
  if (props.isStreaming) {
    visibleCount.value = total
    return
  }
  // 已完成的节点数少于等于 3 个，直接全部显示
  if (total <= 3) {
    visibleCount.value = total
    return
  }
  visibleCount.value = 0
  revealTimer = setInterval(() => {
    if (visibleCount.value < nodeSteps.value.length) {
      visibleCount.value++
    } else {
      clearRevealTimer()
    }
  }, 120)
}

function toggleStep(index) {
  const next = new Set(expandedSteps.value)
  if (next.has(index)) {
    next.delete(index)
  } else {
    next.add(index)
  }
  expandedSteps.value = next
}

function hasExpandableContent(step) {
  return step.message || step.detail || hasKvData(step.input) || hasKvData(step.outputs) || step.nextNodeId || step.nodeId
}

/** 按事件顺序构建链路，保留重复节点经过记录（不再按 nodeId 去重） */
const nodeSteps = computed(() => {
  const steps = []
  const runningByNodeId = new Map()
  const stepByIndex = new Map()

  for (const e of props.workflowEvents) {
    if (e.type === 'workflow_node_start' && e.nodeId) {
      const step = {
        nodeId: e.nodeId,
        nodeType: e.nodeType,
        nodeLabel: e.nodeLabel,
        input: e.input,
        stepIndex: e.stepIndex,
        stepKey: `start_${e.stepIndex ?? steps.length}_${e.nodeId}`,
        status: 'running',
      }
      steps.push(step)
      runningByNodeId.set(e.nodeId, step)
      if (e.stepIndex != null) stepByIndex.set(e.stepIndex, step)
    } else if (e.type === 'workflow_node_complete' && e.nodeId) {
      let step = null
      if (e.stepIndex != null) {
        step = stepByIndex.get(e.stepIndex) || null
      }
      if (!step) {
        step = runningByNodeId.get(e.nodeId) || null
      }
      if (!step) {
        step = {
          nodeId: e.nodeId,
          nodeType: e.nodeType,
          nodeLabel: e.nodeLabel,
          stepIndex: e.stepIndex,
          stepKey: `complete_${e.stepIndex ?? steps.length}_${e.nodeId}`,
          status: 'pending',
        }
        steps.push(step)
      }
      step.nodeType = e.nodeType ?? step.nodeType
      step.nodeLabel = e.nodeLabel ?? step.nodeLabel
      step.message = e.message
      step.detail = e.detail
      step.durationMs = e.durationMs
      step.success = e.success
      step.outputs = e.outputs
      step.nextNodeId = e.nextNodeId
      step.status = e.success === false ? 'failed' : 'done'
      runningByNodeId.delete(e.nodeId)
      if (e.stepIndex != null) stepByIndex.set(e.stepIndex, step)
    }
  }
  return steps
})

const runningCount = computed(() =>
  nodeSteps.value.filter(s => s.status === 'running').length
)

const nodeLabels = computed(() =>
  nodeSteps.value.map(s => s.nodeLabel || getNodeTypeName(s.nodeType)).filter(Boolean)
)

// 流式时自动展开新节点，完成后全部收起
watch(
  () => [props.isDone, nodeSteps.value.length],
  ([done, len]) => {
    if (done) {
      expandedSteps.value = new Set()
      // 流式结束时确保所有节点可见
      visibleCount.value = len
      clearRevealTimer()
    } else if (len > 0) {
      expandedSteps.value = new Set(Array.from({ length: len }, (_, i) => i))
      // 流式中新节点到达时，立即显示（不走定时器）
      if (isExpanded.value) {
        visibleCount.value = len
      }
    }
  },
  { immediate: true }
)

// 展开状态变化时触发动画
watch(isExpanded, (val) => {
  if (val) {
    startRevealAnimation()
  } else {
    clearRevealTimer()
    visibleCount.value = 0
  }
})

onUnmounted(() => {
  clearRevealTimer()
})

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
    code: '代码',
    loop_start: '迭代开始',
    loop_end: '迭代结束',
    batch_start: '并行处理',
    batch_end: '并行结束',
  }
  return map[type] || type || '节点'
}

function hasKvData(value) {
  return value && typeof value === 'object' && Object.keys(value).length > 0
}

function formatKv(value) {
  if (!value) return ''
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return String(value)
  }
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
.icon-spinning { color: #7c3aed; animation: spin 1s linear infinite; }

@keyframes spin {
  to { transform: rotate(360deg); }
}

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

/* 节点逐个淡入动画 */
.workflow-step {
  font-size: 13px;
  animation: stepFadeIn 0.3s ease-out both;
}

@keyframes stepFadeIn {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.event-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 6px;
  background: #fff;
  transition: border-color 0.3s, background 0.3s, box-shadow 0.3s;
}

/* 当前执行节点高亮：脉冲边框 + 微光背景 */
.event-running {
  border: 1px solid #c4b5fd;
  background: #faf5ff;
  box-shadow: 0 0 0 2px rgba(124, 58, 237, 0.1);
  animation: runningPulse 2s ease-in-out infinite;
}

@keyframes runningPulse {
  0%, 100% { box-shadow: 0 0 0 2px rgba(124, 58, 237, 0.1); }
  50% { box-shadow: 0 0 0 4px rgba(124, 58, 237, 0.2); }
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

.event-icon-col {
  flex-shrink: 0;
  margin-top: 2px;
}

.event-icon {
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
  cursor: pointer;
  user-select: none;
  border-radius: 4px;
  padding: 2px 4px;
  margin: -2px -4px;
}

.event-head:hover {
  background: #f5f3ff;
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
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

/* 执行中脉冲圆点 */
.running-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #7c3aed;
  animation: dotPulse 1.4s ease-in-out infinite;
}

@keyframes dotPulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.4; transform: scale(0.7); }
}

.step-toggle-icon {
  font-size: 10px;
  color: #9ca3af;
  transition: transform 0.2s;
  flex-shrink: 0;
}

.step-toggle-icon.expanded {
  transform: rotate(90deg);
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

.event-kv-block {
  margin-top: 6px;
  padding: 8px;
  border-radius: 6px;
  border: 1px solid #ede9fe;
  background: #faf5ff;
}

.event-kv-title {
  margin-bottom: 6px;
  font-size: 12px;
  font-weight: 600;
  color: #6d28d9;
}

.event-kv-block pre {
  margin: 0;
  font-size: 12px;
  line-height: 1.45;
  color: #4c1d95;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.event-next-node {
  margin-top: 6px;
  font-size: 11px;
  color: #6b7280;
  font-family: ui-monospace, monospace;
}

.event-node-id {
  margin-top: 4px;
  font-size: 11px;
  color: #9ca3af;
  font-family: ui-monospace, monospace;
}
</style>
