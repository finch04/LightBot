<template>
  <div class="execute-code-result" :class="langClass">
    <div v-if="isPlainText" class="ecr-plain">
      <pre>{{ displayText }}</pre>
    </div>
    <template v-else>
      <!-- 状态栏 -->
      <div class="ecr-status" :class="[data.success ? 'success' : 'error', langClass]">
        <span class="ecr-lang">{{ langLabel }}</span>
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

const langClass = computed(() => {
  const lang = data.value?.language?.toLowerCase()
  if (lang === 'java') return 'lang-java'
  if (lang === 'javascript' || lang === 'js') return 'lang-js'
  if (lang === 'python' || lang === 'py') return 'lang-python'
  return 'lang-unknown'
})

const langLabel = computed(() => {
  const lang = data.value?.language?.toLowerCase()
  if (lang === 'java') return 'Java'
  if (lang === 'javascript' || lang === 'js') return 'JavaScript'
  if (lang === 'python' || lang === 'py') return 'Python'
  return data.value?.language || 'unknown'
})
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
    padding: 6px 10px;
    border-bottom: 1px solid var(--gray-100);

    &.success { background: var(--color-success-bg); }
    &.error { background: var(--color-error-bg); }

    // Java: 红色系
    &.lang-java {
      &.success { background: var(--color-error-bg); border-bottom-color: var(--color-error-soft); }
      &.error { background: var(--color-error-bg); }
    }
    // JavaScript: 黄色系
    &.lang-js {
      &.success { background: var(--color-warn-bg); border-bottom-color: #fde68a; }
      &.error { background: var(--color-error-bg); }
    }
    // Python: 蓝色系
    &.lang-python {
      &.success { background: var(--color-info-bg); border-bottom-color: #bfdbfe; }
      &.error { background: var(--color-error-bg); }
    }
  }

  .ecr-lang {
    font-family: 'Monaco', 'Menlo', monospace;
    font-size: 11px; font-weight: 600;
    padding: 1px 8px; border-radius: 4px;

    .lang-java & {
      color: #be123c; background: #ffe4e6;
    }
    .lang-js & {
      color: #92400e; background: var(--color-warn-bg-deep);
    }
    .lang-python & {
      color: #1e40af; background: var(--color-info-bg);
    }
    .lang-unknown & {
      color: var(--gray-500); background: var(--gray-100);
    }
  }

  .ecr-elapsed {
    font-size: 11px; color: var(--gray-400);
    margin-left: auto;
  }

  .ecr-badge {
    font-size: 11px; font-weight: 600; padding: 1px 6px;
    border-radius: 4px;
    &.success { color: #16a34a; background: var(--color-success-bg); }
    &.error { color: #dc2626; background: var(--color-error-bg); }
  }

  .ecr-section-title {
    font-size: 11px; font-weight: 600; color: var(--gray-500);
    padding: 4px 10px 0; text-transform: uppercase; letter-spacing: 0.5px;
  }

  .ecr-error pre {
    margin: 0; padding: 8px 10px;
    background: var(--color-error-bg); color: #dc2626;
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

  // 返回值区域也按语言着色
  .ecr-return pre {
    .lang-java & { background: var(--color-error-bg); color: #881337; }
    .lang-js & { background: var(--color-warn-bg); color: #78350f; }
    .lang-python & { background: var(--color-info-bg); color: #1e3a5f; }
    .lang-unknown & { background: var(--color-info-bg); color: var(--gray-800); }
  }
}
</style>
