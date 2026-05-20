import request from '../utils/request'

export function getRecentLogs(params) {
  return request.get('/logs/recent', { params })
}
