import request from '../utils/request'

export function getDashboardBasic() {
  return request.get('/dashboard/basic')
}

export function getDashboardAgents() {
  return request.get('/dashboard/agents')
}

export function getDashboardKnowledge() {
  return request.get('/dashboard/knowledge')
}

export function getDashboardChat() {
  return request.get('/dashboard/chat')
}
