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
  FileTextOutlined,
  GlobalOutlined,
  CalculatorOutlined,
  TableOutlined,
  CodeOutlined,
  QuestionCircleOutlined,
  PictureOutlined,
  DatabaseOutlined,
  BranchesOutlined,
  FolderOpenOutlined,
} from '@ant-design/icons-vue'
import QueryKnowledgeResult from './tools/QueryKnowledgeResult.vue'
import FindInDocumentResult from './tools/FindInDocumentResult.vue'
import SearchDocumentsResult from './tools/SearchDocumentsResult.vue'
import WebSearchResult from './tools/WebSearchResult.vue'
import CalculatorResult from './tools/CalculatorResult.vue'
import PgSqlQueryResult from './tools/PgSqlQueryResult.vue'
import PgSqlListTablesResult from './tools/PgSqlListTablesResult.vue'
import PgSqlDescribeTableResult from './tools/PgSqlDescribeTableResult.vue'
import AskUserResult from './tools/AskUserResult.vue'
import ImageGenResult from './tools/ImageGenResult.vue'
import ListKnowledgeBasesResult from './tools/ListKnowledgeBasesResult.vue'
import GetMindmapResult from './tools/GetMindmapResult.vue'
import OpenKbDocumentResult from './tools/OpenKbDocumentResult.vue'
import ReadSkillResult from './tools/ReadSkillResult.vue'
import ListSkillFilesResult from './tools/ListSkillFilesResult.vue'

// 工具渲染组件映射
export const TOOL_RENDERERS = {
  // 知识库
  query_knowledge: QueryKnowledgeResult,
  find_in_document: FindInDocumentResult,
  search_documents: SearchDocumentsResult,
  list_knowledge_bases: ListKnowledgeBasesResult,
  get_mindmap: GetMindmapResult,
  open_kb_document: OpenKbDocumentResult,
  // 搜索
  web_search: WebSearchResult,
  // 计算
  calculator: CalculatorResult,
  // 数据库
  pg_list_tables: PgSqlListTablesResult,
  pg_describe_table: PgSqlDescribeTableResult,
  pg_query: PgSqlQueryResult,
  // 交互
  ask_user: AskUserResult,
  // 图片
  image_generation: ImageGenResult,
  // 技能
  read_skill: ReadSkillResult,
  list_skill_files: ListSkillFilesResult,
}

// 工具图标
export const TOOL_ICON_MAP = {
  query_knowledge: SearchOutlined,
  find_in_document: FileSearchOutlined,
  search_documents: FolderOutlined,
  list_knowledge_bases: DatabaseOutlined,
  get_mindmap: BranchesOutlined,
  open_kb_document: FileTextOutlined,
  web_search: GlobalOutlined,
  calculator: CalculatorOutlined,
  pg_list_tables: TableOutlined,
  pg_describe_table: TableOutlined,
  pg_query: CodeOutlined,
  ask_user: QuestionCircleOutlined,
  image_generation: PictureOutlined,
  read_skill: ThunderboltOutlined,
  list_skill_files: FolderOpenOutlined,
  skill_active: ThunderboltOutlined,
  subagent_call: RobotOutlined,
  subagent_result: RobotOutlined,
}

// 工具显示名称
export const TOOL_DISPLAY_NAMES = {
  query_knowledge: '知识库检索',
  find_in_document: '文档内容定位',
  search_documents: '文档名称搜索',
  list_knowledge_bases: '知识库列表',
  get_mindmap: '思维导图',
  open_kb_document: '文档原文',
  web_search: '联网搜索',
  calculator: '计算器',
  pg_list_tables: '数据库表列表',
  pg_describe_table: '表结构',
  pg_query: 'SQL查询',
  ask_user: '向用户提问',
  image_generation: '图片生成',
  read_skill: '读取技能',
  list_skill_files: '技能文件列表',
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
