<template>
  <a-drawer
    :open="open"
    :title="drawerTitle"
    width="520"
    destroy-on-close
    class="node-single-test-drawer"
    :closable="!loading"
    :mask-closable="!loading"
    :keyboard="!loading"
    @close="onDrawerClose"
  >
    <div v-if="node" class="node-test-body">
      <a-alert
        type="info"
        show-icon
        message="单节点测试"
        description="仅执行当前节点逻辑，不跑完整工作流；请填写节点依赖的输入变量。"
        class="test-tip"
      />

      <section class="test-section">
        <div class="section-title">输入变量</div>
        <div v-if="!inputs.length" class="test-empty">
          <a-empty description="该节点无需额外输入，可直接运行" />
        </div>
        <div v-else class="test-input-list">
          <div v-for="item in inputs" :key="item.key" class="test-input-item">
            <div class="var-meta">
              <code class="var-name">{{ item.key }}</code>
              <span v-if="item.label && item.label !== item.key" class="var-hint">{{ item.label }}</span>
            </div>
            <a-textarea v-model:value="item.value" :rows="3" placeholder="填写测试值" :disabled="loading" />
          </div>
        </div>
      </section>

      <div class="test-actions">
        <a-button type="primary" :loading="loading" @click="runTest">
          {{ loading ? '运行中...' : '运行' }}
        </a-button>
        <a-button :disabled="loading" @click="resetInputs">重置</a-button>
      </div>

      <section v-if="hasResult" class="test-section test-result-section">
        <div class="result-status-bar" :class="statusClass">
          <component :is="statusIcon" class="status-icon" />
          <span class="status-text">{{ statusText }}</span>
          <span v-if="durationMs != null" class="status-duration">{{ durationMs }}ms</span>
        </div>

        <div class="result-block" :class="{ 'result-block-error': !lastSuccess }">
          <div class="result-block-title">{{ lastSuccess ? '输出' : '错误信息' }}</div>
          <pre
            class="result-pre code-block-scroll code-block-scroll--dark"
            :class="{ 'result-pre-error': !lastSuccess }"
          >{{ outputText }}</pre>
        </div>

        <div v-if="lastSuccess && detailText" class="result-block">
          <div class="result-block-title">详情</div>
          <pre class="result-pre result-pre-muted code-block-scroll code-block-scroll--dark">{{ detailText }}</pre>
        </div>
      </section>
    </div>
  </a-drawer>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { message } from 'ant-design-vue'
import {
  CheckCircleFilled,
  CloseCircleFilled,
  LoadingOutlined,
} from '@ant-design/icons-vue'
import { testWorkflowNode } from '../../../api/workflow'
import { buildNodeTestInputs } from '../workflowNodeTest'

const props = defineProps({
  open: { type: Boolean, default: false },
  agentId: { type: [String, Number], required: true },
  node: { type: Object, default: null },
  graphPayload: { type: Object, default: null },
})

const emit = defineEmits(['update:open', 'test-complete'])

const inputs = ref([])
const loading = ref(false)
const resultData = ref(null)
const lastSuccess = ref(true)
const statusText = ref('')
const durationMs = ref(null)
const outputText = ref('')
const detailText = ref('')

const drawerTitle = computed(() => {
  const label = props.node?.data?.label || props.node?.type || '节点'
  return `测试运行 · ${label}`
})

const hasResult = computed(() => resultData.value != null && !loading.value)

const statusClass = computed(() => ({
  success: lastSuccess.value,
  fail: !lastSuccess.value,
}))

const statusIcon = computed(() => {
  if (loading.value) return LoadingOutlined
  return lastSuccess.value ? CheckCircleFilled : CloseCircleFilled
})

function onDrawerClose() {
  if (loading.value) return
  emit('update:open', false)
}

function resetResultState() {
  resultData.value = null
  lastSuccess.value = true
  statusText.value = ''
  durationMs.value = null
  outputText.value = ''
  detailText.value = ''
}

function formatSuccessOutput(data, complete) {
  const raw = data?.output
  if (raw != null && String(raw).trim() && String(raw).trim() !== '执行完成') {
    return String(raw).trim()
  }
  const outputs = complete?.outputs
  if (outputs && typeof outputs === 'object') {
    try {
      return JSON.stringify(outputs, null, 2)
    } catch {
      return String(outputs)
    }
  }
  if (complete?.detail && String(complete.detail).trim()) {
    return String(complete.detail).trim()
  }
  return ''
}

function applyResultPayload(data) {
  if (!data) {
    lastSuccess.value = false
    statusText.value = '执行失败'
    outputText.value = '未收到有效响应'
    detailText.value = ''
    return
  }
  resultData.value = data
  const events = Array.isArray(data.nodeEvents) ? data.nodeEvents : []
  const complete = [...events].reverse().find(e => e?.type === 'workflow_node_complete')
  if (!complete) {
    lastSuccess.value = false
    statusText.value = '执行失败'
    durationMs.value = null
    outputText.value = '未收到节点执行结果，请检查网络或后端日志'
    detailText.value = ''
    return
  }
  lastSuccess.value = complete.success === true
  statusText.value = complete.message || (lastSuccess.value ? '运行成功' : '运行失败')
  durationMs.value = complete.durationMs != null ? Number(complete.durationMs) : null

  if (lastSuccess.value) {
    const formatted = formatSuccessOutput(data, complete)
    outputText.value = formatted || '（无输出）'
    const detail = complete.detail ? String(complete.detail).trim() : ''
    detailText.value = detail && detail !== formatted ? detail : ''
    return
  }

  const detail = complete.detail ? String(complete.detail).trim() : ''
  const formatted = formatSuccessOutput(data, complete)
  outputText.value = complete.message || detail || formatted || '节点执行失败'
  detailText.value = ''
}

watch(
  () => [props.open, props.node?.id],
  () => {
    if (!props.open || !props.node) return
    inputs.value = buildNodeTestInputs(props.node).map(i => ({ ...i }))
    resetResultState()
  },
  { immediate: true },
)

function resetInputs() {
  if (loading.value) return
  inputs.value = buildNodeTestInputs(props.node).map(i => ({ ...i }))
  resetResultState()
}

async function runTest() {
  if (!props.node?.id) return
  loading.value = true
  resetResultState()
  try {
    const inputParams = {}
    inputs.value.forEach(item => {
      inputParams[item.key] = item.value ?? ''
    })
    const res = await testWorkflowNode(props.agentId, {
      nodeId: props.node.id,
      graph: props.graphPayload,
      inputParams,
    })
    const data = res?.data != null ? res.data : res
    applyResultPayload(data)
    emit('test-complete', {
      nodeId: props.node.id,
      success: lastSuccess.value,
      result: data,
    })
    if (lastSuccess.value) {
      message.success('节点测试完成')
    } else {
      message.warning('节点测试未通过，请查看错误信息')
    }
  } catch (e) {
    lastSuccess.value = false
    statusText.value = '请求失败'
    durationMs.value = null
    outputText.value = e?.message || '测试请求失败'
    detailText.value = ''
    resultData.value = { nodeEvents: [{ success: false, message: outputText.value }] }
    emit('test-complete', { nodeId: props.node.id, success: false, result: null })
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.node-test-body {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.test-tip {
  margin-bottom: 0;
}
.test-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.section-title {
  font-size: 13px;
  font-weight: 600;
  line-height: 20px;
  color: var(--color-text-code);
}
.test-input-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.test-input-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.var-meta {
  display: flex;
  align-items: baseline;
  gap: 8px;
  flex-wrap: wrap;
}
.var-name {
  font-size: 13px;
  font-weight: 500;
  background: var(--color-canvas-soft);
  padding: 2px 8px;
  border-radius: 4px;
  color: var(--color-text-dark);
}
.var-hint {
  font-size: 12px;
  color: var(--color-mute);
}
.test-actions {
  display: flex;
  gap: 10px;
}
.test-result-section {
  padding-top: 16px;
  border-top: 1px solid var(--color-border-slate);
  gap: 16px;
}
.result-status-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  border-radius: 8px;
  background: var(--color-canvas-soft);
  border: 1px solid var(--color-border-slate);
}
.result-status-bar.success {
  background: var(--color-success-bg);
  border-color: #bbf7d0;
}
.result-status-bar.fail {
  background: var(--color-error-bg);
  border-color: #fecaca;
}
.status-icon {
  font-size: 18px;
  flex-shrink: 0;
}
.result-status-bar.success .status-icon {
  color: #16a34a;
}
.result-status-bar.fail .status-icon {
  color: #dc2626;
}
.status-text {
  flex: 1;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-ink);
}
.status-duration {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-mute);
  font-variant-numeric: tabular-nums;
  padding: 2px 10px;
  background: rgba(255, 255, 255, 0.7);
  border-radius: 999px;
  border: 1px solid rgba(0, 0, 0, 0.06);
}
.result-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.result-block-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-mute);
  letter-spacing: 0.02em;
}
.result-pre {
  margin: 0;
  padding: 14px 16px;
  border-radius: 8px;
  background: #0f172a;
  color: #e2e8f0;
  font-size: 12px;
  line-height: 1.6;
  max-height: 280px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
}
.result-pre-muted {
  background: #1e293b;
  color: #cbd5e1;
  max-height: 200px;
}
.result-pre-error {
  background: #450a0a;
  color: #fecaca;
  border: 1px solid #7f1d1d;
}
.result-block-error .result-block-title {
  color: #b91c1c;
}
.test-empty {
  padding: 8px 0;
}
</style>
