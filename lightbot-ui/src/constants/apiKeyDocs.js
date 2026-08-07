/**
 * API Key 在线文档目录（左侧能力导航 + 右侧接口详情）
 * 与后端 Controller 对齐
 */

/** @typedef {{ name: string, type: string, required?: boolean, desc: string, example?: string, in?: 'path'|'query'|'body'|'header' }} ApiParam */

/**
 * @typedef {{ name: string, type: string, desc: string, example?: string }} ApiResponseField
 */

/**
 * @typedef {object} ApiEndpoint
 * @property {string} id
 * @property {string} method
 * @property {string} path
 * @property {string} summary
 * @property {string} description
 * @property {ApiParam[]} [params]
 * @property {string} [bodyExample]
 * @property {ApiResponseField[]} [responseFields]
 * @property {string} [responseExample]
 * @property {boolean} [testable]
 * @property {'json'|'sse'|'multipart'|'none'} [contentType]
 */

/**
 * @typedef {object} ApiDocGroup
 * @property {string} id
 * @property {string} title
 * @property {string} [desc]
 * @property {'guide'|'apis'} type
 * @property {string} [guideHtml]
 * @property {ApiEndpoint[]} [apis]
 */

/** Result 通用包装字段 */
const RESULT_FIELDS = [
  { name: 'code', type: 'number', desc: '业务状态码，200 表示成功', example: '200' },
  { name: 'message', type: 'string', desc: '提示信息', example: 'success' },
]

/** 分页对象字段（挂在 data 下） */
const PAGE_FIELDS = [
  { name: 'data.records', type: 'array', desc: '当前页数据列表', example: '[]' },
  { name: 'data.total', type: 'number', desc: '总记录数', example: '10' },
  { name: 'data.current', type: 'number', desc: '当前页码（从 1 开始）', example: '1' },
  { name: 'data.size', type: 'number', desc: '每页条数', example: '20' },
  { name: 'data.pages', type: 'number', desc: '总页数', example: '1' },
]

/** @type {ApiDocGroup[]} */
export const API_DOC_GROUPS = [
  {
    id: 'overview',
    title: '开始使用',
    type: 'guide',
    desc: '认证、权限与调用约定',
    guideHtml: `
      <h3>认证方式</h3>
      <p>在请求头携带 API Key：</p>
      <pre>Authorization: Bearer lbkey_xxxx</pre>
      <p>密钥仅在创建时显示一次；支持限流、每日 Token 配额、绑定 Agent 作用域。</p>
      <h3>权限说明</h3>
      <ul>
        <li><code>chat</code> 仅对话：可调用对话相关接口（同步/流式对话、附件、停止/重连）</li>
        <li><code>full</code> 完全访问：在对话能力之外，还可调用 <code>/api/open/v1/**</code> 开放数据接口（如数据池）；不可访问控制台管理接口</li>
      </ul>
      <p><strong>注意：</strong>「完全访问」不等于可调用草稿 Agent。开放 API（含 API Key）仅可调用<strong>已发布</strong>的 Agent（状态为已发布 / 编辑中）；草稿请先在控制台发布。</p>
      <h3>调用方身份（callerContext）与长期记忆</h3>
      <ul>
        <li><code>sessionId</code>：隔离单次对话线程（短期上下文）</li>
        <li><code>callerContext</code>（推荐）：结构化身份，供 Tool / MCP / 问数做数据与权限隔离
          <pre>{
  "externalUserId": "biz_user_10086",
  "regionId": "330100",
  "enterpriseId": "ent_001",
  "profile": { "name": "张三", "roles": ["admin"] }
}</pre>
        </li>
        <li><code>externalUserId</code>（兼容）：等价于 <code>callerContext.externalUserId</code>；传入后启用该用户在本 Key 下的<strong>跨会话长期记忆</strong></li>
        <li>首轮写入会话后续聊可省略；<code>externalUserId</code> / <code>regionId</code> / <code>enterpriseId</code> 不可中途更换（冲突返回 94012）</li>
        <li><code>profile</code> 供 Tool/MCP 读取，<strong>默认不进入系统提示词</strong>；提示词变量请用 <code>bizParams</code></li>
        <li>MCP：身份经工具调用 <code>_meta.callerContext</code> 透传（勿依赖改 MCP 连接 Header）</li>
        <li>问数：数据集配置 <code>tenantDimensions</code>（仅 <code>regionId</code>/<code>enterpriseId</code>）后强制隔离：有 <code>enterpriseId</code> 为企业视角（只滤企业）；否则为行业视角（只滤地区，配了地区则必传 <code>regionId</code>）</li>
        <li>HTTP API 工具：可在工具 <code>config.contextHeaders</code> / <code>contextQuery</code> 中用 <code>{{callerContext.regionId}}</code> 等模板注入</li>
        <li>业务办理页：工具结果写入 <code>callerContext</code>；内嵌 HTML 默认证 <code>X-Zhiyuan-*</code> Header（身份不进 URL）</li>
        <li>智元不为终端用户建账号；身份由上游业务系统经 API Key 断言</li>
        <li>控制台调试会自动使用 <code>debug_user_&#123;建设者用户ID&#125;</code>，与开放 API 的 Key 命名空间<strong>互不共享</strong></li>
      </ul>
      <h3>业务办理页（present_business_page）</h3>
      <p>开发者在能力中心「业务页组件」注册 <code>pageType</code> + <strong>内嵌 HTML / 外链</strong>。内嵌模式下宿主静默拦截成功业务请求并回传对话；外链跨域需页面或业务后端主动回传。</p>
      <pre>{
  "type": "tool_result",
  "toolName": "present_business_page",
  "result": "{ \"success\": true, \"pageType\": \"utility_bill_pay\", \"title\": \"水电燃气缴费\", \"mode\": \"inline\", \"pageHtml\": \"<!DOCTYPE html>...\", \"props\": {...}, \"callerContext\": { \"regionId\": \"510100\" }, \"identityHeaders\": { \"X-Zhiyuan-Region-Id\": \"510100\" }, \"actions\": [\"submit\",\"cancel\"], \"wait_for_user\": true }"
}</pre>
      <ul>
        <li>主路径：<code>pageHtml</code> → iframe srcdoc；可选外链 <code>pageUrl</code></li>
        <li>身份：工具结果写入权威 <code>callerContext</code>（与 props 分离）；内嵌页默认证 <code>X-Zhiyuan-*</code> Header；身份不进 URL</li>
        <li>上层也可 <code>registerBusinessPageComponent</code> 注入 Vue 组件（优先级最高）</li>
        <li>白名单：对话型 Agent「业务页组件」绑定 ∩ 企业 API Key 业务页配置；未绑定则不注入该工具</li>
        <li>用户提交后回灌对话；Workflow HITL 类型为 <code>business_page</code></li>
      </ul>
      <h3>通用返回</h3>
      <pre>{
  "code": 200,
  "message": "success",
  "data": { ... }
}</pre>
      <p><code>code !== 200</code> 表示业务失败；HTTP 401/403/429 分别为未授权、无权限、限流。</p>
      <p>分页接口的 <code>data</code> 通常包含 <code>records</code>、<code>total</code>、<code>current</code>、<code>size</code>、<code>pages</code>。</p>
    `,
  },
  {
    id: 'chat',
    title: '对话',
    type: 'apis',
    desc: '同步/流式对话与附件',
    apis: [
      {
        id: 'chat-sync',
        method: 'POST',
        path: '/api/chat',
        summary: '同步对话',
        description: '发起一轮非流式对话，等待完整回复后返回。适合脚本集成；交互场景推荐流式接口。',
        contentType: 'json',
        testable: true,
        params: [
          { name: 'agentId', type: 'string', required: true, in: 'body', desc: 'Agent ID（雪花 ID，按字符串传递）', example: '2056961707612393473' },
          { name: 'message', type: 'string', required: false, in: 'body', desc: '用户消息；仅附件时可为空', example: '你好，请介绍一下自己' },
          { name: 'sessionId', type: 'string', required: false, in: 'body', desc: '会话 ID；空则新建会话', example: 'null' },
          { name: 'externalUserId', type: 'string', required: false, in: 'body', desc: '兼容字段，等价于 callerContext.externalUserId；启用跨会话长期记忆', example: 'biz_user_10086' },
          { name: 'callerContext', type: 'object', required: false, in: 'body', desc: '调用方身份：externalUserId/regionId/enterpriseId/profile；首轮绑定会话，供 Tool/MCP/问数隔离', example: '{ "externalUserId": "biz_user_10086", "regionId": "330100", "enterpriseId": "ent_001" }' },
          { name: 'bizParams', type: 'object', required: false, in: 'body', desc: '入参变量，替换系统提示词 {{变量}}（不承载身份）', example: '{ "city": "杭州" }' },
          { name: 'attachments', type: 'array', required: false, in: 'body', desc: '附件列表（先调用上传接口）', example: '[{ "id": "...", "url": "https://..." }]' },
        ],
        bodyExample: `{
  "agentId": "2056961707612393473",
  "message": "你好，请介绍一下自己",
  "sessionId": null,
  "callerContext": {
    "externalUserId": "biz_user_10086",
    "regionId": "330100",
    "enterpriseId": "ent_001",
    "profile": { "name": "张三" }
  }
}`,
        responseFields: [
          ...RESULT_FIELDS,
          { name: 'data', type: 'string', desc: '助手完整回复文本', example: '你好！我是 智元智能助手……' },
        ],
        responseExample: `{
  "code": 200,
  "message": "success",
  "data": "你好！我是 智元智能助手……"
}`,
      },
      {
        id: 'chat-stream',
        method: 'POST',
        path: '/api/chat/stream',
        summary: '流式对话（SSE）',
        description: '以 Server-Sent Events 流式返回模型输出与工具/思考事件。响应 Content-Type 为 text/event-stream（非 Result 包装）。不传 sessionId 时服务端会自动建会话；请从首包 [SESSION_ID] 或 [DONE].sessionId 取出并在后续请求中回传。注意：requestId ≠ sessionId。可选 callerContext / externalUserId 绑定调用方身份并启用长期记忆。',
        contentType: 'sse',
        testable: true,
        params: [
          { name: 'agentId', type: 'string', required: true, in: 'body', desc: 'Agent ID', example: '2056961707612393473' },
          { name: 'message', type: 'string', required: false, in: 'body', desc: '用户消息', example: '用三句话介绍智元' },
          { name: 'sessionId', type: 'string', required: false, in: 'body', desc: '会话 ID；空则新建。多轮续聊必须回传上一轮返回的 sessionId', example: '2056961707612393500' },
          { name: 'externalUserId', type: 'string', required: false, in: 'body', desc: '兼容字段；续聊须与会话已绑定值一致', example: 'biz_user_10086' },
          { name: 'callerContext', type: 'object', required: false, in: 'body', desc: '调用方身份上下文；续聊可省略，隔离主键不可更换', example: '{ "externalUserId": "biz_user_10086", "regionId": "330100", "enterpriseId": "ent_001" }' },
          { name: 'bizParams', type: 'object', required: false, in: 'body', desc: '入参变量（提示词），不承载身份', example: '{ "role": "助手" }' },
        ],
        bodyExample: `{
  "agentId": "2056961707612393473",
  "message": "用三句话介绍智元",
  "callerContext": {
    "externalUserId": "biz_user_10086",
    "regionId": "330100",
    "enterpriseId": "ent_001"
  }
}`,
        responseFields: [
          { name: 'id', type: 'string', desc: 'SSE 事件序号，可用于断线重连', example: '1' },
          { name: 'data（[SESSION_ID]）', type: 'string', desc: '会话 ID（多轮续聊用，不是 requestId）', example: '[SESSION_ID]2056961707612393500' },
          { name: 'data（纯文本）', type: 'string', desc: '模型增量文本；换行转义为 \\n', example: '你好' },
          { name: 'data（[REQUEST_ID]）', type: 'string', desc: '本轮流式请求 ID，仅用于停止/重连', example: '[REQUEST_ID]req_abc123' },
          { name: 'data（[STATUS]）', type: 'object', desc: '状态事件 JSON，含 type（tool_call / reasoning_content 等）', example: '[STATUS]{"type":"reasoning_content","content":"..."}' },
          { name: 'data（[METADATA]）', type: 'object', desc: '中途元数据（RAG 引用、工具偏移等）', example: '[METADATA]{"ragReferences":[]}' },
          { name: 'data（[DONE]）', type: 'object', desc: '结束事件；含 sessionId、requestId、totalTokens、assistantMessageId 等', example: '[DONE]{"sessionId":"2056961707612393500","requestId":"req_abc123"}' },
        ],
        responseExample: `id: 1
data: [REQUEST_ID]req_abc123
id: 2
data: [SESSION_ID]2056961707612393500
id: 3
data: 你好
id: 4
data: [DONE]{"sessionId":"2056961707612393500","requestId":"req_abc123","totalTokens":128}`,
      },
      {
        id: 'chat-reconnect',
        method: 'POST',
        path: '/api/chat/reconnect',
        summary: 'SSE 断线重连',
        description: '流式中断后，携带 requestId 与 lastEventId 拉取缓冲事件。',
        contentType: 'json',
        testable: true,
        params: [
          { name: 'requestId', type: 'string', required: true, in: 'body', desc: '流式请求 ID', example: 'req_abc123' },
          { name: 'lastEventId', type: 'string', required: false, in: 'body', desc: '已接收的最后事件 ID', example: '12' },
        ],
        bodyExample: `{
  "requestId": "req_xxx",
  "lastEventId": "12"
}`,
        responseFields: [
          ...RESULT_FIELDS,
          { name: 'data.status', type: 'string', desc: 'already_delivered / completed / cancelled', example: 'completed' },
          { name: 'data.events', type: 'array', desc: '缓冲的 SSE 事件列表（已追上时可能无此字段）', example: '[{ "id": 13, "data": "..." }]' },
          { name: 'data.events[].id', type: 'number', desc: '事件 ID', example: '13' },
          { name: 'data.events[].data', type: 'string', desc: '与流式接口相同的 data 载荷', example: '你好' },
        ],
        responseExample: `{
  "code": 200,
  "message": "success",
  "data": {
    "status": "completed",
    "events": [{ "id": 13, "data": "..." }]
  }
}`,
      },
      {
        id: 'chat-stop',
        method: 'POST',
        path: '/api/chat/stream/stop',
        summary: '停止流式对话',
        description: '停止指定 requestId 的流式生成。',
        contentType: 'none',
        testable: true,
        params: [
          { name: 'requestId', type: 'string', required: true, in: 'query', desc: '流式请求 ID', example: 'req_abc123' },
        ],
        responseFields: [
          ...RESULT_FIELDS,
          { name: 'data', type: 'null', desc: '无业务载荷', example: 'null' },
        ],
        responseExample: `{
  "code": 200,
  "message": "success",
  "data": null
}`,
      },
      {
        id: 'chat-upload',
        method: 'POST',
        path: '/api/chat/attachments',
        summary: '上传对话附件',
        description: '上传图片/视频/文档，返回附件描述供后续对话引用。multipart/form-data。',
        contentType: 'multipart',
        testable: true,
        params: [
          { name: 'agentId', type: 'string', required: true, in: 'query', desc: 'Agent ID', example: '2056961707612393473' },
          { name: 'sessionId', type: 'string', required: false, in: 'query', desc: '会话 ID（可选）', example: '2056961707612393500' },
          { name: 'file', type: 'file', required: true, in: 'form', desc: '文件本体（multipart 字段名 file）', example: 'a.png' },
        ],
        bodyExample: '(multipart form-data: file=<binary>)',
        responseFields: [
          ...RESULT_FIELDS,
          { name: 'data.id', type: 'string', desc: '附件 ID', example: '2056961707612393900' },
          { name: 'data.type', type: 'string', desc: 'image / video / document', example: 'image' },
          { name: 'data.mimeType', type: 'string', desc: 'MIME 类型', example: 'image/png' },
          { name: 'data.objectKey', type: 'string', desc: '对象存储路径', example: 'chat/xxx/a.png' },
          { name: 'data.previewUrl', type: 'string', desc: '预览签名 URL（短期有效）', example: 'https://...' },
          { name: 'data.fileName', type: 'string', desc: '原始文件名', example: 'a.png' },
          { name: 'data.warning', type: 'string', desc: '非阻塞提示（如文本抽取失败）', example: 'null' },
        ],
        responseExample: `{
  "code": 200,
  "message": "success",
  "data": {
    "id": "2056961707612393900",
    "type": "image",
    "mimeType": "image/png",
    "fileName": "a.png",
    "previewUrl": "https://..."
  }
}`,
      },
      {
        id: 'chat-rag-refs',
        method: 'GET',
        path: '/api/chat/rag-references',
        summary: '获取 RAG 引用',
        description: '按会话与问题查询本轮 RAG 引用片段。',
        contentType: 'none',
        testable: true,
        params: [
          { name: 'sessionId', type: 'string', required: true, in: 'query', desc: '会话 ID', example: '2056961707612393500' },
          { name: 'question', type: 'string', required: true, in: 'query', desc: '问题文本', example: '智元支持哪些能力？' },
          { name: 'agentId', type: 'string', required: false, in: 'query', desc: 'Agent ID', example: '2056961707612393473' },
        ],
        responseFields: [
          ...RESULT_FIELDS,
          { name: 'data', type: 'array', desc: '引用片段列表', example: '[]' },
          { name: 'data[].documentName', type: 'string', desc: '来源文档名', example: '产品手册.pdf' },
          { name: 'data[].contentPreview', type: 'string', desc: '片段预览（约 200 字）', example: '智元支持 Agent...' },
          { name: 'data[].score', type: 'number', desc: '相似度分数', example: '0.86' },
          { name: 'data[].knowledgeId', type: 'string', desc: '知识库 ID', example: '2056...' },
          { name: 'data[].documentId', type: 'string', desc: '文档 ID', example: '2056...' },
          { name: 'data[].chunkId', type: 'string', desc: '分块 ID', example: '2056...' },
          { name: 'data[].sourceType', type: 'string', desc: 'chunk / qa_pair', example: 'chunk' },
          { name: 'data[].qaPairId', type: 'string', desc: '问答对 ID（sourceType=qa_pair 时）', example: 'null' },
        ],
        responseExample: `{
  "code": 200,
  "message": "success",
  "data": [{
    "documentName": "产品手册.pdf",
    "contentPreview": "智元支持 Agent...",
    "score": 0.86,
    "sourceType": "chunk"
  }]
}`,
      },
    ],
  },
  {
    id: 'session',
    title: '会话',
    type: 'apis',
    desc: '会话与消息管理',
    apis: [
      {
        id: 'session-create',
        method: 'POST',
        path: '/api/chat/sessions',
        summary: '创建会话',
        description: '创建新会话，可选绑定 Agent。',
        contentType: 'none',
        testable: true,
        params: [
          { name: 'agentId', type: 'string', required: false, in: 'query', desc: 'Agent ID', example: '2056961707612393473' },
        ],
        responseFields: [
          ...RESULT_FIELDS,
          { name: 'data.id', type: 'string', desc: '会话 ID', example: '2056961707612393500' },
          { name: 'data.agentId', type: 'string', desc: '绑定的 Agent ID', example: '2056961707612393473' },
          { name: 'data.userId', type: 'string', desc: '所属用户 ID', example: '1' },
          { name: 'data.title', type: 'string', desc: '会话标题', example: '新对话' },
          { name: 'data.status', type: 'string', desc: 'active / archived', example: 'active' },
          { name: 'data.messageCount', type: 'number', desc: '消息数量', example: '0' },
          { name: 'data.totalTokens', type: 'number', desc: '累计 Token', example: '0' },
          { name: 'data.pinned', type: 'boolean', desc: '是否置顶', example: 'false' },
          { name: 'data.createTime', type: 'string', desc: '创建时间', example: '2026-07-29T12:00:00' },
          { name: 'data.updateTime', type: 'string', desc: '更新时间', example: '2026-07-29T12:00:00' },
        ],
        responseExample: `{
  "code": 200,
  "message": "success",
  "data": {
    "id": "2056961707612393500",
    "title": "新对话",
    "agentId": "2056961707612393473",
    "status": "active"
  }
}`,
      },
      {
        id: 'session-list',
        method: 'GET',
        path: '/api/chat/sessions',
        summary: '会话列表',
        description: '分页查询当前 Key 所属用户的会话。',
        contentType: 'none',
        testable: true,
        params: [
          { name: 'pageNum', type: 'number', required: false, in: 'query', desc: '页码，默认 1', example: '1' },
          { name: 'pageSize', type: 'number', required: false, in: 'query', desc: '每页条数，默认 20', example: '20' },
          { name: 'keyword', type: 'string', required: false, in: 'query', desc: '标题关键词', example: '产品咨询' },
        ],
        responseFields: [
          ...RESULT_FIELDS,
          ...PAGE_FIELDS,
          { name: 'data.records[].id', type: 'string', desc: '会话 ID', example: '2056...' },
          { name: 'data.records[].title', type: 'string', desc: '会话标题', example: '产品咨询' },
          { name: 'data.records[].agentId', type: 'string', desc: 'Agent ID', example: '2056...' },
          { name: 'data.records[].status', type: 'string', desc: '会话状态', example: 'active' },
          { name: 'data.records[].messageCount', type: 'number', desc: '消息数', example: '4' },
          { name: 'data.records[].lastMessageAt', type: 'string', desc: '最后消息时间', example: '2026-07-29T12:00:00' },
          { name: 'data.records[].pinned', type: 'boolean', desc: '是否置顶', example: 'false' },
        ],
        responseExample: `{
  "code": 200,
  "message": "success",
  "data": {
    "records": [{ "id": "2056...", "title": "产品咨询", "status": "active" }],
    "total": 10,
    "current": 1,
    "size": 20
  }
}`,
      },
      {
        id: 'session-detail',
        method: 'GET',
        path: '/api/chat/sessions/{id}',
        summary: '会话详情',
        description: '获取单个会话元信息。',
        contentType: 'none',
        testable: true,
        params: [
          { name: 'id', type: 'string', required: true, in: 'path', desc: '会话 ID', example: '2056961707612393500' },
        ],
        responseFields: [
          ...RESULT_FIELDS,
          { name: 'data.id', type: 'string', desc: '会话 ID', example: '2056961707612393500' },
          { name: 'data.agentId', type: 'string', desc: 'Agent ID', example: '2056961707612393473' },
          { name: 'data.title', type: 'string', desc: '会话标题', example: '产品咨询' },
          { name: 'data.status', type: 'string', desc: 'active / archived', example: 'active' },
          { name: 'data.messageCount', type: 'number', desc: '消息数量', example: '4' },
          { name: 'data.totalTokens', type: 'number', desc: '累计 Token', example: '1280' },
          { name: 'data.lastMessageAt', type: 'string', desc: '最后消息时间', example: '2026-07-29T12:00:00' },
          { name: 'data.pinned', type: 'boolean', desc: '是否置顶', example: 'false' },
          { name: 'data.createTime', type: 'string', desc: '创建时间', example: '2026-07-29T11:00:00' },
          { name: 'data.updateTime', type: 'string', desc: '更新时间', example: '2026-07-29T12:00:00' },
        ],
        responseExample: `{
  "code": 200,
  "message": "success",
  "data": { "id": "2056...", "title": "产品咨询", "status": "active" }
}`,
      },
      {
        id: 'session-messages',
        method: 'GET',
        path: '/api/chat/sessions/{id}/messages',
        summary: '消息历史',
        description: '分页获取会话消息。',
        contentType: 'none',
        testable: true,
        params: [
          { name: 'id', type: 'string', required: true, in: 'path', desc: '会话 ID', example: '2056961707612393500' },
          { name: 'pageNum', type: 'number', required: false, in: 'query', desc: '页码', example: '1' },
          { name: 'pageSize', type: 'number', required: false, in: 'query', desc: '每页条数', example: '20' },
        ],
        responseFields: [
          ...RESULT_FIELDS,
          ...PAGE_FIELDS,
          { name: 'data.records[].id', type: 'string', desc: '消息 ID', example: '2056...' },
          { name: 'data.records[].sessionId', type: 'string', desc: '所属会话 ID', example: '2056...' },
          { name: 'data.records[].role', type: 'string', desc: 'user / assistant / system / tool', example: 'user' },
          { name: 'data.records[].content', type: 'string', desc: '消息正文', example: '你好' },
          { name: 'data.records[].contentType', type: 'string', desc: 'text / image / file', example: 'text' },
          { name: 'data.records[].tokenCount', type: 'number', desc: '本条 Token 数', example: '12' },
          { name: 'data.records[].metadata', type: 'string', desc: 'JSON 元数据（RAG、思考等）', example: '{}' },
          { name: 'data.records[].toolEvents', type: 'string', desc: '工具事件 JSON', example: 'null' },
          { name: 'data.records[].starred', type: 'boolean', desc: '是否收藏', example: 'false' },
          { name: 'data.records[].createTime', type: 'string', desc: '创建时间', example: '2026-07-29T12:00:00' },
        ],
        responseExample: `{
  "code": 200,
  "message": "success",
  "data": {
    "records": [{ "role": "user", "content": "你好" }],
    "total": 2,
    "current": 1,
    "size": 20
  }
}`,
      },
      {
        id: 'session-title-put',
        method: 'PUT',
        path: '/api/chat/sessions/{id}/title',
        summary: '更新标题',
        description: '修改会话标题。',
        contentType: 'none',
        testable: true,
        params: [
          { name: 'id', type: 'string', required: true, in: 'path', desc: '会话 ID', example: '2056961707612393500' },
          { name: 'title', type: 'string', required: true, in: 'query', desc: '新标题', example: '产品咨询' },
        ],
        responseFields: [
          ...RESULT_FIELDS,
          { name: 'data', type: 'null', desc: '无业务载荷', example: 'null' },
        ],
        responseExample: `{ "code": 200, "message": "success", "data": null }`,
      },
      {
        id: 'session-delete',
        method: 'DELETE',
        path: '/api/chat/sessions/{id}',
        summary: '删除会话',
        description: '物理删除会话及其消息。',
        contentType: 'none',
        testable: true,
        params: [
          { name: 'id', type: 'string', required: true, in: 'path', desc: '会话 ID', example: '2056961707612393500' },
        ],
        responseFields: [
          ...RESULT_FIELDS,
          { name: 'data', type: 'null', desc: '无业务载荷', example: 'null' },
        ],
        responseExample: `{ "code": 200, "message": "success", "data": null }`,
      },
    ],
  },
  {
    id: 'datapool',
    title: '开放数据池',
    type: 'apis',
    desc: '数据池开放 CRUD（以数据库表名识别模型，参数均在 Query）',
    apis: [
      {
        id: 'dp-page',
        method: 'GET',
        path: '/api/open/v1/data-pools/records',
        summary: '分页查询',
        description: '按数据模型物理表名分页查询记录。tableName / 筛选等均放在 Query；记录字段随模型动态变化。',
        contentType: 'none',
        testable: true,
        params: [
          { name: 'tableName', type: 'string', required: true, in: 'query', desc: '数据库表名（创建模型时的完整表名，如 sjc_data_customer）', example: 'sjc_data_customer' },
          { name: 'pageNum', type: 'number', required: false, in: 'query', desc: '页码，默认 1', example: '1' },
          { name: 'pageSize', type: 'number', required: false, in: 'query', desc: '每页条数，默认 20', example: '20' },
          { name: 'keyword', type: 'string', required: false, in: 'query', desc: '模糊搜索关键词', example: '浙A' },
          { name: 'filters', type: 'string', required: false, in: 'query', desc: '筛选条件 JSON 字符串', example: '{"status":"active"}' },
        ],
        responseFields: [
          ...RESULT_FIELDS,
          ...PAGE_FIELDS,
          { name: 'data.records[].id', type: 'string', desc: '记录 ID', example: '2056...' },
          { name: 'data.records[].{fieldKey}', type: 'any', desc: '业务字段，key 为模型字段英文名', example: 'plate_no: "浙A12345"' },
          { name: 'data.records[].createTime', type: 'string', desc: '创建时间 yyyy-MM-dd HH:mm:ss', example: '2026-07-29 12:00:00' },
          { name: 'data.records[].updateTime', type: 'string', desc: '更新时间', example: '2026-07-29 12:00:00' },
        ],
        responseExample: `{
  "code": 200,
  "message": "success",
  "data": {
    "records": [{ "id": "2056...", "plate_no": "浙A12345" }],
    "total": 1,
    "current": 1,
    "size": 20
  }
}`,
      },
      {
        id: 'dp-create',
        method: 'POST',
        path: '/api/open/v1/data-pools/records',
        summary: '新增记录',
        description: '新增一条业务记录。tableName 放 Query；body.data 的 key 为字段英文名。',
        contentType: 'json',
        testable: true,
        params: [
          { name: 'tableName', type: 'string', required: true, in: 'query', desc: '数据库表名，如 sjc_data_customer', example: 'sjc_data_customer' },
          { name: 'data', type: 'object', required: true, in: 'body', desc: '字段英文名 → 值', example: '{ "full_name": "张三", "quantity": 2 }' },
        ],
        bodyExample: `{
  "data": {
    "full_name": "张三",
    "quantity": 2
  }
}`,
        responseFields: [
          ...RESULT_FIELDS,
          { name: 'data.id', type: 'string', desc: '新建记录 ID', example: '2056961707612393701' },
          { name: 'data.{fieldKey}', type: 'any', desc: '写入后的业务字段', example: 'full_name: "张三"' },
          { name: 'data.createTime', type: 'string', desc: '创建时间', example: '2026-07-29 12:00:00' },
          { name: 'data.updateTime', type: 'string', desc: '更新时间', example: '2026-07-29 12:00:00' },
        ],
        responseExample: `{
  "code": 200,
  "message": "success",
  "data": { "id": "2056...", "full_name": "张三", "quantity": 2 }
}`,
      },
      {
        id: 'dp-batch',
        method: 'POST',
        path: '/api/open/v1/data-pools/records/batch',
        summary: '批量新增',
        description: '批量写入多条记录。tableName 放 Query。',
        contentType: 'json',
        testable: true,
        params: [
          { name: 'tableName', type: 'string', required: true, in: 'query', desc: '数据库表名，如 sjc_data_customer', example: 'sjc_data_customer' },
          { name: 'records', type: 'array', required: true, in: 'body', desc: '记录数组，每项为字段 Map', example: '[{ "full_name": "张三" }, { "full_name": "李四" }]' },
        ],
        bodyExample: `{
  "records": [
    { "full_name": "张三" },
    { "full_name": "李四" }
  ]
}`,
        responseFields: [
          ...RESULT_FIELDS,
          { name: 'data', type: 'array', desc: '新建记录列表', example: '[]' },
          { name: 'data[].id', type: 'string', desc: '记录 ID', example: '2056...' },
          { name: 'data[].{fieldKey}', type: 'any', desc: '业务字段', example: 'full_name: "张三"' },
        ],
        responseExample: `{
  "code": 200,
  "message": "success",
  "data": [{ "id": "..." }, { "id": "..." }]
}`,
      },
      {
        id: 'dp-update',
        method: 'PUT',
        path: '/api/open/v1/data-pools/records',
        summary: '修改记录',
        description: '按记录 ID 更新字段。tableName、recordId 均放 Query。',
        contentType: 'json',
        testable: true,
        params: [
          { name: 'tableName', type: 'string', required: true, in: 'query', desc: '数据库表名，如 sjc_data_customer', example: 'sjc_data_customer' },
          { name: 'recordId', type: 'string', required: true, in: 'query', desc: '记录 ID（雪花 ID，按字符串传递）', example: '2056961707612393701' },
          { name: 'data', type: 'object', required: true, in: 'body', desc: '待更新字段', example: '{ "quantity": 3 }' },
        ],
        bodyExample: `{ "data": { "quantity": 3 } }`,
        responseFields: [
          ...RESULT_FIELDS,
          { name: 'data.id', type: 'string', desc: '记录 ID', example: '2056961707612393701' },
          { name: 'data.{fieldKey}', type: 'any', desc: '更新后的业务字段', example: 'quantity: 3' },
          { name: 'data.updateTime', type: 'string', desc: '更新时间', example: '2026-07-29 12:30:00' },
        ],
        responseExample: `{
  "code": 200,
  "message": "success",
  "data": { "id": "...", "quantity": 3 }
}`,
      },
      {
        id: 'dp-delete',
        method: 'DELETE',
        path: '/api/open/v1/data-pools/records',
        summary: '删除记录',
        description: '删除单条记录。tableName、recordId 均放 Query。',
        contentType: 'none',
        testable: true,
        params: [
          { name: 'tableName', type: 'string', required: true, in: 'query', desc: '数据库表名，如 sjc_data_customer', example: 'sjc_data_customer' },
          { name: 'recordId', type: 'string', required: true, in: 'query', desc: '记录 ID（雪花 ID，按字符串传递）', example: '2056961707612393701' },
        ],
        responseFields: [
          ...RESULT_FIELDS,
          { name: 'data', type: 'null', desc: '无业务载荷', example: 'null' },
        ],
        responseExample: `{ "code": 200, "message": "success", "data": null }`,
      },
      {
        id: 'dp-batch-delete',
        method: 'POST',
        path: '/api/open/v1/data-pools/records/batch-delete',
        summary: '批量删除',
        description: '按 ID 列表批量删除。tableName 放 Query。',
        contentType: 'json',
        testable: true,
        params: [
          { name: 'tableName', type: 'string', required: true, in: 'query', desc: '数据库表名，如 sjc_data_customer', example: 'sjc_data_customer' },
          { name: 'ids', type: 'array', required: true, in: 'body', desc: '记录 ID 列表', example: '["2056961707612393701", "2056961707612393702"]' },
        ],
        bodyExample: `{ "ids": ["2056...", "2057..."] }`,
        responseFields: [
          ...RESULT_FIELDS,
          { name: 'data', type: 'number', desc: '实际删除条数', example: '2' },
        ],
        responseExample: `{ "code": 200, "message": "success", "data": 2 }`,
      },
    ],
  },
  {
    id: 'task',
    title: '任务',
    type: 'apis',
    desc: '异步任务查询与进度',
    apis: [
      {
        id: 'task-list',
        method: 'GET',
        path: '/api/tasks',
        summary: '任务列表',
        description: '分页查询当前用户任务。',
        contentType: 'none',
        testable: true,
        params: [
          { name: 'pageNum', type: 'number', required: false, in: 'query', desc: '页码', example: '1' },
          { name: 'pageSize', type: 'number', required: false, in: 'query', desc: '每页条数', example: '20' },
          { name: 'status', type: 'string', required: false, in: 'query', desc: '状态过滤', example: 'running' },
          { name: 'type', type: 'string', required: false, in: 'query', desc: '类型过滤', example: 'document_parse' },
        ],
        responseFields: [
          ...RESULT_FIELDS,
          ...PAGE_FIELDS,
          { name: 'data.records[].id', type: 'string', desc: '任务 ID', example: '2056...' },
          { name: 'data.records[].name', type: 'string', desc: '任务名称', example: '文档解析' },
          { name: 'data.records[].type', type: 'string', desc: '任务类型（展示名）', example: '文档上传' },
          { name: 'data.records[].status', type: 'string', desc: 'pending / running / success / failed / cancelled 等', example: 'running' },
          { name: 'data.records[].progress', type: 'number', desc: '进度 0–100', example: '40' },
          { name: 'data.records[].message', type: 'string', desc: '状态说明', example: '处理中' },
          { name: 'data.records[].error', type: 'string', desc: '失败原因', example: 'null' },
          { name: 'data.records[].attempts', type: 'number', desc: '已尝试次数', example: '1' },
          { name: 'data.records[].createTime', type: 'string', desc: '创建时间', example: '2026-07-29T12:00:00' },
          { name: 'data.records[].completedAt', type: 'string', desc: '完成时间', example: 'null' },
        ],
        responseExample: `{
  "code": 200,
  "message": "success",
  "data": {
    "records": [{ "id": "...", "status": "running", "progress": 40 }],
    "total": 1,
    "current": 1,
    "size": 20
  }
}`,
      },
      {
        id: 'task-detail',
        method: 'GET',
        path: '/api/tasks/{taskId}',
        summary: '任务详情',
        description: '获取任务详情（progress / message 可能来自 Redis 实时快照）。',
        contentType: 'none',
        testable: true,
        params: [
          { name: 'taskId', type: 'string', required: true, in: 'path', desc: '任务 ID', example: '2056961707612393800' },
        ],
        responseFields: [
          ...RESULT_FIELDS,
          { name: 'data.id', type: 'string', desc: '任务 ID', example: '2056961707612393800' },
          { name: 'data.name', type: 'string', desc: '任务名称', example: '文档解析' },
          { name: 'data.type', type: 'string', desc: '任务类型', example: '文档上传' },
          { name: 'data.status', type: 'string', desc: '任务状态', example: 'running' },
          { name: 'data.progress', type: 'number', desc: '进度 0–100', example: '60' },
          { name: 'data.message', type: 'string', desc: '状态说明', example: '处理中' },
          { name: 'data.payload', type: 'string', desc: '输入 JSON', example: '{}' },
          { name: 'data.result', type: 'string', desc: '输出 JSON', example: 'null' },
          { name: 'data.error', type: 'string', desc: '错误信息', example: 'null' },
          { name: 'data.attempts', type: 'number', desc: '已尝试次数', example: '1' },
          { name: 'data.maxAttempts', type: 'number', desc: '最大重试次数', example: '3' },
          { name: 'data.refId', type: 'string', desc: '关联业务 ID', example: 'null' },
          { name: 'data.createTime', type: 'string', desc: '创建时间', example: '2026-07-29T12:00:00' },
          { name: 'data.startedAt', type: 'string', desc: '开始时间', example: '2026-07-29T12:00:01' },
          { name: 'data.completedAt', type: 'string', desc: '完成时间', example: 'null' },
        ],
        responseExample: `{
  "code": 200,
  "message": "success",
  "data": { "id": "...", "status": "running", "progress": 60, "message": "处理中" }
}`,
      },
      {
        id: 'task-progress',
        method: 'GET',
        path: '/api/tasks/{taskId}/progress',
        summary: '任务进度',
        description: '读取任务实时进度快照；无快照时 data 可能为 null。',
        contentType: 'none',
        testable: true,
        params: [
          { name: 'taskId', type: 'string', required: true, in: 'path', desc: '任务 ID', example: '2056961707612393800' },
        ],
        responseFields: [
          ...RESULT_FIELDS,
          { name: 'data.progress', type: 'number', desc: '进度 0–100', example: '80' },
          { name: 'data.message', type: 'string', desc: '状态文本', example: '即将完成' },
          { name: 'data.ts', type: 'number', desc: '快照时间戳（毫秒）', example: '1722230400000' },
        ],
        responseExample: `{
  "code": 200,
  "message": "success",
  "data": { "progress": 80, "message": "即将完成", "ts": 1722230400000 }
}`,
      },
      {
        id: 'task-stream',
        method: 'GET',
        path: '/api/tasks/stream',
        summary: '任务事件流（SSE）',
        description: '订阅任务计数/进度事件流。响应为 text/event-stream（非 Result 包装）。',
        contentType: 'sse',
        testable: true,
        params: [],
        responseFields: [
          { name: 'event', type: 'string', desc: '事件名，如 count', example: 'count' },
          { name: 'data.active', type: 'number', desc: '未完成任务数（pending+running+pendingRetry）', example: '3' },
          { name: 'data.pending', type: 'number', desc: '等待中', example: '1' },
          { name: 'data.running', type: 'number', desc: '执行中', example: '2' },
          { name: 'data.pendingRetry', type: 'number', desc: '待重试', example: '0' },
        ],
        responseExample: `event: count
data: {"active":3,"pending":1,"running":2,"pendingRetry":0}`,
      },
    ],
  },
]
