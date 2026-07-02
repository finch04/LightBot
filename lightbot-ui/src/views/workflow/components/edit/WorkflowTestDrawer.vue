<template>
  <a-drawer
    v-model:open="open"
    title="测试运行"
    :width="640"
    :mask-closable="!testRunning && (!testAnimating || viewingHistory)"
    :keyboard="!testRunning && (!testAnimating || viewingHistory)"
    @close="$emit('close')"
  >
    <a-segmented
      v-model:value="drawerTab"
      :options="[
        { label: '当前运行', value: 'current' },
        { label: '历史记录', value: 'history' },
      ]"
      block
      class="drawer-main-tab"
    />

    <template v-if="drawerTab === 'current'">
      <a-alert v-if="viewingHistory" type="info" show-icon message="正在查看历史测试记录" class="test-alert" />
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
          <a-button v-if="viewingHistory" @click="$emit('back-to-live')">返回当前</a-button>
        </div>
      </a-form>

      <a-divider v-if="testResult || testAnimating" />
      <div v-if="testAnimating && testCurrentNodeId" class="test-current-node">
        当前节点：<strong>{{ getNodeTitleById(testCurrentNodeId) }}</strong>
      </div>
      <div v-if="testResult">
        <WorkflowConfirmForm
          v-if="testPendingConfirm?.confirmForm && !viewingHistory"
          :confirm-form="testPendingConfirm.confirmForm"
          :submitting="testRunning || testAnimating"
          @submit="formData => $emit('resume', formData)"
        />
        <a-alert
          v-else-if="viewingHistory && testResult.suspended"
          type="warning"
          show-icon
          message="该记录处于挂起状态"
          description="人工确认详情见节点轨迹；历史记录无法继续恢复执行，请重新测试。"
          class="test-alert"
        />
        <h4 v-if="!testPendingConfirm || viewingHistory">输出结果</h4>
        <pre v-if="!testPendingConfirm || viewingHistory" class="test-output">{{ testResult.output || '（无输出）' }}</pre>
        <div class="result-tab-header">
          <h4 :class="{ active: resultTab === 'trace' }" @click="resultTab = 'trace'">节点轨迹</h4>
          <h4 :class="{ active: resultTab === 'variables' }" @click="resultTab = 'variables'">
            变量面板
            <span v-if="variableCount > 0" class="var-count">{{ variableCount }}</span>
          </h4>
        </div>
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
        <WorkflowTestTimeline
          v-if="resultTab === 'trace'"
          :node-events="testResult.nodeEvents || []"
          :active-node-id="testCurrentNodeId"
          @select-node="nodeId => $emit('select-node', nodeId)"
        />
      </div>
    </template>

    <template v-else>
      <div class="history-toolbar">
        <span class="history-count">最近 {{ historyList.length }} 条</span>
        <a-popconfirm title="确定清空全部测试历史？" @confirm="$emit('clear-history')">
          <a-button size="small" danger :disabled="!historyList.length || historyLoading">清空历史</a-button>
        </a-popconfirm>
      </div>
      <a-spin :spinning="historyLoading">
        <div v-if="!historyList.length" class="history-empty">暂无测试记录，运行一次测试后会自动保存</div>
        <div v-else class="history-list">
          <div
            v-for="item in historyList"
            :key="item.runId"
            class="history-item"
            :class="{ active: item.runId === selectedHistoryRunId }"
            @click="$emit('open-history-run', item.runId)"
          >
            <div class="history-item-head">
              <a-tag :color="statusColor(item.status)" size="small">{{ formatTestStatus(item.status) }}</a-tag>
              <span class="history-time">{{ formatTime(item.startTime) }}</span>
              <span class="history-duration">{{ formatTestDuration(item.durationMs) }}</span>
              <a-button type="text" size="small" danger @click.stop="$emit('delete-history', item.runId)">删除</a-button>
            </div>
            <div class="history-input">{{ item.userInputSummary || '（无输入）' }}</div>
            <div class="history-meta">
              <span>{{ item.testMode === 'conversation' ? '对话' : '生成' }}</span>
              <span>{{ item.usedDraft ? '草稿' : '已发布' }}</span>
            </div>
          </div>
        </div>
      </a-spin>
    </template>
  </a-drawer>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import WorkflowConfirmForm from '../../../../components/WorkflowConfirmForm.vue'
import WorkflowTestTimeline from '../WorkflowTestTimeline.vue'
import { formatTestStatus, formatTestDuration } from '../../composables/useWorkflowNodeSteps.js'

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
  historyList: { type: Array, default: () => [] },
  historyLoading: Boolean,
  selectedHistoryRunId: { type: String, default: null },
  viewingHistory: Boolean,
})

defineEmits([
  'close', 'run', 'resume', 'clear-conversation', 'select-node',
  'open-history-run', 'delete-history', 'clear-history', 'back-to-live',
  'update:testMode', 'update:testInput', 'update:testUseDraft',
])

const open = defineModel('open', { type: Boolean, default: false })

const drawerTab = ref('current')
const resultTab = ref('trace')
const expandedVars = ref(new Set())

const variableCount = computed(() => {
  if (!props.testResult?.variables) return 0
  return Object.keys(props.testResult.variables).length
})

watch(() => props.testResult, () => {
  resultTab.value = 'trace'
  expandedVars.value = new Set()
})

watch(open, (val) => {
  if (val) drawerTab.value = 'current'
})

watch(() => props.viewingHistory, (val) => {
  if (val) drawerTab.value = 'current'
})

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

function statusColor(status) {
  const map = { completed: 'success', failed: 'error', suspended: 'warning', running: 'processing' }
  return map[status] || 'default'
}

function formatTime(time) {
  if (!time) return '-'
  const d = new Date(time)
  if (Number.isNaN(d.getTime())) return String(time)
  const pad = n => String(n).padStart(2, '0')
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
</script>

<style scoped>
.drawer-main-tab { margin-bottom: 12px; }
.test-alert { margin-bottom: 12px; }
.test-mode-segment { margin-bottom: 8px; }
.test-mode-hint { font-size: 12px; color: var(--color-mute); margin-bottom: 12px; line-height: 1.5; }
.test-chat-box {
  max-height: 220px; overflow-y: auto; margin-bottom: 12px; padding: 10px;
  background: var(--color-canvas-soft); border: 1px solid var(--color-hairline); border-radius: 8px;
}
.test-chat-empty { font-size: 12px; color: var(--color-mute); text-align: center; padding: 16px 0; }
.test-chat-msg { margin-bottom: 10px; }
.test-chat-msg.user .test-chat-content { background: var(--color-info-bg); }
.test-chat-msg.assistant .test-chat-content { background: var(--color-canvas); border: 1px solid var(--color-hairline); }
.test-chat-role { font-size: 11px; color: var(--color-mute); display: block; margin-bottom: 4px; }
.test-chat-content { font-size: 13px; color: var(--color-text-dark); padding: 8px 10px; border-radius: 6px; white-space: pre-wrap; word-break: break-word; }
.test-actions { display: flex; gap: 8px; flex-wrap: wrap; }
.test-current-node {
  font-size: 13px; color: var(--color-link); margin-bottom: 12px; padding: 8px 12px;
  background: var(--color-info-bg); border-radius: 6px;
}
.test-output {
  background: var(--color-canvas-soft); padding: 12px; border-radius: 6px;
  white-space: pre-wrap; font-size: 12px; margin-bottom: 8px;
}
.result-tab-header { display: flex; gap: 16px; margin: 12px 0 8px; }
.result-tab-header h4 {
  font-size: 13px; font-weight: 500; color: var(--color-mute); cursor: pointer; margin: 0;
  padding-bottom: 4px; border-bottom: 2px solid transparent; transition: all 0.2s;
}
.result-tab-header h4.active { color: var(--color-link); border-bottom-color: var(--color-link); }
.var-count {
  display: inline-block; margin-left: 4px; font-size: 10px; font-weight: 600; color: var(--color-link);
  background: var(--color-info-bg); padding: 0 5px; border-radius: 8px; line-height: 16px;
}
.vars-panel { display: flex; flex-direction: column; gap: 4px; }
.vars-empty { font-size: 12px; color: var(--color-mute); text-align: center; padding: 20px 0; }
.var-item { border: 1px solid var(--color-hairline); border-radius: 6px; background: var(--color-canvas); overflow: hidden; }
.var-item.expanded { border-color: #c7d2fe; }
.var-item-head { display: flex; align-items: center; gap: 8px; padding: 6px 10px; cursor: pointer; user-select: none; }
.var-item-head:hover { background: var(--color-canvas-soft); }
.var-key { font-size: 13px; font-weight: 600; color: var(--color-text-dark); font-family: ui-monospace, monospace; }
.var-type { font-size: 10px; color: var(--color-link); background: var(--color-info-bg); padding: 1px 6px; border-radius: 8px; }
.var-toggle { flex-shrink: 0; font-size: 12px; color: var(--color-mute); transition: transform 0.2s; margin-left: auto; }
.var-toggle.expanded { transform: rotate(90deg); }
.var-item-body { padding: 0 10px 10px; }
.var-value-json {
  margin: 0; font-size: 12px; background: var(--color-canvas-soft); border: 1px solid var(--color-border-slate);
  border-radius: 6px; padding: 8px; white-space: pre-wrap; word-break: break-word; max-height: 200px; overflow: auto;
  font-family: ui-monospace, monospace;
}
.var-value-text { font-size: 13px; line-height: 1.5; white-space: pre-wrap; word-break: break-word; }
.var-preview { padding: 0 10px 6px; font-size: 12px; color: var(--color-mute); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.history-toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.history-count { font-size: 12px; color: var(--color-mute); }
.history-empty { font-size: 13px; color: var(--color-mute); text-align: center; padding: 40px 0; }
.history-list { display: flex; flex-direction: column; gap: 8px; }
.history-item {
  border: 1px solid var(--color-hairline); border-radius: 8px; padding: 10px 12px; cursor: pointer;
  background: var(--color-canvas); transition: border-color 0.15s, background 0.15s;
}
.history-item:hover { border-color: #c7d2fe; background: var(--color-canvas-soft); }
.history-item.active { border-color: #818cf8; background: var(--color-info-bg); }
.history-item-head { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.history-time { font-size: 12px; color: var(--color-mute); }
.history-duration { font-size: 12px; color: var(--color-mute); margin-left: auto; margin-right: 4px; }
.history-input { font-size: 13px; color: var(--color-text-dark); line-height: 1.5; word-break: break-word; }
.history-meta { display: flex; gap: 10px; margin-top: 6px; font-size: 11px; color: var(--color-mute); }
</style>
