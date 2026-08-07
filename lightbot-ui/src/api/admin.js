import request from '../utils/request'

/** 管理员 - 分页查询用户列表 */
export function listUsers(params) {
  return request.get('/admin/users', { params })
}

/** 管理员 - 创建用户账号 */
export function adminCreateUser(data) {
  return request.post('/admin/users', data)
}

/** 管理员 - 获取用户详情 */
export function getUserDetail(id) {
  return request.get(`/admin/users/${id}`)
}

/** 管理员 - 更新用户信息 */
export function adminUpdateUser(id, data) {
  return request.put(`/admin/users/${id}`, data)
}

/** 管理员 - 删除用户 */
export function adminDeleteUser(id) {
  return request.delete(`/admin/users/${id}`)
}

/** 管理员 - 查询用户创建的 Agent（审计） */
export function getUserAgents(id) {
  return request.get(`/admin/users/${id}/agents`)
}

/** 管理员 - 查询用户创建的知识库（审计） */
export function getUserKnowledges(id) {
  return request.get(`/admin/users/${id}/knowledges`)
}
