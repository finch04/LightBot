<template>
  <div class="calculator-result">
    <div v-if="isPlainText" class="calc-plain">
      <pre>{{ displayText }}</pre>
    </div>
    <template v-else>
      <div class="calc-expression">{{ data.expression }}</div>
      <div class="calc-result">{{ data.result }}</div>
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
.calculator-result {
  .calc-plain pre {
    margin: 0; padding: 8px 10px; background: var(--gray-25);
    border-radius: 6px; font-size: 12px; line-height: 1.5;
    color: var(--gray-700); white-space: pre-wrap; word-break: break-word;
  }

  .calc-expression {
    padding: 8px 10px; font-size: 13px; color: var(--gray-500);
    font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  }

  .calc-result {
    padding: 10px 12px; font-size: 28px; font-weight: 700;
    color: var(--gray-800); text-align: center;
    font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  }
}
</style>
