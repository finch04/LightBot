import request from '../utils/request'

/** ---------- 任务配置 ---------- */
export function listAutomationJobs(params) {
  return request.get('/automation/jobs', { params })
}

export function getAutomationJob(id) {
  return request.get(`/automation/jobs/${id}`)
}

export function createAutomationJob(data) {
  return request.post('/automation/jobs', data)
}

export function updateAutomationJob(id, data) {
  return request.put(`/automation/jobs/${id}`, data)
}

export function deleteAutomationJob(id) {
  return request.delete(`/automation/jobs/${id}`)
}

export function enableAutomationJob(id) {
  return request.post(`/automation/jobs/${id}/enable`)
}

export function disableAutomationJob(id) {
  return request.post(`/automation/jobs/${id}/disable`)
}

export function runAutomationJob(id) {
  return request.post(`/automation/jobs/${id}/run`)
}

/** ---------- 执行记录 ---------- */
export function pageAutomationRuns(params) {
  return request.get('/automation/runs', { params })
}

export function getAutomationRun(id) {
  return request.get(`/automation/runs/${id}`)
}
