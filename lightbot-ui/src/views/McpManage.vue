<template>
  <div class="page">
    <LbManageHeader
      v-if="!hideHeader"
      title="MCP Server"
      v-model="searchText"
      search-placeholder="搜索 Server 名称..."
      :refresh-disabled="loading"
      create-text="新增 Server"
      @refresh="refresh"
      @create="openDialog()"
    >
      <template #searchPrefix><SearchOutlined /></template>
    </LbManageHeader>

    <a-spin :spinning="loading" style="min-height: 300px; display: block;">
    <div class="provider-grid">
      <EntityCard
        v-for="s in list"
        :key="s.id"
        type="mcp"
        :name="s.name"
        @click="openDetail(s)"
      >
        <template #icon>
          <span v-if="isBuiltin(s)" class="builtin-badge">内置</span>
          <span class="status-dot" :class="isDisabled(s) ? 'status-disabled' : 'status-active'"></span>
          <DynamicIcon :name="s.icon" :fallback="s.name" />
        </template>
        <template #actions>
          <a-tooltip v-if="!isBuiltin(s)" title="删除">
            <button class="btn-icon danger" @click="handleDelete(s.id)"><DeleteOutlined /></button>
          </a-tooltip>
          <a-dropdown :trigger="['click']">
            <button class="btn-icon" @click.prevent><MoreOutlined /></button>
            <template #overlay>
              <a-menu>
                <a-menu-item @click="handleToggleEnabled(s)">
                  <CheckCircleOutlined v-if="!isDisabled(s)" style="color: var(--green-600); margin-right: 6px" />
                  <CloseCircleOutlined v-else style="color: var(--color-mute); margin-right: 6px" />
                  {{ isDisabled(s) ? '启用' : '禁用' }}
                </a-menu-item>
                <a-menu-item @click="handleTest(s)">
                  <ApiOutlined style="margin-right: 6px" /> 测试连接
                </a-menu-item>
                <a-menu-item @click="openToolsDrawer(s)">
                  <ToolOutlined style="margin-right: 6px" /> 查看工具
                </a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
        </template>
        <div class="card-body">
          <div class="card-tags">
            <span class="tag tag-install" :class="'tag-' + (s.installType?.code || s.installType)">
              {{ getInstallTypeLabel(s.installType?.code || s.installType) }}
            </span>
            <span v-if="s.transport?.code || s.transport" class="tag tag-transport">
              {{ s.transport?.code || s.transport }}
            </span>
          </div>
          <a-tooltip v-if="s.description" :title="s.description" placement="topLeft" :overlay-style="{ maxWidth: '400px' }">
            <span class="card-desc">{{ truncateText(s.description, 50) }}</span>
          </a-tooltip>
          <div v-if="s.lastSyncTime" class="card-sync-time">
            <SyncOutlined style="font-size: 10px; margin-right: 4px" />
            <span>同步于 {{ formatSyncTime(s.lastSyncTime) }}</span>
          </div>
        </div>
      </EntityCard>

      <LbEmptyState
        v-if="list.length === 0 && !loading"
        :icon="ApiOutlined"
        :title="searchText ? '没有匹配的 MCP Server' : '还没有 MCP Server，点击右上角创建一个吧'"
      />
    </div>
    </a-spin>

    <!-- 新增/编辑弹窗 -->
    <a-modal v-model:open="dialogVisible" :width="560" :maskClosable="false">
      <template #title>
        <span>{{ form.id ? '编辑 MCP Server' : '新增 MCP Server' }}</span>
        <QuestionCircleOutlined class="help-icon" @click="guideVisible = true" />
      </template>
      <div class="dialog-scroll-body">
      <a-form :model="form" :label-col="{ flex: '0 0 100px' }">
        <a-form-item label="名称" required>
          <a-input v-model:value="form.name" placeholder="请输入服务名称（例如：filesystem-server，不超过 50 字）" :maxlength="50" show-count />
        </a-form-item>
        <a-form-item label="图标">
          <IconPicker v-model:value="form.icon" />
        </a-form-item>
        <a-form-item label="描述">
          <a-input v-model:value="form.description" placeholder="请输入服务用途说明（不超过 200 字）" :maxlength="200" show-count />
        </a-form-item>
        <a-form-item label="安装类型" required>
          <a-select v-model:value="form.installType" style="width: 100%" @change="onInstallTypeChange">
            <a-select-option value="npx">NPX (Node.js)</a-select-option>
            <a-select-option value="uvx">UVX (Python)</a-select-option>
            <a-select-option value="sse">SSE (远程服务)</a-select-option>
          </a-select>
        </a-form-item>

        <!-- npx / uvx 配置 -->
        <template v-if="form.installType === 'npx' || form.installType === 'uvx'">
          <a-form-item label="包名">
            <a-input v-model:value="deployForm.packageName" placeholder="如：@modelcontextprotocol/server-filesystem" />
          </a-form-item>
          <a-form-item label="命令参数">
            <a-textarea v-model:value="deployForm.args" :rows="2" placeholder="每行一个参数，如：--allow-read&#10;/tmp" />
          </a-form-item>
          <a-form-item label="环境变量">
            <a-textarea v-model:value="deployForm.env" :rows="2" placeholder="KEY=VALUE，每行一个" />
          </a-form-item>
        </template>

        <!-- sse 配置 -->
        <template v-if="form.installType === 'sse'">
          <a-form-item label="传输协议">
            <a-select v-model:value="form.transport" style="width: 100%">
              <a-select-option value="sse">SSE</a-select-option>
              <a-select-option value="streamable_http">Streamable HTTP</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="服务地址" required>
            <a-input v-model:value="form.host" placeholder="请输入服务地址（不超过 2048 字）" :maxlength="2048" show-count />
          </a-form-item>
          <a-form-item label="请求头">
            <JsonInput v-model="deployForm.headers" :rows="2" :max-length="8000" placeholder='JSON 请求头（不超过 8000 字）' />
          </a-form-item>
        </template>
      </a-form>
      </div>
      <template #footer>
        <LbDialogFooter
          :loading="submitting"
          @cancel="dialogVisible = false"
          @confirm="handleSubmit"
        />
      </template>
    </a-modal>

    <!-- 配置指南弹窗 -->
    <a-modal v-model:open="guideVisible" title="MCP Server 配置指南" :width="640" :footer="null">
      <div class="dialog-scroll-body">
      <div class="guide">
        <div class="guide-section">
          <div class="guide-h3">什么是 MCP？</div>
          <p>MCP (Model Context Protocol) 是 Anthropic 开放的 AI 工具协议，让 AI 能调用外部工具（如文件操作、图表生成、数据库查询等）。</p>
        </div>

        <div class="guide-section">
          <div class="guide-h3">配置步骤</div>
          <div class="guide-step">
            <span class="guide-num">1</span>
            <div>
              <b>选择安装类型</b>
              <p>根据 MCP Server 的发布方式选择：NPX（Node.js 包）、UVX（Python 包）、SSE（远程 HTTP 服务）</p>
            </div>
          </div>
          <div class="guide-step">
            <span class="guide-num">2</span>
            <div>
              <b>填写配置</b>
              <p>不同类型的配置项不同，详见下方示例</p>
            </div>
          </div>
          <div class="guide-step">
            <span class="guide-num">3</span>
            <div>
              <b>测试连接</b>
              <p>创建后点击卡片上的「测试连接」，验证 MCP Server 是否可达</p>
            </div>
          </div>
          <div class="guide-step">
            <span class="guide-num">4</span>
            <div>
              <b>绑定 Agent</b>
              <p>在 Agent 详情页的「MCP 工具」Tab 中绑定该 MCP Server，对话时 LLM 即可自动调用其工具</p>
            </div>
          </div>
        </div>

        <div class="guide-section">
          <div class="guide-h3">NPX 示例（本地 Node.js 工具）</div>
          <div class="guide-code">
            <div><b>安装类型</b>：NPX (Node.js)</div>
            <div><b>包名</b>：@modelcontextprotocol/server-filesystem</div>
            <div><b>命令参数</b>（每行一个）：</div>
            <pre>--allow-read
/tmp</pre>
          </div>
        </div>

        <div class="guide-section">
          <div class="guide-h3">UVX 示例（本地 Python 工具）</div>
          <div class="guide-code">
            <div><b>安装类型</b>：UVX (Python)</div>
            <div><b>包名</b>：mcp-server-fetch</div>
          </div>
        </div>

        <div class="guide-section">
          <div class="guide-h3">SSE / Streamable HTTP 示例（远程服务）</div>
          <div class="guide-code">
            <div><b>安装类型</b>：SSE (远程服务)</div>
            <div><b>传输协议</b>：SSE 或 Streamable HTTP（根据服务端支持选择）</div>
            <div><b>服务地址</b>：http://localhost:3001/sse</div>
            <div><b>请求头</b>（如需认证）：</div>
            <pre>{"Authorization": "Bearer your-token"}</pre>
          </div>
        </div>

        <div class="guide-section">
          <div class="guide-h3">环境变量说明</div>
          <p>部分 MCP Server 需要 API Key 等环境变量，格式为 <code>KEY=VALUE</code>，每行一个。例如：</p>
          <pre class="guide-pre">GITHUB_TOKEN=ghp_xxxxxxxxxxxx
OPENAI_API_KEY=sk-xxxxxxxxxxxx</pre>
        </div>
      </div>
      </div>
    </a-modal>

    <!-- 详情弹窗 -->
    <a-modal v-model:open="detailVisible" title="MCP Server 详情" :width="640" :maskClosable="false">
      <div class="dialog-scroll-body">
      <div class="detail-scroll-body">
        <template v-if="detailRow">
          <a-descriptions bordered :column="1" size="small">
            <a-descriptions-item label="名称">
              {{ detailRow.name }}
              <a-tag v-if="isBuiltin(detailRow)" color="blue" style="margin-left: 8px">内置</a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="描述">{{ detailRow.description || '—' }}</a-descriptions-item>
            <a-descriptions-item label="安装类型">
              <a-tag :color="installTypeColor(detailRow.installType?.code || detailRow.installType)">
                {{ getInstallTypeLabel(detailRow.installType?.code || detailRow.installType) }}
              </a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="传输协议">{{ detailRow.transport?.code || detailRow.transport || 'stdio' }}</a-descriptions-item>
            <a-descriptions-item v-if="detailRow.host" label="服务地址">{{ detailRow.host }}</a-descriptions-item>
            <a-descriptions-item label="状态">
              <a-tag :color="isDisabled(detailRow) ? 'default' : 'success'">
                {{ isDisabled(detailRow) ? '已禁用' : '已启用' }}
              </a-tag>
            </a-descriptions-item>
          </a-descriptions>
        </template>
      </div>
      </div>
      <template #footer>
        <LbDialogFooter
          cancel-text="关闭"
          hide-confirm
          @cancel="detailVisible = false"
        >
          <template #left>
            <button v-if="detailRow" class="lb-btn" @click="detailVisible = false; openDialog(detailRow)">
              <EditOutlined /> 编辑
            </button>
          </template>
        </LbDialogFooter>
      </template>
    </a-modal>

    <!-- 工具列表抽屉 -->
    <a-drawer
      v-model:open="toolsDrawerVisible"
      title="MCP 工具列表"
      :width="520"
      :bodyStyle="{ padding: '16px' }"
    >
      <div class="tools-header">
        <div class="tools-header-left">
          <span class="tools-server-name">{{ currentServer?.name }}</span>
          <span class="tools-count">{{ toolsList.length }} 个工具</span>
        </div>
        <button class="btn-refresh" :disabled="toolsLoading" @click="handleRefreshTools">
          <SyncOutlined :spin="toolsLoading" /> 刷新工具
        </button>
      </div>
      <a-spin :spinning="toolsLoading">
        <div v-if="toolsError" class="tools-error">
          <div class="tools-error-title">加载失败</div>
          <div class="tools-error-msg">{{ toolsError }}</div>
          <button class="btn-retry" @click="loadTools">重新加载</button>
        </div>
        <div v-else-if="toolsList.length === 0 && !toolsLoading" class="tools-empty">暂无工具，请检查 MCP Server 配置</div>
        <div v-for="tool in toolsList" :key="tool.name" class="tool-item">
          <div class="tool-header">
            <div class="tool-info">
              <div class="tool-name">{{ tool.name }}</div>
              <div class="tool-desc">{{ tool.description || '暂无描述' }}</div>
            </div>
            <div class="tool-actions">
              <button class="tool-detail-btn" @click="openToolDetailModal(tool)">
                <EyeOutlined /> 详情
              </button>
              <a-switch
                :checked="tool.enabled"
                size="small"
                :loading="toolToggling === tool.name"
                @change="(checked) => handleToggleTool(tool.name, checked)"
              />
            </div>
          </div>
        </div>
      </a-spin>
    </a-drawer>

    <!-- 工具详情弹窗 -->
    <a-modal
      v-model:open="toolDetailVisible"
      :title="toolDetail?.name || '工具详情'"
      :width="720"
      :footer="null"
    >
      <div class="dialog-scroll-body">
      <div class="tool-detail-modal">
        <div class="detail-section">
          <div class="detail-label">工具名称</div>
          <div class="detail-value">{{ toolDetail?.name }}</div>
        </div>
        <div class="detail-section">
          <div class="detail-label">工具描述</div>
          <div class="detail-value">{{ toolDetail?.description || '暂无描述' }}</div>
        </div>
        <div class="detail-section">
          <div class="detail-label">参数定义</div>
          <div v-if="parsedSchema.length > 0" class="schema-table-wrap">
            <table class="schema-table">
              <thead>
                <tr>
                  <th>参数名</th>
                  <th>类型</th>
                  <th>描述</th>
                  <th>默认值</th>
                  <th>必填</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="prop in parsedSchema" :key="prop.name">
                  <td class="prop-name">{{ prop.name }}</td>
                  <td class="prop-type">{{ prop.type }}</td>
                  <td class="prop-desc">{{ prop.description || '-' }}</td>
                  <td class="prop-default">{{ formatDefaultValue(prop.default) }}</td>
                  <td class="prop-required">{{ prop.required ? '是' : '否' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <div v-else class="schema-empty">无参数（可直接调用）</div>
        </div>
        <div v-if="parsedSchema.length > 0" class="detail-section">
          <div class="detail-label">请求示例</div>
          <div class="example-wrap">
            <pre class="example-json">{{ requestExample }}</pre>
            <button class="copy-btn" @click="copyExample">
              <CopyOutlined /> 复制
            </button>
          </div>
        </div>
      </div>
      </div>
    </a-modal>

    <!-- 测试连接结果弹窗 -->
    <a-modal
      v-model:open="testResultVisible"
      :title="testResultData.title"
      :width="480"
      :mask-closable="true"
      wrap-class-name="mcp-test-result-modal"
    >
      <p v-if="testResultData.summary" class="test-result-summary">{{ testResultData.summary }}</p>
      <ul v-if="testResultData.tools.length" class="test-result-list">
        <li v-for="t in testResultData.tools" :key="t.name">{{ t.name }}</li>
      </ul>
      <div v-if="testResultData.error" class="test-result-error">{{ testResultData.error }}</div>
      <template #footer>
        <LbDialogFooter
          hide-cancel
          confirm-text="知道了"
          @confirm="testResultVisible = false"
        />
      </template>
    </a-modal>
  </div>
</template>

<script setup>
defineOptions({ name: 'McpManage' })
defineProps({ hideHeader: Boolean })
import { ref, reactive, watch, onMounted, computed } from 'vue'
import { PlusOutlined, EditOutlined, DeleteOutlined, SearchOutlined, ReloadOutlined, ApiOutlined, ToolOutlined, QuestionCircleOutlined, SyncOutlined, EyeOutlined, CopyOutlined, MoreOutlined, CheckCircleOutlined, CloseCircleOutlined } from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import { getMcpServers, createMcpServer, updateMcpServer, deleteMcpServer, testMcpServer, getMcpServerTools, refreshMcpServerTools, toggleMcpTool, setMcpServerEnabled } from '../api/mcp'
import JsonInput from '../components/JsonInput.vue'
import EntityCard from '../components/EntityCard.vue'
import DynamicIcon from '../components/DynamicIcon.vue'
import IconPicker from '../components/IconPicker.vue'
import LbDialogFooter from '../components/common/LbDialogFooter.vue'
import LbManageHeader from '../components/common/LbManageHeader.vue'
import LbEmptyState from '../components/common/LbEmptyState.vue'
import { truncateText } from '../utils/format'
import { copyToClipboard } from '../utils/clipboard'

const list = ref([])
const loading = ref(false)
const searchText = ref('')
const dialogVisible = ref(false)
const submitting = ref(false)
const form = reactive({ id: null, name: '', icon: '', description: '', installType: 'npx', transport: 'stdio', host: '' })
const deployForm = reactive({ packageName: '', args: '', env: '', headers: '{}' })
const testingId = ref(null)
const toolsDrawerVisible = ref(false)
const toolsList = ref([])
const toolsLoading = ref(false)
const toolsError = ref('')
const toolsServerId = ref(null)
const currentServer = ref(null)
const toolToggling = ref(null)
const guideVisible = ref(false)
const detailVisible = ref(false)
const detailRow = ref(null)
// 工具详情弹窗
const toolDetailVisible = ref(false)
const toolDetail = ref(null)
// 测试连接结果弹窗
const testResultVisible = ref(false)
const testResultData = reactive({
  title: '',
  summary: '',
  tools: [],
  error: '',
})

function isBuiltin(server) {
  return Number(server?.isBuiltin) === 1
}

// 解析 inputSchema 为表格数据
const parsedSchema = computed(() => {
  if (!toolDetail.value?.inputSchema) return []
  try {
    const schema = typeof toolDetail.value.inputSchema === 'string'
      ? JSON.parse(toolDetail.value.inputSchema)
      : toolDetail.value.inputSchema
    const properties = schema?.properties || {}
    const required = schema?.required || []
    return Object.entries(properties).map(([name, prop]) => ({
      name,
      type: prop.type || 'any',
      description: prop.description || '',
      required: required.includes(name),
      default: prop.default,        // 保留原始默认值
      example: prop.example || prop.examples?.[0]  // 保留原始示例值
    }))
  } catch {
    return []
  }
})

// 根据参数定义生成请求示例（优先使用原始default/example）
const requestExample = computed(() => {
  if (parsedSchema.value.length === 0) return '{}'
  const example = {}
  for (const prop of parsedSchema.value) {
    // 优先使用原始default/example，否则根据类型生成
    if (prop.default !== undefined) {
      example[prop.name] = prop.default
    } else if (prop.example !== undefined) {
      example[prop.name] = prop.example
    } else {
      example[prop.name] = generateExampleValue(prop.type)
    }
  }
  return JSON.stringify(example, null, 2)
})

// 根据类型生成示例值（作为fallback）
function generateExampleValue(type) {
  switch (type) {
    case 'string': return ''
    case 'number': return 0
    case 'integer': return 0
    case 'boolean': return false
    case 'array': return []
    case 'object': return {}
    default: return null
  }
}

// 复制示例
async function copyExample() {
  await copyToClipboard(requestExample.value)
  message.success('已复制到剪贴板')
}

// 格式化默认值显示
function formatDefaultValue(val) {
  if (val === undefined) return '-'
  if (val === null) return 'null'
  if (typeof val === 'object') return JSON.stringify(val)
  return String(val)
}

async function loadData() {
  loading.value = true
  try {
    const params = { pageNum: 1, pageSize: 50 }
    if (searchText.value) params.name = searchText.value
    const res = await getMcpServers(params)
    list.value = res.data.records || []
  } finally {
    loading.value = false
  }
}

let searchDebounceTimer = null
watch(searchText, () => {
  clearTimeout(searchDebounceTimer)
  // 立刻置 loading，避免 debounce 的 300ms 窗口期里 list=[] + loading=false 触发空状态闪现
  loading.value = true
  searchDebounceTimer = setTimeout(() => loadData(), 300)
})

function parseDeployConfig(configStr) {
  deployForm.packageName = ''
  deployForm.args = ''
  deployForm.env = ''
  deployForm.headers = '{}'
  if (!configStr) return
  try {
    const cfg = typeof configStr === 'string' ? JSON.parse(configStr) : configStr
    deployForm.packageName = cfg.packageName || ''
    deployForm.args = Array.isArray(cfg.args) ? cfg.args.join('\n') : (cfg.args || '')
    deployForm.env = cfg.env ? Object.entries(cfg.env).map(([k, v]) => `${k}=${v}`).join('\n') : ''
    deployForm.headers = cfg.headers ? JSON.stringify(cfg.headers, null, 2) : '{}'
  } catch { /* ignore */ }
}

function buildDeployConfig() {
  const cfg = {}
  if (form.installType === 'npx' || form.installType === 'uvx') {
    if (deployForm.packageName) cfg.packageName = deployForm.packageName
    if (deployForm.args) cfg.args = deployForm.args.split('\n').map(s => s.trim()).filter(Boolean)
    if (deployForm.env) {
      cfg.env = {}
      deployForm.env.split('\n').forEach(line => {
        const idx = line.indexOf('=')
        if (idx > 0) cfg.env[line.substring(0, idx).trim()] = line.substring(idx + 1).trim()
      })
    }
  }
  return Object.keys(cfg).length > 0 ? JSON.stringify(cfg) : null
}

function buildHeaders() {
  if (!deployForm.headers) return null
  try {
    return typeof deployForm.headers === 'string' ? deployForm.headers : JSON.stringify(deployForm.headers)
  } catch { return null }
}

function onInstallTypeChange() {
  deployForm.packageName = ''
  deployForm.args = ''
  deployForm.env = ''
  deployForm.headers = '{}'
  form.host = ''
  form.transport = form.installType === 'sse' ? 'sse' : 'stdio'
}

function openDialog(row) {
  if (row) {
    const transport = row.transport?.code || row.transport || (row.installType === 'sse' ? 'sse' : 'stdio')
    Object.assign(form, { ...row, installType: row.installType?.code || row.installType, transport })
    parseDeployConfig(row.deployConfig)
    // Headers are stored separately; only fall back to legacy deployConfig headers if absent.
    if (row.headers !== undefined) {
      if (typeof row.headers === 'string') {
        deployForm.headers = row.headers.trim() ? row.headers : '{}'
      } else {
        deployForm.headers = row.headers ? JSON.stringify(row.headers, null, 2) : '{}'
      }
    }
  } else {
    Object.assign(form, { id: null, name: '', icon: '', description: '', installType: 'npx', transport: 'stdio', host: '' })
    onInstallTypeChange()
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!form.name.trim()) return message.warning('请输入名称')
  submitting.value = true
  try {
    const data = { ...form, deployConfig: buildDeployConfig(), headers: buildHeaders() }
    if (form.id) {
      await updateMcpServer(data)
      message.success('更新成功')
    } else {
      await createMcpServer(data)
      message.success('创建成功')
    }
    dialogVisible.value = false
    loadData()
  } finally {
    submitting.value = false
  }
}

function handleDelete(id) {
  Modal.confirm({
    title: '确认删除',
    content: '删除后该 MCP Server 将无法恢复，是否继续？',
    okText: '确认删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      await deleteMcpServer(id)
      message.success('删除成功')
      loadData()
    },
  })
}

async function handleTest(server) {
  testingId.value = server.id
  try {
    const res = await testMcpServer(server.id)
    const tools = res.data || []
    testResultData.title = `连接成功 — ${server.name}`
    testResultData.summary = `发现 ${tools.length} 个工具：`
    testResultData.tools = tools
    testResultData.error = ''
    testResultVisible.value = true
  } catch (e) {
    const msg = e?.response?.data?.message || e?.message || '连接失败，请检查配置'
    testResultData.title = `连接失败 — ${server.name}`
    testResultData.summary = ''
    testResultData.tools = []
    testResultData.error = msg
    testResultVisible.value = true
  } finally {
    testingId.value = null
  }
}

async function openToolsDrawer(server) {
  currentServer.value = server
  toolsServerId.value = server.id
  toolsDrawerVisible.value = true
  await loadTools()
}

async function loadTools() {
  toolsLoading.value = true
  toolsError.value = ''
  try {
    const res = await getMcpServerTools(toolsServerId.value)
    toolsList.value = res.data || []
  } catch (e) {
    toolsList.value = []
    toolsError.value = e?.response?.data?.message || e?.message || '连接失败，请检查配置'
  } finally {
    toolsLoading.value = false
  }
}

function openToolDetailModal(tool) {
  toolDetail.value = tool
  toolDetailVisible.value = true
}

async function handleRefreshTools() {
  toolsLoading.value = true
  try {
    await refreshMcpServerTools(toolsServerId.value)
    message.success('工具已刷新')
    await loadTools()
  } catch (e) {
    message.error('刷新失败: ' + (e?.response?.data?.message || e?.message))
  } finally {
    toolsLoading.value = false
  }
}

async function handleToggleTool(toolName, enabled) {
  toolToggling.value = toolName
  try {
    await toggleMcpTool(toolsServerId.value, toolName)
    // 更新本地状态
    const tool = toolsList.value.find(t => t.name === toolName)
    if (tool) {
      tool.enabled = enabled
    }
    message.success(enabled ? '工具已启用' : '工具已禁用')
  } catch (e) {
    message.error('操作失败: ' + (e?.response?.data?.message || e?.message))
  } finally {
    toolToggling.value = null
  }
}

function formatSchema(schema) {
  if (!schema) return '{}'
  try {
    const obj = typeof schema === 'string' ? JSON.parse(schema) : schema
    return JSON.stringify(obj, null, 2)
  } catch {
    return schema
  }
}

onMounted(loadData)

function getInstallTypeLabel(type) {
  const map = { npx: 'NPX', uvx: 'UVX', sse: 'SSE' }
  return map[type] || type
}

function installTypeColor(type) {
  const map = { npx: 'green', uvx: 'orange', sse: 'pink' }
  return map[type] || 'default'
}

function isDisabled(s) {
  const status = s.status?.code || s.status
  return status === 'disabled' || status === 'DISABLED'
}

function openDetail(row) {
  detailRow.value = row
  detailVisible.value = true
}

async function handleToggleEnabled(server) {
  const next = isDisabled(server)
  await setMcpServerEnabled(server.id, next)
  message.success(next ? '已启用' : '已禁用')
  loadData()
}

function search(text) {
  const next = text || ''
  if (searchText.value === next) return
  searchText.value = next
  loadData()
}

function refresh() {
  searchText.value = ''
  loadData()
}

function formatSyncTime(time) {
  if (!time) return ''
  const date = new Date(time)
  const now = new Date()
  const diffMs = now - date
  const diffMin = Math.floor(diffMs / 60000)
  if (diffMin < 1) return '刚刚'
  if (diffMin < 60) return `${diffMin}分钟前`
  const diffHour = Math.floor(diffMin / 60)
  if (diffHour < 24) return `${diffHour}小时前`
  const diffDay = Math.floor(diffHour / 24)
  return `${diffDay}天前`
}

defineExpose({ openDialog, search, refresh, loading })
</script>

<style scoped>

.provider-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}
.empty-state {
  grid-column: 1 / -1;
  text-align: center;
  padding: 48px 24px;
  color: var(--color-mute);
}
.empty-icon {
  font-size: 48px;
  color: var(--gray-300);
  margin-bottom: 12px;
  display: block;
}
.empty-state p {
  margin: 0;
  font-size: 14px;
}
.builtin-badge {
  position: absolute;
  top: -4px;
  right: -4px;
  font-size: 10px;
  padding: 1px 4px;
  background: var(--color-link);
  color: var(--color-on-primary);
  border-radius: 4px;
  z-index: 1;
}
.status-dot {
  position: absolute;
  bottom: -2px;
  right: -2px;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  border: 2px solid var(--color-canvas);
  z-index: 1;
}
.status-active {
  background: var(--green-600);
}
.status-disabled {
  background: var(--gray-400);
}
.card-type {
  font-size: 12px;
  color: var(--color-mute);
  background: var(--color-canvas-soft-2);
  padding: 2px 8px;
  border-radius: 100px;
}
.card-desc {
  font-size: 13px;
  color: var(--color-mute);
  line-height: 1.5;
}
.card-tags {
  display: flex;
  gap: 6px;
  margin-top: 8px;
  flex-wrap: wrap;
}
.tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  font-weight: 500;
  padding: 3px 10px;
  border-radius: 6px;
  line-height: 1.4;
}
.tag-install {
  background: var(--blue-50);
  color: var(--blue-500);
  border: 1px solid var(--blue-100);
}
.tag-npx {
  background: var(--color-success-bg);
  color: var(--green-600);
  border: 1px solid var(--green-100);
}
.tag-uvx {
  background: var(--color-warn-bg-deep);
  color: var(--color-warning-deep);
  border: 1px solid var(--color-warn-bg-deep);
}
.tag-sse {
  background: var(--color-error-bg);
  color: var(--color-error-deep);
  border: 1px solid var(--color-error-soft);
}
.tag-transport {
  background: var(--color-purple-bg);
  color: var(--purple-600);
  border: 1px solid var(--purple-100);
}
.card-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.dialog-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 16px;
  border-top: 1px solid var(--color-hairline);
  margin-top: 8px;
}
.dialog-footer-right {
  display: flex;
  gap: 8px;
}
.dialog-footer-left {
  display: flex;
  gap: 8px;
}
.btn-cancel {
  padding: 6px 14px;
  background: var(--color-canvas);
  color: var(--color-mute);
  border: 1px solid var(--color-hairline);
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
}
.btn-cancel:hover {
  border-color: var(--color-ink);
  color: var(--color-ink);
}
.btn-primary-sm {
  padding: 6px 14px;
  background: var(--color-primary);
  color: var(--color-on-primary);
  border: none;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
}
.btn-primary-sm:hover:not(:disabled) {
  background: var(--gray-800);
}
.btn-primary-sm:disabled {
  background: var(--gray-300);
  cursor: not-allowed;
}
.card-footer {
  display: flex;
  gap: 8px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--color-hairline);
}
.btn-text {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  background: transparent;
  border: 1px solid var(--color-hairline);
  border-radius: 6px;
  font-size: 12px;
  color: var(--color-body);
  cursor: pointer;
  transition: all 0.15s;
}
.btn-text:hover:not(:disabled) {
  border-color: var(--color-link);
  color: var(--color-link);
}
.btn-text:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.tools-empty {
  text-align: center;
  color: var(--color-mute);
  padding: 40px 0;
}
.tools-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 0;
  margin-bottom: 16px;
  border-bottom: 1px solid var(--color-hairline);
}
.tools-header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.tools-server-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-ink);
}
.tools-count {
  font-size: 12px;
  color: var(--color-mute);
  background: var(--color-canvas-soft-2);
  padding: 2px 8px;
  border-radius: 100px;
}
.btn-refresh {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
  border-radius: 6px;
  font-size: 12px;
  color: var(--color-body);
  cursor: pointer;
  transition: all 0.15s;
}
.btn-refresh:hover:not(:disabled) {
  border-color: var(--color-link);
  color: var(--color-link);
}
.btn-refresh:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.btn-retry {
  margin-top: 12px;
  padding: 6px 16px;
  background: var(--color-link);
  color: var(--color-on-primary);
  border: none;
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
}
.btn-retry:hover {
  background: var(--color-link-deep);
}
.tool-item {
  display: flex;
  flex-direction: column;
  padding: 12px;
  border: 1px solid var(--color-hairline);
  border-radius: 8px;
  margin-bottom: 8px;
}
.tool-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}
.tool-info {
  flex: 1;
  min-width: 0;
}
.tool-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-ink);
}
.tool-desc {
  font-size: 12px;
  color: var(--color-mute);
  margin-top: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tool-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}
.tool-detail-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
  border-radius: 6px;
  font-size: 12px;
  color: var(--color-body);
  cursor: pointer;
  transition: all 0.15s;
}
.tool-detail-btn:hover {
  border-color: var(--color-link);
  color: var(--color-link);
}
.tool-detail-modal {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.detail-section {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.detail-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-body);
}
.detail-value {
  font-size: 14px;
  color: var(--color-ink);
}
.detail-scroll-body {
  max-height: calc(100vh - 260px);
  overflow-y: auto;
  padding-right: var(--scroll-content-gap, 8px);
  scrollbar-gutter: stable;
}
.detail-scroll-body :deep(.ant-descriptions-view) {
  table-layout: fixed;
}
.detail-scroll-body :deep(.ant-descriptions-item-label) {
  width: 100px;
  min-width: 100px;
  white-space: nowrap;
}
.detail-scroll-body :deep(.ant-descriptions-item-content) {
  word-break: break-all;
  overflow-wrap: break-word;
}
.schema-table-wrap {
  border: 1px solid var(--color-hairline);
  border-radius: 8px;
  overflow: hidden;
}
.schema-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.schema-table th {
  background: var(--color-canvas-soft);
  padding: 10px 12px;
  text-align: left;
  font-weight: 500;
  color: var(--color-body);
  border-bottom: 1px solid var(--color-hairline);
  white-space: nowrap;
}
.schema-table td {
  padding: 10px 12px;
  border-bottom: 1px solid var(--color-hairline);
  color: var(--color-ink);
  word-break: break-word;
  overflow-wrap: break-word;
}
.schema-table tr:last-child td {
  border-bottom: none;
}
.prop-name {
  font-weight: 500;
  color: var(--color-link);
}
.prop-type {
  color: var(--purple-600);
  font-size: 12px;
}
.prop-desc {
  color: var(--color-mute);
}
.prop-required {
  text-align: center;
}
.schema-empty {
  color: var(--color-mute);
  font-size: 13px;
  padding: 16px;
  text-align: center;
  background: var(--color-canvas-soft);
  border-radius: 8px;
}
.example-wrap {
  position: relative;
}
.example-json {
  background: #1e1e2e;
  color: #cdd6f4;
  padding: 12px 16px;
  border-radius: 8px;
  font-size: 13px;
  margin: 0;
  overflow-x: auto;
  max-height: 200px;
}
.copy-btn {
  position: absolute;
  top: 8px;
  right: 8px;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  background: rgba(255, 255, 255, 0.1);
  border: none;
  border-radius: 4px;
  font-size: 12px;
  color: #cdd6f4;
  cursor: pointer;
  transition: all 0.15s;
}
.copy-btn:hover {
  background: rgba(255, 255, 255, 0.2);
}
.tools-error {
  text-align: center;
  padding: 24px;
}
.tools-error-title {
  font-size: 14px;
  color: var(--color-error);
  font-weight: 500;
}
.tools-error-msg {
  font-size: 13px;
  color: var(--color-mute);
  margin-top: 8px;
}
.help-icon {
  margin-left: 8px;
  color: var(--color-mute);
  cursor: pointer;
  font-size: 16px;
  vertical-align: middle;
}
.help-icon:hover {
  color: var(--color-link);
}
.guide-section {
  margin-bottom: 20px;
}
.guide-section:last-child {
  margin-bottom: 0;
}
.guide-h3 {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-ink);
  margin-bottom: 8px;
}
.guide p {
  font-size: 13px;
  color: var(--color-body);
  line-height: 1.6;
  margin: 0;
}
.guide-step {
  display: flex;
  gap: 10px;
  margin-bottom: 10px;
}
.guide-num {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--color-primary);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-top: 2px;
}
.guide-step b {
  font-size: 13px;
  color: var(--color-ink);
}
.guide-step p {
  font-size: 12px;
  color: var(--color-mute);
  margin-top: 2px;
}
.guide-code {
  background: var(--color-canvas-soft);
  border: 1px solid var(--color-hairline);
  border-radius: 8px;
  padding: 12px 14px;
  font-size: 13px;
  color: var(--color-text-dark);
  line-height: 1.8;
}
.guide-code b {
  color: var(--color-ink);
}
.guide-code pre {
  background: #1e1e2e;
  color: #cdd6f4;
  padding: 8px 12px;
  border-radius: 6px;
  font-size: 12px;
  margin: 4px 0 0 0;
  overflow-x: auto;
}
.guide-pre {
  background: #1e1e2e;
  color: #cdd6f4;
  padding: 8px 12px;
  border-radius: 6px;
  font-size: 12px;
  margin: 6px 0 0 0;
  overflow-x: auto;
}
.guide code {
  background: var(--color-canvas-soft-3);
  padding: 1px 5px;
  border-radius: 4px;
  font-size: 12px;
  color: var(--color-error-deep);
}
.card-sync-time {
  display: flex;
  align-items: center;
  font-size: 11px;
  color: var(--color-mute);
  margin-top: 6px;
}
.test-result-summary {
  margin: 0 0 12px;
  font-size: 14px;
  color: var(--color-body);
}
.test-result-list {
  margin: 0;
  padding-left: 20px;
}
.test-result-list li {
  margin-bottom: 4px;
  font-size: 13px;
  color: var(--color-ink);
}
.test-result-error {
  padding: 12px 14px;
  background: var(--color-error-bg);
  border: 1px solid var(--color-error-soft);
  border-radius: 8px;
  color: var(--color-error-deep);
  font-size: 13px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>

<style>
/* MCP 测试连接结果弹窗：480px 窄宽度下，全局 max-height 让弹窗又窄又高。
   限定 50vh 上限，工具列表多时也只占半屏，配合 min-height: 80px 视觉更协调。 */
.mcp-test-result-modal .ant-modal-body {
  min-height: 80px;
  max-height: 50vh;
}
</style>
