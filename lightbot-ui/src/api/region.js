import request from '../utils/request'

/** 地区库统计（含省/市/区分布） */
export function getRegionStats() {
  return request.get('/regions/stats')
}

/** 懒加载子节点；parentCode 空=省级 */
export function listRegionChildren(parentCode) {
  return request.get('/regions/children', {
    params: parentCode ? { parentCode } : {},
  })
}

/** 搜索 */
export function searchRegions(keyword, limit = 50) {
  return request.get('/regions/search', { params: { keyword, limit } })
}

/** 区划路径（根→本级） */
export function getRegionPath(code) {
  return request.get(`/regions/${encodeURIComponent(code)}/path`)
}

/** 本级及下级编码 */
export function listRegionDescendants(code) {
  return request.get(`/regions/${encodeURIComponent(code)}/descendants`)
}

/** 空库导入种子 */
export function seedRegions() {
  return request.post('/regions/seed')
}

/** 清空并重导（慎用） */
export function reseedRegions() {
  return request.post('/regions/reseed')
}
