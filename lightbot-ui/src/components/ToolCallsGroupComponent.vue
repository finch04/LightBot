<template>
  <div v-if="toolEvents && toolEvents.length > 0" class="tool-calls-group">
    <button type="button" class="tool-calls-summary" :class="{ 'is-expanded': isExpanded }" @click="isExpanded = !isExpanded">
      <span class="summary-icon">
        <CheckCircleOutlined v-if="isDone" class="icon-success" />
        <LoadingOutlined v-else class="icon-spinning" />
      </span>
      <span class="summary-content">
        <span class="summary-title">调用了 {{ uniqueToolNames.length }} 个工具</span>
        <span class="summary-separator" v-if="uniqueToolNames.length > 1">·</span>
        <span class="summary-meta" v-if="uniqueToolNames.length > 1">{{ uniqueToolNames.join(' · ') }}</span>
      </span>
      <span class="summary-trailing">
        <RightOutlined :class="{ expanded: isExpanded }" class="expand-icon" />
      </span>
    </button>

    <div v-show="isExpanded" class="tool-calls-panel">
      <div v-for="(evt, ti) in toolEvents" :key="ti" class="tool-event-item">
        <!-- tool_call: 工具调用发起 -->
        <div v-if="evt.type === 'tool_call'" class="event-row event-call">
          <SearchOutlined v-if="evt.toolName === 'query_knowledge'" class="event-icon" />
          <LoadingOutlined v-else-if="!isDone" class="event-icon icon-spinning" />
          <CheckCircleOutlined v-else class="event-icon icon-success" />
          <span class="event-label">调用 <strong>{{ evt.toolName }}</strong></span>
          <span class="event-args" v-if="evt.args">{{ formatArgs(evt.args) }}</span>
        </div>
        <!-- tool_status: 执行中间状态 -->
        <div v-else-if="evt.type === 'tool_status'" class="event-row event-status">
          <CheckCircleOutlined v-if="isDone" class="event-icon icon-success" />
          <LoadingOutlined v-else class="event-icon icon-spinning" />
          <span class="event-text">{{ evt.message }}</span>
        </div>
        <!-- tool_result: 执行结果 -->
        <div v-else-if="evt.type === 'tool_result'" class="event-row event-result">
          <CheckCircleOutlined class="event-icon icon-success" />
          <span class="event-label"><strong>{{ evt.toolName }}</strong> 执行完成</span>
          <button class="result-toggle" @click="toggleResult(ti)" v-if="evt.result">
            <RightOutlined :class="{ expanded: expandedResults.has(ti) }" class="expand-icon-sm" />
            <span>查看结果</span>
          </button>
        </div>
        <!-- 结果详情展开 -->
        <div v-if="evt.type === 'tool_result' && expandedResults.has(ti)" class="result-detail">
          <pre>{{ evt.result }}</pre>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { CheckCircleOutlined, LoadingOutlined, RightOutlined, SearchOutlined } from '@ant-design/icons-vue'

const props = defineProps({
  toolEvents: { type: Array, default: () => [] },
  isDone: { type: Boolean, default: true },
  defaultExpanded: { type: Boolean, default: true }
})

const isExpanded = ref(props.defaultExpanded)
const expandedResults = ref(new Set())
const manualToggled = ref(new Set())

function syncExpandedResults() {
  const s = new Set(expandedResults.value)
  props.toolEvents.forEach((e, i) => {
    if (e.type === 'tool_result' && e.result && !manualToggled.value.has(i)) {
      s.add(i)
    }
  })
  expandedResults.value = s
}

watch(
  () => props.toolEvents,
  () => syncExpandedResults(),
  { immediate: true, deep: true }
)

const uniqueToolNames = computed(() => {
  const names = new Set()
  props.toolEvents.forEach(e => { if (e.toolName) names.add(e.toolName) })
  return [...names]
})

function formatArgs(args) {
  if (!args) return ''
  try {
    const obj = JSON.parse(args)
    const entries = Object.entries(obj)
    if (entries.length === 0) return ''
    return entries.map(([k, v]) => {
      const val = typeof v === 'string' && v.length > 60 ? v.substring(0, 60) + '...' : v
      return `${k}=${val}`
    }).join(', ')
  } catch {
    return args.length > 80 ? args.substring(0, 80) + '...' : args
  }
}

function toggleResult(index) {
  manualToggled.value.add(index)
  const s = new Set(expandedResults.value)
  if (s.has(index)) s.delete(index)
  else s.add(index)
  expandedResults.value = s
}
</script>

<style lang="less" scoped>
.tool-calls-group {
  margin-top: 8px;
  padding: 10px 12px;
  background: #F9FAFB;
  border: 1px solid lightgray;
  border-radius: 8px;
}

.tool-calls-summary {
  appearance: none;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 4px 10px;
  border: 1px solid var(--gray-100);
  border-radius: 8px;
  background: var(--gray-25);
  color: var(--gray-500);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s ease;
  user-select: none;

  &:hover { background: var(--gray-50); color: var(--gray-700); }
  &.is-expanded { color: var(--gray-700); background: var(--gray-50); border-color: var(--gray-200); }

  .summary-icon {
    display: inline-flex;
    align-items: center;
    flex-shrink: 0;
  }

  .icon-success { color: var(--color-success-500); font-size: 14px; }
  .icon-spinning { color: var(--main-600); font-size: 14px; animation: spin 1s linear infinite; }

  .summary-content {
    display: flex;
    align-items: center;
    gap: 6px;
    flex: 1;
    min-width: 0;
  }

  .summary-title { font-weight: 500; white-space: nowrap; }
  .summary-separator { color: var(--gray-300); flex-shrink: 0; }
  .summary-meta { color: var(--gray-400); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

  .summary-trailing {
    display: inline-flex;
    align-items: center;
    color: var(--gray-300);
    flex-shrink: 0;
  }

  .expand-icon {
    transition: transform 0.2s ease;
    font-size: 12px;
    &.expanded { transform: rotate(90deg); }
  }
}

.tool-calls-panel {
  padding: 4px 0 4px 12px;
  border-left: 2px solid var(--gray-100);
  margin-left: 16px;
  margin-top: 6px;
  margin-bottom: 8px;
}

.tool-event-item {
  margin-bottom: 4px;
  &:last-child { margin-bottom: 0; }
}

.event-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  font-size: 13px;
  padding: 3px 0;
  color: var(--gray-600);

  .event-icon {
    flex-shrink: 0;
    font-size: 13px;
    margin-top: 2px;
    &.icon-success { color: var(--color-success-500); }
    &.icon-spinning { color: var(--main-600); animation: spin 1s linear infinite; }
  }

  .event-label {
    flex: 1;
    min-width: 0;
    strong { color: var(--main-700); font-weight: 600; }
  }

  .event-args {
    color: var(--gray-400);
    font-size: 12px;
    flex: 1;
    min-width: 0;
    word-break: break-all;
  }

  .event-text {
    color: var(--gray-500);
    flex: 1;
  }

  .result-toggle {
    appearance: none;
    display: inline-flex;
    align-items: center;
    gap: 4px;
    padding: 0 6px;
    border: none;
    border-radius: 4px;
    background: transparent;
    color: var(--gray-400);
    font-size: 12px;
    cursor: pointer;
    transition: all 0.15s ease;
    flex-shrink: 0;
    &:hover { background: var(--gray-50); color: var(--gray-600); }
  }

  .expand-icon-sm {
    font-size: 10px;
    transition: transform 0.2s ease;
    &.expanded { transform: rotate(90deg); }
  }
}

.result-detail {
  margin: 4px 0 4px 21px;
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
  }
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
