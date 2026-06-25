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
import { defineAsyncComponent } from 'vue'

// 工具结果组件懒加载
const QueryKnowledgeResult = defineAsyncComponent(() => import('./tools/QueryKnowledgeResult.vue'))
const FindInDocumentResult = defineAsyncComponent(() => import('./tools/FindInDocumentResult.vue'))
const SearchDocumentsResult = defineAsyncComponent(() => import('./tools/SearchDocumentsResult.vue'))
const WebSearchResult = defineAsyncComponent(() => import('./tools/WebSearchResult.vue'))
const CalculatorResult = defineAsyncComponent(() => import('./tools/CalculatorResult.vue'))
const PgSqlQueryResult = defineAsyncComponent(() => import('./tools/PgSqlQueryResult.vue'))
const PgSqlListTablesResult = defineAsyncComponent(() => import('./tools/PgSqlListTablesResult.vue'))
const PgSqlDescribeTableResult = defineAsyncComponent(() => import('./tools/PgSqlDescribeTableResult.vue'))
const AskUserResult = defineAsyncComponent(() => import('./tools/AskUserResult.vue'))
const ImageGenResult = defineAsyncComponent(() => import('./tools/ImageGenResult.vue'))
const ListKnowledgeBasesResult = defineAsyncComponent(() => import('./tools/ListKnowledgeBasesResult.vue'))
const GetMindmapResult = defineAsyncComponent(() => import('./tools/GetMindmapResult.vue'))
const OpenKbDocumentResult = defineAsyncComponent(() => import('./tools/OpenKbDocumentResult.vue'))
const ReadSkillResult = defineAsyncComponent(() => import('./tools/ReadSkillResult.vue'))
const ListSkillFilesResult = defineAsyncComponent(() => import('./tools/ListSkillFilesResult.vue'))
const ExecuteCodeResult = defineAsyncComponent(() => import('./tools/ExecuteCodeResult.vue'))
const SandboxFileResult = defineAsyncComponent(() => import('./tools/SandboxFileResult.vue'))

// 工具渲染组件映射
export const TOOL_RENDERERS = {
  // 知识库，
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
  // 沙盒
  execute_code: ExecuteCodeResult,
  sandbox_read_file: SandboxFileResult,
  sandbox_list_files: SandboxFileResult,
  sandbox_write_file: SandboxFileResult,
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
  execute_code: CodeOutlined,
  sandbox_read_file: FileTextOutlined,
  sandbox_list_files: FolderOpenOutlined,
  sandbox_write_file: FileTextOutlined,
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
  execute_code: '代码执行',
  sandbox_read_file: '读取文件',
  sandbox_list_files: '文件列表',
  sandbox_write_file: '写入文件',
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
