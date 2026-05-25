<template>
  <span class="config-field-label">
    <span class="config-field-label-text">{{ label }}</span>
    <WorkflowTooltip v-if="tip" :title="tip" placement="topLeft">
      <QuestionCircleOutlined class="config-field-help-icon" />
    </WorkflowTooltip>
    <slot />
  </span>
</template>

<script setup>
import { QuestionCircleOutlined } from '@ant-design/icons-vue'
import WorkflowTooltip from './WorkflowTooltip.vue'

defineProps({
  label: { type: String, required: true },
  tip: { type: String, default: '' },
})
</script>

<style scoped>
.config-field-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.config-field-label-text {
  font-weight: 500;
}
.config-field-help-icon {
  font-size: 13px;
  color: #9ca3af;
  cursor: help;
}
.config-field-help-icon:hover {
  color: #6366f1;
}
</style>
