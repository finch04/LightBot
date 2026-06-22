<template>
  <div class="read-skill-result">
    <div v-if="isPlainText" class="rs-plain">
      <pre>{{ rawResult }}</pre>
    </div>
    <template v-else>
      <div class="rs-header">
        <ThunderboltOutlined class="rs-icon" />
        <span class="rs-slug">{{ data.slug }}</span>
        <span v-if="data.activated" class="rs-activated">已激活</span>
      </div>
      <div class="rs-content">
        <pre>{{ data.content }}</pre>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { ThunderboltOutlined } from '@ant-design/icons-vue'

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

const isPlainText = computed(() => !data.value)
</script>

<style lang="less" scoped>
.read-skill-result {
  .rs-plain pre {
    margin: 0; padding: 8px 10px; background: var(--gray-25);
    border-radius: 6px; font-size: 12px; line-height: 1.5;
    color: var(--gray-700); white-space: pre-wrap; word-break: break-word;
  }

  .rs-header {
    display: flex; align-items: center; gap: 6px;
    padding: 8px 10px; background: var(--gray-25);
    border-radius: 6px; font-size: 12px; color: var(--gray-700);
    margin-bottom: 8px;
    .rs-icon { color: var(--main-600); font-size: 13px; }
    .rs-slug { font-weight: 600; font-family: monospace; }
    .rs-activated {
      margin-left: auto; font-size: 11px; color: var(--color-success-500);
      background: #f0fdf4; border: 1px solid #bbf7d0;
      border-radius: 4px; padding: 0 6px;
    }
  }

  .rs-content {
    pre {
      margin: 0; padding: 10px; background: var(--gray-25);
      border-radius: 6px; font-size: 12px; line-height: 1.6;
      color: var(--gray-700); white-space: pre-wrap; word-break: break-word;
      max-height: 400px; overflow-y: auto;
    }
  }
}
</style>
