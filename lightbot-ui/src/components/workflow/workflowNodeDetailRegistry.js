/**
 * 工作流节点详情渲染注册表（对齐 toolRegistry 模式）
 * Chat 对话与测试轨迹通过 WorkflowStepDetail 统一路由到此注册表
 */
import { defineAsyncComponent } from 'vue'

const StartNodeDetail = defineAsyncComponent(() => import('./nodeDetails/StartNodeDetail.vue'))
const EndNodeDetail = defineAsyncComponent(() => import('./nodeDetails/EndNodeDetail.vue'))
const InputNodeDetail = defineAsyncComponent(() => import('./nodeDetails/InputNodeDetail.vue'))
const OutputNodeDetail = defineAsyncComponent(() => import('./nodeDetails/OutputNodeDetail.vue'))
const VariableNodeDetail = defineAsyncComponent(() => import('./nodeDetails/VariableNodeDetail.vue'))
const VariableHandleNodeDetail = defineAsyncComponent(() => import('./nodeDetails/VariableHandleNodeDetail.vue'))
const LlmNodeDetail = defineAsyncComponent(() => import('./nodeDetails/LlmNodeDetail.vue'))
const RetrievalNodeDetail = defineAsyncComponent(() => import('./nodeDetails/RetrievalNodeDetail.vue'))
const ToolNodeDetail = defineAsyncComponent(() => import('./nodeDetails/ToolNodeDetail.vue'))
const McpNodeDetail = defineAsyncComponent(() => import('./nodeDetails/McpNodeDetail.vue'))
const ConditionNodeDetail = defineAsyncComponent(() => import('./nodeDetails/ConditionNodeDetail.vue'))
const ClassifierNodeDetail = defineAsyncComponent(() => import('./nodeDetails/ClassifierNodeDetail.vue'))
const ApiNodeDetail = defineAsyncComponent(() => import('./nodeDetails/ApiNodeDetail.vue'))
const ScriptNodeDetail = defineAsyncComponent(() => import('./nodeDetails/ScriptNodeDetail.vue'))
const ParameterExtractorNodeDetail = defineAsyncComponent(() => import('./nodeDetails/ParameterExtractorNodeDetail.vue'))
const AppComponentNodeDetail = defineAsyncComponent(() => import('./nodeDetails/AppComponentNodeDetail.vue'))
const SubAgentNodeDetail = defineAsyncComponent(() => import('./nodeDetails/SubAgentNodeDetail.vue'))
const ConfirmNodeDetail = defineAsyncComponent(() => import('./nodeDetails/ConfirmNodeDetail.vue'))
const ContainerNodeDetail = defineAsyncComponent(() => import('./nodeDetails/ContainerNodeDetail.vue'))
const DefaultNodeDetail = defineAsyncComponent(() => import('./nodeDetails/DefaultNodeDetail.vue'))

/** 节点类型 → 详情组件 */
export const WORKFLOW_NODE_DETAIL_RENDERERS = {
  start: StartNodeDetail,
  end: EndNodeDetail,
  input: InputNodeDetail,
  output: OutputNodeDetail,
  variable: VariableNodeDetail,
  variable_handle: VariableHandleNodeDetail,
  llm: LlmNodeDetail,
  retrieval: RetrievalNodeDetail,
  tool: ToolNodeDetail,
  mcp: McpNodeDetail,
  condition: ConditionNodeDetail,
  classifier: ClassifierNodeDetail,
  api: ApiNodeDetail,
  script: ScriptNodeDetail,
  parameter_extractor: ParameterExtractorNodeDetail,
  app_component: AppComponentNodeDetail,
  sub_agent: SubAgentNodeDetail,
  confirm: ConfirmNodeDetail,
  loop: ContainerNodeDetail,
  batch: ContainerNodeDetail,
}

/**
 * 获取节点详情渲染组件
 * @param {string} nodeType
 * @returns {import('vue').Component}
 */
export function getWorkflowNodeDetailRenderer(nodeType) {
  if (!nodeType) return DefaultNodeDetail
  return WORKFLOW_NODE_DETAIL_RENDERERS[nodeType] || DefaultNodeDetail
}

/** 是否已注册专用详情组件 */
export function hasWorkflowNodeDetailRenderer(nodeType) {
  if (!nodeType) return false
  return Object.prototype.hasOwnProperty.call(WORKFLOW_NODE_DETAIL_RENDERERS, String(nodeType).trim())
}
