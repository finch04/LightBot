<template>
  <div v-if="span" class="workflow-node-execution">
    <a-divider orientation="left" class="exec-divider">本次执行</a-divider>
    <div class="exec-summary">
      <div class="exec-item">
        <span class="exec-key">状态</span>
        <a-tag :color="span.status === 'completed' ? 'success' : 'error'" size="small">
          {{ span.status === 'completed' ? '成功' : '失败' }}
        </a-tag>
      </div>
      <div class="exec-item">
        <span class="exec-key">耗时</span>
        <span class="exec-val">{{ formatDuration(span.durationMs) }}</span>
      </div>
      <div v-if="span.attributes?.message" class="exec-item exec-item-full">
        <span class="exec-key">消息</span>
        <span class="exec-val">{{ span.attributes.message }}</span>
      </div>
    </div>

    <div v-if="span.attributes?.input && Object.keys(span.attributes.input).length" class="exec-block">
      <div class="exec-block-title">输入参数</div>
      <pre class="exec-json">{{ JSON.stringify(span.attributes.input, null, 2) }}</pre>
    </div>
    <div v-if="span.attributes?.outputs && Object.keys(span.attributes.outputs).length" class="exec-block">
      <div class="exec-block-title">输出结果</div>
      <pre class="exec-json">{{ JSON.stringify(filterOutputs(span.attributes.outputs), null, 2) }}</pre>
    </div>
    <div v-if="llmMessagesList?.length" class="exec-block">
      <div class="exec-block-title">LLM 上下文</div>
      <div class="llm-messages-list">
        <div v-for="(msg, mi) in llmMessagesList" :key="mi" class="llm-msg-item" :class="'llm-msg-' + msg.role">
          <span class="llm-msg-role">{{ msg.role }}</span>
          <pre class="llm-msg-content">{{ msg.content }}</pre>
        </div>
      </div>
    </div>
    <div v-if="span.attributes?.detail" class="exec-block">
      <div class="exec-block-title">执行详情</div>
      <div class="exec-text">{{ span.attributes.detail }}</div>
    </div>
    <div v-if="llmSpan" class="exec-block">
      <div class="exec-block-title">LLM 调用</div>
      <pre class="exec-json">{{ formatLlmAttrs(llmSpan.attributes) }}</pre>
    </div>
  </div>
  <a-alert v-else-if="showEmptyHint" type="warning" show-icon message="该节点在本次运行中未执行" class="exec-empty" />
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  span: { type: Object, default: null },
  llmSpan: { type: Object, default: null },
  showEmptyHint: { type: Boolean, default: false },
})

const llmMessagesList = computed(() => {
  const msgs = props.span?.attributes?.outputs?.llmMessages
  return Array.isArray(msgs) && msgs.length ? msgs : null
})

function filterOutputs(outputs) {
  if (!outputs) return {}
  const filtered = { ...outputs }
  delete filtered.llmMessages
  return filtered
}

function formatLlmAttrs(attrs) {
  if (!attrs) return '{}'
  const filtered = { ...attrs }
  for (const k of Object.keys(filtered)) {
    if (filtered[k] === 0 || filtered[k] === '' || filtered[k] == null) {
      delete filtered[k]
    }
  }
  return JSON.stringify(filtered, null, 2)
}

function formatDuration(ms) {
  if (!ms && ms !== 0) return '-'
  if (ms < 1000) return ms + 'ms'
  return (ms / 1000).toFixed(1) + 's'
}
</script>

<style scoped>
.workflow-node-execution {
  margin-top: 8px;
}
.exec-divider {
  margin: 16px 0 12px;
  font-size: 13px;
  font-weight: 600;
}
.exec-summary {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  margin-bottom: 4px;
}
.exec-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}
.exec-item-full {
  grid-column: 1 / -1;
  align-items: flex-start;
}
.exec-key {
  color: var(--color-mute);
  min-width: 36px;
}
.exec-val {
  flex: 1;
  word-break: break-word;
}
.exec-block {
  margin-top: 12px;
}
.exec-block-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-body);
  margin-bottom: 6px;
}
.exec-json {
  background: var(--color-canvas-soft);
  border: 1px solid var(--color-hairline);
  border-radius: 6px;
  padding: 10px 12px;
  font-size: 11px;
  line-height: 1.5;
  max-height: 240px;
  overflow: auto;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
}
.exec-text {
  background: var(--color-canvas-soft);
  border: 1px solid var(--color-hairline);
  border-radius: 6px;
  padding: 10px 12px;
  font-size: 12px;
  line-height: 1.6;
  max-height: 200px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-all;
}
.exec-empty {
  margin-top: 16px;
}
.llm-messages-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.llm-msg-item {
  border-radius: 6px;
  padding: 8px 12px;
  border: 1px solid var(--color-hairline);
}
.llm-msg-system { background: var(--color-warn-bg); }
.llm-msg-user { background: #e6f7ff; border-color: #91d5ff; }
.llm-msg-assistant { background: var(--color-success-bg); border-color: #b7eb8f; }
.llm-msg-role {
  display: inline-block;
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  margin-bottom: 4px;
  padding: 1px 6px;
  border-radius: 3px;
  background: rgba(0, 0, 0, 0.06);
}
.llm-msg-content {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 300px;
  overflow: auto;
}
</style>
