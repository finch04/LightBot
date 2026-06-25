<template>
  <div v-if="toolEvents && toolEvents.length > 0" class="tool-calls-group">
    <button type="button" class="tool-calls-summary" :class="{ 'is-expanded': isExpanded }" @click="toggleExpand($event)">
      <span class="summary-icon">
        <CheckCircleOutlined v-if="isDone" class="icon-success" />
        <LoadingOutlined v-else class="icon-spinning" />
      </span>
      <span class="summary-content">
        <span class="summary-title" v-if="uniqueToolNames.length > 0">调用了{{ uniqueToolNames.length }}个工具</span>
        <span class="summary-title" v-else>正在调用工具...</span>
        <span class="summary-separator" v-if="uniqueToolNames.length > 1">，</span>
        <span class="summary-meta" v-if="uniqueToolNames.length > 1">{{ uniqueToolNames.join('、') }}</span>
      </span>
      <span class="summary-trailing">
        <RightOutlined :class="{ expanded: isExpanded }" class="expand-icon" />
      </span>
    </button>

    <div v-show="isExpanded" class="tool-calls-panel">
      <div v-for="(evt, ti) in toolEvents" :key="ti" class="tool-event-item">
        <!-- tool_call: 工具调用发起 -->
        <div v-if="evt.type === 'tool_call'" class="event-call-wrap">
          <div class="event-row event-call">
            <SearchOutlined v-if="evt.toolName === 'query_knowledge'" class="event-icon" />
            <LoadingOutlined v-else-if="!isDone" class="event-icon icon-spinning" />
            <CheckCircleOutlined v-else class="event-icon icon-success" />
            <span class="event-label">调用 <strong>{{ resolveDisplayName(evt) }}</strong> 工具</span>
            <a-tooltip v-if="hasArgs(evt)" title="查看参数详情">
              <button class="args-detail-btn" @click.stop="openArgs(ti)">
                <FileSearchOutlined />
              </button>
            </a-tooltip>
          </div>
          <div v-if="hasArgs(evt)" class="event-args-block">
            <div v-for="(arg, ai) in parseArgsPreview(evt.args)" :key="ai" class="event-arg-line">
              <span class="arg-key">{{ arg.key }}</span>
              <span class="arg-val">{{ arg.value }}</span>
            </div>
          </div>
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
          <span class="event-label"><strong>{{ resolveDisplayName(evt) }}</strong> 执行完成</span>
          <button class="result-toggle" @click="toggleResult(ti)" v-if="evt.result">
            <RightOutlined :class="{ expanded: expandedResults.has(ti) }" class="expand-icon-sm" />
            <span>查看结果</span>
          </button>
        </div>
        <!-- 结果详情展开 -->
        <div v-if="evt.type === 'tool_result' && expandedResults.has(ti)" class="result-detail">
          <ToolCallRenderer :event="evt" :messageIndex="messageIndex" />
        </div>
      </div>
    </div>

    <!-- 参数详情弹窗 -->
    <a-modal
      v-model:open="argsModalOpen"
      title="参数详情"
      :footer="null"
      :width="520"
      destroyOnClose
    >
      <div class="args-modal-body">
        <div v-for="(entry, ei) in argsEntries" :key="ei" class="args-entry">
          <span class="args-key">{{ entry.key }}</span>
          <span class="args-val" :class="entry.type">{{ entry.value }}</span>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'
import { CheckCircleOutlined, LoadingOutlined, RightOutlined, SearchOutlined, FileSearchOutlined } from '@ant-design/icons-vue'
import ToolCallRenderer from './ToolCallRenderer.vue'
import { getToolDisplayName } from './toolRegistry'

const props = defineProps({
  toolEvents: { type: Array, default: () => [] },
  isDone: { type: Boolean, default: true },
  defaultExpanded: { type: Boolean, default: true },
  messageIndex: { type: Number, default: -1 }
})

const emit = defineEmits(['heightChange'])

const isExpanded = ref(props.defaultExpanded)

function toggleExpand(event) {
  isExpanded.value = !isExpanded.value
  nextTick(() => emit('heightChange', event))
}
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
  props.toolEvents.forEach(e => { if (e.toolName) names.add(resolveDisplayName(e)) })
  return [...names]
})

function resolveDisplayName(evt) {
  return evt.displayName || getToolDisplayName(evt.toolName)
}

// ── 参数展示 ──
const MAX_INLINE_ARGS = 3
const MAX_DISPLAY_WIDTH = 60

function hasArgs(evt) {
  if (!evt.args) return false
  try {
    return Object.keys(JSON.parse(evt.args)).length > 0
  } catch {
    return false
  }
}

/** 计算字符串显示宽度：中文算2，英文/其他算1 */
function displayWidth(s) {
  let w = 0
  for (const ch of s) {
    w += ch.charCodeAt(0) > 0x7f ? 2 : 1
  }
  return w
}

/** 按显示宽度截断字符串 */
function truncateByWidth(s, maxW) {
  let w = 0
  for (let i = 0; i < s.length; i++) {
    w += s.charCodeAt(i) > 0x7f ? 2 : 1
    if (w > maxW) return s.substring(0, i) + '...'
  }
  return s
}

function parseArgsPreview(args) {
  if (!args) return []
  try {
    const obj = JSON.parse(args)
    const entries = Object.entries(obj)
    const shown = entries.slice(0, MAX_INLINE_ARGS)
    const result = shown.map(([k, v]) => {
      let val = v === null || v === undefined ? 'null' : String(v)
      if (displayWidth(val) > MAX_DISPLAY_WIDTH) val = truncateByWidth(val, MAX_DISPLAY_WIDTH)
      return { key: k, value: val }
    })
    if (entries.length > MAX_INLINE_ARGS) {
      result.push({ key: '...', value: `还有${entries.length - MAX_INLINE_ARGS}个参数` })
    }
    return result
  } catch {
    const s = args.length > 80 ? args.substring(0, 80) + '...' : args
    return [{ key: 'raw', value: s }]
  }
}

// ── 参数详情弹窗 ──
const argsModalOpen = ref(false)
const argsEntries = ref([])

function formatValue(v) {
  if (v === null || v === undefined) return { value: 'null', type: 'null' }
  if (typeof v === 'boolean') return { value: String(v), type: 'bool' }
  if (typeof v === 'number') return { value: String(v), type: 'number' }
  if (Array.isArray(v)) return { value: JSON.stringify(v, null, 2), type: 'string' }
  if (typeof v === 'object') return { value: JSON.stringify(v, null, 2), type: 'string' }
  return { value: String(v), type: 'string' }
}

function openArgs(index) {
  const evt = props.toolEvents[index]
  if (!evt?.args) return
  try {
    const obj = JSON.parse(evt.args)
    argsEntries.value = Object.entries(obj).map(([k, v]) => ({
      key: k,
      ...formatValue(v),
    }))
  } catch {
    argsEntries.value = [{ key: 'raw', value: evt.args, type: 'string' }]
  }
  argsModalOpen.value = true
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
  .summary-separator { color: var(--gray-400); flex-shrink: 0; }
  .summary-meta {
    color: var(--main-700);
    font-weight: 600;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

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

.event-call-wrap {
  margin-bottom: 2px;
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

.event-args-block {
  margin-left: auto;
  margin-top: 2px;
  margin-bottom: 4px;
  padding: 4px 10px;
  background: var(--gray-25);
  border: 1px solid var(--gray-100);
  border-radius: 6px;
  max-width: 80%;
  margin-right: 0;
}

.event-arg-line {
  display: flex;
  align-items: baseline;
  justify-content: flex-end;
  gap: 6px;
  font-size: 12px;
  line-height: 1.6;

  .arg-val {
    color: var(--gray-700);
    word-break: break-all;
    text-align: right;
  }

  .arg-key {
    flex-shrink: 0;
    color: var(--gray-500);
    font-weight: 500;
    &::after { content: ':'; }
  }
}

.args-detail-btn {
  appearance: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  padding: 0;
  border: none;
  border-radius: 4px;
  background: none;
  color: var(--gray-400);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s ease;
  flex-shrink: 0;
  &:hover { color: var(--main-600); background: var(--gray-100); }
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

.args-modal-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 480px;
  overflow-y: auto;
}

.args-entry {
  display: flex;
  align-items: baseline;
  gap: 12px;
  padding: 6px 10px;
  border-radius: 6px;
  background: var(--gray-25);
  border: 1px solid var(--gray-100);
}

.args-key {
  flex-shrink: 0;
  min-width: 80px;
  font-size: 12px;
  font-weight: 600;
  color: var(--gray-500);
  text-align: right;
}

.args-val {
  flex: 1;
  font-size: 13px;
  color: var(--gray-800);
  word-break: break-all;
  white-space: pre-wrap;

  &.string { color: var(--gray-800); }
  &.number { color: #2563eb; }
  &.bool { color: #7c3aed; }
  &.null { color: var(--gray-400); font-style: italic; }
  &.array { color: #059669; }
  &.object { color: #d97706; }
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
