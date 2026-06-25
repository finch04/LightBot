<template>
  <div class="short-memory-form">
    <div class="sm-header">
      <span class="sm-title">记忆
        <a-tooltip placement="topLeft">
          <template #title>
            <div style="max-width: 300px; line-height: 1.6;">
              <b>关闭时</b>：大模型仅根据当前输入和提示词回复，不携带任何历史对话。<br><br>
              <b>开启后</b>：自动将近期对话历史注入大模型上下文，实现多轮对话记忆。<br><br>
              <b>记忆类型</b>：<br>
              · <b>本节点缓存</b>：使用系统自动注入的会话历史（history_list）<br>
              · <b>自定义缓存</b>：使用工作流中自定义变量作为历史上下文<br><br>
              <b>记忆轮次</b>：控制保留最近几轮对话（1轮 = 1条用户消息 + 1条AI回复），设为 5 则保留最近 10 条消息。
            </div>
          </template>
          <QuestionCircleOutlined class="sm-help-icon" />
        </a-tooltip>
      </span>
      <a-switch v-model:checked="local.enabled" :disabled="disabled" @change="emitChange" />
    </div>
    <template v-if="local.enabled">
      <a-form-item label="记忆类型">
        <a-select v-model:value="local.type" :disabled="disabled" @change="emitChange">
          <a-select-option value="self">本节点缓存 — 仅记住本节点内上下文</a-select-option>
          <a-select-option value="custom">自定义缓存 — 使用全局上下文变量</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item v-if="local.type === 'self'" label="记忆轮次">
        <a-slider v-model:value="local.round" :min="1" :max="50" :step="1" :disabled="disabled" @change="emitChange" />
        <span class="param-value">{{ local.round }}</span>
      </a-form-item>
      <a-form-item v-else label="上下文变量">
        <a-input v-model:value="local.paramKey" :disabled="disabled" placeholder="变量名，如 history" @change="emitChange" />
      </a-form-item>
    </template>
  </div>
</template>

<script setup>
import { reactive, watch } from 'vue'
import { SHORT_MEMORY_DEFAULT } from '../nodeMeta'
import { QuestionCircleOutlined } from '@ant-design/icons-vue'

const props = defineProps({
  modelValue: { type: Object, default: () => ({ ...SHORT_MEMORY_DEFAULT }) },
  disabled: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue'])

const local = reactive({ ...SHORT_MEMORY_DEFAULT, ...props.modelValue })

watch(() => props.modelValue, (val) => {
  Object.assign(local, SHORT_MEMORY_DEFAULT, val || {})
}, { deep: true })

function emitChange() {
  if (props.disabled) return
  emit('update:modelValue', { ...local })
}
</script>

<style scoped>
.short-memory-form {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 12px;
  background: var(--color-canvas-soft);
}
.sm-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.sm-title {
  font-weight: 600;
  font-size: 13px;
}
.sm-help-icon {
  margin-left: 4px;
  font-size: 13px;
  color: #9ca3af;
  cursor: pointer;
}
.sm-help-icon:hover {
  color: #6366f1;
}
.param-value {
  margin-left: 8px;
  font-size: 12px;
  color: #6b7280;
}
</style>
