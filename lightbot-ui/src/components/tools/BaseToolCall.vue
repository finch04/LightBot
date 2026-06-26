<template>
  <div class="base-tool-call" :class="[`status-${status}`, { 'is-expanded': expanded }]">
    <!-- Header -->
    <div class="btc-header" @click="toggleable && (expanded = !expanded)">
      <span class="btc-icon">
        <LoadingOutlined v-if="status === 'loading'" class="icon-spin" />
        <CheckCircleOutlined v-else-if="status === 'success'" class="icon-success" />
        <CloseCircleOutlined v-else-if="status === 'error'" class="icon-error" />
        <component v-else-if="icon" :is="icon" class="icon-default" />
      </span>
      <slot name="header">
        <span class="btc-name">{{ displayName || toolName }}</span>
      </slot>
      <span v-if="elapsedMs" class="btc-elapsed">{{ elapsedMs }}ms</span>
      <RightOutlined v-if="toggleable" class="btc-toggle" :class="{ expanded }" />
    </div>

    <!-- Content -->
    <div v-show="expanded" class="btc-body">
      <slot>
        <!-- 默认：JSON 格式化或纯文本 -->
        <div v-if="isPlainText" class="btc-plain">
          <pre>{{ rawResult }}</pre>
        </div>
        <div v-else-if="parsed" class="btc-json">
          <pre>{{ formattedJson }}</pre>
        </div>
      </slot>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { LoadingOutlined, CheckCircleOutlined, CloseCircleOutlined, RightOutlined } from '@ant-design/icons-vue'

const props = defineProps({
  toolName: { type: String, default: '' },
  displayName: { type: String, default: '' },
  icon: { type: Object, default: null },
  status: { type: String, default: 'success' }, // loading | success | error
  result: { type: String, default: '' },
  elapsedMs: { type: Number, default: 0 },
  defaultExpanded: { type: Boolean, default: true },
  toggleable: { type: Boolean, default: true },
})

const expanded = ref(props.defaultExpanded)

function toggle() {
  expanded.value = !expanded.value
}

const rawResult = computed(() => props.result || '')

const parsed = computed(() => {
  try {
    const p = JSON.parse(rawResult.value)
    return typeof p === 'object' && p !== null ? p : null
  } catch {
    return null
  }
})

const isPlainText = computed(() => !parsed.value)

const formattedJson = computed(() => {
  if (!parsed.value) return rawResult.value
  try {
    return JSON.stringify(parsed.value, null, 2)
  } catch {
    return rawResult.value
  }
})

defineExpose({ toggle })
</script>

<style lang="less" scoped>
.base-tool-call {
  border: 1px solid var(--gray-150);
  border-radius: 8px;
  overflow: hidden;
  transition: border-color 0.2s;

  &:hover { border-color: var(--gray-250); }

  &.status-error { border-left: 3px solid var(--color-error-500); }
  &.status-loading { border-left: 3px solid var(--main-500); }
}

.btc-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  background: var(--gray-25);
  cursor: pointer;
  user-select: none;
  transition: background 0.15s;

  &:hover { background: var(--gray-50); }
}

.btc-icon {
  display: inline-flex;
  align-items: center;
  flex-shrink: 0;

  .icon-spin {
    color: var(--main-600);
    font-size: 14px;
    animation: spin 1s linear infinite;
  }
  .icon-success { color: var(--color-success-500); font-size: 14px; }
  .icon-error { color: var(--color-error-500); font-size: 14px; }
  .icon-default { color: var(--main-600); font-size: 14px; }
}

.btc-name {
  flex: 1;
  font-size: 13px;
  font-weight: 600;
  color: var(--gray-700);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.btc-elapsed {
  font-size: 11px;
  color: var(--gray-400);
  flex-shrink: 0;
}

.btc-toggle {
  font-size: 10px;
  color: var(--gray-400);
  transition: transform 0.2s ease;
  flex-shrink: 0;

  &.expanded { transform: rotate(90deg); }
}

.btc-body {
  border-top: 1px solid var(--gray-100);
}

.btc-plain pre,
.btc-json pre {
  margin: 0;
  padding: 8px 10px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--gray-700);
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 400px;
  overflow-y: auto;
}

.btc-json pre {
  background: var(--gray-900);
  color: var(--gray-300);
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
