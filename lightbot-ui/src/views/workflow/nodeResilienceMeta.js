/**
 * 工作流节点超时/重试配置元数据（与后端 NodeTimeoutRetryHelper 默认值对齐）
 */

/** 支持在节点详情中配置超时/重试的节点类型 */
export const RESILIENCE_NODE_TYPES = new Set([
  'llm',
  'classifier',
  'parameter_extractor',
  'retrieval',
  'tool',
  'api',
  'mcp',
  'script',
  'loop',
  'batch',
  'app_component',
  'sub_agent',
  'condition',
  'variable',
  'variable_handle',
  'confirm',
])

/**
 * 各节点超时/重试画像
 * connectTimeout: 连接超时（秒），0 表示不展示
 * readTimeout: 响应/执行超时（秒）
 */
export const NODE_RESILIENCE_PROFILES = {
  llm: {
    showConnectTimeout: true,
    showReadTimeout: true,
    showRetry: true,
    connectTimeout: { min: 1, max: 60, default: 10 },
    readTimeout: { min: 1, max: 300, default: 60 },
    retryConfig: { enabled: false, maxAttempts: 2, delayMs: 2000 },
  },
  classifier: {
    showConnectTimeout: true,
    showReadTimeout: true,
    showRetry: true,
    connectTimeout: { min: 1, max: 30, default: 10 },
    readTimeout: { min: 1, max: 120, default: 30 },
    retryConfig: { enabled: false, maxAttempts: 2, delayMs: 1000 },
  },
  parameter_extractor: {
    showConnectTimeout: true,
    showReadTimeout: true,
    showRetry: true,
    connectTimeout: { min: 1, max: 30, default: 10 },
    readTimeout: { min: 1, max: 120, default: 30 },
    retryConfig: { enabled: false, maxAttempts: 2, delayMs: 1000 },
  },
  retrieval: {
    showConnectTimeout: true,
    showReadTimeout: true,
    showRetry: true,
    connectTimeout: { min: 1, max: 30, default: 5 },
    readTimeout: { min: 1, max: 60, default: 15 },
    retryConfig: { enabled: false, maxAttempts: 2, delayMs: 500 },
  },
  tool: {
    showConnectTimeout: true,
    showReadTimeout: true,
    showRetry: true,
    connectTimeout: { min: 1, max: 30, default: 5 },
    readTimeout: { min: 1, max: 120, default: 20 },
    retryConfig: { enabled: false, maxAttempts: 2, delayMs: 500 },
  },
  api: {
    showConnectTimeout: true,
    showReadTimeout: true,
    showRetry: true,
    connectTimeout: { min: 1, max: 30, default: 5 },
    readTimeout: { min: 1, max: 120, default: 30 },
    retryConfig: { enabled: false, maxAttempts: 2, delayMs: 500 },
  },
  mcp: {
    showConnectTimeout: true,
    showReadTimeout: true,
    showRetry: true,
    connectTimeout: { min: 1, max: 30, default: 5 },
    readTimeout: { min: 1, max: 120, default: 30 },
    retryConfig: { enabled: false, maxAttempts: 2, delayMs: 1000 },
  },
  script: {
    showConnectTimeout: false,
    showReadTimeout: true,
    showRetry: true,
    readTimeout: { min: 1, max: 60, default: 10 },
    retryConfig: { enabled: false, maxAttempts: 2, delayMs: 500 },
  },
  loop: {
    showConnectTimeout: false,
    showReadTimeout: true,
    showRetry: false,
    readTimeout: { min: 10, max: 600, default: 120 },
  },
  batch: {
    showConnectTimeout: false,
    showReadTimeout: true,
    showRetry: false,
    readTimeout: { min: 10, max: 600, default: 120 },
  },
  app_component: {
    showConnectTimeout: false,
    showReadTimeout: true,
    showRetry: false,
    readTimeout: { min: 10, max: 300, default: 60 },
  },
  sub_agent: {
    showConnectTimeout: false,
    showReadTimeout: true,
    showRetry: false,
    readTimeout: { min: 10, max: 600, default: 120 },
  },
  condition: {
    showConnectTimeout: false,
    showReadTimeout: true,
    showRetry: false,
    readTimeout: { min: 1, max: 30, default: 3 },
  },
  variable: {
    showConnectTimeout: false,
    showReadTimeout: true,
    showRetry: false,
    readTimeout: { min: 1, max: 30, default: 3 },
  },
  variable_handle: {
    showConnectTimeout: false,
    showReadTimeout: true,
    showRetry: false,
    readTimeout: { min: 1, max: 60, default: 5 },
  },
  confirm: {
    showConnectTimeout: false,
    showReadTimeout: true,
    showRetry: false,
    readTimeout: { min: 60, max: 86400, default: 3600 },
  },
}

export function supportsNodeResilience(nodeType) {
  return RESILIENCE_NODE_TYPES.has(nodeType)
}

export function getNodeResilienceProfile(nodeType) {
  return NODE_RESILIENCE_PROFILES[nodeType] || null
}

/**
 * 补齐 node.data 中的 timeoutConfig / retryConfig，并迁移旧 timeout 字段
 */
export function ensureNodeResilienceConfig(nodeData, nodeType) {
  if (!nodeData || !supportsNodeResilience(nodeType)) return
  const profile = getNodeResilienceProfile(nodeType)
  if (!profile) return

  if (!nodeData.timeoutConfig || typeof nodeData.timeoutConfig !== 'object') {
    nodeData.timeoutConfig = {}
  }
  const tc = nodeData.timeoutConfig

  if (profile.showConnectTimeout && tc.connectTimeout == null && profile.connectTimeout) {
    tc.connectTimeout = profile.connectTimeout.default
  }
  if (profile.showReadTimeout && tc.readTimeout == null) {
    if (nodeData.timeout != null && nodeData.timeout !== '') {
      tc.readTimeout = Number(nodeData.timeout)
    } else if (profile.readTimeout) {
      tc.readTimeout = profile.readTimeout.default
    }
  }

  if (profile.showRetry) {
    if (!nodeData.retryConfig || typeof nodeData.retryConfig !== 'object') {
      nodeData.retryConfig = { ...profile.retryConfig }
    } else {
      const enabled = !!nodeData.retryConfig.enabled
      const maxAttempts = nodeData.retryConfig.maxAttempts ?? profile.retryConfig.maxAttempts
      const delayMs = nodeData.retryConfig.delayMs ?? profile.retryConfig.delayMs
      const cur = nodeData.retryConfig
      if (cur.enabled !== enabled || cur.maxAttempts !== maxAttempts || cur.delayMs !== delayMs) {
        nodeData.retryConfig = { enabled, maxAttempts, delayMs }
      }
    }
  }
}

export function getResilienceFieldHint(field) {
  const hints = {
    connectTimeout: '建立连接的最长等待时间（秒），超时则本次执行失败',
    readTimeout: '等待响应或节点执行完成的最长时间（秒）',
    retryEnabled: '开启后失败将按次数与间隔自动重试',
    maxAttempts: '最大尝试次数（含首次执行），建议 2 次',
    retryDelayMs: '两次重试之间的等待间隔（毫秒），采用指数退避',
  }
  return hints[field] || ''
}
