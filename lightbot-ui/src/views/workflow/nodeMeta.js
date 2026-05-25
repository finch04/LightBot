import {
  RobotOutlined, ForkOutlined, BookOutlined, ToolOutlined, PlayCircleOutlined,
  StopOutlined, ApiOutlined, SyncOutlined, EditOutlined, TagsOutlined,
  ClusterOutlined, CodeOutlined, CloudServerOutlined, ImportOutlined,
  ExportOutlined, FunctionOutlined, FilterOutlined, AppstoreOutlined
} from '@ant-design/icons-vue'

export const SHORT_MEMORY_DEFAULT = {
  enabled: false,
  type: 'self',
  round: 3,
  paramKey: ''
}

export const NODE_META = {
  start: { title: '开始', color: '#22c55e', icon: PlayCircleOutlined, desc: '工作流入口' },
  end: { title: '结束', color: '#ef4444', icon: StopOutlined, desc: '工作流出口' },
  llm: { title: '大模型', color: '#7c3aed', icon: RobotOutlined, desc: '调用大模型生成内容' },
  condition: { title: '条件判断', color: '#d97706', icon: ForkOutlined, desc: '根据条件选择分支' },
  retrieval: { title: '知识检索', color: '#4f46e5', icon: BookOutlined, desc: '从知识库检索内容' },
  tool: { title: '工具调用', color: '#059669', icon: ToolOutlined, desc: '执行预设工具' },
  api: { title: 'HTTP API', color: '#0ea5e9', icon: ApiOutlined, desc: '调用外部 HTTP 接口' },
  loop: { title: '循环', color: '#8b5cf6', icon: SyncOutlined, desc: '迭代处理列表数据' },
  variable: { title: '变量赋值', color: '#ec4899', icon: EditOutlined, desc: '设置会话变量' },
  classifier: { title: '意图分类', color: '#f59e0b', icon: TagsOutlined, desc: '按意图路由分支' },
  batch: { title: '批处理', color: '#14b8a6', icon: ClusterOutlined, desc: '并行处理多条数据' },
  script: { title: '脚本', color: '#64748b', icon: CodeOutlined, desc: '执行脚本逻辑' },
  mcp: { title: 'MCP', color: '#6366f1', icon: CloudServerOutlined, desc: '调用 MCP 工具' },
  input: { title: '流程输入', color: '#0d9488', icon: ImportOutlined, desc: '在流程中补充输入参数' },
  output: { title: '流程输出', color: '#0891b2', icon: ExportOutlined, desc: '输出流程中间结果' },
  variable_handle: { title: '变量处理', color: '#db2777', icon: FunctionOutlined, desc: '对变量进行模板/分组处理' },
  parameter_extractor: { title: '参数提取', color: '#e11d48', icon: FilterOutlined, desc: '从文本提取结构化参数' },
  app_component: { title: '应用组件', color: '#2563eb', icon: AppstoreOutlined, desc: '引用已发布的工作流或智能体' }
}

/** 节点库分组（细致分类） */
export const NODE_LIBRARY_GROUPS = [
  { key: 'core', title: '核心能力', types: ['llm', 'retrieval', 'condition'] },
  { key: 'io', title: '流程交互', types: ['input', 'output'] },
  { key: 'variable', title: '变量与参数', types: ['variable', 'variable_handle', 'parameter_extractor'] },
  { key: 'logic', title: '逻辑控制', types: ['classifier', 'loop', 'batch'] },
  { key: 'integration', title: '集成调用', types: ['tool', 'api', 'mcp', 'app_component', 'script'] }
]

export function getNodeLibraryGroups(search = '') {
  const kw = (search || '').toLowerCase().trim()
  return NODE_LIBRARY_GROUPS.map(group => ({
    ...group,
    items: group.types.filter(type => {
      const meta = NODE_META[type]
      if (!meta) return false
      if (!kw) return true
      return meta.title.toLowerCase().includes(kw)
        || (meta.desc || '').toLowerCase().includes(kw)
        || type.includes(kw)
    })
  })).filter(g => g.items.length > 0)
}

export function getNodeMeta(type) {
  return NODE_META[type] || { title: type, color: '#6b7280', icon: RobotOutlined, desc: '' }
}

export function getNodeTitle(type) {
  return getNodeMeta(type).title
}

export function getNodeColor(type) {
  return getNodeMeta(type).color
}

export function createConditionId() {
  return `cond_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`
}

export function getDefaultNodeData(type) {
  const defaults = {
    llm: {
      label: '大模型',
      providerId: null,
      providerName: '',
      modelId: null,
      modelName: '',
      promptTemplate: '{{input}}',
      sysPrompt: '',
      temperature: 0.7,
      short_memory: { ...SHORT_MEMORY_DEFAULT }
    },
    condition: { label: '条件判断', branches: [] },
    retrieval: {
      label: '知识检索',
      knowledgeId: null,
      knowledgeName: '',
      topK: 5,
      threshold: 0.5,
      overrideConfig: false,
      knowledgeBaseTopK: null,
      knowledgeBaseThreshold: null,
      inputVariable: '{{input}}'
    },
    tool: { label: '工具调用', toolId: null, toolName: '' },
    api: {
      label: 'HTTP API',
      url: '',
      method: 'GET',
      headers: '{}',
      body: '{}',
      timeout: 30
    },
    loop: {
      label: '循环',
      iteratorType: 'byArray',
      arrayVariable: '{{input}}',
      countLimit: 10
    },
    variable: { label: '变量赋值', variableName: '', variableValue: '' },
    classifier: {
      label: '意图分类',
      inputVariable: '{{input}}',
      providerId: null,
      providerName: '',
      modelId: null,
      modelName: '',
      conditions: [{ id: createConditionId(), subject: '' }],
      mode_switch: 'efficient',
      short_memory: { ...SHORT_MEMORY_DEFAULT },
      instruction: ''
    },
    batch: {
      label: '批处理',
      batchSize: 10,
      concurrentSize: 3,
      errorStrategy: 'continueOnError',
      arrayVariable: '{{input}}'
    },
    script: { label: '脚本', scriptContent: '' },
    mcp: { label: 'MCP', mcpServerName: '', toolName: '', inputParams: '{}' },
    input: {
      label: '流程输入',
      outputParams: [{ key: 'query', type: 'String', defaultValue: '' }]
    },
    output: {
      label: '流程输出',
      output: '{{input}}',
      streamSwitch: true
    },
    variable_handle: {
      label: '变量处理',
      handleType: 'template',
      templateContent: '{{input}}',
      groupStrategy: 'firstNotNull',
      groups: [{ variables: [{ value: '' }, { value: '' }] }]
    },
    parameter_extractor: {
      label: '参数提取',
      providerId: null,
      providerName: '',
      modelId: null,
      modelName: '',
      inputVariable: '{{input}}',
      instruction: '',
      extractParams: [
        { key: 'city', type: 'String', required: true, desc: '城市' },
        { key: 'date', type: 'String', required: true, desc: '日期' }
      ],
      short_memory: { ...SHORT_MEMORY_DEFAULT }
    },
    app_component: {
      label: '应用组件',
      componentCode: '',
      componentName: '',
      componentType: 'workflow',
      streamSwitch: false
    }
  }
  return defaults[type] || { label: getNodeTitle(type) }
}
