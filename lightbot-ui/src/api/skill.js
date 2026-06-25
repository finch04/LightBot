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

/** 远程仓库：列出可用 Skill */
export function listRemoteSkills(source) {
  return request.post('/skills/remote/list', { source })
}

/** 远程仓库：全局搜索 Skill */
export function searchRemoteSkills(query) {
  return request.post('/skills/remote/search', { source: query })
}

/** 远程仓库：准备安装（下载并暂存草稿） */
export function prepareRemoteInstall(source, skills) {
  return request.post('/skills/remote/prepare', { source, skills })
}

/** 远程仓库：确认安装 */
export function commitRemoteInstall(draftId, slug) {
  return request.post('/skills/remote/commit', null, {
    params: { draftId, slug }
  })
}

/** 远程仓库：清理草稿 */
export function cleanupRemoteDraft(draftId) {
  return request.post('/skills/remote/cleanup', null, {
    params: { draftId }
  })
}

// ==================== 文件管理 ====================

/** 获取 Skill 文件树 */
export function getSkillFiles(id) {
  return request.get(`/skills/${id}/files`)
}

/** 读取 Skill 文件内容（返回 Result<byte[]>，byte[] 被 Jackson 序列化为 base64） */
export function readSkillFile(id, path) {
  return request.get(`/skills/${id}/file`, { params: { path } })
}

/** 创建 Skill 文件/目录 */
export function createSkillFile(id, data) {
  return request.post(`/skills/${id}/file`, data)
}

/** 更新 Skill 文件内容 */
export function updateSkillFile(id, data) {
  return request.put(`/skills/${id}/file`, data)
}

/** 删除 Skill 文件/目录 */
export function deleteSkillFile(id, path) {
  return request.delete(`/skills/${id}/file`, { params: { path } })
}
