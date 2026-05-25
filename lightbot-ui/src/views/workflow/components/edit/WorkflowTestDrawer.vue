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
      <div
        v-for="(ev, i) in testResult.nodeEvents"
        :key="i"
        class="test-event"
        :class="{ active: ev.nodeId === testCurrentNodeId }"
      >
        <span class="test-event-type">{{ ev.type === 'workflow_node_start' ? '▶' : ev.type === 'workflow_node_complete' ? '✓' : '•' }}</span>
        {{ ev.nodeLabel || ev.nodeId }} — {{ ev.message || ev.nodeType }}
      </div>
    </div>
  </a-drawer>
</template>

<script setup>
defineProps({
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
.test-event {
  font-size: 12px;
  color: #6b7280;
  padding: 4px 0;
}
.test-event.active {
  background: #eef2ff;
  color: #4338ca;
  font-weight: 600;
}
.test-event-type { margin-right: 6px; }
</style>
