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
      <template v-for="(step, i) in nodeSteps" :key="step.stepKey || i">
        <div
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
                  <span v-if="step.isContainer && step.children?.length" class="event-child-count">
                    {{ step.children.length }} 个子节点
                  </span>
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
                <div v-if="step.status === 'done' && extractResultText(step)" class="event-result">
                  {{ extractResultText(step) }}
                </div>
              </div>
            </div>
          </div>
          <!-- 容器内部子节点 -->
          <div v-if="step.isContainer && step.children?.length && expandedSteps.has(i)" class="container-children">
            <div
              v-for="(child, ci) in step.children"
              :key="child.stepKey || ci"
              class="workflow-step child-step"
              :style="{ animationDelay: `${ci * 60}ms` }"
            >
              <div class="event-row child-row" :class="stepStatusClass(child)">
                <div class="event-icon-col">
                  <LoadingOutlined v-if="child.status === 'running'" class="event-icon icon-spinning" />
                  <CheckCircleOutlined v-else-if="child.status === 'done'" class="event-icon icon-success" />
                  <CloseCircleOutlined v-else-if="child.status === 'failed'" class="event-icon icon-fail" />
                  <PlayCircleOutlined v-else class="event-icon start" />
                </div>
                <div class="event-main">
                  <div class="event-head" @click="toggleStep(`child_${i}_${ci}`)">
                    <span class="event-label">
                      <strong>{{ child.nodeLabel || getNodeTypeName(child.nodeType) }}</strong>
                      <span class="event-type-tag">{{ getNodeTypeName(child.nodeType) }}</span>
                      <span v-if="child.iterationIndex != null" class="event-iteration-tag">
                        #{{ child.iterationIndex + 1 }}
                      </span>
                    </span>
                    <span v-if="child.durationMs != null" class="event-duration">{{ child.durationMs }}ms</span>
                    <span v-else-if="child.status === 'running'" class="event-duration running">
                      <span class="running-dot"></span> 执行中
                    </span>
                  </div>
                  <div v-show="expandedSteps.has(`child_${i}_${ci}`)" class="step-detail-body">
                    <div v-if="child.status === 'failed'" class="event-message fail">
                      {{ child.message || '执行失败' }}
                    </div>
                    <div v-else-if="child.status === 'done' && child.message" class="event-message">
                      {{ child.message }}
                    </div>
                    <div v-if="child.status === 'done' && extractResultText(child)" class="event-result">
                      {{ extractResultText(child) }}
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </template>
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
  return step.message || step.detail || hasKvData(step.outputs) || extractResultText(step)
}

/** 按事件顺序构建链路，支持容器节点（循环/批处理）折叠展示内部子节点 */
const nodeSteps = computed(() => {
  const steps = []
  const runningByNodeId = new Map()
  const stepByIndex = new Map()
  // 容器节点栈：处理嵌套事件归属
  const containerStack = []

  for (const e of props.workflowEvents) {
    if (e.type === 'workflow_node_start' && e.nodeId) {
      const isContainerStart = !e.parentNodeId && isContainerNodeType(e.nodeType)
      const step = {
        nodeId: e.nodeId,
        nodeType: e.nodeType,
        nodeLabel: e.nodeLabel,
        input: e.input,
        stepIndex: e.stepIndex,
        stepKey: `start_${e.stepIndex ?? steps.length}_${e.nodeId}`,
        status: 'running',
        parentNodeId: e.parentNodeId || null,
        iterationIndex: e.iterationIndex ?? null,
        isContainer: isContainerStart,
        children: isContainerStart ? [] : undefined,
      }
      if (e.parentNodeId && containerStack.length > 0) {
        // 子节点：挂到当前容器的 children
        const parent = containerStack[containerStack.length - 1]
        if (parent.children) parent.children.push(step)
      } else {
        steps.push(step)
      }
      runningByNodeId.set(e.nodeId, step)
      if (e.stepIndex != null) stepByIndex.set(e.stepIndex, step)
      if (isContainerStart) containerStack.push(step)
    } else if (e.type === 'workflow_node_complete' && e.nodeId) {
      let step = null
      if (e.stepIndex != null) {
        step = stepByIndex.get(e.stepIndex) || null
      }
      if (!step) {
        step = runningByNodeId.get(e.nodeId) || null
      }
      if (!step) {
        // 未匹配到已有 step，按容器归属决定插入位置
        const isChild = !!e.parentNodeId
        step = {
          nodeId: e.nodeId,
          nodeType: e.nodeType,
          nodeLabel: e.nodeLabel,
          stepIndex: e.stepIndex,
          stepKey: `complete_${e.stepIndex ?? steps.length}_${e.nodeId}`,
          status: 'pending',
          parentNodeId: e.parentNodeId || null,
          iterationIndex: e.iterationIndex ?? null,
        }
        if (isChild && containerStack.length > 0) {
          const parent = containerStack[containerStack.length - 1]
          if (parent.children) parent.children.push(step)
        } else {
          steps.push(step)
        }
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
      if (e.isContainer != null) step.isContainer = e.isContainer
      runningByNodeId.delete(e.nodeId)
      if (e.stepIndex != null) stepByIndex.set(e.stepIndex, step)
      // 容器完成：出栈
      if (step.isContainer && containerStack.length > 0 &&
          containerStack[containerStack.length - 1].nodeId === e.nodeId) {
        containerStack.pop()
      }
    }
  }
  return steps
})

function isContainerNodeType(type) {
  return type === 'loop' || type === 'batch'
}

const runningCount = computed(() => {
  let count = nodeSteps.value.filter(s => s.status === 'running').length
  for (const s of nodeSteps.value) {
    if (s.children) count += s.children.filter(c => c.status === 'running').length
  }
  return count
})

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

/** 从 step.detail / step.outputs 中提取可读的执行结果文本 */
function extractResultText(step) {
  // 优先从 outputs 中提取有意义的值
  let outputs = step.outputs
  // 兼容 outputs 为 JSON 字符串的情况
  if (typeof outputs === 'string') {
    try { outputs = JSON.parse(outputs) } catch { outputs = null }
  }
  if (outputs && typeof outputs === 'object' && Object.keys(outputs).length > 0) {
    const preferKeys = ['result', 'output', 'text', 'answer', 'llmOutput']
    for (const k of preferKeys) {
      const v = outputs[k]
      if (v != null && String(v).trim()) return String(v)
    }
    // 取第一个非空值
    for (const v of Object.values(outputs)) {
      if (v != null && String(v).trim()) return String(v)
    }
  }
  // 回退到 detail
  if (step.detail && String(step.detail).trim()) {
    const detail = String(step.detail)
    // 尝试解析 JSON，提取内部值
    try {
      const parsed = JSON.parse(detail)
      if (typeof parsed === 'string') return parsed
      if (typeof parsed === 'object' && parsed !== null) {
        const preferKeys = ['result', 'output', 'text', 'answer', 'llmOutput', 'input']
        for (const k of preferKeys) {
          const v = parsed[k]
          if (v != null && String(v).trim()) return String(v)
        }
        // 取第一个值
        const first = Object.values(parsed).find(v => v != null && String(v).trim())
        if (first != null) return String(first)
      }
    } catch {
      // 非 JSON，直接返回
      return detail
    }
  }
  return ''
}
</script>

<style scoped>
.workflow-nodes-group {
  margin-bottom: 8px;
  padding: 10px 12px;
  background: var(--color-purple-bg);
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
  color: var(--color-mute);
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
  background: var(--color-canvas);
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
  border: 1px solid var(--color-error-soft);
  background: var(--color-error-bg);
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
  background: var(--color-purple-bg);
}

.event-label {
  flex: 1;
  min-width: 0;
  color: var(--color-text-dark);
  line-height: 1.5;
}

.event-type-tag {
  margin-left: 6px;
  font-size: 11px;
  font-weight: normal;
  color: var(--color-mute);
}

.event-duration {
  flex-shrink: 0;
  font-size: 11px;
  color: var(--color-mute);
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
  color: var(--color-mute);
  transition: transform 0.2s;
  flex-shrink: 0;
}

.step-toggle-icon.expanded {
  transform: rotate(90deg);
}

.event-message {
  margin-top: 4px;
  font-size: 12px;
  color: var(--color-mute);
}

.event-message.fail {
  color: #dc2626;
}

.event-result {
  margin-top: 6px;
  padding: 8px 10px;
  background: var(--color-canvas-soft);
  border: 1px solid var(--color-border-slate);
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--color-text-dark);
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 200px;
  overflow-y: auto;
}

.event-detail {
  margin-top: 6px;
  padding: 8px;
  background: var(--color-canvas-soft);
  border: 1px solid var(--color-border-slate);
  border-radius: 6px;
  max-height: 160px;
  overflow: auto;
}

.event-detail pre {
  margin: 0;
  font-size: 12px;
  line-height: 1.45;
  color: var(--color-text-dark);
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
  color: var(--color-mute);
  font-family: ui-monospace, monospace;
}

.event-node-id {
  margin-top: 4px;
  font-size: 11px;
  color: var(--color-mute);
  font-family: ui-monospace, monospace;
}

/* 容器内部子节点 */
.container-children {
  margin-left: 20px;
  padding-left: 12px;
  border-left: 2px solid #e9d5ff;
  margin-top: 6px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.child-step .child-row {
  padding: 6px 8px;
  background: #faf5ff;
  border: 1px solid #f3e8ff;
}

.child-step .child-row.event-running {
  border-color: #d8b4fe;
  background: #faf5ff;
}

.child-step .child-row.event-done {
  border-color: #e9d5ff;
}

.child-step .child-row.event-fail {
  border-color: #fecaca;
  background: var(--color-error-bg);
}

.event-child-count {
  margin-left: 6px;
  font-size: 11px;
  font-weight: normal;
  color: #7c3aed;
  background: var(--color-purple-bg);
  padding: 1px 6px;
  border-radius: 8px;
}

.event-iteration-tag {
  margin-left: 4px;
  font-size: 10px;
  font-weight: normal;
  color: var(--color-link);
  background: var(--color-info-bg);
  padding: 1px 5px;
  border-radius: 6px;
}
</style>
