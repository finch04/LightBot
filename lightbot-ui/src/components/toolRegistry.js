/**
 * 工具调用元数据注册表
 * <p>集中管理工具名称映射、图标、隐藏工具等</p>
 */
import {
  SearchOutlined,
  FileSearchOutlined,
  FolderOutlined,
  ThunderboltOutlined,
  RobotOutlined,
  FileTextOutlined
} from '@ant-design/icons-vue'
import QueryKnowledgeResult from './tools/QueryKnowledgeResult.vue'
import FindInDocumentResult from './tools/FindInDocumentResult.vue'
import SearchDocumentsResult from './tools/SearchDocumentsResult.vue'

// 工具渲染组件映射
export const TOOL_RENDERERS = {
  query_knowledge: QueryKnowledgeResult,
  find_in_document: FindInDocumentResult,
  search_documents: SearchDocumentsResult,
}

// 工具图标
export const TOOL_ICON_MAP = {
  query_knowledge: SearchOutlined,
  find_in_document: FileSearchOutlined,
  search_documents: FolderOutlined,
  skill_active: ThunderboltOutlined,
  subagent_call: RobotOutlined,
  subagent_result: RobotOutlined,
}

// 工具显示名称
export const TOOL_DISPLAY_NAMES = {
  query_knowledge: '知识库检索',
  find_in_document: '文档内容定位',
  search_documents: '文档名称搜索',
  skill_active: 'Skill 启用',
  subagent_call: 'SubAgent 委派',
  subagent_result: 'SubAgent 结果',
}

// 不在 ToolCallsGroup 中展示的工具（由 AgentCapabilityPanel 单独处理）
export const HIDDEN_TOOL_NAMES = new Set([
  'skill_active',
  'subagent_call',
  'subagent_result',
])

export function getToolIcon(toolName) {
  return TOOL_ICON_MAP[toolName] || FileTextOutlined
}

export function getToolDisplayName(toolName) {
  return TOOL_DISPLAY_NAMES[toolName] || toolName
}

export function isHiddenTool(toolName) {
  return HIDDEN_TOOL_NAMES.has(toolName)
}
