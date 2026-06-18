import request from '../utils/request'

export function getLandingConfig() {
  return request.get('/landing/config')
}

export function updateLandingConfig(data) {
  return request.put('/landing/config', data, { headers: { 'Content-Type': 'application/json' } })
}
