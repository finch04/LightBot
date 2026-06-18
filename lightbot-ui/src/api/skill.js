import request from '../utils/request'

/** 分页查询全局 Skill 库 */
export function getSkills(params) {
  return request.get('/skills', { params })
}

/** 获取所有启用的全局 Skill（供 Agent 绑定下拉） */
export function getEnabledSkills() {
  return request.get('/skills/enabled')
}

/** 兼容旧接口：按 Agent 私有查询 */
export function getSkillsByAgent(agentId, name) {
  const params = {}
  if (name) params.name = name
  return request.get(`/skills/by-agent/${agentId}`, { params })
}

export function createSkill(data) {
  return request.post('/skills', data)
}

export function updateSkill(data) {
  return request.put('/skills', data)
}

export function deleteSkill(id) {
  return request.delete(`/skills/${id}`)
}

export function setSkillEnabled(id, enabled) {
  return request.put(`/skills/${id}/enabled`, null, { params: { enabled } })
}

/** ZIP 导入预览 */
export function importSkillPreview(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/skills/import/preview', formData)
}

/** ZIP 导入确认 */
export function importSkillCommit(draftId, targetSlug) {
  return request.post('/skills/import/commit', null, {
    params: { draftId, targetSlug: targetSlug || undefined }
  })
}

/** 导出 Skill ZIP */
export function exportSkillZip(id) {
  return request.get(`/skills/${id}/export`, { responseType: 'blob' })
}
