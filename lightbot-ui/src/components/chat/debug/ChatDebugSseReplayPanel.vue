<template>
  <div class="debug-sse-panel">
    <div class="debug-panel-toolbar">
      <a-button type="primary" :loading="isReplaying" @click="startReplay">
        {{ isReplaying ? '回放中' : '开始回放' }}
      </a-button>
      <a-button :disabled="!isReplaying" @click="stopReplay">停止</a-button>
      <a-select v-model:value="replayInterval" style="width: 152px" :options="intervalOptions" />
      <a-button @click="generateSseLog">生成组合 SSE</a-button>
      <a-button @click="clearAll">清空</a-button>
    </div>

    <div class="debug-sse-builder">
      <div class="debug-editor-label">事件积木（可多选组合）</div>
      <a-checkbox-group v-model:value="selectedBlockIds" class="debug-sse-blocks">
        <a-checkbox v-for="block in eventBlocks" :key="block.id" :value="block.id" class="debug-sse-block">
          <span class="debug-sse-block-title">{{ block.label }}</span>
          <span class="debug-sse-block-desc">{{ block.description }}</span>
        </a-checkbox>
      </a-checkbox-group>
      <div class="debug-tool-composer">
        <div class="debug-editor-label">普通工具组合</div>
        <a-select
          v-model:value="selectedToolNames"
          mode="multiple"
          allow-clear
          :options="toolOptions"
          placeholder="选择任意工具，按选择顺序组合 tool_call / tool_result"
          style="width: 100%"
        />
      </div>
      <div class="debug-sse-body-row">
        <div class="debug-sse-body-field">
          <div class="debug-editor-label">助手正文（Markdown）</div>
          <a-textarea v-model:value="bodyContent" :rows="4" class="debug-json-textarea" />
        </div>
        <div class="debug-sse-options">
          <a-checkbox v-model:checked="includeMetadata">追加 metadata 快照</a-checkbox>
          <span>用于一并检查实时 SSE 与历史消息回显。</span>
        </div>
      </div>
    </div>

    <div class="debug-sse-grid">
      <div class="debug-sse-col">
        <div class="debug-editor-label">SSE 原始日志（可编辑）</div>
        <a-textarea v-model:value="sseLog" :rows="14" class="debug-json-textarea" placeholder="粘贴 data: 行..." />
      </div>
      <div class="debug-sse-col">
        <div class="debug-editor-label">基础消息 JSON（可选）</div>
        <a-textarea v-model:value="baseJson" :rows="14" class="debug-json-textarea" />
      </div>
    </div>
    <a-alert v-if="parseError" type="error" :message="parseError" show-icon closable class="debug-parse-error" @close="parseError = ''" />
    <div class="debug-sse-hint">回放按照 SSE 原始行顺序逐条推送；间隔设为“立即”可快速得到最终渲染结果。</div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, ref } from 'vue'
import { message } from 'ant-design-vue'
import {
  apiMessageToEditorJson,
  createDefaultApiMessage,
  editorJsonToApiMessage,
} from '@/utils/chat/debug/debugMessageBuilder'
import { parseSseDebugLog, buildApiMessageFromSse } from '@/utils/chat/debug/debugSseReplay'
import { getToolDisplayName } from '@/components/toolRegistry'
import {
  getToolSampleEvent,
  getToolSelectOptions,
  UNREGISTERED_TOOL_NAME,
} from '@/utils/chat/debug/toolDebugSamples'

const emit = defineEmits(['preview'])

const eventBlocks = [
  {
    id: 'delegated-subagent',
    label: '委派 SubAgent',
    description: '旧协议 + 子工具完整链路',
    events: [
      { type: 'subagent_call', subagentName: 'research-agent', displayName: '研究助手', task: '总结 智元 核心模块', contentOffset: 0, delegationIndex: 0 },
      { type: 'subagent_tool_call', subagentName: 'research-agent', toolName: 'query_knowledge', toolDisplayName: '知识库检索', args: '{"query":"智元 模块"}', contentOffset: 0, delegationIndex: 0 },
      { type: 'subagent_tool_result', subagentName: 'research-agent', toolName: 'query_knowledge', toolDisplayName: '知识库检索', result: '{"total":3}', contentOffset: 0, delegationIndex: 0 },
      { type: 'subagent_result', subagentName: 'research-agent', contentOffset: 0, delegationIndex: 0, result: '{"reply":"## 调研结论\\n\\n- Agent\\n- RAG\\n- Workflow"}' },
    ],
  },
  {
    id: 'batch-subagent',
    label: '批次 SubAgents',
    description: '新协议 + 并行任务 + 子工具',
    events: [
      { type: 'subagent_batch_start', batch_id: 'debug-batch-001', mode: 'parallel', aggregation: 'return_all', delegationIndex: 0, contentOffset: 0, tasks: [
        { task_index: 0, task_id: 'debug-task-research', subagent_name: 'research-agent', task: '调研模块职责' },
        { task_index: 1, task_id: 'debug-task-review', subagent_name: 'review-agent', task: '检查实现边界' },
      ] },
      { type: 'subagent_task_start', batch_id: 'debug-batch-001', task_id: 'debug-task-research', task_index: 0, subagentName: 'research-agent', status: 'running', contentOffset: 0, delegationIndex: 0 },
      { type: 'subagent_tool_call', batch_id: 'debug-batch-001', task_id: 'debug-task-research', task_index: 0, subagentName: 'research-agent', toolName: 'web_search', toolDisplayName: '联网搜索', args: '{"query":"智元"}', contentOffset: 0, delegationIndex: 0 },
      { type: 'subagent_tool_result', batch_id: 'debug-batch-001', task_id: 'debug-task-research', task_index: 0, subagentName: 'research-agent', toolName: 'web_search', toolDisplayName: '联网搜索', result: '{"results":2}', contentOffset: 0, delegationIndex: 0 },
      { type: 'subagent_task_done', batch_id: 'debug-batch-001', task_id: 'debug-task-research', task_index: 0, subagentName: 'research-agent', status: 'completed', contentOffset: 0, delegationIndex: 0, result: { reply: '## 调研结果\\n\\n- 支持流式对话' } },
      { type: 'subagent_task_done', batch_id: 'debug-batch-001', task_id: 'debug-task-review', task_index: 1, subagentName: 'review-agent', status: 'completed', contentOffset: 0, delegationIndex: 0, result: { reply: '## 检查结果\\n\\n- 保持单一委派渲染链路' } },
      { type: 'subagent_batch_done', batch_id: 'debug-batch-001', status: 'completed', contentOffset: 0, delegationIndex: 0 },
    ],
  },
  {
    id: 'skill',
    label: 'Skill 启用',
    description: '顶部能力块',
    events: [
      { type: 'skill_active', contentOffset: 0, skills: [{ slug: 'code-review', name: 'code-review', displayName: '代码审查', description: '审查代码质量', builtin: true }] },
    ],
  },
  {
    id: 'workflow',
    label: '工作流',
    description: '节点开始、完成与收束',
    events: [
      { type: 'workflow_node_start', nodeId: 'debug-node-1', nodeType: 'llm', nodeLabel: '需求分析', contentOffset: 0 },
      { type: 'workflow_node_complete', nodeId: 'debug-node-1', nodeType: 'llm', nodeLabel: '需求分析', message: '执行完成', success: true, contentOffset: 0 },
      { type: 'workflow_complete', contentOffset: 0 },
    ],
  },
]

const intervalOptions = [
  { value: 0, label: '立即完成' },
  { value: 150, label: '150 ms / 条' },
  { value: 400, label: '400 ms / 条' },
  { value: 800, label: '800 ms / 条' },
  { value: 1500, label: '1.5 s / 条' },
]

// 与「工具渲染」页共用同一份真实工具下拉与结果样例，排除仅用于兜底测试的假工具。
const toolOptions = getToolSelectOptions().filter(option => option.value !== UNREGISTERED_TOOL_NAME)

const selectedBlockIds = ref(['batch-subagent', 'skill'])
const selectedToolNames = ref(['query_knowledge', 'web_search'])
const bodyContent = ref('根据执行结果，智元 已完成对应能力展示。')
const includeMetadata = ref(true)
const sseLog = ref('')
const baseJson = ref(apiMessageToEditorJson({
  ...createDefaultApiMessage(),
  content: '',
  metadata: { toolEvents: [], workflowEvents: [], reasoningContent: '' },
}))
const parseError = ref('')
const replayInterval = ref(400)
const isReplaying = ref(false)
let replayTimer = null

function getSelectedEvents() {
  const blockEvents = eventBlocks
    .filter(block => selectedBlockIds.value.includes(block.id))
    .flatMap(block => block.events.map(event => JSON.parse(JSON.stringify(event))))
  const toolEvents = selectedToolNames.value.flatMap((toolName) => {
    const sample = getToolSampleEvent(toolName)
    const displayName = getToolDisplayName(toolName)
    return [
      { type: 'tool_call', toolName, displayName, args: '{}', contentOffset: 0 },
      { ...sample, type: 'tool_result', toolName, displayName, contentOffset: 0 },
    ]
  })
  return [...toolEvents, ...blockEvents]
}

function generateSseLog() {
  const events = getSelectedEvents()
  const lines = events.map(event => `data:[STATUS]${JSON.stringify(event)}`)
  if (bodyContent.value) {
    lines.push(`data:${bodyContent.value.replace(/\n/g, '\\n')}`)
  }
  if (includeMetadata.value) {
    const workflowEvents = events.filter(event => String(event.type || '').startsWith('workflow_'))
    const toolEvents = events.filter(event => !String(event.type || '').startsWith('workflow_') && event.type !== 'reasoning_content')
    lines.push(`data:[METADATA]${JSON.stringify({ toolEvents, workflowEvents })}`)
  }
  sseLog.value = lines.join('\n')
}

function stopReplay() {
  if (replayTimer) {
    clearTimeout(replayTimer)
    replayTimer = null
  }
  isReplaying.value = false
}

function startReplay() {
  parseError.value = ''
  stopReplay()
  try {
    const base = editorJsonToApiMessage(baseJson.value)
    const parsed = parseSseDebugLog(sseLog.value)
    if (!parsed.entries.length) {
      throw new Error('请先生成或粘贴 SSE 日志')
    }
    isReplaying.value = true
    let cursor = 0
    const replayNext = () => {
      if (!isReplaying.value) return
      cursor += 1
      const consumed = parsed.entries.slice(0, cursor)
      const partial = {
        chunks: consumed.filter(item => item.kind === 'chunk').map(item => item.content),
        statusEvents: consumed.filter(item => item.kind === 'status').map(item => item.event),
        metadata: [...consumed].reverse().find(item => item.kind === 'metadata')?.metadata || null,
      }
      emit('preview', {
        apiMessage: buildApiMessageFromSse(base, partial),
        streaming: cursor < parsed.entries.length,
      })
      if (cursor >= parsed.entries.length) {
        stopReplay()
        message.success(`已回放 ${parsed.entries.length} 条 SSE 消息`)
        return
      }
      if (replayInterval.value === 0) {
        replayNext()
      } else {
        replayTimer = setTimeout(replayNext, replayInterval.value)
      }
    }
    replayNext()
  } catch (e) {
    parseError.value = e.message || '回放失败'
    emit('preview', null)
  }
}

function clearAll() {
  stopReplay()
  sseLog.value = ''
  emit('preview', null)
}

generateSseLog()
onBeforeUnmount(stopReplay)
</script>

<style scoped>
.debug-sse-panel { display: flex; flex-direction: column; gap: 12px; min-height: 0; }
.debug-panel-toolbar { display: flex; flex-wrap: wrap; gap: 8px; }
.debug-sse-builder { padding: 12px; border: 1px solid var(--color-hairline); border-radius: 8px; background: var(--color-canvas); }
.debug-sse-blocks { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 8px; width: 100%; }
.debug-sse-block { display: flex; margin-inline-start: 0; padding: 8px 10px; border: 1px solid var(--color-hairline); border-radius: 6px; }
.debug-sse-block-title, .debug-sse-block-desc { display: block; }
.debug-sse-block-title { color: var(--color-ink); font-weight: 600; }
.debug-sse-block-desc { margin-top: 2px; color: var(--gray-500); font-size: 12px; }
.debug-tool-composer { margin-top: 12px; }
.debug-sse-body-row { display: grid; grid-template-columns: minmax(0, 1fr) 220px; gap: 12px; margin-top: 12px; }
.debug-sse-options { display: flex; flex-direction: column; gap: 6px; justify-content: center; color: var(--gray-500); font-size: 12px; }
.debug-sse-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; min-height: 0; }
.debug-sse-col { display: flex; flex-direction: column; min-height: 0; }
.debug-editor-label { margin-bottom: 8px; color: var(--gray-700); font-size: 13px; font-weight: 600; }
.debug-json-textarea { font-family: 'Menlo', 'Monaco', 'Consolas', monospace; font-size: 12px; line-height: 1.6; }
.debug-parse-error { margin-top: 8px; }
.debug-sse-hint { color: var(--gray-500); font-size: 12px; }
@media (max-width: 960px) { .debug-sse-grid, .debug-sse-body-row { grid-template-columns: 1fr; } }
</style>
