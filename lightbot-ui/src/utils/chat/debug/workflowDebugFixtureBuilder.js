import { createDefaultApiMessage } from './debugMessageBuilder'

export const WORKFLOW_DEBUG_SCENARIOS = [
  {
    id: 'linear-basic',
    title: '线性基础流程',
    description: 'start -> llm -> end，验证最小合法 DAG',
    tags: ['基础', '合法'],
    builder: buildLinearBasicFixture,
  },
  {
    id: 'rag-answer',
    title: 'RAG 问答流程',
    description: 'start -> retrieval -> llm -> end，验证检索入参与结果回显',
    tags: ['RAG', '合法'],
    builder: buildRagAnswerFixture,
  },
  {
    id: 'condition-branch',
    title: '条件分支流程',
    description: '条件节点分出两条路径并汇聚到 end',
    tags: ['分支', '合法'],
    builder: buildConditionBranchFixture,
  },
  {
    id: 'batch-basic',
    title: '批处理容器流程',
    description: '批处理父节点内包含并发开始、脚本子节点、并发结束',
    tags: ['批处理', '父子节点'],
    builder: buildBatchBasicFixture,
  },
  {
    id: 'loop-basic',
    title: '循环容器流程',
    description: '循环父节点内包含迭代开始、脚本子节点、迭代结束',
    tags: ['循环', '父子节点'],
    builder: buildLoopBasicFixture,
  },
  {
    id: 'confirm-suspended',
    title: '人工确认挂起',
    description: 'confirm_required + suspended，验证时间线和对话挂起态',
    tags: ['人工确认', '挂起'],
    builder: buildConfirmSuspendedFixture,
  },
  {
    id: 'failure-retry',
    title: '失败与重试',
    description: 'API 节点先重试再失败，验证错误态和 retry 轨迹',
    tags: ['失败', '重试'],
    builder: buildFailureRetryFixture,
  },
  {
    id: 'invalid-start-order',
    title: '非法样例：开始节点不在第一位',
    description: '仅用于验证 Debug 本地校验提示，不作为正常预览',
    tags: ['非法样例'],
    invalid: true,
    builder: buildInvalidStartOrderFixture,
  },
]

const NODE_LABELS = {
  start: '开始',
  end: '结束',
  llm: '大模型生成',
  retrieval: '知识库检索',
  condition: '条件判断',
  script: '脚本处理',
  api: '外部 API',
  confirm: '人工确认',
  batch: '批处理',
  loop: '循环',
  batch_start: '并行处理',
  batch_end: '并行结束',
  loop_start: '迭代开始',
  loop_end: '迭代结束',
}

function node(id, type, x, y, data = {}, extra = {}) {
  return {
    id,
    type,
    position: { x, y },
    data: {
      label: data.label || NODE_LABELS[type] || type,
      ...data,
    },
    ...extra,
  }
}

function groupNode(id, type, x, y, label) {
  return node(id, type, x, y, { label }, {
    style: { width: '560px', height: '380px' },
    zIndex: 0,
  })
}

function childNode(id, type, parentNode, x, y, data = {}) {
  return node(id, type, x, y, {
    groupId: parentNode,
    ...data,
  }, {
    parentNode,
    extent: 'parent',
    expandParent: true,
    zIndex: 10,
  })
}

function edge(source, target, extra = {}) {
  return {
    id: `${source}->${target}`,
    source,
    target,
    type: 'workflow-bezier',
    ...extra,
  }
}

function pairEvent(event, stepIndex) {
  const durationMs = event.durationMs ?? 120
  return [
    {
      type: 'workflow_node_start',
      nodeId: event.nodeId,
      nodeType: event.nodeType,
      nodeLabel: event.nodeLabel,
      parentNodeId: event.parentNodeId,
      stepIndex,
      input: event.input,
      toolName: event.toolName,
    },
    {
      type: 'workflow_node_complete',
      nodeId: event.nodeId,
      nodeType: event.nodeType,
      nodeLabel: event.nodeLabel,
      parentNodeId: event.parentNodeId,
      stepIndex,
      success: event.success !== false,
      suspended: event.suspended,
      durationMs,
      outputs: event.outputs,
      message: event.message || `${event.nodeLabel} 执行完成`,
      failureReason: event.failureReason,
      userMessage: event.userMessage,
      traceData: event.traceData,
      nextNodeId: event.nextNodeId,
      toolName: event.toolName,
    },
  ].map((item) => Object.fromEntries(Object.entries(item).filter(([, value]) => value !== undefined)))
}

function buildEvents(steps, { success = true } = {}) {
  const events = []
  steps.forEach((step, index) => {
    events.push(...pairEvent(step, index))
  })
  events.push({ type: 'workflow_complete', success })
  return events
}

function buildMessage(fixture) {
  const base = createDefaultApiMessage()
  return {
    ...base,
    content: fixture.content || `以下为「${fixture.title}」的工作流渲染预览。`,
    metadata: {
      ...base.metadata,
      workflowEvents: fixture.events,
    },
  }
}

function finalizeFixture(fixture) {
  const message = buildMessage(fixture)
  return {
    ...fixture,
    payload: {
      graph: fixture.graph,
      nodeEvents: fixture.events,
      message,
    },
  }
}

export function buildWorkflowDebugFixture(scenarioId = 'linear-basic') {
  const scenario = WORKFLOW_DEBUG_SCENARIOS.find((item) => item.id === scenarioId) || WORKFLOW_DEBUG_SCENARIOS[0]
  return finalizeFixture({
    id: scenario.id,
    title: scenario.title,
    description: scenario.description,
    tags: scenario.tags,
    invalid: !!scenario.invalid,
    expected: { valid: !scenario.invalid },
    ...scenario.builder(),
  })
}

export function getWorkflowDebugScenarioOptions() {
  return WORKFLOW_DEBUG_SCENARIOS.map((scenario) => ({
    value: scenario.id,
    label: scenario.title,
  }))
}

export const WORKFLOW_ADVANCED_NODE_TYPE_OPTIONS = [
  { value: 'retrieval', label: '知识检索' },
  { value: 'llm', label: '大模型' },
  { value: 'condition', label: '条件判断' },
  { value: 'script', label: '脚本' },
  { value: 'api', label: '外部 API' },
  { value: 'confirm', label: '人工确认' },
  { value: 'batch', label: '批处理容器' },
  { value: 'loop', label: '循环容器' },
]

export function buildWorkflowDebugFixtureFromNodeTypes(nodeTypes = [], options = {}) {
  const steps = normalizeAdvancedNodeTypes(nodeTypes)
  const nodes = [node('start', 'start', 80, 240, { label: '开始' })]
  const edges = []
  const events = [
    ...pairEvent({
      nodeId: 'start',
      nodeType: 'start',
      nodeLabel: '开始',
      outputs: { query: 'Debug advanced workflow assembly' },
      message: '工作流已启动',
    }, 0),
  ]
  let previousExitNodeId = 'start'
  let x = 300
  let stepIndex = 1

  steps.forEach((type, index) => {
    if (type === 'batch') {
      const result = appendAdvancedBatch(nodes, edges, events, previousExitNodeId, x, stepIndex, index)
      previousExitNodeId = result.exitNodeId
      x += 660
      stepIndex = result.nextStepIndex
      return
    }
    if (type === 'loop') {
      const result = appendAdvancedLoop(nodes, edges, events, previousExitNodeId, x, stepIndex, index)
      previousExitNodeId = result.exitNodeId
      x += 660
      stepIndex = result.nextStepIndex
      return
    }

    const id = `advanced-${type}-${index + 1}`
    const label = ADVANCED_NODE_LABELS[type] || NODE_LABELS[type] || type
    nodes.push(node(id, type, x, 226, advancedNodeData(type, label)))
    edges.push(edge(previousExitNodeId, id))
    events.push(...pairEvent(advancedNodeEvent(id, type, label), stepIndex))
    previousExitNodeId = id
    x += 250
    stepIndex += 1
  })

  nodes.push(node('end', 'end', x, 240, { label: '结束' }))
  edges.push(edge(previousExitNodeId, 'end'))
  events.push(...pairEvent({
    nodeId: 'end',
    nodeType: 'end',
    nodeLabel: '结束',
    outputs: { answer: '高级拼装工作流本地回放完成。' },
    durationMs: 8,
  }, stepIndex))
  events.push({ type: 'workflow_complete', success: true })

  return finalizeFixture({
    id: options.id || 'advanced-custom',
    title: options.title || '高级拼装：合法工作流',
    description: '由 Debug 本地生成器构造完整图数据、父子节点和 workflowEvents，可直接用于校验、画布预览与轨迹回放。',
    tags: ['高级拼装', '合法', '本地生成'],
    expected: { valid: true },
    graph: { nodes, edges },
    events,
    content: '以下为高级拼装工作流的本地回放预览。',
  })
}

const ADVANCED_NODE_LABELS = {
  retrieval: '检索输入资料',
  llm: '生成处理结果',
  condition: '判断处理路径',
  script: '结构化脚本处理',
  api: '调用外部接口',
  confirm: '等待人工确认',
  batch: '批处理容器',
  loop: '循环容器',
}

function normalizeAdvancedNodeTypes(nodeTypes) {
  const normalized = (nodeTypes || [])
    .filter((type) => type && !['start', 'end', 'batch_start', 'batch_end', 'loop_start', 'loop_end'].includes(type))
    .slice(0, 8)
  return normalized.length ? normalized : ['retrieval', 'llm']
}

function advancedNodeData(type, label) {
  const common = { label }
  if (type === 'retrieval') return { ...common, knowledgeName: 'Debug Knowledge Base', topK: 3 }
  if (type === 'llm') return { ...common, modelName: 'Debug Mock Model', prompt: '{{workflow.input}}' }
  if (type === 'condition') return { ...common, expression: 'score >= 0.7' }
  if (type === 'script') return { ...common, language: 'javascript' }
  if (type === 'api') return { ...common, url: 'https://api.example.com/debug' }
  if (type === 'confirm') return { ...common, message: '是否继续执行后续节点？' }
  return common
}

function advancedNodeEvent(nodeId, type, label, extra = {}) {
  const base = {
    nodeId,
    nodeType: type,
    nodeLabel: label,
    durationMs: extra.durationMs ?? 180,
    input: extra.input || { value: 'debug-input' },
    outputs: extra.outputs || { value: `${label} output` },
    message: `${label} 执行完成`,
    ...extra,
  }
  if (type === 'retrieval') {
    base.outputs = {
      query: 'Debug advanced workflow assembly',
      results: [
        { documentName: 'workflow-debug.md', score: 0.91, content: '高级拼装需要生成合法图和完整轨迹。' },
      ],
    }
    base.message = '检索到 1 条本地模拟资料'
  }
  if (type === 'llm') {
    base.outputs = { text: '已根据上游结果生成调试响应。' }
    base.durationMs = 860
  }
  if (type === 'condition') {
    base.outputs = { matched: true, branch: 'default' }
    base.nextNodeId = extra.nextNodeId
  }
  if (type === 'confirm') {
    base.outputs = { approved: true }
    base.durationMs = 40
  }
  return base
}

function appendAdvancedBatch(nodes, edges, events, previousExitNodeId, x, stepIndex, index) {
  const groupId = `advanced-batch-${index + 1}`
  const group = groupNode(groupId, 'batch', x, 80, '批处理容器')
  const startId = `${groupId}_batch_start`
  const scriptAId = `${groupId}-script-a`
  const scriptBId = `${groupId}-script-b`
  const endId = `${groupId}_batch_end`
  nodes.push(
    group,
    childNode(startId, 'batch_start', groupId, 48, 150, { label: '并发开始' }),
    childNode(scriptAId, 'script', groupId, 220, 88, { label: '处理分片 A', language: 'javascript' }),
    childNode(scriptBId, 'script', groupId, 220, 220, { label: '处理分片 B', language: 'javascript' }),
    childNode(endId, 'batch_end', groupId, 444, 150, { label: '并发结束' }),
  )
  edges.push(
    edge(previousExitNodeId, startId),
    edge(startId, scriptAId),
    edge(startId, scriptBId),
    edge(scriptAId, endId),
    edge(scriptBId, endId),
  )
  events.push(
    { type: 'workflow_node_start', nodeId: groupId, nodeType: 'batch', nodeLabel: '批处理容器', stepIndex, input: { count: 2 } },
    ...pairEvent({ nodeId: startId, nodeType: 'batch_start', nodeLabel: '并发开始', parentNodeId: groupId, outputs: { started: true }, durationMs: 6 }, stepIndex + 1),
    ...pairEvent({ nodeId: scriptAId, nodeType: 'script', nodeLabel: '处理分片 A', parentNodeId: groupId, input: { item: 'A' }, outputs: { summary: 'A 处理完成' }, durationMs: 210 }, stepIndex + 2),
    ...pairEvent({ nodeId: scriptBId, nodeType: 'script', nodeLabel: '处理分片 B', parentNodeId: groupId, input: { item: 'B' }, outputs: { summary: 'B 处理完成' }, durationMs: 230 }, stepIndex + 3),
    ...pairEvent({ nodeId: endId, nodeType: 'batch_end', nodeLabel: '并发结束', parentNodeId: groupId, outputs: { merged: ['A 处理完成', 'B 处理完成'] }, durationMs: 8 }, stepIndex + 4),
    { type: 'workflow_node_complete', nodeId: groupId, nodeType: 'batch', nodeLabel: '批处理容器', stepIndex, success: true, durationMs: 520, outputs: { count: 2 }, message: '批处理容器执行完成' },
  )
  return { exitNodeId: endId, nextStepIndex: stepIndex + 5 }
}

function appendAdvancedLoop(nodes, edges, events, previousExitNodeId, x, stepIndex, index) {
  const groupId = `advanced-loop-${index + 1}`
  const group = groupNode(groupId, 'loop', x, 80, '循环容器')
  const startId = `${groupId}_loop_start`
  const scriptId = `${groupId}-script`
  const endId = `${groupId}_loop_end`
  nodes.push(
    group,
    childNode(startId, 'loop_start', groupId, 48, 150, { label: '迭代开始' }),
    childNode(scriptId, 'script', groupId, 232, 150, { label: '处理当前项', language: 'javascript' }),
    childNode(endId, 'loop_end', groupId, 444, 150, { label: '迭代结束' }),
  )
  edges.push(
    edge(previousExitNodeId, startId),
    edge(startId, scriptId),
    edge(scriptId, endId),
  )
  events.push(
    { type: 'workflow_node_start', nodeId: groupId, nodeType: 'loop', nodeLabel: '循环容器', stepIndex, input: { count: 2 } },
    ...pairEvent({ nodeId: startId, nodeType: 'loop_start', nodeLabel: '迭代开始', parentNodeId: groupId, outputs: { iterationIndex: 0 }, durationMs: 6 }, stepIndex + 1),
    ...pairEvent({ nodeId: scriptId, nodeType: 'script', nodeLabel: '处理当前项', parentNodeId: groupId, input: { item: 'first' }, outputs: { result: '第一轮处理完成' }, durationMs: 180 }, stepIndex + 2),
    ...pairEvent({ nodeId: scriptId, nodeType: 'script', nodeLabel: '处理当前项', parentNodeId: groupId, input: { item: 'second' }, outputs: { result: '第二轮处理完成' }, durationMs: 190 }, stepIndex + 3),
    ...pairEvent({ nodeId: endId, nodeType: 'loop_end', nodeLabel: '迭代结束', parentNodeId: groupId, outputs: { finished: true }, durationMs: 8 }, stepIndex + 4),
    { type: 'workflow_node_complete', nodeId: groupId, nodeType: 'loop', nodeLabel: '循环容器', stepIndex, success: true, durationMs: 620, outputs: { iterations: 2 }, message: '循环容器执行完成' },
  )
  return { exitNodeId: endId, nextStepIndex: stepIndex + 5 }
}

function buildLinearBasicFixture() {
  const nodes = [
    node('start', 'start', 80, 160),
    node('llm-summary', 'llm', 300, 148, { label: '大模型总结', prompt: '{{query}}' }),
    node('end', 'end', 560, 160),
  ]
  const edges = [
    edge('start', 'llm-summary'),
    edge('llm-summary', 'end'),
  ]
  const events = buildEvents([
    { nodeId: 'start', nodeType: 'start', nodeLabel: '开始', outputs: { query: '总结 智元 Debug 能力' }, message: '工作流已启动' },
    { nodeId: 'llm-summary', nodeType: 'llm', nodeLabel: '大模型总结', input: { prompt: '总结 智元 Debug 能力' }, outputs: { text: 'Debug Lab 用于验证前端渲染链路。' }, durationMs: 860 },
    { nodeId: 'end', nodeType: 'end', nodeLabel: '结束', outputs: { answer: 'Debug Lab 可复现工作流渲染问题。' }, durationMs: 8 },
  ])
  return { graph: { nodes, edges }, events }
}

function buildRagAnswerFixture() {
  const nodes = [
    node('start', 'start', 80, 160),
    node('retrieval-docs', 'retrieval', 300, 146, { label: '检索 Debug 文档', knowledgeName: '智元 Docs' }),
    node('llm-answer', 'llm', 560, 146, { label: '生成答案', modelName: 'Debug Mock Model' }),
    node('end', 'end', 820, 160),
  ]
  const edges = [
    edge('start', 'retrieval-docs'),
    edge('retrieval-docs', 'llm-answer'),
    edge('llm-answer', 'end'),
  ]
  const events = buildEvents([
    { nodeId: 'start', nodeType: 'start', nodeLabel: '开始', outputs: { query: '工作流 Debug 如何测试父子节点？' } },
    {
      nodeId: 'retrieval-docs',
      nodeType: 'retrieval',
      nodeLabel: '检索 Debug 文档',
      input: { query: '工作流 Debug 如何测试父子节点？' },
      outputs: {
        query: '工作流 Debug 如何测试父子节点？',
        results: [
          { documentName: 'Debug Lab 扩展设计.md', score: 0.94, content: '工作流 Tab 应覆盖父子节点、容器节点、轨迹回放。' },
          { documentName: 'Workflow 渲染规范.md', score: 0.89, content: '容器节点内部包含内置开始和结束节点。' },
        ],
      },
      durationMs: 320,
      message: '检索到 2 条相关文档',
    },
    {
      nodeId: 'llm-answer',
      nodeType: 'llm',
      nodeLabel: '生成答案',
      input: { prompt: '基于检索结果回答用户问题' },
      outputs: { text: '应使用合法 fixture 驱动画布、时间线和对话回显三条链路。' },
      durationMs: 1280,
    },
    { nodeId: 'end', nodeType: 'end', nodeLabel: '结束', outputs: { answer: '已完成 RAG 工作流渲染预览。' } },
  ])
  return { graph: { nodes, edges }, events }
}

function buildConditionBranchFixture() {
  const nodes = [
    node('start', 'start', 80, 190),
    node('condition-score', 'condition', 300, 178, {
      label: '置信度判断',
      conditionGroups: [
        {
          id: 'cg_high',
          label: '高置信',
          relation: 'and',
          rules: [{ id: 'r1', variable: '{{score}}', operator: 'contains', value: '0.7' }],
        },
      ],
    }),
    node('llm-high', 'llm', 560, 80, { label: '高置信回答' }),
    node('llm-low', 'llm', 560, 280, { label: '低置信澄清' }),
    node('end', 'end', 840, 190),
  ]
  const edges = [
    edge('start', 'condition-score'),
    edge('condition-score', 'llm-high', { sourceHandle: 'condition-score_cg_high' }),
    edge('condition-score', 'llm-low', { sourceHandle: 'condition-score_default' }),
    edge('llm-high', 'end'),
    edge('llm-low', 'end'),
  ]
  const events = buildEvents([
    { nodeId: 'start', nodeType: 'start', nodeLabel: '开始', outputs: { score: 0.92 } },
    { nodeId: 'condition-score', nodeType: 'condition', nodeLabel: '置信度判断', input: { score: 0.92 }, outputs: { matched: true, branch: 'high' }, nextNodeId: 'llm-high', durationMs: 14 },
    { nodeId: 'llm-high', nodeType: 'llm', nodeLabel: '高置信回答', outputs: { text: '直接回答用户问题。' }, durationMs: 760 },
    { nodeId: 'end', nodeType: 'end', nodeLabel: '结束', outputs: { branch: 'high' } },
  ])
  return { graph: { nodes, edges }, events }
}

function buildBatchBasicFixture() {
  const group = groupNode('batch-1', 'batch', 300, 80, '批量并行处理')
  const nodes = [
    node('start', 'start', 80, 245),
    group,
    childNode('batch-1_batch_start', 'batch_start', group.id, 48, 150),
    childNode('batch-script-a', 'script', group.id, 220, 88, { label: '分析问题 A' }),
    childNode('batch-script-b', 'script', group.id, 220, 220, { label: '分析问题 B' }),
    childNode('batch-1_batch_end', 'batch_end', group.id, 444, 150),
    node('llm-merge', 'llm', 930, 230, { label: '汇总批处理结果' }),
    node('end', 'end', 1190, 245),
  ]
  const edges = [
    edge('start', 'batch-1_batch_start'),
    edge('batch-1_batch_start', 'batch-script-a'),
    edge('batch-1_batch_start', 'batch-script-b'),
    edge('batch-script-a', 'batch-1_batch_end'),
    edge('batch-script-b', 'batch-1_batch_end'),
    edge('batch-1_batch_end', 'llm-merge'),
    edge('llm-merge', 'end'),
  ]
  const events = [
    ...pairEvent({ nodeId: 'start', nodeType: 'start', nodeLabel: '开始', outputs: { items: ['问题 A', '问题 B'] } }, 0),
    { type: 'workflow_node_start', nodeId: 'batch-1', nodeType: 'batch', nodeLabel: '批量并行处理', stepIndex: 1, input: { count: 2 } },
    ...pairEvent({ nodeId: 'batch-1_batch_start', nodeType: 'batch_start', nodeLabel: '并行处理', parentNodeId: 'batch-1', outputs: { started: true }, durationMs: 6 }, 2),
    ...pairEvent({ nodeId: 'batch-script-a', nodeType: 'script', nodeLabel: '分析问题 A', parentNodeId: 'batch-1', input: { item: '问题 A' }, outputs: { summary: 'A 的处理结果' }, durationMs: 210 }, 3),
    ...pairEvent({ nodeId: 'batch-script-b', nodeType: 'script', nodeLabel: '分析问题 B', parentNodeId: 'batch-1', input: { item: '问题 B' }, outputs: { summary: 'B 的处理结果' }, durationMs: 240 }, 4),
    ...pairEvent({ nodeId: 'batch-1_batch_end', nodeType: 'batch_end', nodeLabel: '并行结束', parentNodeId: 'batch-1', outputs: { merged: ['A 的处理结果', 'B 的处理结果'] }, durationMs: 8 }, 5),
    {
      type: 'workflow_node_complete',
      nodeId: 'batch-1',
      nodeType: 'batch',
      nodeLabel: '批量并行处理',
      stepIndex: 1,
      success: true,
      durationMs: 680,
      outputs: { count: 2 },
      message: '批处理容器执行完成',
    },
    ...pairEvent({ nodeId: 'llm-merge', nodeType: 'llm', nodeLabel: '汇总批处理结果', outputs: { text: '两个问题已经并行处理并汇总。' }, durationMs: 920 }, 6),
    ...pairEvent({ nodeId: 'end', nodeType: 'end', nodeLabel: '结束', outputs: { answer: '批处理完成。' } }, 7),
    { type: 'workflow_complete', success: true },
  ]
  return { graph: { nodes, edges }, events }
}

function buildLoopBasicFixture() {
  const group = groupNode('loop-1', 'loop', 300, 80, '循环处理')
  const nodes = [
    node('start', 'start', 80, 245),
    group,
    childNode('loop-1_loop_start', 'loop_start', group.id, 48, 150),
    childNode('loop-script', 'script', group.id, 232, 150, { label: '处理当前项' }),
    childNode('loop-1_loop_end', 'loop_end', group.id, 444, 150),
    node('llm-summary', 'llm', 930, 230, { label: '循环结果总结' }),
    node('end', 'end', 1190, 245),
  ]
  const edges = [
    edge('start', 'loop-1_loop_start'),
    edge('loop-1_loop_start', 'loop-script'),
    edge('loop-script', 'loop-1_loop_end'),
    edge('loop-1_loop_end', 'llm-summary'),
    edge('llm-summary', 'end'),
  ]
  const events = [
    ...pairEvent({ nodeId: 'start', nodeType: 'start', nodeLabel: '开始', outputs: { items: ['第一轮', '第二轮'] } }, 0),
    { type: 'workflow_node_start', nodeId: 'loop-1', nodeType: 'loop', nodeLabel: '循环处理', stepIndex: 1, input: { count: 2 } },
    ...pairEvent({ nodeId: 'loop-1_loop_start', nodeType: 'loop_start', nodeLabel: '迭代开始', parentNodeId: 'loop-1', outputs: { iterationIndex: 0 }, durationMs: 6 }, 2),
    ...pairEvent({ nodeId: 'loop-script', nodeType: 'script', nodeLabel: '处理当前项', parentNodeId: 'loop-1', input: { item: '第一轮' }, outputs: { result: '第一轮处理完成' }, durationMs: 180 }, 3),
    ...pairEvent({ nodeId: 'loop-script', nodeType: 'script', nodeLabel: '处理当前项', parentNodeId: 'loop-1', input: { item: '第二轮' }, outputs: { result: '第二轮处理完成' }, durationMs: 190 }, 4),
    ...pairEvent({ nodeId: 'loop-1_loop_end', nodeType: 'loop_end', nodeLabel: '迭代结束', parentNodeId: 'loop-1', outputs: { finished: true }, durationMs: 8 }, 5),
    {
      type: 'workflow_node_complete',
      nodeId: 'loop-1',
      nodeType: 'loop',
      nodeLabel: '循环处理',
      stepIndex: 1,
      success: true,
      durationMs: 760,
      outputs: { iterations: 2 },
      message: '循环容器执行完成',
    },
    ...pairEvent({ nodeId: 'llm-summary', nodeType: 'llm', nodeLabel: '循环结果总结', outputs: { text: '循环处理了两轮数据。' }, durationMs: 820 }, 6),
    ...pairEvent({ nodeId: 'end', nodeType: 'end', nodeLabel: '结束', outputs: { answer: '循环完成。' } }, 7),
    { type: 'workflow_complete', success: true },
  ]
  return { graph: { nodes, edges }, events }
}

function buildConfirmSuspendedFixture() {
  const nodes = [
    node('start', 'start', 80, 160),
    node('confirm-risk', 'confirm', 300, 146, { label: '确认是否继续', message: '是否继续执行后续节点？' }),
    node('llm-after-confirm', 'llm', 560, 146, { label: '确认后生成' }),
    node('end', 'end', 820, 160),
  ]
  const edges = [
    edge('start', 'confirm-risk'),
    edge('confirm-risk', 'llm-after-confirm'),
    edge('llm-after-confirm', 'end'),
  ]
  const events = [
    ...pairEvent({ nodeId: 'start', nodeType: 'start', nodeLabel: '开始', outputs: { query: '需要人工确认' } }, 0),
    {
      type: 'workflow_node_start',
      nodeId: 'confirm-risk',
      nodeType: 'confirm',
      nodeLabel: '确认是否继续',
      stepIndex: 1,
    },
    {
      type: 'workflow_confirm_required',
      nodeId: 'confirm-risk',
      nodeType: 'confirm',
      nodeLabel: '确认是否继续',
      stepIndex: 1,
      runId: 'debug-confirm-run',
      confirmForm: {
        hitlType: 'ask_user',
        question: '是否继续执行后续节点？',
        options: [
          { label: '继续', value: 'continue' },
          { label: '终止', value: 'abort' },
        ],
      },
    },
    {
      type: 'workflow_node_complete',
      nodeId: 'confirm-risk',
      nodeType: 'confirm',
      nodeLabel: '确认是否继续',
      stepIndex: 1,
      success: true,
      suspended: true,
      durationMs: 40,
      message: '等待用户确认',
    },
    {
      type: 'workflow_suspended',
      nodeId: 'confirm-risk',
      runId: 'debug-confirm-run',
      message: '工作流已挂起，等待人工确认',
    },
  ]
  return { graph: { nodes, edges }, events, content: '以下为人工确认挂起态预览。' }
}

function buildFailureRetryFixture() {
  const nodes = [
    node('start', 'start', 80, 160),
    node('api-status', 'api', 300, 146, { label: '调用外部 API', url: 'https://api.example.com/status' }),
    node('end', 'end', 560, 160),
  ]
  const edges = [
    edge('start', 'api-status'),
    edge('api-status', 'end'),
  ]
  const events = [
    ...pairEvent({ nodeId: 'start', nodeType: 'start', nodeLabel: '开始', outputs: { query: '检查外部接口' } }, 0),
    { type: 'workflow_node_start', nodeId: 'api-status', nodeType: 'api', nodeLabel: '调用外部 API', stepIndex: 1, input: { url: 'https://api.example.com/status' } },
    { type: 'workflow_node_retry', nodeId: 'api-status', nodeType: 'api', nodeLabel: '调用外部 API', stepIndex: 1, attempt: 1, maxAttempts: 2, message: '连接超时，正在重试' },
    { type: 'workflow_node_failure', nodeId: 'api-status', nodeType: 'api', nodeLabel: '调用外部 API', stepIndex: 1, reason: 'TIMEOUT', message: '外部 API 响应超时' },
    {
      type: 'workflow_node_complete',
      nodeId: 'api-status',
      nodeType: 'api',
      nodeLabel: '调用外部 API',
      stepIndex: 1,
      success: false,
      durationMs: 30000,
      failureReason: 'TIMEOUT',
      userMessage: '外部 API 响应超时',
      message: '节点执行失败',
      outputs: null,
    },
    { type: 'workflow_complete', success: false },
  ]
  return { graph: { nodes, edges }, events, content: '以下为失败与重试状态预览。' }
}

function buildInvalidStartOrderFixture() {
  const nodes = [
    node('llm-before-start', 'llm', 80, 146, { label: '错误前置节点' }),
    node('start', 'start', 340, 160),
    node('end', 'end', 600, 160),
  ]
  const edges = [
    edge('llm-before-start', 'start'),
    edge('start', 'end'),
  ]
  const events = buildEvents([
    { nodeId: 'llm-before-start', nodeType: 'llm', nodeLabel: '错误前置节点', outputs: { text: '这是非法样例。' } },
    { nodeId: 'start', nodeType: 'start', nodeLabel: '开始', outputs: { query: '非法流程' } },
    { nodeId: 'end', nodeType: 'end', nodeLabel: '结束', outputs: { answer: '不会作为正常样例使用。' } },
  ])
  return { graph: { nodes, edges }, events, content: '这是非法样例，仅用于验证 Debug 校验提示。' }
}
