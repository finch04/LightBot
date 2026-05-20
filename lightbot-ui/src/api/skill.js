import request from '../utils/request'

export function getSkillsByAgent(agentId) {
  return request.get(`/skills/by-agent/${agentId}`)
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
