<template>
  <div class="json-input-wrapper" :class="{ 'json-error': error }">
    <a-textarea
      :value="modelValue"
      @input="$emit('update:modelValue', $event.target.value)"
      :placeholder="placeholder"
      :rows="rows"
      spellcheck="false"
      class="json-textarea code-block-scroll"
    />
    <div class="json-input-actions">
      <WorkflowTooltip title="格式化 JSON" placement="top">
        <button type="button" class="json-btn" @click="formatJson" :disabled="!modelValue">
          <CodeOutlined />
        </button>
      </WorkflowTooltip>
      <WorkflowTooltip title="压缩 JSON" placement="top">
        <button type="button" class="json-btn" @click="compressJson" :disabled="!modelValue">
          <CompressOutlined />
        </button>
      </WorkflowTooltip>
    </div>
    <div v-if="error" class="json-error-msg">{{ error }}</div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { CodeOutlined, CompressOutlined } from '@ant-design/icons-vue'
import WorkflowTooltip from '../views/workflow/components/WorkflowTooltip.vue'

const props = defineProps({
  modelValue: { type: String, default: '{}' },
  placeholder: { type: String, default: 'JSON 格式' },
  rows: { type: Number, default: 3 },
})

const emit = defineEmits(['update:modelValue'])

const error = ref('')

function formatJson() {
  try {
    const parsed = JSON.parse(props.modelValue)
    emit('update:modelValue', JSON.stringify(parsed, null, 2))
    error.value = ''
  } catch (e) {
    error.value = 'JSON 格式错误: ' + e.message
  }
}

function compressJson() {
  try {
    const parsed = JSON.parse(props.modelValue)
    emit('update:modelValue', JSON.stringify(parsed))
    error.value = ''
  } catch (e) {
    error.value = 'JSON 格式错误: ' + e.message
  }
}

watch(() => props.modelValue, (val) => {
  if (!val || val === '{}' || val === '[]') {
    error.value = ''
    return
  }
  try {
    JSON.parse(val)
    error.value = ''
  } catch (e) {
    error.value = 'JSON 格式错误: ' + e.message
  }
})
</script>

<style scoped>
.json-input-wrapper {
  width: 100%;
  position: relative;
}
.json-input-wrapper :deep(.ant-input) {
  font-family: 'SF Mono', 'Menlo', 'Consolas', monospace;
  font-size: 13px;
  line-height: 1.5;
  padding-bottom: 32px;
}
.json-input-wrapper.json-error :deep(.ant-input) {
  border-color: #ef4444;
}
.json-input-actions {
  position: absolute;
  bottom: 4px;
  right: 4px;
  display: flex;
  gap: 4px;
  z-index: 1;
}
.json-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border: 1px solid #e4e4e7;
  border-radius: 4px;
  background: #fff;
  color: #71717a;
  cursor: pointer;
  font-size: 12px;
  transition: all 0.15s;
  box-shadow: 0 1px 2px rgba(0,0,0,0.05);
}
.json-btn:hover:not(:disabled) {
  border-color: #0070f3;
  color: #0070f3;
  background: #f0f7ff;
}
.json-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.json-error-msg {
  margin-top: 4px;
  font-size: 12px;
  color: #ef4444;
}
</style>
