import request from '../utils/request'

export function getToolTypes() {
  return request.get('/enums/tool-types')
}

export function getModelProviderTypes() {
  return request.get('/enums/model-provider-types')
}

export function getAgentStatuses() {
  return request.get('/enums/agent-statuses')
}

export function getModelTypes() {
  return request.get('/enums/model-types')
}