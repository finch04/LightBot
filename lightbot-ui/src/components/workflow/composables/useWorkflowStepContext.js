import { computed, toValue } from 'vue'
import {
  parseStepOutputs, buildAssignmentRows, listOutputKeys, previewValue,
  extractUserInputText, buildWorkflowToolEvent, canRenderWorkflowTool,
  hasWorkflowToolRenderer,
} from '../workflowStepUtils.js'

/**
 * 工作流步骤详情组件共享上下文
 * @param {import('vue').MaybeRefOrGetter<object>} stepSource
 */
export function useWorkflowStepContext(stepSource) {
  const step = computed(() => toValue(stepSource))

  const outputs = computed(() => parseStepOutputs(step.value?.outputs) || {})

  const filteredOutputs = computed(() => {
    const o = { ...outputs.value }
    delete o.llmOutput
    delete o.toolResultText
    if (step.value?.nodeType === 'parameter_extractor') delete o.extractRaw
    return o
  })

  const userInputText = computed(() => extractUserInputText(step.value))

  const endResultText = computed(() => {
    const r = outputs.value.result
    return r != null ? String(r) : ''
  })

  const outputText = computed(() => {
    const o = outputs.value.output
    return o != null ? String(o) : ''
  })

  const assignmentRows = computed(() => buildAssignmentRows(outputs.value))

  const extractRows = computed(() => {
    const keys = listOutputKeys(outputs.value)
    return keys.map(key => ({ key, value: previewValue(outputs.value[key], 160) }))
  })

  const extractRawText = computed(() => {
    const fromOutputs = outputs.value.extractRaw
    if (fromOutputs != null && String(fromOutputs).trim()) {
      return String(fromOutputs)
    }
    const fromTrace = step.value?.traceData?.extractRaw
    if (fromTrace != null && String(fromTrace).trim()) {
      return String(fromTrace)
    }
    return ''
  })

  const tokenSummary = computed(() => {
    const td = step.value?.traceData
    if (!td) return ''
    const inTok = td.inputTokens
    const outTok = td.outputTokens
    if (inTok == null && outTok == null) return ''
    return `Token ${inTok ?? 0} → ${outTok ?? 0}`
  })

  const retrievalChunks = computed(() => {
    const chunks = outputs.value.retrievalChunks
    return Array.isArray(chunks) ? chunks : []
  })

  const workflowToolEvent = computed(() => buildWorkflowToolEvent(step.value))
  const workflowToolRenderable = computed(() => canRenderWorkflowTool(workflowToolEvent.value))
  const workflowToolHasRegistryRenderer = computed(() => hasWorkflowToolRenderer(step.value))
  const mcpPseudoEvent = computed(() => buildWorkflowToolEvent(step.value))

  const toolRawResult = computed(() => {
    const raw = outputs.value.output ?? outputs.value.mcpResult ?? outputs.value.toolResultText
    return raw != null ? String(raw) : ''
  })

  return {
    step,
    outputs,
    filteredOutputs,
    userInputText,
    endResultText,
    outputText,
    assignmentRows,
    extractRows,
    extractRawText,
    tokenSummary,
    retrievalChunks,
    workflowToolEvent,
    workflowToolRenderable,
    workflowToolHasRegistryRenderer,
    mcpPseudoEvent,
    toolRawResult,
  }
}

export function handleLabel(handle) {
  if (!handle) return ''
  if (String(handle).endsWith('_default')) return '都未命中'
  const parts = String(handle).split('_')
  return parts.length > 1 ? parts.slice(1).join('_') : handle
}

export function formatScore(score) {
  const n = Number(score)
  if (Number.isNaN(n)) return ''
  return n <= 1 ? `${Math.round(n * 100)}%` : String(n)
}

export function formatMessages(msgs) {
  if (!Array.isArray(msgs)) return ''
  return msgs.map(m => `[${m.role}] ${m.content}`).join('\n\n')
}
