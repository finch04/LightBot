<template>
  <component :is="renderer" :event="event" />
</template>

<script setup>
import { computed } from 'vue'
import { TOOL_RENDERERS } from './toolRegistry'
import DefaultToolResult from './tools/DefaultToolResult.vue'

const props = defineProps({
  event: { type: Object, required: true }
})

const renderer = computed(() => {
  return TOOL_RENDERERS[props.event.toolName] || DefaultToolResult
})
</script>
