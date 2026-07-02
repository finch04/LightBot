/**
 * 工作流节点配置只读展示：将 ID 解析为友好名称
 */

export function formatModelDisplay(data, modelOptions = []) {
  if (!data?.providerId && !data?.modelId && !data?.modelName) return '—'
  if (data.providerName && (data.modelName || data.modelId)) {
    return `${data.providerName}：${data.modelName || data.modelId}`
  }
  if (modelOptions.length && data.providerId != null && data.modelId) {
    const opt = modelOptions.find(o =>
      String(o.providerId) === String(data.providerId) &&
      String(o.modelId) === String(data.modelId)
    )
    if (opt?.providerName && opt?.modelId) {
      return `${opt.providerName}：${opt.modelId}`
    }
  }
  const provider = data.providerName || (data.providerId != null ? String(data.providerId) : '')
  const model = data.modelName || data.modelId || ''
  if (provider && model) return `${provider}：${model}`
  return model || provider || '—'
}

export function resolveKnowledgeName(data, knowledgeList = []) {
  if (data?.knowledgeName) return data.knowledgeName
  if (data?.knowledgeId != null && knowledgeList.length) {
    const k = knowledgeList.find(x => String(x.id) === String(data.knowledgeId))
    if (k?.name) return k.name
  }
  if (data?.knowledgeId != null) return String(data.knowledgeId)
  return '—'
}

export function resolveToolName(data, tools = []) {
  if (data?.toolName) return data.toolName
  if (data?.toolId != null && tools.length) {
    const t = tools.find(x => String(x.id) === String(data.toolId))
    if (t) return t.displayName || t.name || String(data.toolId)
  }
  if (data?.toolId != null) return String(data.toolId)
  return '—'
}

export function resolveMcpServerName(data, mcpServers = []) {
  if (data?.mcpServerName) return data.mcpServerName
  if (data?.mcpServerId != null && mcpServers.length) {
    const s = mcpServers.find(x => String(x.id) === String(data.mcpServerId))
    if (s?.name) return s.name
  }
  if (data?.mcpServerId != null) return String(data.mcpServerId)
  return '—'
}

export function resolveMcpToolName(data) {
  return data?.toolName || '—'
}
