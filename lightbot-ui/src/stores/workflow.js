import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * 工作流状态管理
 */
export const useWorkflowStore = defineStore('workflow', () => {
  // 节点列表
  const nodes = ref([])
  // 边列表
  const edges = ref([])

  /**
   * 获取节点默认数据
   */
  function getDefaultNodeData(type) {
    const defaults = {
      start: {},
      end: {},
      llm: {
        promptTemplate: '{{input}}',
        modelId: null
      },
      condition: {
        branches: []
      },
      retrieval: {
        knowledgeId: null
      },
      tool: {
        toolId: null,
        params: {}
      },
      script: {
        script: ''
      }
    }
    return defaults[type] || {}
  }

  /**
   * 添加节点
   */
  function addNode(type, position) {
    const id = `node_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`
    const node = {
      id,
      type,
      position,
      data: getDefaultNodeData(type)
    }
    nodes.value.push(node)
    return node
  }

  /**
   * 更新节点数据
   */
  function updateNodeData(nodeId, data) {
    const node = nodes.value.find(n => n.id === nodeId)
    if (node) {
      node.data = { ...node.data, ...data }
    }
  }

  /**
   * 删除节点
   */
  function deleteNode(nodeId) {
    nodes.value = nodes.value.filter(n => n.id !== nodeId)
    edges.value = edges.value.filter(e => e.source !== nodeId && e.target !== nodeId)
  }

  /**
   * 连接节点
   */
  function connectEdge(source, target, label = '') {
    const id = `e_${source}_${target}`
    const existing = edges.value.find(e => e.id === id)
    if (existing) return existing
    const edge = { id, source, target, label }
    edges.value.push(edge)
    return edge
  }

  /**
   * 删除边
   */
  function deleteEdge(edgeId) {
    edges.value = edges.value.filter(e => e.id !== edgeId)
  }

  /**
   * 加载工作流数据（从 Agent.config.workflow）
   */
  function loadWorkflow(workflowData) {
    if (workflowData && workflowData.nodes) {
      nodes.value = workflowData.nodes
    }
    if (workflowData && workflowData.edges) {
      edges.value = workflowData.edges
    }
  }

  /**
   * 保存工作流数据
   */
  function saveWorkflow() {
    return {
      nodes: nodes.value,
      edges: edges.value
    }
  }

  /**
   * 清空工作流
   */
  function clearWorkflow() {
    nodes.value = []
    edges.value = []
  }

  return {
    nodes,
    edges,
    addNode,
    updateNodeData,
    deleteNode,
    connectEdge,
    deleteEdge,
    loadWorkflow,
    saveWorkflow,
    clearWorkflow
  }
})