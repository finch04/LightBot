import { computed } from 'vue'

function isContainerNodeType(type) {
  return type === 'loop' || type === 'batch'
}

/**
 * 将 workflow nodeEvents 合并为时间线步骤
 * @param {import('vue').Ref|import('vue').ComputedRef|Array} nodeEventsSource
 */
export function useWorkflowNodeSteps(nodeEventsSource) {
  const nodeSteps = computed(() => {
    const events = typeof nodeEventsSource === 'function'
      ? nodeEventsSource()
      : (nodeEventsSource?.value ?? nodeEventsSource)
    if (!events?.length) return []

    const steps = []
    const runningByNodeId = new Map()
    const stepByIndex = new Map()
    const containerStack = []

    for (const e of events) {
      if (e.type === 'workflow_node_start' && e.nodeId) {
        const isContainerStart = !e.parentNodeId && isContainerNodeType(e.nodeType)
        const step = {
          nodeId: e.nodeId,
          nodeType: e.nodeType,
          nodeLabel: e.nodeLabel,
          input: e.input,
          stepIndex: e.stepIndex,
          status: 'running',
          parentNodeId: e.parentNodeId || null,
          iterationIndex: e.iterationIndex ?? null,
          isContainer: isContainerStart,
          children: isContainerStart ? [] : undefined,
        }
        if (e.parentNodeId && containerStack.length > 0) {
          const parent = containerStack[containerStack.length - 1]
          if (parent.children) parent.children.push(step)
        } else {
          steps.push(step)
        }
        runningByNodeId.set(e.nodeId, step)
        if (e.stepIndex != null) stepByIndex.set(e.stepIndex, step)
        if (isContainerStart) containerStack.push(step)
      } else if (e.type === 'workflow_node_complete' && e.nodeId) {
        let step = (e.stepIndex != null ? stepByIndex.get(e.stepIndex) : null) || runningByNodeId.get(e.nodeId)
        if (!step) {
          const isChild = !!e.parentNodeId
          step = {
            nodeId: e.nodeId,
            nodeType: e.nodeType,
            nodeLabel: e.nodeLabel,
            stepIndex: e.stepIndex,
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
        step.detail = e.detail
        step.durationMs = e.durationMs
        step.success = e.success
        step.outputs = e.outputs
        step.nextNodeId = e.nextNodeId
        if (e.suspended) {
          step.status = 'suspended'
        } else {
          step.status = e.success === false ? 'failed' : 'done'
        }
        if (e.isContainer != null) step.isContainer = e.isContainer
        runningByNodeId.delete(e.nodeId)
        if (e.stepIndex != null) stepByIndex.set(e.stepIndex, step)
        if (step.isContainer && containerStack.length > 0 && containerStack[containerStack.length - 1].nodeId === e.nodeId) {
          containerStack.pop()
        }
      }
    }
    return steps
  })

  return { nodeSteps, isContainerNodeType }
}

export function getNodeTypeName(type) {
  const map = {
    start: '开始', end: '结束', llm: '大模型', condition: '条件判断',
    retrieval: '知识检索', tool: '工具调用', classifier: '意图分类',
    api: 'API', loop: '循环', variable: '变量', batch: '批处理',
    script: '脚本', mcp: 'MCP', input: '输入', confirm: '人工确认', output: '输出',
    variable_handle: '变量处理', parameter_extractor: '参数提取',
    app_component: '应用组件', code: '代码',
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
