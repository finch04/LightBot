<template>
  <div class="execute-code-result">
    <div v-if="isPlainText" class="ecr-plain">
      <pre>{{ displayText }}</pre>
    </div>
    <template v-else>
      <!-- 状态栏 -->
      <div class="ecr-status" :class="data.success ? 'success' : 'error'">
        <span class="ecr-lang">{{ data.language || 'unknown' }}</span>
        <span v-if="data.elapsedMs" class="ecr-elapsed">{{ data.elapsedMs }}ms</span>
        <span v-if="data.success" class="ecr-badge success">成功</span>
        <span v-else class="ecr-badge error">失败</span>
      </div>

      <!-- 错误信息 -->
      <div v-if="data.error" class="ecr-error">
        <pre>{{ data.error }}</pre>
      </div>

      <!-- stdout 输出 -->
      <div v-if="data.output" class="ecr-output">
        <div class="ecr-section-title">输出</div>
        <pre>{{ data.output }}</pre>
      </div>

      <!-- 返回值 -->
      <div v-if="data.returnValue != null" class="ecr-return">
        <div class="ecr-section-title">返回值</div>
        <pre>{{ data.returnValue }}</pre>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  event: { type: Object, required: true }
})

const rawResult = computed(() => props.event.result || '')

const data = computed(() => {
  try {
    return JSON.parse(rawResult.value)
  } catch {
    return null
  }
})

const isPlainText = computed(() => !data.value || typeof data.value !== 'object')
const displayText = computed(() => typeof data.value === 'string' ? data.value : rawResult.value)
</script>

<style lang="less" scoped>
.execute-code-result {
  font-size: 12px;

  .ecr-plain pre {
    margin: 0; padding: 8px 10px; background: var(--gray-25);
    border-radius: 6px; line-height: 1.5;
    color: var(--gray-700); white-space: pre-wrap; word-break: break-word;
  }

  .ecr-status {
    display: flex; align-items: center; gap: 8px;
    padding: 6px 10px; background: var(--gray-25);
    border-bottom: 1px solid var(--gray-100);

    &.success { background: #f0fdf4; }
    &.error { background: #fef2f2; }
  }

  .ecr-lang {
    font-family: 'Monaco', 'Menlo', monospace;
    font-size: 11px; color: var(--gray-500);
    padding: 1px 6px; background: var(--gray-100);
    border-radius: 4px;
  }

  .ecr-elapsed {
    font-size: 11px; color: var(--gray-400);
    margin-left: auto;
  }

  .ecr-badge {
    font-size: 11px; font-weight: 600; padding: 1px 6px;
    border-radius: 4px;
    &.success { color: #16a34a; background: #dcfce7; }
    &.error { color: #dc2626; background: #fee2e2; }
  }

  .ecr-section-title {
    font-size: 11px; font-weight: 600; color: var(--gray-500);
    padding: 4px 10px 0; text-transform: uppercase; letter-spacing: 0.5px;
  }

  .ecr-error pre {
    margin: 0; padding: 8px 10px;
    background: #fef2f2; color: #dc2626;
    font-size: 12px; line-height: 1.5;
    white-space: pre-wrap; word-break: break-word;
  }

  .ecr-output pre,
  .ecr-return pre {
    margin: 0; padding: 8px 10px;
    background: var(--gray-25); color: var(--gray-700);
    font-size: 12px; line-height: 1.5;
    white-space: pre-wrap; word-break: break-word;
    max-height: 300px; overflow-y: auto;
    font-family: 'Monaco', 'Menlo', monospace;
  }

  .ecr-return pre {
    background: #f0f9ff; color: var(--gray-800);
  }
}
</style>
