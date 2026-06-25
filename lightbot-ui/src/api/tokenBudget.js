import request from '../utils/request'

/** 获取 Token 限额配置 */
export function getTokenBudgetConfig() {
  return request.get('/system-config/token-budget/config')
}

/** 更新 Token 限额配置 */
export function updateTokenBudgetConfig(data) {
  return request.put('/system-config/token-budget/config', data)
}

/** 获取全局 Token 使用统计 */
export function getTokenBudgetStats() {
  return request.get('/system-config/token-budget/stats')
}

/** 获取用户 Token 消耗排行 */
export function getTokenBudgetRanking(limit = 20) {
  return request.get('/system-config/token-budget/ranking', { params: { limit } })
}
