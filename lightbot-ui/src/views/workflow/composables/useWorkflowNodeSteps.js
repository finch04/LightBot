import { computed } from 'vue'

function isContainerNodeType(type) {
  return type === 'loop' || type === 'batch' || type === 'app_component'
}

const GROUP_BUILTIN_NODE_TYPES = new Set(['loop_start', 'loop_end', 'batch_start', 'batch_end'])

function isHiddenContainerBuiltinEvent(event) {
  return !!event?.parentNodeId && GROUP_BUILTIN_NODE_TYPES.has(event.nodeType)
}

function getEventExecutionKey(event) {
  if (!event) return ''
  if (event.executionId) return String(event.executionId)
  if (event.stepIndex != null) return `step:${event.stepIndex}`
  return [
    event.parentNodeId || '-',
    event.nodeId || '-',
    event.iterationIndex ?? '-',
  ].join(':')
}

/**
 * 将 workflow nodeEvents 合并为时间线步骤
 * @param {import('vue').Ref|import('vue').ComputedRef|Array|Function} nodeEventsSource
 */
export function useWorkflowNodeSteps(nodeEventsSource) {
  const nodeSteps = computed(() => {
    const events = typeof nodeEventsSource === 'function'
      ? nodeEventsSource()
      : (nodeEventsSource?.value ?? nodeEventsSource)
    if (!events?.length) return []

    const steps = []
    const runningByExecutionKey = new Map()
    const stepByIndex = new Map()
    const containerStack = []

    for (const e of events) {
      if (isHiddenContainerBuiltinEvent(e)) continue
      const executionKey = getEventExecutionKey(e)
      if (e.type === 'workflow_node_start' && e.nodeId) {
        const isContainerStart = !e.parentNodeId && isContainerNodeType(e.nodeType)
        const step = {
          nodeId: e.nodeId,
          executionId: e.executionId || executionKey,
          nodeType: e.nodeType,
          nodeLabel: e.nodeLabel,
          input: e.input,
          stepIndex: e.stepIndex,
          stepKey: `start_${executionKey || steps.length}_${e.nodeId}`,
          status: 'running',
          parentNodeId: e.parentNodeId || null,
          iterationIndex: e.iterationIndex ?? null,
          isContainer: isContainerStart,
          children: isContainerStart ? [] : undefined,
          toolName: e.toolName || null,
        }
        if (e.parentNodeId && containerStack.length > 0) {
          const parent = containerStack[containerStack.length - 1]
          if (parent.children) parent.children.push(step)
        } else {
          steps.push(step)
        }
        runningByExecutionKey.set(executionKey, step)
        if (e.stepIndex != null) stepByIndex.set(e.stepIndex, step)
        if (isContainerStart) containerStack.push(step)
      } else if (e.type === 'workflow_node_complete' && e.nodeId) {
        let step = runningByExecutionKey.get(executionKey) || (e.stepIndex != null ? stepByIndex.get(e.stepIndex) : null)
        if (!step) {
          const isChild = !!e.parentNodeId
          step = {
            nodeId: e.nodeId,
            executionId: e.executionId || executionKey,
            nodeType: e.nodeType,
            nodeLabel: e.nodeLabel,
            stepIndex: e.stepIndex,
            stepKey: `complete_${executionKey || steps.length}_${e.nodeId}`,
            status: 'pending',
            parentNodeId: e.parentNodeId || null,
            iterationIndex: e.iterationIndex ?? null,
          }
          if (isChild && containerStack.length > 0) {
            const parent = containerStack[containerStack.length - 1]
            if (parent.children) parent.children.push(step)
          } else {
            steps.push(step)
          }
        }
        step.nodeType = e.nodeType ?? step.nodeType
        step.nodeLabel = e.nodeLabel ?? step.nodeLabel
        step.message = e.message
        step.userMessage = e.userMessage || (e.success === false ? e.message : null)
        step.failureReason = e.failureReason ?? step.failureReason
        step.detail = e.detail
        step.durationMs = e.durationMs
        step.success = e.success
        step.outputs = e.outputs
        step.traceData = e.traceData
        step.nextNodeId = e.nextNodeId
        step.toolName = e.toolName ?? step.toolName
        if (e.suspended) {
          step.status = 'suspended'
        } else {
          step.status = e.success === false ? 'failed' : 'done'
        }
        if (e.isContainer != null) step.isContainer = e.isContainer
        runningByExecutionKey.delete(executionKey)
        if (e.stepIndex != null) stepByIndex.set(e.stepIndex, step)
        if (step.isContainer && containerStack.length > 0 && containerStack[containerStack.length - 1].nodeId === e.nodeId) {
          containerStack.pop()
        }
      } else if (e.type === 'workflow_node_retry' && e.nodeId) {
        attachResilienceEvent(runningByExecutionKey, stepByIndex, executionKey, e, 'retry')
      } else if (e.type === 'workflow_node_failure' && e.nodeId) {
        attachResilienceEvent(runningByExecutionKey, stepByIndex, executionKey, e, 'failure')
      } else if (e.type === 'workflow_confirm_required' && e.nodeId) {
        const step = runningByExecutionKey.get(executionKey) || stepByIndex.get(e.stepIndex)
        if (step) {
          step.confirmForm = e.confirmForm
          if (!e.resolved && !e.submittedData) {
            step.status = 'suspended'
          }
        }
      }
    }
    return steps
  })

  return { nodeSteps, isContainerNodeType }
}

function attachResilienceEvent(runningByExecutionKey, stepByIndex, executionKey, event, kind) {
  let step = runningByExecutionKey.get(executionKey)
  if (!step && event.stepIndex != null) {
    step = stepByIndex.get(event.stepIndex)
  }
  if (!step) return
  if (!step.resilienceEvents) step.resilienceEvents = []
  step.resilienceEvents.push({ ...event, kind })
  step.lastResilienceMessage = event.message
  if (kind === 'failure') {
    step.failureReason = event.reason
  }
}

export function getNodeTypeName(type) {
  const map = {
    start: '开始', end: '结束', llm: '大模型', condition: '条件判断',
    retrieval: '知识检索', tool: '工具调用', classifier: '意图分类',
    api: 'API', loop: '循环', variable: '变量', batch: '批处理',
    script: '脚本', mcp: 'MCP', input: '输入', confirm: '人工确认', output: '输出',
    variable_handle: '变量处理', parameter_extractor: '参数提取',
    app_component: '应用组件',
    sub_agent: 'SubAgent',
    loop_start: '迭代开始', loop_end: '迭代结束',
    batch_start: '并行处理', batch_end: '并行结束',
  }
  return map[type] || type || '节点'
}

export function formatTestStatus(status) {
  const map = {
    running: '运行中',
    suspended: '已挂起',
    completed: '成功',
    failed: '失败',
  }
  return map[status] || status || '-'
}

export function formatTestDuration(ms) {
  if (ms == null) return '-'
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(1)}s`
}

export { isContainerNodeType }
