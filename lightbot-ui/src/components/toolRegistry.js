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
  ScanOutlined,
  CheckSquareOutlined,
  AppstoreOutlined,
} from '@ant-design/icons-vue'
import { defineAsyncComponent } from 'vue'

// 工具结果组件懒加载
const QueryKnowledgeResult = defineAsyncComponent(() => import('./tools/QueryKnowledgeResult.vue'))
const FindInDocumentResult = defineAsyncComponent(() => import('./tools/FindInDocumentResult.vue'))
const SearchDocumentsResult = defineAsyncComponent(() => import('./tools/SearchDocumentsResult.vue'))
const WebSearchResult = defineAsyncComponent(() => import('./tools/WebSearchResult.vue'))
const CalculatorResult = defineAsyncComponent(() => import('./tools/CalculatorResult.vue'))
const PgSqlQueryResult = defineAsyncComponent(() => import('./tools/PgSqlQueryResult.vue'))
const AskDataResult = defineAsyncComponent(() => import('./tools/AskDataResult.vue'))
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
const OcrParseFileResult = defineAsyncComponent(() => import('./tools/OcrParseFileResult.vue'))
const DeliverFileResult = defineAsyncComponent(() => import('./tools/DeliverFileResult.vue'))
const InstallSkillResult = defineAsyncComponent(() => import('./tools/InstallSkillResult.vue'))
const UserMemoryResult = defineAsyncComponent(() => import('./tools/UserMemoryResult.vue'))
const ChartResult = defineAsyncComponent(() => import('./tools/ChartResult.vue'))
const WriteTodosResult = defineAsyncComponent(() => import('./tools/WriteTodosResult.vue'))
const PresentBusinessPageResult = defineAsyncComponent(() => import('./businessPages/PresentBusinessPageResult.vue'))

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
  // 智能问数
  ask_data_search_catalog: AskDataResult,
  ask_data_describe_dataset: AskDataResult,
  ask_data_execute: AskDataResult,
  // 交互
  ask_user: AskUserResult,
  // 协作待办：避免在对话中直接回显工具 JSON
  write_todos: WriteTodosResult,
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
  sandbox_append_file: SandboxFileResult,
  ocr_parse_file: OcrParseFileResult,
  // 交付 / 安装
  present_artifacts: DeliverFileResult,
  present_business_page: PresentBusinessPageResult,
  install_skill: InstallSkillResult,
  memory_save: UserMemoryResult,
  memory_search: UserMemoryResult,
  memory_delete: UserMemoryResult,
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
  write_todos: CheckSquareOutlined,
  image_generation: PictureOutlined,
  read_skill: ThunderboltOutlined,
  list_skill_files: FolderOpenOutlined,
  execute_code: CodeOutlined,
  sandbox_read_file: FileTextOutlined,
  sandbox_list_files: FolderOpenOutlined,
  sandbox_write_file: FileTextOutlined,
  sandbox_append_file: FileTextOutlined,
  ocr_parse_file: ScanOutlined,
  present_artifacts: FolderOpenOutlined,
  present_business_page: AppstoreOutlined,
  install_skill: ThunderboltOutlined,
  memory_save: DatabaseOutlined,
  memory_search: DatabaseOutlined,
  memory_delete: DatabaseOutlined,
  delegate_to_subagent: RobotOutlined,
  get_subagent_task_result: RobotOutlined,
  cancel_subagent_task: RobotOutlined,
  skill_active: ThunderboltOutlined,
  subagent_call: RobotOutlined,
  subagent_result: RobotOutlined,
  subagent_token: RobotOutlined,
  subagent_tool_call: RobotOutlined,
  subagent_tool_result: RobotOutlined,
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
  write_todos: '更新待办',
  image_generation: '图片生成',
  read_skill: '读取技能',
  list_skill_files: '技能文件列表',
  execute_code: '代码执行',
  sandbox_read_file: '读取文件',
  sandbox_list_files: '文件列表',
  sandbox_write_file: '写入文件',
  sandbox_append_file: '追加文件',
  ocr_parse_file: 'OCR 解析文件',
  present_artifacts: '文件交付',
  present_business_page: '业务办理页',
  install_skill: '技能安装',
  memory_save: '保存长期记忆',
  memory_search: '查询长期记忆',
  memory_delete: '停用长期记忆',
  delegate_to_subagent: '委派 SubAgent',
  get_subagent_task_result: '查询 SubAgent 任务结果',
  cancel_subagent_task: '取消 SubAgent 任务',
  skill_active: 'Skill 启用',
  subagent_call: 'SubAgent 委派',
  subagent_result: 'SubAgent 结果',
  subagent_token: 'SubAgent 输出',
  subagent_tool_call: 'SubAgent 工具调用',
  subagent_tool_result: 'SubAgent 工具结果',
}

// 不在 ToolCallsGroup 中展示的工具（由 capabilities/AgentCapabilityPanel 单独处理）
export const HIDDEN_TOOL_NAMES = new Set([
  'skill_active',
  'subagent_call',
  'subagent_result',
  'subagent_token',
  'subagent_tool_call',
  'subagent_tool_result',
  'subagent_batch_start',
  'subagent_task_start',
  'subagent_task_done',
  'subagent_batch_done',
  'subagent_batch_update',
  'delegate_to_subagent',
  'get_subagent_task_result',
  'cancel_subagent_task',
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

/** 业务办理类工具：结果应在对话中默认展开（不当作普通工具 JSON 收起） */
export const PRESENTATION_TOOL_NAMES = new Set([
  'present_business_page',
])

/** 是否已在 toolRegistry 注册专用渲染组件 */
export function hasToolRenderer(toolName) {
  if (!toolName) return false
  return Object.prototype.hasOwnProperty.call(TOOL_RENDERERS, String(toolName).trim())
}

/** 是否为对话内业务呈现工具 */
export function isPresentationTool(toolName) {
  return PRESENTATION_TOOL_NAMES.has(String(toolName || '').trim())
}

/**
 * Yuxi 的 @antv/mcp-server-chart 返回 content block 数组，首项通常为图片 URL。
 * MCP 工具名并非平台契约，因此同时按图表语义和返回结构识别，避免把服务器名写死在前端。
 */
export function isChartToolResult(toolName, result) {
  const normalizedName = String(toolName || '').toLowerCase()
  if (/(chart|graph|visual)/.test(normalizedName)) return true

  if (typeof result !== 'string' || !result.trim()) return false
  try {
    const parsed = JSON.parse(result)
    return Array.isArray(parsed) && parsed.some(item =>
      item?.type === 'image' || (item?.type === 'text' && /^https?:\/\//i.test(String(item.text || '')))
    )
  } catch {
    return false
  }
}

export { ChartResult }
