import request from '../utils/request'

export function getTaskList(params) {
  return request.get('/tasks', { params })
}

export function getTask(taskId) {
  return request.get(`/tasks/${taskId}`)
}

export function cancelTask(taskId) {
  return request.post(`/tasks/${taskId}/cancel`)
}
