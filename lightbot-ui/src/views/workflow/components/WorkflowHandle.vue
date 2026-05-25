<template>
  <Handle
  v-bind="$attrs"
  :type="type"
  :position="position"
  :id="id"
  class="wf-handle"
  />
</template>

<script setup>
import { Handle } from '@vue-flow/core'

defineProps({
  type: { type: String, required: true },
  position: { type: String, required: true },
  id: { type: String, default: undefined }
})
</script>

<style scoped>
:deep(.wf-handle) {
  width: 16px !important;
  height: 16px !important;
  border: 2px solid #6366f1 !important;
  background: #fff !important;
  border-radius: 50% !important;
  transition: width 0.15s, height 0.15s, background 0.15s;
}
:deep(.wf-handle:hover) {
  width: 22px !important;
  height: 22px !important;
  background: #6366f1 !important;
}
</style>
