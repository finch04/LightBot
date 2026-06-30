import request from '../utils/request'

/**
 * 获取 Agent 的 @ mention 候选资源（按版本快照或当前绑定聚合）
 * @param {string|number} agentId Agent ID
 * @param {string|number} [agentVersionId] Agent 版本快照 ID
 * @param {string} [types] 限定返回的资源类型（逗号分隔），如 "knowledge,subagent,skill"
 */
export function getMentionOptions(agentId, agentVersionId, types) {
  return request.get(`/agents/${agentId}/mention-options`, {
    params: {
      agentVersionId: agentVersionId || undefined,
      types: types || undefined,
    },
  })
}
