import request from '../utils/request'

export function getTraces(params) {
  return request.get('/observability/traces', { params })
}

export function getTraceDetail(id) {
  return request.get(`/observability/traces/${id}`)
}

export function getTraceOverview() {
  return request.get('/observability/overview')
}
