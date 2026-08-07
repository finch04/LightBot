<template>
  <div v-if="toolEvents && toolEvents.length > 0" class="tool-calls-wrap">
    <!-- 业务办理页：固化模板卡片，默认直接展示在对话中 -->
    <div
      v-for="item in presentationResults"
      :key="'presentation-' + item.index"
      class="presentation-page-block"
    >
      <div v-if="!item.event.result && loadingFullResult.has(item.index)" class="presentation-loading">
        <LoadingOutlined class="icon-spinning" />
        <span>正在加载业务办理页…</span>
      </div>
      <div v-else-if="!item.event.result" class="presentation-loading">
        <span>业务办理页结果暂不可用</span>
      </div>
      <ToolCallRenderer
        v-else
        :event="item.event"
        :args="getPairedCallArgs(item.index)"
        :message-index="messageIndex"
      />
    </div>

    <div v-if="hasRegularToolEvents" class="tool-calls-group">
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

    <CollapseTransition :open="isExpanded">
      <div class="tool-calls-panel">
        <div v-for="(evt, ti) in toolEvents" :key="ti" class="tool-event-item">
        <!-- 业务办理页已在上方直接展示，折叠组内跳过避免重复 -->
        <template v-if="!isPresentationTool(evt.toolName)">
        <!-- tool_call: 工具调用发起 -->
        <div v-if="evt.type === 'tool_call'" class="event-call-wrap">
          <div class="event-row event-call">
            <LoadingOutlined v-if="!isCallCompleted(ti)" class="event-icon icon-spinning" />
            <CheckCircleOutlined v-else class="event-icon icon-success" />
            <span class="event-label">
              调用 <component :is="resolveEventIcon(evt)" class="event-tool-icon" /> <strong>{{ resolveDisplayName(evt) }}</strong> 工具
            </span>
            <div v-if="hasArgs(evt)" class="args-actions">
              <button class="args-toggle-btn" @click.stop="toggleArgs(ti, $event)">
                <RightOutlined :class="{ expanded: expandedArgs.has(ti) }" class="expand-icon-sm" />
                <span>{{ expandedArgs.has(ti) ? '收起参数' : '展开参数' }}</span>
              </button>
              <a-tooltip title="查看参数详情">
                <button class="args-detail-btn" @click.stop="openArgs(ti)">
                  <FileSearchOutlined />
                </button>
              </a-tooltip>
            </div>
          </div>
          <CollapseTransition v-if="hasArgs(evt)" :open="expandedArgs.has(ti)">
            <pre class="event-args-raw">{{ evt.args }}</pre>
          </CollapseTransition>
        </div>
        <!-- tool_status: 执行中间状态 -->
        <div v-else-if="evt.type === 'tool_status'" class="event-row event-status">
          <CheckCircleOutlined v-if="isStatusCompleted(ti)" class="event-icon icon-success" />
          <LoadingOutlined v-else class="event-icon icon-spinning" />
          <span class="event-text">{{ evt.message }}</span>
        </div>
        <!-- tool_result: 执行结果 -->
        <div v-else-if="evt.type === 'tool_result'" class="event-row event-result">
          <CheckCircleOutlined class="event-icon icon-success" />
          <span class="event-label">
            <component :is="resolveEventIcon(evt)" class="event-tool-icon" /> <strong>{{ resolveDisplayName(evt) }}</strong> 执行完成
          </span>
          <span v-if="evt.resultTotalLength" class="result-size-hint">
            {{ formatLength(evt.resultTotalLength) }}
          </span>
          <button v-if="evt.toolCallId || evt.result" class="result-toggle"
                  @click="toggleResult(ti, $event)" :disabled="loadingFullResult.has(ti)">
            <LoadingOutlined v-if="loadingFullResult.has(ti)" class="icon-spinning" />
            <RightOutlined v-else :class="{ expanded: expandedResults.has(ti) }" class="expand-icon-sm" />
            <span>{{ loadingFullResult.has(ti) ? '加载中…' : '查看结果' }}</span>
          </button>
        </div>
        <!-- 结果详情展开 -->
        <CollapseTransition v-if="evt.type === 'tool_result'" :open="expandedResults.has(ti)">
          <div class="result-detail">
            <ToolCallRenderer :event="evt" :args="getPairedCallArgs(ti)" :messageIndex="messageIndex" />
          </div>
        </CollapseTransition>
        </template>
      </div>
      </div>
    </CollapseTransition>
    </div>
    <a-modal v-model:open="argsModalOpen" title="参数详情" :footer="null" :width="520" destroyOnClose>
      <div class="dialog-scroll-body">
      <pre class="args-modal-raw">{{ argsModalContent }}</pre>
      </div>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'
import { CheckCircleOutlined, LoadingOutlined, RightOutlined, FileSearchOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import ToolCallRenderer from './ToolCallRenderer.vue'
import CollapseTransition from './common/CollapseTransition.vue'
import { getToolDisplayName, getToolIcon, isPresentationTool } from './toolRegistry'
import { getToolResultDetail } from '@/api/chatSession'
import { resolveIcon } from '@/utils/iconRegistry'

const props = defineProps({
  toolEvents: { type: Array, default: () => [] },
  isDone: { type: Boolean, default: true },
  defaultExpanded: { type: Boolean, default: true },
  messageIndex: { type: Number, default: -1 },
  /** 所属消息 ID（历史消息按需拉取完整 tool_result 时必传） */
  messageId: { type: [String, Number], default: null },
})

const emit = defineEmits(['heightChange'])

const isExpanded = ref(props.defaultExpanded)

function toggleExpand(event) {
  isExpanded.value = !isExpanded.value
  nextTick(() => emit('heightChange', { target: event?.target, preserveViewport: true }))
}
const expandedResults = ref(new Set())
const manualToggled = ref(new Set())
const expandedArgs = ref(new Set())
const argsModalOpen = ref(false)
const argsModalContent = ref('')
/** index → 是否正在拉取完整 result（历史消息 result 被后端截断时按需拉取） */
const loadingFullResult = ref(new Set())

function syncExpandedResults() {
  // 历史消息（非流式）默认折叠所有 tool_result 详情；流式中保持自动展开
  if (!props.defaultExpanded) {
    return
  }
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
  () => {
    syncExpandedResults()
    // 历史列表会剥离长 result（含 pageHtml），呈现类工具需自动回填才能显示表单
    hydratePresentationResults()
  },
  { immediate: true, deep: true }
)

/** 业务办理页结果：从工具折叠组中抽出，始终可见（含待回填的历史瘦身结果） */
const presentationResults = computed(() => {
  const list = []
  ;(props.toolEvents || []).forEach((event, index) => {
    if (event?.type !== 'tool_result' || !isPresentationTool(event.toolName)) return
    // 有完整 result，或具备按需拉取条件（toolCallId / resultTotalLength）
    if (event.result || event.toolCallId || event.resultTotalLength) {
      list.push({ event, index })
    }
  })
  return list
})

/** 自动拉取被历史接口剥离的呈现类工具完整结果 */
function hydratePresentationResults() {
  ;(props.toolEvents || []).forEach((event, index) => {
    if (event?.type !== 'tool_result' || !isPresentationTool(event.toolName)) return
    if (event.result || !event.toolCallId) return
    if (loadingFullResult.value.has(index)) return
    fetchFullResult(index)
  })
}

const hasRegularToolEvents = computed(() =>
  (props.toolEvents || []).some((e) => e?.toolName && !isPresentationTool(e.toolName))
)

const uniqueToolNames = computed(() => {
  const names = new Set()
  props.toolEvents.forEach((e) => {
    if (e.toolName && !isPresentationTool(e.toolName)) names.add(resolveDisplayName(e))
  })
  return [...names]
})

/**
 * 按事件顺序为每个 tool_call 找到最近的匹配 tool_result（同 toolName，且后于该 call）。
 * 后端 SSE 不下发 toolCallId，同一 block 内 call/result 严格按顺序推送，故按 toolName 顺序配对。
 * 返回：Map<callIndex, resultIndex>，未匹配到的 call 视为未完成。
 */
const callResultPairing = computed(() => {
  const pairing = new Map()
  const events = props.toolEvents || []
  const consumed = new Set()
  for (let i = 0; i < events.length; i++) {
    const call = events[i]
    if (call?.type !== 'tool_call') continue
    for (let j = i + 1; j < events.length; j++) {
      if (consumed.has(j)) continue
      const r = events[j]
      if (r?.type === 'tool_result' && r.toolName === call.toolName) {
        pairing.set(i, j)
        consumed.add(j)
        break
      }
    }
  }
  return pairing
})

function isCallCompleted(index) {
  return callResultPairing.value.has(index)
}

/**
 * 取得与 tool_result 配对的 tool_call 的 args（同 toolName，且后于该 call）。
 * SandboxFileResult 等渲染组件在工作区路径（无 url）时需从 args 反查 content 做内联预览。
 * 复用 callResultPairing 的配对结果避免重复计算；未匹配到返回空串。
 */
function getPairedCallArgs(resultIndex) {
  const events = props.toolEvents || []
  // 反向查最近的同 toolName 的 tool_call；与 callResultPairing 顺序配对保持一致
  for (let i = resultIndex - 1; i >= 0; i--) {
    const prev = events[i]
    if (prev?.type === 'tool_call' && prev?.toolName === events[resultIndex]?.toolName) {
      return prev?.args || ''
    }
  }
  return ''
}

/**
 * tool_status 是中间状态，取其最近的前置 tool_call：只要该 call 已匹配到 result，就视为完成。
 * 若无前置 tool_call，退化为消息级 isDone。
 */
function isStatusCompleted(index) {
  const events = props.toolEvents || []
  for (let i = index - 1; i >= 0; i--) {
    if (events[i]?.type === 'tool_call') {
      return callResultPairing.value.has(i)
    }
  }
  return props.isDone
}

function resolveDisplayName(evt) {
  return evt.displayName || getToolDisplayName(evt.toolName)
}

/**
 * 解析事件的图标组件：优先用后端配置的 evt.icon（Ant Design 图标名），
 * 回退到内置工具注册表图标
 */
function resolveEventIcon(evt) {
  if (evt.icon) {
    const resolved = resolveIcon(evt.icon)
    if (resolved) return resolved
  }
  return getToolIcon(evt.toolName)
}

function hasArgs(evt) {
  const rawArgs = typeof evt?.args === 'string' ? evt.args.trim() : ''
  if (!rawArgs) return false
  try {
    const parsed = JSON.parse(rawArgs)
    if (Array.isArray(parsed)) return parsed.length > 0
    if (parsed && typeof parsed === 'object') return Object.keys(parsed).length > 0
  } catch {
    return true
  }
  return true
}

function toggleArgs(index, event) {
  const next = new Set(expandedArgs.value)
  if (next.has(index)) next.delete(index)
  else next.add(index)
  expandedArgs.value = next
  nextTick(() => emit('heightChange', { target: event?.target, preserveViewport: true }))
}

function openArgs(index) {
  argsModalContent.value = props.toolEvents[index]?.args || ''
  argsModalOpen.value = true
}

async function toggleResult(index, event) {
  manualToggled.value.add(index)
  const s = new Set(expandedResults.value)
  const willExpand = !s.has(index)
  if (willExpand) {
    s.add(index)
  } else {
    s.delete(index)
  }
  expandedResults.value = s
  nextTick(() => emit('heightChange', { target: event?.target, preserveViewport: true }))
  // 展开时若无完整 result（历史消息被瘦身），按需从数据库拉取完整 JSON
  if (willExpand && needFetchFullResult(index)) {
    await fetchFullResult(index)
  }
}

function needFetchFullResult(index) {
  const evt = props.toolEvents[index]
  // 历史消息列表接口会剥离 result 正文；流式中 SSE 推送的 tool_result 本身就带完整 result
  return !evt?.result
}

/** 归一化工具结果详情：兼容字符串 / 已解析对象（Jackson NON_NULL 下 data 可能缺失） */
function normalizeToolResultPayload(data) {
  if (typeof data === 'string' && data) return data
  if (data && typeof data === 'object') {
    try {
      return JSON.stringify(data)
    } catch {
      return null
    }
  }
  return null
}

/** 呈现类工具：用配对 tool_call.args 拼可回填的瘦身 stub，避免误报「执行失败」 */
function buildPresentationStubFromArgs(args) {
  try {
    const parsed = typeof args === 'string' ? JSON.parse(args || '{}') : (args || {})
    const pageType = String(parsed?.pageType || '').trim()
    if (!pageType) return null
    let props = parsed.props
    let options = parsed.options
    if (typeof props === 'string') {
      try { props = JSON.parse(props || '{}') } catch { props = {} }
    }
    if (typeof options === 'string') {
      try { options = JSON.parse(options || '{}') } catch { options = {} }
    }
    return JSON.stringify({
      success: true,
      pageType,
      title: parsed.title || pageType,
      mode: parsed.mode || 'inline',
      props: props && typeof props === 'object' ? props : {},
      options: options && typeof options === 'object' ? options : {},
      needsPageContent: true,
    })
  } catch {
    return null
  }
}

async function fetchFullResult(index) {
  const evt = props.toolEvents[index]
  const toolCallId = evt?.toolCallId
  if (!toolCallId) {
    // 历史数据未带 toolCallId：呈现类工具仍可按 args 回填
    if (isPresentationTool(evt?.toolName) && !evt.result) {
      const stub = buildPresentationStubFromArgs(getPairedCallArgs(index))
      if (stub) {
        evt.result = stub
        emit('heightChange', { target: null, preserveViewport: true })
      }
    }
    return
  }
  const loadingSet = new Set(loadingFullResult.value)
  loadingSet.add(index)
  loadingFullResult.value = loadingSet
  try {
    const res = await getToolResultDetail(toolCallId)
    const payload = normalizeToolResultPayload(res?.data)
    if (payload) {
      evt.result = payload
      emit('heightChange', { target: null, preserveViewport: true })
      return
    }
    // 呈现类工具：勿伪造 _error（会显示「业务办理页 执行失败」）
    if (isPresentationTool(evt.toolName)) {
      const stub = buildPresentationStubFromArgs(getPairedCallArgs(index))
      if (stub) {
        evt.result = stub
        emit('heightChange', { target: null, preserveViewport: true })
      }
      return
    }
    evt.result = JSON.stringify({ _error: true, message: '工具执行结果不可用' })
    emit('heightChange', { target: null, preserveViewport: true })
  } catch (e) {
    if (isPresentationTool(evt?.toolName)) {
      const stub = buildPresentationStubFromArgs(getPairedCallArgs(index))
      if (stub) {
        evt.result = stub
        emit('heightChange', { target: null, preserveViewport: true })
        return
      }
    }
    message.error('加载结果失败')
  } finally {
    const finalSet = new Set(loadingFullResult.value)
    finalSet.delete(index)
    loadingFullResult.value = finalSet
  }
}

function formatLength(n) {
  if (typeof n !== 'number' || n <= 0) return ''
  if (n < 1024) return `${n} 字符`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  return `${(n / 1024 / 1024).toFixed(1)} MB`
}
</script>

<style lang="less" scoped>
.tool-calls-wrap {
  width: 100%;
  min-width: 0;
  max-width: 100%;
}

.presentation-page-block {
  margin-top: 10px;
}

.presentation-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 14px;
  border: 1px solid var(--color-hairline, #e5e7eb);
  border-radius: 12px;
  background: var(--color-canvas-soft, #fafafa);
  color: var(--color-mute, #737373);
  font-size: 13px;
}

.presentation-page-block + .tool-calls-group,
.presentation-page-block + .presentation-page-block {
  margin-top: 10px;
}

.tool-calls-group {
  width: 100%;
  min-width: 0;
  max-width: 100%;
  margin-top: 8px;
  padding: 10px 12px;
  background: var(--color-canvas-soft);
  border: 1px solid lightgray;
  border-radius: 8px;
  overflow: hidden;
}

.tool-calls-summary {
  appearance: none;
  display: flex;
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
  max-width: 100%;
  min-width: 0;

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
  min-width: 0;
  overflow: hidden;
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
  min-width: 0;

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
    word-break: break-word;
    strong { color: var(--main-700); font-weight: 600; word-break: break-word; }
  }

  .event-tool-icon {
    color: var(--main-600);
    font-size: 13px;
    vertical-align: -1px;
  }

  .event-text {
    color: var(--gray-500);
    flex: 1;
    min-width: 0;
    word-break: break-word;
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
    &:disabled { cursor: progress; opacity: 0.7; }
  }

  .result-size-hint {
    flex-shrink: 0;
    padding: 1px 6px;
    border-radius: 4px;
    font-size: 11px;
    color: var(--gray-500);
    background: var(--gray-50);
  }

  .expand-icon-sm {
    font-size: 10px;
    transition: transform 0.2s ease;
    &.expanded { transform: rotate(90deg); }
  }
}

.event-args-raw {
  margin: 2px 0 5px 21px;
  padding: 4px 8px;
  color: var(--gray-600);
  background: var(--gray-25);
  border-radius: 4px;
  font-family: var(--font-mono, 'Menlo', 'Monaco', monospace);
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
}

.args-actions {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  margin-left: auto;
  flex-shrink: 0;
}

.args-toggle-btn,
.args-detail-btn {
  appearance: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 3px;
  padding: 0 4px;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: var(--gray-400);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.args-toggle-btn:hover,
.args-detail-btn:hover { color: var(--gray-600); background: var(--gray-50); }
.args-detail-btn { width: 20px; height: 20px; font-size: 13px; }

.args-modal-raw {
  max-height: 480px;
  margin: 0;
  padding: 10px;
  overflow: auto;
  color: var(--gray-700);
  background: var(--gray-25);
  border-radius: 6px;
  font-family: var(--font-mono, 'Menlo', 'Monaco', monospace);
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
}

.result-detail {
  margin: 4px 0 4px 21px;
  min-width: 0;
  overflow: hidden;
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
