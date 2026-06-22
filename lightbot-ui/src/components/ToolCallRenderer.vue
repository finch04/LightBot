<template>
  <ErrorToolResult v-if="isError" :event="event" :message="errorMessage" />
  <component v-else :is="renderer" :event="event" />
</template>

<script setup>
import { computed, h, defineComponent } from 'vue'
import { TOOL_RENDERERS, getToolIcon, getToolDisplayName } from './toolRegistry'
import BaseToolCall from './tools/BaseToolCall.vue'
import ErrorToolResult from './tools/ErrorToolResult.vue'

const props = defineProps({
  event: { type: Object, required: true }
})

// 错误检测：JSON 中含 _error:true
const isError = computed(() => {
  const raw = props.event.result || ''
  try {
    const parsed = JSON.parse(raw)
    return parsed?._error === true
  } catch {
    return false
  }
})

const errorMessage = computed(() => {
  if (!isError.value) return ''
  try {
    return JSON.parse(props.event.result)?.message || '未知错误'
  } catch {
    return props.event.result || '未知错误'
  }
})

const FallbackRenderer = defineComponent({
  name: 'FallbackRenderer',
  props: { event: { type: Object, required: true } },
  setup(fallbackProps) {
    return () => h(BaseToolCall, {
      toolName: fallbackProps.event.toolName,
      displayName: getToolDisplayName(fallbackProps.event.toolName),
      icon: getToolIcon(fallbackProps.event.toolName),
      status: 'success',
      result: fallbackProps.event.result || '',
    })
  }
})

const renderer = computed(() => {
  const r = TOOL_RENDERERS[props.event.toolName]
  return r || FallbackRenderer
})
</script>
