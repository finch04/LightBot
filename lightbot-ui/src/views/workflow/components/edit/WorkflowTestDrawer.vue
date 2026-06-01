<template>
  <a-drawer
    v-model:open="open"
    title="测试运行"
    :width="600"
    :mask-closable="!testRunning && !testAnimating"
    :keyboard="!testRunning && !testAnimating"
    @close="$emit('close')"
  >
    <a-alert v-if="testAnimating" type="info" show-icon message="正在执行工作流..." description="画布上当前节点会高亮显示执行状态" class="test-alert" />
    <a-segmented
      :value="testMode"
      :options="[
        { label: '文本生成', value: 'generation' },
        { label: '文本对话', value: 'conversation' }
      ]"
      block
      class="test-mode-segment"
      @change="val => $emit('update:testMode', val)"
    />
    <p class="test-mode-hint">
      {{ testMode === 'generation' ? '单轮生成：输入一次问题，执行完整工作流并返回结果。' : '多轮对话：保留历史消息，每轮携带 history_list / query 变量执行。' }}
    </p>

    <template v-if="testMode === 'conversation'">
      <div class="test-chat-box">
        <div v-if="!testMessages.length" class="test-chat-empty">暂无对话，在下方输入并发送</div>
        <div v-for="(msg, i) in testMessages" :key="i" :class="['test-chat-msg', msg.role]">
          <span class="test-chat-role">{{ msg.role === 'user' ? '用户' : '助手' }}</span>
          <div class="test-chat-content">{{ msg.content }}</div>
        </div>
      </div>
    </template>

    <a-form layout="vertical">
      <a-form-item :label="testMode === 'generation' ? '测试内容' : '本轮输入'" required>
        <a-textarea
          :value="testInput"
          :rows="testMode === 'generation' ? 5 : 3"
          :placeholder="testMode === 'generation' ? '输入要生成的文本或问题' : '输入本轮用户消息'"
          @update:value="val => $emit('update:testInput', val)"
        />
      </a-form-item>
      <a-form-item label="使用草稿配置">
        <a-switch :checked="testUseDraft" @change="val => $emit('update:testUseDraft', val)" />
      </a-form-item>
      <div class="test-actions">
        <a-button type="primary" :loading="testRunning || testAnimating" @click="$emit('run')">
          {{ testAnimating ? '执行中...' : (testMode === 'conversation' ? '发送并运行' : '开始测试') }}
        </a-button>
        <a-button v-if="testMode === 'conversation'" :disabled="testRunning || testAnimating" @click="$emit('clear-conversation')">
          清空对话
        </a-button>
      </div>
    </a-form>
    <a-divider v-if="testResult || testAnimating" />
    <div v-if="testAnimating && testCurrentNodeId" class="test-current-node">
      当前节点：<strong>{{ getNodeTitleById(testCurrentNodeId) }}</strong>
    </div>
    <div v-if="testResult">
      <h4>输出结果</h4>
      <pre class="test-output">{{ testResult.output || '（无输出）' }}</pre>
      <h4>节点轨迹</h4>
      <div class="trace-steps">
        <div v-for="(step, i) in nodeSteps" :key="i" class="trace-step" :class="{ 'trace-active': step.nodeId === testCurrentNodeId }">
          <div class="trace-step-head" @click="toggleStep(i)">
            <span class="trace-step-icon">
              <span v-if="step.status === 'done'" class="trace-icon-done">✓</span>
              <span v-else-if="step.status === 'failed'" class="trace-icon-fail">✗</span>
              <span v-else class="trace-icon-run">▶</span>
            </span>
            <span class="trace-step-label">
              <strong>{{ step.nodeLabel || step.nodeId }}</strong>
              <span class="trace-step-type">{{ getNodeTypeName(step.nodeType) }}</span>
            </span>
            <span v-if="step.durationMs != null" class="trace-step-duration">{{ step.durationMs }}ms</span>
            <span v-else-if="step.status === 'running'" class="trace-step-duration trace-running">执行中</span>
            <span class="trace-toggle" :class="{ expanded: expandedSteps.has(i) }">›</span>
          </div>
          <div v-show="expandedSteps.has(i)" class="trace-step-body">
            <div v-if="step.status === 'failed'" class="trace-msg trace-fail">{{ step.message || '执行失败' }}</div>
            <div v-else-if="step.status === 'done' && step.message" class="trace-msg">{{ step.message }}</div>
            <div v-if="step.detail" class="trace-detail">
              <pre>{{ step.detail }}</pre>
            </div>
            <div v-if="hasKvData(step.input)" class="trace-kv">
              <div class="trace-kv-title">入参</div>
              <pre>{{ formatKv(step.input) }}</pre>
            </div>
            <div v-if="hasKvData(step.outputs)" class="trace-kv">
              <div class="trace-kv-title">出参</div>
              <pre>{{ formatKv(step.outputs) }}</pre>
            </div>
            <div v-if="step.nextNodeId" class="trace-meta">下一节点: {{ step.nextNodeId }}</div>
          </div>
        </div>
      </div>
    </div>
  </a-drawer>
</template>

<script setup>
import { ref, computed, watch } from 'vue'

const props = defineProps({
  testMode: String,
  testInput: String,
  testUseDraft: Boolean,
  testRunning: Boolean,
  testAnimating: Boolean,
  testMessages: { type: Array, default: () => [] },
  testResult: { type: Object, default: null },
  testCurrentNodeId: [String, Number],
  getNodeTitleById: { type: Function, required: true },
})

defineEmits([
  'close', 'run', 'clear-conversation',
  'update:testMode', 'update:testInput', 'update:testUseDraft',
])

const open = defineModel('open', { type: Boolean, default: false })

const expandedSteps = ref(new Set())

function toggleStep(index) {
  const next = new Set(expandedSteps.value)
  if (next.has(index)) next.delete(index)
  else next.add(index)
  expandedSteps.value = next
}

/** 将 start+complete 事件合并为节点步骤 */
const nodeSteps = computed(() => {
  if (!props.testResult?.nodeEvents) return []
  const steps = []
  const runningByNodeId = new Map()
  const stepByIndex = new Map()

  for (const e of props.testResult.nodeEvents) {
    if (e.type === 'workflow_node_start' && e.nodeId) {
      const step = {
        nodeId: e.nodeId, nodeType: e.nodeType, nodeLabel: e.nodeLabel,
        input: e.input, stepIndex: e.stepIndex,
        status: 'running',
      }
      steps.push(step)
      runningByNodeId.set(e.nodeId, step)
      if (e.stepIndex != null) stepByIndex.set(e.stepIndex, step)
    } else if (e.type === 'workflow_node_complete' && e.nodeId) {
      let step = (e.stepIndex != null ? stepByIndex.get(e.stepIndex) : null) || runningByNodeId.get(e.nodeId)
      if (!step) {
        step = { nodeId: e.nodeId, nodeType: e.nodeType, nodeLabel: e.nodeLabel, stepIndex: e.stepIndex, status: 'pending' }
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

// 测试完成后展开所有步骤
watch(() => props.testResult, (val) => {
  if (val?.nodeEvents) {
    expandedSteps.value = new Set(Array.from({ length: nodeSteps.value.length }, (_, i) => i))
  }
}, { immediate: true })

function hasKvData(value) {
  return value && typeof value === 'object' && Object.keys(value).length > 0
}

function formatKv(value) {
  if (!value) return ''
  try { return JSON.stringify(value, null, 2) } catch { return String(value) }
}

function getNodeTypeName(type) {
  const map = {
    start: '开始', end: '结束', llm: '大模型', condition: '条件判断',
    retrieval: '知识检索', tool: '工具调用', classifier: '意图分类',
    api: 'API', loop: '循环', variable: '变量', batch: '批处理',
    script: '脚本', mcp: 'MCP', input: '输入', output: '输出',
    variable_handle: '变量处理', parameter_extractor: '参数提取',
    app_component: '应用组件', code: '代码',
    loop_start: '迭代开始', loop_end: '迭代结束',
    batch_start: '并行处理', batch_end: '并行结束',
  }
  return map[type] || type || '节点'
}
</script>

<style scoped>
.test-alert { margin-bottom: 12px; }
.test-mode-segment { margin-bottom: 8px; }
.test-mode-hint { font-size: 12px; color: #6b7280; margin-bottom: 12px; line-height: 1.5; }
.test-chat-box {
  max-height: 220px;
  overflow-y: auto;
  margin-bottom: 12px;
  padding: 10px;
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}
.test-chat-empty { font-size: 12px; color: #9ca3af; text-align: center; padding: 16px 0; }
.test-chat-msg { margin-bottom: 10px; }
.test-chat-msg.user .test-chat-content { background: #eef2ff; }
.test-chat-msg.assistant .test-chat-content { background: #fff; border: 1px solid #e5e7eb; }
.test-chat-role { font-size: 11px; color: #9ca3af; display: block; margin-bottom: 4px; }
.test-chat-content { font-size: 13px; color: #374151; padding: 8px 10px; border-radius: 6px; white-space: pre-wrap; word-break: break-word; }
.test-actions { display: flex; gap: 8px; flex-wrap: wrap; }
.test-current-node {
  font-size: 13px;
  color: #6366f1;
  margin-bottom: 12px;
  padding: 8px 12px;
  background: #eef2ff;
  border-radius: 6px;
}
.test-output {
  background: #f9fafb;
  padding: 12px;
  border-radius: 6px;
  white-space: pre-wrap;
  font-size: 12px;
}
.trace-steps { display: flex; flex-direction: column; gap: 6px; }
.trace-step { border: 1px solid #e5e7eb; border-radius: 6px; background: #fff; }
.trace-step.trace-active { border-color: #818cf8; background: #eef2ff; }
.trace-step-head {
  display: flex; align-items: center; gap: 8px;
  padding: 8px 10px; cursor: pointer; user-select: none;
}
.trace-step-head:hover { background: #f9fafb; }
.trace-step-icon { flex-shrink: 0; font-size: 13px; }
.trace-icon-done { color: #22c55e; }
.trace-icon-fail { color: #dc2626; }
.trace-icon-run { color: #6366f1; }
.trace-step-label { flex: 1; min-width: 0; font-size: 13px; color: #374151; }
.trace-step-label strong { font-weight: 600; }
.trace-step-type { margin-left: 6px; font-size: 11px; font-weight: normal; color: #9ca3af; }
.trace-step-duration { flex-shrink: 0; font-size: 11px; color: #6b7280; font-variant-numeric: tabular-nums; }
.trace-running { color: #6366f1; }
.trace-toggle {
  flex-shrink: 0; font-size: 12px; color: #9ca3af;
  transition: transform 0.2s; display: inline-block;
}
.trace-toggle.expanded { transform: rotate(90deg); }
.trace-step-body { padding: 0 10px 10px; }
.trace-msg { font-size: 12px; color: #6b7280; margin-top: 4px; }
.trace-msg.trace-fail { color: #dc2626; }
.trace-detail {
  margin-top: 6px; padding: 8px; background: #f8fafc;
  border: 1px solid #e2e8f0; border-radius: 6px; max-height: 160px; overflow: auto;
}
.trace-detail pre { margin: 0; font-size: 12px; line-height: 1.45; color: #334155; white-space: pre-wrap; word-break: break-word; font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace; }
.trace-kv {
  margin-top: 6px; padding: 8px; border-radius: 6px;
  border: 1px solid #ede9fe; background: #faf5ff;
}
.trace-kv-title { margin-bottom: 4px; font-size: 12px; font-weight: 600; color: #6d28d9; }
.trace-kv pre { margin: 0; font-size: 12px; line-height: 1.45; color: #4c1d95; white-space: pre-wrap; word-break: break-word; font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace; }
.trace-meta { margin-top: 4px; font-size: 11px; color: #9ca3af; font-family: ui-monospace, monospace; }
</style>
