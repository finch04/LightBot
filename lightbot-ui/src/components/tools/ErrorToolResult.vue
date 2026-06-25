<template>
  <div class="error-tool-result">
    <div class="etr-header">
      <CloseCircleOutlined class="etr-icon" />
      <span class="etr-label">{{ displayName }} 执行失败</span>
    </div>
    <div class="etr-body">
      <div class="etr-message">{{ message }}</div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { CloseCircleOutlined } from '@ant-design/icons-vue'
import { getToolDisplayName } from '../toolRegistry'

const props = defineProps({
  event: { type: Object, required: true },
  message: { type: String, default: '未知错误' },
})

const displayName = computed(() => getToolDisplayName(props.event.toolName))
</script>

<style lang="less" scoped>
.error-tool-result {
  border: 1px solid var(--color-error-soft);
  border-left: 3px solid #ef4444;
  border-radius: 8px;
  overflow: hidden;
  background: var(--color-error-bg);
}

.etr-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  border-bottom: 1px solid #fecaca;
  background: var(--color-error-bg);

  .etr-icon {
    color: #ef4444;
    font-size: 14px;
    flex-shrink: 0;
  }

  .etr-label {
    font-size: 12px;
    font-weight: 600;
    color: #991b1b;
  }
}

.etr-body {
  padding: 10px 12px;

  .etr-message {
    font-size: 12px;
    line-height: 1.6;
    color: #7f1d1d;
    white-space: pre-wrap;
    word-break: break-word;
    font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  }
}
</style>
