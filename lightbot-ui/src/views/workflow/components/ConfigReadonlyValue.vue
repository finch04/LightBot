<template>
  <div class="config-readonly-value" :title="displayText">{{ displayText }}</div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  text: { type: String, default: '' },
})

const displayText = computed(() => props.text || '—')
</script>

<style scoped>
.config-readonly-value {
  min-height: 32px;
  padding: 4px 11px;
  background: var(--color-canvas-soft, #f9fafb);
  border: 1px solid var(--color-hairline, #e5e7eb);
  border-radius: 6px;
  font-size: 14px;
  line-height: 22px;
  color: var(--color-ink, #111827);
  word-break: break-word;
}
</style>
