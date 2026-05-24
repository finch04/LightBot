import request from '../utils/request'

export function getToolTypes() {
  return request.get('/enums/tool-types')
}