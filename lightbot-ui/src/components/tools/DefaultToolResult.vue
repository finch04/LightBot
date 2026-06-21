<template>
  <div class="default-tool-result">
    <pre :class="{ 'is-json': isJson }">{{ displayResult }}</pre>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  event: { type: Object, required: true }
})

const raw = computed(() => props.event.result || '')

const isJson = computed(() => {
  try {
    const parsed = JSON.parse(raw.value)
    return typeof parsed === 'object' && parsed !== null
  } catch {
    return false
  }
})

const displayResult = computed(() => {
  if (!isJson.value) return raw.value
  try {
    return JSON.stringify(JSON.parse(raw.value), null, 2)
  } catch {
    return raw.value
  }
})
</script>

<style lang="less" scoped>
.default-tool-result {
  pre {
    margin: 0;
    padding: 8px 10px;
    background: var(--gray-25);
    border-radius: 6px;
    font-size: 12px;
    line-height: 1.5;
    color: var(--gray-700);
    white-space: pre-wrap;
    word-break: break-word;
    max-height: 300px;
    overflow-y: auto;
    &.is-json {
      background: #1e1e1e;
      color: #d4d4d4;
    }
  }
}
</style>
