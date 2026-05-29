import request from '../utils/request'

/**
 * 分页查询工具调用记录
 */
export function getToolCalls(params) {
  return request.get('/tool-calls', { params })
}
