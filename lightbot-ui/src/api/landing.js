import request from '../utils/request'

export function getLandingConfig() {
  return request.get('/landing/config')
}
