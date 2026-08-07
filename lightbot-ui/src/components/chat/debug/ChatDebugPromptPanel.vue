<template>
  <div class="debug-prompt-panel">
    <div class="debug-panel-toolbar">
      <a-button type="primary" @click="$emit('parse')">解析预览</a-button>
      <a-select
        v-model:value="selectedSample"
        :options="sampleOptions"
        style="width: 200px"
        @change="loadSample"
      />
      <a-button @click="formatJson">格式化 JSON</a-button>
    </div>

    <div class="debug-field">
      <div class="debug-editor-label">Prompt Fixture JSON</div>
      <a-textarea
        v-model:value="payloadJson"
        :rows="20"
        class="debug-json-textarea"
        @change="parseError = ''"
      />
    </div>

    <a-alert
      v-if="parseError"
      type="error"
      :message="parseError"
      show-icon
      closable
      class="debug-parse-error"
      @close="parseError = ''"
    />
  </div>
</template>

<script setup>
import { ref } from 'vue'

const emit = defineEmits(['parse'])

const sampleOptions = [
  { value: 'agent-system', label: 'Agent 系统提示词' },
  { value: 'summary', label: '摘要提示词' },
  { value: 'missing-vars', label: '缺失变量' },
]

const samples = {
  'agent-system': {
    title: '客服助手系统提示词',
    description: '验证 Mustache 变量替换、变量列表和渲染结果。',
    template: '你是 {{agentName}}，负责回答 {{domain}} 相关问题。\n\n语气：{{tone}}\n限制：不要泄露 {{secretName}}。',
    variables: [
      { key: 'agentName', label: '智能体名称', value: '智元 客服助手' },
      { key: 'domain', label: '业务领域', value: '知识库与工作流编排' },
      { key: 'tone', label: '回复语气', value: '清晰、克制、可执行' },
      { key: 'secretName', label: '敏感变量', value: '系统密钥' },
    ],
  },
  summary: {
    title: '会话摘要提示词',
    description: '验证长文本 Prompt 的只读预览。',
    template: '请将以下对话整理成结构化摘要：\n\n{{conversation}}\n\n输出字段：背景、结论、待办、风险。',
    variables: [
      { key: 'conversation', label: '对话内容', value: '用户询问批处理工作流无法运行，助手排查了脚本解析、父子节点和回放状态。' },
    ],
  },
  'missing-vars': {
    title: '缺失变量提示',
    description: '验证未赋值变量的展示样式。',
    template: '请根据 {{input}} 生成 {{format}}，并使用 {{language}} 输出。',
    variables: [
      { key: 'input', label: '输入', value: 'Debug Lab 扩展需求' },
      { key: 'format', label: '格式', value: '' },
    ],
  },
}

const selectedSample = ref('agent-system')
const payloadJson = ref('')
const parseError = ref('')

function loadSample() {
  payloadJson.value = JSON.stringify(samples[selectedSample.value], null, 2)
  parseError.value = ''
}

function formatJson() {
  const payload = validateAndGetPayload()
  if (!payload) return
  payloadJson.value = JSON.stringify(payload, null, 2)
}

function validateAndGetPayload() {
  parseError.value = ''
  try {
    const payload = JSON.parse(payloadJson.value)
    if (!payload || typeof payload !== 'object') throw new Error('Fixture 必须是 JSON 对象')
    if (!payload.template) throw new Error('template 不能为空')
    if (!Array.isArray(payload.variables)) payload.variables = []
    return payload
  } catch (e) {
    parseError.value = e.message || 'JSON 格式错误'
    return null
  }
}

loadSample()

defineExpose({ validateAndGetPayload, loadSample })
</script>

<style scoped>
.debug-prompt-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.debug-panel-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.debug-field {
  margin-bottom: 12px;
}

.debug-editor-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--gray-700);
  margin-bottom: 8px;
}

.debug-json-textarea {
  font-family: 'Menlo', 'Monaco', 'Consolas', monospace;
  font-size: 12px;
  line-height: 1.6;
}

.debug-parse-error {
  margin-top: 8px;
}
</style>
