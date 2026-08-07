<template>
  <div v-if="outputs.subAgentName" class="wf-detail-pill subagent">SubAgent · {{ outputs.subAgentName }}</div>
  <div v-if="outputs.reply || outputs.output" class="wf-detail-pre">{{ truncateText(outputs.reply || outputs.output, 600) }}</div>
  <div v-else-if="!outputs.subAgentName" class="wf-detail-hint">暂无输出</div>
</template>

<script setup>
import { toRef } from 'vue'
import { truncateText } from '../workflowStepUtils.js'
import { useWorkflowStepContext } from '../composables/useWorkflowStepContext.js'

const props = defineProps({
  step: { type: Object, required: true },
})

const { outputs } = useWorkflowStepContext(toRef(props, 'step'))
</script>
