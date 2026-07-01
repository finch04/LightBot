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
      <WorkflowConfirmForm
        v-if="testPendingConfirm?.confirmForm"
        :confirm-form="testPendingConfirm.confirmForm"
        :submitting="testRunning || testAnimating"
        @submit="formData => $emit('resume', formData)"
      />
      <h4 v-if="!testPendingConfirm">输出结果</h4>
      <pre v-if="!testPendingConfirm" class="test-output">{{ testResult.output || '（无输出）' }}</pre>
      <div class="result-tab-header">
        <h4 :class="{ active: resultTab === 'trace' }" @click="resultTab = 'trace'">节点轨迹</h4>
        <h4 :class="{ active: resultTab === 'variables' }" @click="resultTab = 'variables'">
          变量面板
          <span v-if="variableCount > 0" class="var-count">{{ variableCount }}</span>
        </h4>
      </div>
      <!-- 变量面板 -->
      <div v-if="resultTab === 'variables' && testResult.variables" class="vars-panel">
        <div v-if="Object.keys(testResult.variables).length === 0" class="vars-empty">暂无变量</div>
        <div v-for="(val, key) in testResult.variables" :key="key" class="var-item" :class="{ expanded: expandedVars.has(key) }">
          <div class="var-item-head" @click="toggleVar(key)">
            <span class="var-key">{{ key }}</span>
            <span class="var-type">{{ getValueType(val) }}</span>
            <span class="var-toggle" :class="{ expanded: expandedVars.has(key) }">›</span>
          </div>
          <div v-if="expandedVars.has(key)" class="var-item-body">
            <pre v-if="isComplexValue(val)" class="var-value-json">{{ formatVarValue(val) }}</pre>
            <div v-else class="var-value-text">{{ formatVarValue(val) }}</div>
          </div>
          <div v-if="!expandedVars.has(key)" class="var-preview">{{ getValuePreview(val) }}</div>
        </div>
      </div>
      <!-- 节点轨迹 -->
      <template v-if="resultTab === 'trace'">
      <div class="trace-steps">
        <template v-for="(step, i) in nodeSteps" :key="i">
          <div class="trace-step" :class="{ 'trace-active': step.nodeId === testCurrentNodeId }">
            <div class="trace-step-head" @click="toggleStep(i)">
              <span class="trace-step-icon">
                <span v-if="step.status === 'done'" class="trace-icon-done">✓</span>
                <span v-else-if="step.status === 'failed'" class="trace-icon-fail">✗</span>
                <span v-else class="trace-icon-run">▶</span>
              </span>
              <span class="trace-step-label">
                <strong>{{ step.nodeLabel || step.nodeId }}</strong>
                <span class="trace-step-type">{{ getNodeTypeName(step.nodeType) }}</span>
                <span v-if="step.isContainer && step.children?.length" class="trace-child-count">{{ step.children.length }} 个子节点</span>
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
            <!-- 容器内部子节点 -->
            <div v-if="step.isContainer && step.children?.length && expandedSteps.has(i)" class="trace-container-children">
              <div
                v-for="(child, ci) in step.children"
                :key="ci"
                class="trace-step trace-child-step"
                :class="{ 'trace-active': child.nodeId === testCurrentNodeId }"
              >
                <div class="trace-step-head" @click="toggleStep(`child_${i}_${ci}`)">
                  <span class="trace-step-icon">
                    <span v-if="child.status === 'done'" class="trace-icon-done">✓</span>
                    <span v-else-if="child.status === 'failed'" class="trace-icon-fail">✗</span>
                    <span v-else class="trace-icon-run">▶</span>
                  </span>
                  <span class="trace-step-label">
                    <strong>{{ child.nodeLabel || child.nodeId }}</strong>
                    <span class="trace-step-type">{{ getNodeTypeName(child.nodeType) }}</span>
                    <span v-if="child.iterationIndex != null" class="trace-iteration-tag">#{{ child.iterationIndex + 1 }}</span>
                  </span>
                  <span v-if="child.durationMs != null" class="trace-step-duration">{{ child.durationMs }}ms</span>
                  <span class="trace-toggle" :class="{ expanded: expandedSteps.has(`child_${i}_${ci}`) }">›</span>
                </div>
                <div v-show="expandedSteps.has(`child_${i}_${ci}`)" class="trace-step-body">
                  <div v-if="child.status === 'failed'" class="trace-msg trace-fail">{{ child.message || '执行失败' }}</div>
                  <div v-else-if="child.status === 'done' && child.message" class="trace-msg">{{ child.message }}</div>
                  <div v-if="hasKvData(child.outputs)" class="trace-kv">
                    <div class="trace-kv-title">出参</div>
                    <pre>{{ formatKv(child.outputs) }}</pre>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </template>
      </div>
      </template>
    </div>
  </a-drawer>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import WorkflowConfirmForm from '../../../../components/WorkflowConfirmForm.vue'

const props = defineProps({
  testMode: String,
  testInput: String,
  testUseDraft: Boolean,
  testRunning: Boolean,
  testAnimating: Boolean,
  testMessages: { type: Array, default: () => [] },
  testResult: { type: Object, default: null },
  testPendingConfirm: { type: Object, default: null },
  testCurrentNodeId: [String, Number],
  getNodeTitleById: { type: Function, required: true },
})

defineEmits([
  'close', 'run', 'resume', 'clear-conversation',
  'update:testMode', 'update:testInput', 'update:testUseDraft',
])

const open = defineModel('open', { type: Boolean, default: false })

const expandedSteps = ref(new Set())
const resultTab = ref('trace')
const expandedVars = ref(new Set())

const variableCount = computed(() => {
  if (!props.testResult?.variables) return 0
  return Object.keys(props.testResult.variables).length
})

// 测试结果变化时重置为轨迹 tab
watch(() => props.testResult, () => {
  resultTab.value = 'trace'
  expandedVars.value = new Set()
})

function toggleStep(index) {
  const next = new Set(expandedSteps.value)
  if (next.has(index)) next.delete(index)
  else next.add(index)
  expandedSteps.value = next
}

/** 将 start+complete 事件合并为节点步骤，支持容器节点折叠展示 */
const nodeSteps = computed(() => {
  if (!props.testResult?.nodeEvents) return []
  const steps = []
  const runningByNodeId = new Map()
  const stepByIndex = new Map()
  const containerStack = []

  for (const e of props.testResult.nodeEvents) {
    if (e.type === 'workflow_node_start' && e.nodeId) {
      const isContainerStart = !e.parentNodeId && isContainerNodeType(e.nodeType)
      const step = {
        nodeId: e.nodeId, nodeType: e.nodeType, nodeLabel: e.nodeLabel,
        input: e.input, stepIndex: e.stepIndex,
        status: 'running',
        parentNodeId: e.parentNodeId || null,
        iterationIndex: e.iterationIndex ?? null,
        isContainer: isContainerStart,
        children: isContainerStart ? [] : undefined,
      }
      if (e.parentNodeId && containerStack.length > 0) {
        const parent = containerStack[containerStack.length - 1]
        if (parent.children) parent.children.push(step)
      } else {
        steps.push(step)
      }
      runningByNodeId.set(e.nodeId, step)
      if (e.stepIndex != null) stepByIndex.set(e.stepIndex, step)
      if (isContainerStart) containerStack.push(step)
    } else if (e.type === 'workflow_node_complete' && e.nodeId) {
      let step = (e.stepIndex != null ? stepByIndex.get(e.stepIndex) : null) || runningByNodeId.get(e.nodeId)
      if (!step) {
        const isChild = !!e.parentNodeId
        step = { nodeId: e.nodeId, nodeType: e.nodeType, nodeLabel: e.nodeLabel, stepIndex: e.stepIndex, status: 'pending', parentNodeId: e.parentNodeId || null, iterationIndex: e.iterationIndex ?? null }
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
      if (step.isContainer && containerStack.length > 0 && containerStack[containerStack.length - 1].nodeId === e.nodeId) {
        containerStack.pop()
      }
    }
  }
  return steps
})

function isContainerNodeType(type) {
  return type === 'loop' || type === 'batch'
}

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

function toggleVar(key) {
  const next = new Set(expandedVars.value)
  if (next.has(key)) next.delete(key)
  else next.add(key)
  expandedVars.value = next
}

function getValueType(val) {
  if (val === null || val === undefined) return 'null'
  if (Array.isArray(val)) return `array[${val.length}]`
  return typeof val
}

function isComplexValue(val) {
  if (val === null || val === undefined) return false
  return typeof val === 'object'
}

function formatVarValue(val) {
  if (val === null || val === undefined) return 'null'
  try { return JSON.stringify(val, null, 2) } catch { return String(val) }
}

function getValuePreview(val) {
  if (val === null || val === undefined) return 'null'
  if (typeof val === 'string') return val.length > 120 ? val.slice(0, 120) + '...' : val
  if (typeof val === 'number' || typeof val === 'boolean') return String(val)
  if (Array.isArray(val)) return `[${val.length} 项]`
  if (typeof val === 'object') return `{${Object.keys(val).length} 个字段}`
  return String(val)
}

function getNodeTypeName(type) {
  const map = {
    start: '开始', end: '结束', llm: '大模型', condition: '条件判断',
    retrieval: '知识检索', tool: '工具调用', classifier: '意图分类',
    api: 'API', loop: '循环', variable: '变量', batch: '批处理',
    script: '脚本', mcp: 'MCP', input: '输入', confirm: '人工确认', output: '输出',
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
.test-mode-hint { font-size: 12px; color: var(--color-mute); margin-bottom: 12px; line-height: 1.5; }
.test-chat-box {
  max-height: 220px;
  overflow-y: auto;
  margin-bottom: 12px;
  padding: 10px;
  background: var(--color-canvas-soft);
  border: 1px solid var(--color-hairline);
  border-radius: 8px;
}
.test-chat-empty { font-size: 12px; color: var(--color-mute); text-align: center; padding: 16px 0; }
.test-chat-msg { margin-bottom: 10px; }
.test-chat-msg.user .test-chat-content { background: var(--color-info-bg); }
.test-chat-msg.assistant .test-chat-content { background: var(--color-canvas); border: 1px solid var(--color-hairline); }
.test-chat-role { font-size: 11px; color: var(--color-mute); display: block; margin-bottom: 4px; }
.test-chat-content { font-size: 13px; color: var(--color-text-dark); padding: 8px 10px; border-radius: 6px; white-space: pre-wrap; word-break: break-word; }
.test-actions { display: flex; gap: 8px; flex-wrap: wrap; }
.test-current-node {
  font-size: 13px;
  color: var(--color-link);
  margin-bottom: 12px;
  padding: 8px 12px;
  background: var(--color-info-bg);
  border-radius: 6px;
}
.test-output {
  background: var(--color-canvas-soft);
  padding: 12px;
  border-radius: 6px;
  white-space: pre-wrap;
  font-size: 12px;
}
.trace-steps { display: flex; flex-direction: column; gap: 6px; }
.trace-step { border: 1px solid var(--color-hairline); border-radius: 6px; background: var(--color-canvas); }
.trace-step.trace-active { border-color: #818cf8; background: var(--color-info-bg); }
.trace-step-head {
  display: flex; align-items: center; gap: 8px;
  padding: 8px 10px; cursor: pointer; user-select: none;
}
.trace-step-head:hover { background: var(--color-canvas-soft); }
.trace-step-icon { flex-shrink: 0; font-size: 13px; }
.trace-icon-done { color: #22c55e; }
.trace-icon-fail { color: #dc2626; }
.trace-icon-run { color: var(--color-link); }
.trace-step-label { flex: 1; min-width: 0; font-size: 13px; color: var(--color-text-dark); }
.trace-step-label strong { font-weight: 600; }
.trace-step-type { margin-left: 6px; font-size: 11px; font-weight: normal; color: var(--color-mute); }
.trace-step-duration { flex-shrink: 0; font-size: 11px; color: var(--color-mute); font-variant-numeric: tabular-nums; }
.trace-running { color: var(--color-link); }
.trace-toggle {
  flex-shrink: 0; font-size: 12px; color: var(--color-mute);
  transition: transform 0.2s; display: inline-block;
}
.trace-toggle.expanded { transform: rotate(90deg); }
.trace-step-body { padding: 0 10px 10px; }
.trace-msg { font-size: 12px; color: var(--color-mute); margin-top: 4px; }
.trace-msg.trace-fail { color: #dc2626; }
.trace-detail {
  margin-top: 6px; padding: 8px; background: var(--color-canvas-soft);
  border: 1px solid var(--color-border-slate); border-radius: 6px; max-height: 160px; overflow: auto;
}
.trace-detail pre { margin: 0; font-size: 12px; line-height: 1.45; color: var(--color-text-dark); white-space: pre-wrap; word-break: break-word; font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace; }
.trace-kv {
  margin-top: 6px; padding: 8px; border-radius: 6px;
  border: 1px solid #ede9fe; background: #faf5ff;
}
.trace-kv-title { margin-bottom: 4px; font-size: 12px; font-weight: 600; color: #6d28d9; }
.trace-kv pre { margin: 0; font-size: 12px; line-height: 1.45; color: #4c1d95; white-space: pre-wrap; word-break: break-word; font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace; }
.trace-meta { margin-top: 4px; font-size: 11px; color: var(--color-mute); font-family: ui-monospace, monospace; }

/* 容器内部子节点 */
.trace-container-children {
  margin-left: 18px;
  padding-left: 10px;
  border-left: 2px solid #e9d5ff;
  margin-top: 4px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding-bottom: 6px;
}
.trace-child-step { background: #faf5ff; border-color: #f3e8ff; }
.trace-child-step .trace-step-head { padding: 5px 8px; }
.trace-child-step .trace-step-body { padding: 0 8px 8px; }
.trace-child-count {
  margin-left: 6px;
  font-size: 11px;
  font-weight: normal;
  color: #7c3aed;
  background: var(--color-purple-bg);
  padding: 1px 6px;
  border-radius: 8px;
}
.trace-iteration-tag {
  margin-left: 4px;
  font-size: 10px;
  font-weight: normal;
  color: var(--color-link);
  background: var(--color-info-bg);
  padding: 1px 5px;
  border-radius: 6px;
}

/* 结果 Tab 切换 */
.result-tab-header {
  display: flex; gap: 16px; margin: 12px 0 8px;
}
.result-tab-header h4 {
  font-size: 13px; font-weight: 500; color: var(--color-mute); cursor: pointer; margin: 0; padding-bottom: 4px; border-bottom: 2px solid transparent; transition: all 0.2s;
}
.result-tab-header h4.active { color: var(--color-link); border-bottom-color: var(--color-link); }
.result-tab-header h4:hover { color: #4f46e5; }
.var-count {
  display: inline-block; margin-left: 4px; font-size: 10px; font-weight: 600; color: var(--color-link); background: var(--color-info-bg); padding: 0 5px; border-radius: 8px; line-height: 16px;
}

/* 变量面板 */
.vars-panel { display: flex; flex-direction: column; gap: 4px; }
.vars-empty { font-size: 12px; color: var(--color-mute); text-align: center; padding: 20px 0; }
.var-item {
  border: 1px solid var(--color-hairline); border-radius: 6px; background: var(--color-canvas); overflow: hidden;
}
.var-item.expanded { border-color: #c7d2fe; }
.var-item-head {
  display: flex; align-items: center; gap: 8px; padding: 6px 10px; cursor: pointer; user-select: none;
}
.var-item-head:hover { background: var(--color-canvas-soft); }
.var-key { font-size: 13px; font-weight: 600; color: var(--color-text-dark); font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace; }
.var-type {
  font-size: 10px; color: var(--color-link); background: var(--color-info-bg); padding: 1px 6px; border-radius: 8px; flex-shrink: 0;
}
.var-toggle {
  flex-shrink: 0; font-size: 12px; color: var(--color-mute); transition: transform 0.2s; display: inline-block; margin-left: auto;
}
.var-toggle.expanded { transform: rotate(90deg); }
.var-item-body { padding: 0 10px 10px; }
.var-value-json {
  margin: 0; font-size: 12px; line-height: 1.45; color: var(--color-text-dark); background: var(--color-canvas-soft); border: 1px solid var(--color-border-slate); border-radius: 6px; padding: 8px; white-space: pre-wrap; word-break: break-word; max-height: 200px; overflow: auto; font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}
.var-value-text {
  font-size: 13px; color: var(--color-text-dark); line-height: 1.5; white-space: pre-wrap; word-break: break-word;
}
.var-preview {
  padding: 0 10px 6px; font-size: 12px; color: var(--color-mute); overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
</style>
