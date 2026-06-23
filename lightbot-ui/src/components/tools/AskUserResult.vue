<template>
  <div>
    <div v-if="isPlainText" style="margin:0;padding:8px 10px;background:#fafafa;border-radius:6px;font-size:12px;line-height:1.5;color:#374151;white-space:pre-wrap;word-break:break-word;">
      <pre style="margin:0;">{{ displayText }}</pre>
    </div>

    <template v-else>
      <!-- 问题 -->
      <div style="display:flex;align-items:flex-start;gap:8px;padding:10px 12px;background:#dbeafe;border:1px solid #93c5fd;border-radius:8px;font-size:13px;line-height:1.6;color:#1e40af;margin-bottom:8px;">
        <QuestionCircleOutlined style="color:#2563eb;font-size:15px;margin-top:2px;flex-shrink:0;" />
        <span style="font-weight:500;">{{ data.question }}</span>
      </div>

      <!-- 选项列表 -->
      <div v-if="data.options?.length" style="display:flex;flex-direction:column;gap:6px;margin-bottom:8px;">
        <div v-for="(opt, i) in data.options" :key="i"
          style="display:flex;align-items:center;gap:8px;padding:8px 12px;background:#eff6ff;border:1px solid #93c5fd;border-radius:8px;font-size:12px;color:#1f2937;cursor:default;transition:border-color 0.2s;">
          <span style="display:inline-flex;align-items:center;justify-content:center;min-width:22px;height:22px;background:#3b82f6;color:#fff;border-radius:50%;font-size:11px;font-weight:600;flex-shrink:0;">{{ i + 1 }}</span>
          <span style="flex:1;line-height:1.5;">{{ opt }}</span>
        </div>
      </div>

      <!-- 状态提示 -->
      <div v-if="data.is_open_ended || !answered" style="display:flex;align-items:center;gap:6px;padding:8px 12px;border-radius:8px;font-size:12px;"
        :style="answered ? 'background:#f0fdf4;border:1px solid #86efac;color:#166534' : 'background:#fefce8;border:1px solid #fde68a;color:#92400e'">
        <CheckCircleOutlined v-if="answered" style="color:#22c55e;font-size:13px;flex-shrink:0;" />
        <ClockCircleOutlined v-else style="color:#f59e0b;font-size:13px;flex-shrink:0;" />
        <span>{{ answered ? '用户已回答' : '等待用户回答...' }}</span>
      </div>

      <!-- 未回答：回答按钮 -->
      <div v-if="!answered" style="margin-top:8px;">
        <button @click="handleAnswer"
          style="display:inline-flex;align-items:center;gap:6px;padding:8px 18px;background:#0070f3;color:#fff;border:none;border-radius:8px;font-size:13px;font-weight:500;cursor:pointer;transition:background 0.15s;"
          onmouseover="this.style.background='#005bc4'"
          onmouseout="this.style.background='#0070f3'">
          <QuestionCircleOutlined style="font-size:14px;" />
          回答
        </button>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, inject } from 'vue'
import { QuestionCircleOutlined, CheckCircleOutlined, ClockCircleOutlined } from '@ant-design/icons-vue'

const props = defineProps({
  event: { type: Object, required: true },
  messageIndex: { type: Number, default: -1 }
})

const showAskUserModal = inject('showAskUserModal', null)
const isAskUserUnanswered = inject('isAskUserUnanswered', null)

const rawResult = computed(() => props.event.result || '')
const data = computed(() => { try { return JSON.parse(rawResult.value) } catch { return null } })
const isPlainText = computed(() => !data.value || typeof data.value !== 'object')
const displayText = computed(() => typeof data.value === 'string' ? data.value : rawResult.value)

const answered = computed(() => {
  if (!isAskUserUnanswered || props.messageIndex < 0) return true
  return !isAskUserUnanswered(props.messageIndex)
})

function handleAnswer() {
  if (showAskUserModal && props.messageIndex >= 0) {
    showAskUserModal(props.messageIndex)
  }
}
</script>
