import request from '../utils/request'

/**
 * 分页查询工具调用记录
 */
export function getToolCalls(params) {
  return request.get('/tool-calls', { params })
}

/**
 * 批量删除工具调用记录
 */
export function deleteToolCalls(ids) {
  return request.delete('/tool-calls', { data: ids })
}
