<template>
  <div class="page">
    <div v-if="!hideHeader" class="page-header">
      <div>
        <h1 class="page-title">工具管理</h1>
        <p class="page-desc">管理 Agent 可使用的工具（Tool）</p>
      </div>
      <div class="page-header-actions">
        <a-input
          v-model:value="searchText"
          placeholder="搜索工具名称..."
          allow-clear
          style="width: 180px"
        >
          <template #prefix><SearchOutlined /></template>
        </a-input>
        <a-select
          v-model:value="toolTypeFilter"
          placeholder="工具类型"
          allow-clear
          style="width: 120px"
        >
          <a-select-option v-for="t in toolTypeList" :key="t.value" :value="t.value">{{ t.label }}</a-select-option>
        </a-select>
        <a-switch v-model:checked="showSystemTools" size="small">
          <template #checkedChildren>系统</template>
          <template #unCheckedChildren>普通</template>
        </a-switch>
        <a-tooltip v-if="showSystemTools" title="如何新增系统工具？" :getPopupContainer="getPopupContainer" placement="bottomRight">
          <button class="btn-icon-help" @click="systemToolHelpVisible = true"><QuestionCircleOutlined /></button>
        </a-tooltip>
        <button class="btn-outline" @click="loadData">
          <ReloadOutlined /> 刷新
        </button>
        <button class="btn-primary" @click="openDialog()">
          <PlusOutlined /> 新增工具
        </button>
      </div>
    </div>

    <div class="provider-grid">
      <div v-for="t in list" :key="t.id" class="provider-card" :class="{ 'system-card': t.isSystem }">
        <div class="card-top">
          <div class="card-icon" :style="{ background: typeColors[t.toolType?.code || t.toolType] || '#171717' }">
            {{ (t.displayName || t.name || '?')[0].toUpperCase() }}
          </div>
          <div class="card-info">
            <h3>
              {{ t.displayName || t.name }}
              <span v-if="t.isSystem" class="system-badge">系统</span>
            </h3>
            <span class="card-type">{{ toolTypeLabels[t.toolType?.code || t.toolType] || t.toolType }}</span>
          </div>
          <div class="card-actions">
            <a-tooltip title="测试工具">
              <button class="btn-icon" @click="openTestDialog(t)"><PlayCircleOutlined /></button>
            </a-tooltip>
            <button v-if="!t.isSystem" class="btn-icon" @click="openDialog(t)"><EditOutlined /></button>
            <button v-if="!t.isSystem" class="btn-icon danger" @click="handleDelete(t.id)"><DeleteOutlined /></button>
          </div>
        </div>
        <div class="card-detail">
          <span v-if="t.name" class="detail-tag">标识: {{ t.name }}</span>
          <span v-if="t.description">{{ t.description }}</span>
          <span v-if="t.endpointUrl">端点: {{ t.endpointUrl }}</span>
        </div>
      </div>
      <div v-if="list.length === 0" class="empty-tip">
        {{ searchText ? '没有匹配的工具' : '暂无工具，点击右上角新增' }}
      </div>
    </div>

    <!-- 新增/编辑弹窗 -->
    <a-modal v-model:open="dialogVisible" :title="form.id ? '编辑工具' : '新增工具'" :width="640" :footer="null" :maskClosable="false">
      <a-form :model="form" :label-col="{ span: 5 }">
        <a-form-item label="工具标识" required>
          <a-input v-model:value="form.name" placeholder="如：http_request（英文，唯一标识）" :disabled="form.toolType === 'builtin'" />
        </a-form-item>
        <a-form-item label="显示名称">
          <a-input v-model:value="form.displayName" placeholder="如：HTTP 请求" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="form.description" :rows="2" placeholder="工具用途说明，供 Agent 理解" />
        </a-form-item>
        <a-form-item label="工具类型" required>
          <a-select v-model:value="form.toolType" style="width: 100%">
            <a-select-option value="builtin">内置</a-select-option>
            <a-select-option value="custom">自定义</a-select-option>
            <a-select-option value="api">API调用</a-select-option>
            <a-select-option value="mcp">MCP协议</a-select-option>
          </a-select>
        </a-form-item>
        <!-- 高级选项折叠区 -->
        <div class="advanced-toggle" @click="showAdvanced = !showAdvanced">
          <span>高级选项</span>
          <RightOutlined :class="['toggle-icon', { expanded: showAdvanced }]" />
        </div>
        <template v-if="showAdvanced">
          <a-form-item label="端点地址">
            <a-input v-model:value="form.endpointUrl" placeholder="API 端点 URL（API 类型必填）" />
          </a-form-item>
          <a-form-item label="认证类型">
            <a-select v-model:value="form.authType" style="width: 100%">
              <a-select-option value="none">无认证</a-select-option>
              <a-select-option value="api_key">API Key</a-select-option>
              <a-select-option value="oauth">OAuth</a-select-option>
              <a-select-option value="bearer">Bearer Token</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="输入Schema">
            <JsonInput v-model="form.inputSchema" :rows="4" placeholder='JSON Schema，如：{"type":"object","properties":{...}}' />
            <div class="form-hint">定义工具的输入参数（JSON Schema 格式），供 Agent 理解参数含义</div>
          </a-form-item>
          <a-form-item label="输出Schema">
            <JsonInput v-model="form.outputSchema" :rows="3" placeholder="输出参数 JSON Schema（可选）" />
          </a-form-item>
          <a-form-item label="认证配置">
            <JsonInput v-model="form.authConfig" :rows="2" placeholder='JSON 格式，如：{"apiKey":"xxx"}' />
          </a-form-item>
          <a-form-item label="扩展配置">
            <JsonInput v-model="form.config" :rows="2" placeholder="JSON 格式的扩展配置（可选）" />
          </a-form-item>
        </template>
      </a-form>
      <div class="dialog-footer">
        <div></div>
        <div class="dialog-footer-right">
          <button class="btn-cancel" @click="dialogVisible = false">取消</button>
          <button class="btn-primary-sm" :disabled="submitting" @click="handleSubmit">
            {{ submitting ? '提交中...' : '确定' }}
          </button>
        </div>
      </div>
    </a-modal>

    <!-- 测试工具弹窗 -->
    <a-modal v-model:open="testDialogVisible" title="测试工具" :width="680" :footer="null" :maskClosable="false">
      <div class="test-tool-info">
        <span class="test-tool-name">{{ testToolName }}</span>
        <span class="test-tool-desc">{{ testToolDesc }}</span>
      </div>
      <!-- 参数说明 -->
      <div v-if="testToolParams.length > 0" class="test-params-section">
        <div class="test-params-title">参数说明</div>
        <table class="test-params-table">
          <thead>
            <tr><th>参数名</th><th>类型</th><th>必填</th><th>说明</th></tr>
          </thead>
          <tbody>
            <tr v-for="p in testToolParams" :key="p.name">
              <td><code>{{ p.name }}</code></td>
              <td>{{ p.type }}</td>
              <td><span v-if="p.required" class="param-required">是</span><span v-else class="param-optional">否</span></td>
              <td>{{ p.desc }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="test-params-section">
        <div class="test-params-hint">该工具无需输入参数，直接点击执行即可</div>
      </div>
      <!-- JSON 输入 -->
      <div class="test-input-header">
        <span class="test-input-label">输入参数（JSON）</span>
        <button class="btn-text" @click="formatTestArgs">格式化</button>
      </div>
      <textarea
        ref="testArgsRef"
        v-model="testArgs"
        class="test-json-input"
        rows="6"
        spellcheck="false"
        placeholder='{"key": "value"}'
      />
      <div class="dialog-footer">
        <div></div>
        <div class="dialog-footer-right">
          <button class="btn-cancel" @click="testDialogVisible = false">关闭</button>
          <button class="btn-primary-sm" :disabled="testLoading" @click="handleTest">
            {{ testLoading ? '执行中...' : '执行测试' }}
          </button>
        </div>
      </div>
      <a-divider v-if="testResult !== null" />
      <div v-if="testResult !== null" class="test-result">
        <div class="test-result-label">执行结果</div>
        <pre class="test-result-content">{{ testResult }}</pre>
      </div>
    </a-modal>

    <!-- 系统工具帮助弹窗 -->
    <a-modal
      v-model:open="systemToolHelpVisible"
      title="如何新增系统工具"
      :width="640"
      :footer="null"
    >
      <div class="help-content">
        <div class="help-section">
          <h4>什么是系统工具？</h4>
          <p>系统工具是核心内置工具，自动注入到所有 Agent，用户无法在前端编辑或删除。例如：<code>query_knowledge</code>（知识库检索工具）。</p>
        </div>
        <div class="help-section">
          <h4>如何新增系统工具？</h4>
          <p>系统工具必须通过代码新增，步骤如下：</p>
          <div class="help-steps">
            <div class="help-step">
              <span class="step-num">1</span>
              <div>
                <b>编写工具方法</b>
                <p>在 Java 类中编写带有 <code>@Tool</code> 注解的方法，定义工具的输入参数和返回值。</p>
                <pre class="code-block">@Tool(description = "在知识库中检索相关内容")
public String queryKnowledge(
    @ToolParam(description = "检索关键词") String query,
    @ToolParam(description = "Agent ID") long agentId) {
    // 工具实现逻辑
}</pre>
              </div>
            </div>
            <div class="help-step">
              <span class="step-num">2</span>
              <div>
                <b>标记为系统工具</b>
                <p>工具类需要添加 <code>@Component</code> 注解，方法会被自动发现并注册为 ToolCallback。</p>
              </div>
            </div>
            <div class="help-step">
              <span class="step-num">3</span>
              <div>
                <b>数据库标记</b>
                <p>在 <code>tool</code> 表中将对应工具记录的 <code>is_system</code> 字段设为 <code>true</code>。</p>
                <pre class="code-block">UPDATE tool SET is_system = TRUE WHERE name = 'your_tool_name';</pre>
              </div>
            </div>
          </div>
        </div>
        <div class="help-section">
          <h4>系统工具特性</h4>
          <ul class="help-list">
            <li>自动注入：运行时自动添加到所有 Agent 的工具列表</li>
            <li>不可编辑：前端无法修改系统工具的配置</li>
            <li>不可删除：前端无法删除系统工具</li>
            <li>知识库关联：<code>query_knowledge</code> 工具会在 Agent 绑定知识库后自动启用检索能力</li>
          </ul>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup>
defineProps({ hideHeader: Boolean })
import { ref, reactive, watch, onMounted } from 'vue'
import { PlusOutlined, EditOutlined, DeleteOutlined, SearchOutlined, ReloadOutlined, PlayCircleOutlined, QuestionCircleOutlined } from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import { getTools, createTool, updateTool, deleteTool, testTool } from '../api/tool'
import { getToolTypes } from '../api/enum'
import JsonInput from '../components/JsonInput.vue'

function getPopupContainer() {
  return document.body
}

const toolTypeLabels = { builtin: '内置', custom: '自定义', api: 'API调用', mcp: 'MCP协议' }
const typeColors = { builtin: '#171717', custom: '#0070f3', api: '#10b981', mcp: '#8b5cf6' }

const list = ref([])
const searchText = ref('')
const toolTypeFilter = ref(undefined)
const toolTypeList = ref([])
const showSystemTools = ref(false)
const systemToolHelpVisible = ref(false)
const dialogVisible = ref(false)
const submitting = ref(false)
const form = reactive({
  id: null, name: '', displayName: '', description: '',
  toolType: 'custom', endpointUrl: '', authType: 'none',
  inputSchema: '{}', outputSchema: '{}', authConfig: '{}', config: '{}',
})

const testDialogVisible = ref(false)
const testToolName = ref('')
const testToolDesc = ref('')
const testToolId = ref(null)
const testToolParams = ref([])
const testArgs = ref('{}')
const testResult = ref(null)
const testLoading = ref(false)
const testArgsRef = ref(null)
const showAdvanced = ref(false)

/**
 * 从 JSON Schema 中解析工具参数列表
 */
function parseToolParams(inputSchema) {
  if (!inputSchema || inputSchema === '{}') return []
  try {
    const schema = typeof inputSchema === 'string' ? JSON.parse(inputSchema) : inputSchema
    const properties = schema.properties || {}
    const required = schema.required || []
    return Object.entries(properties).map(([name, prop]) => ({
      name,
      type: prop.type || 'string',
      desc: prop.description || '',
      required: required.includes(name),
    }))
  } catch {
    return []
  }
}

/**
 * 从 JSON Schema 生成示例参数
 */
function generateToolExample(inputSchema) {
  if (!inputSchema || inputSchema === '{}') return {}
  try {
    const schema = typeof inputSchema === 'string' ? JSON.parse(inputSchema) : inputSchema
    const properties = schema.properties || {}
    const example = {}
    for (const [name, prop] of Object.entries(properties)) {
      if (prop.type === 'string') example[name] = prop.description || '示例值'
      else if (prop.type === 'number' || prop.type === 'integer') example[name] = 0
      else if (prop.type === 'boolean') example[name] = true
      else example[name] = null
    }
    return example
  } catch {
    return {}
  }
}

async function loadData() {
  try {
    const params = { pageNum: 1, pageSize: 50 }
    if (searchText.value) params.keyword = searchText.value
    if (toolTypeFilter.value) params.toolType = toolTypeFilter.value
    params.isSystem = showSystemTools.value
    const res = await getTools(params)
    list.value = res.data.records || []
  } catch (e) {
    // interceptor handles error
  }
}

async function loadToolTypes() {
  try {
    const res = await getToolTypes()
    toolTypeList.value = res.data || []
  } catch (e) {
    // ignore
  }
}

watch(searchText, () => loadData())
watch(toolTypeFilter, () => loadData())
watch(showSystemTools, () => loadData())

function openDialog(row) {
  if (row) {
    Object.assign(form, {
      ...row,
      toolType: row.toolType?.code || row.toolType || 'custom',
      authType: row.authType?.code || row.authType || 'none',
    })
  } else {
    Object.assign(form, {
      id: null, name: '', displayName: '', description: '',
      toolType: 'custom', endpointUrl: '', authType: 'none',
      inputSchema: '{}', outputSchema: '{}', authConfig: '{}', config: '{}',
    })
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!form.name.trim()) return message.warning('请输入工具标识')
  submitting.value = true
  try {
    const data = { ...form }
    if (form.id) {
      await updateTool(data)
      message.success('更新成功')
    } else {
      await createTool(data)
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
    content: '删除后该工具将无法恢复，是否继续？',
    okText: '确认删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      await deleteTool(id)
      message.success('删除成功')
      loadData()
    },
  })
}

function openTestDialog(tool) {
  testToolId.value = tool.id
  testToolName.value = tool.displayName || tool.name
  testToolDesc.value = tool.description || ''
  testResult.value = null

  // 从 inputSchema 动态解析参数说明和生成示例
  testToolParams.value = parseToolParams(tool.inputSchema)
  testArgs.value = JSON.stringify(generateToolExample(tool.inputSchema), null, 2)

  testDialogVisible.value = true
}

function formatTestArgs() {
  try {
    const obj = JSON.parse(testArgs.value)
    testArgs.value = JSON.stringify(obj, null, 2)
  } catch {
    message.warning('JSON 格式错误，无法格式化')
  }
}

async function handleTest() {
  if (!testArgs.value.trim()) return message.warning('请输入参数')
  // 验证 JSON 格式
  try {
    JSON.parse(testArgs.value)
  } catch {
    return message.warning('参数必须是合法的 JSON 格式')
  }
  testLoading.value = true
  testResult.value = null
  try {
    const res = await testTool(testToolId.value, testArgs.value)
    testResult.value = res.data
  } catch (e) {
    testResult.value = '请求失败: ' + (e.response?.data?.message || e.message || '未知错误')
  } finally {
    testLoading.value = false
  }
}

onMounted(() => {
  loadToolTypes()
  loadData()
})

function search(text, toolType) {
  const next = text || ''
  if (searchText.value === next && toolTypeFilter.value === (toolType || '')) return
  searchText.value = next
  toolTypeFilter.value = toolType || undefined
  loadData()
}

function refresh() {
  searchText.value = ''
  toolTypeFilter.value = undefined
  loadData()
}

defineExpose({ openDialog, search, refresh })
</script>

<style scoped>
.page {
  padding: 32px;
  height: 100vh;
  overflow-y: auto;
  background: #fafafa;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 32px;
}
.page-title {
  font-size: 24px;
  font-weight: 600;
  color: #171717;
  margin-bottom: 4px;
}
.page-desc {
  font-size: 14px;
  color: #71717a;
}
.btn-primary {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 20px;
  background: #171717;
  color: #fff;
  border: none;
  border-radius: 100px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
}
.btn-primary:hover {
  background: #27272a;
}
.page-header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.btn-outline {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 20px;
  background: transparent;
  border: 1px solid #d9d9d9;
  border-radius: 100px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.15s;
}
.btn-outline:hover {
  border-color: #0070f3;
  color: #0070f3;
}

.provider-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}
.provider-card {
  background: #fff;
  border: 1px solid #ebebeb;
  border-radius: 12px;
  padding: 20px;
}
.card-top {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.card-icon {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  background: #171717;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 16px;
  flex-shrink: 0;
}
.card-info {
  flex: 1;
  min-width: 0;
}
.card-info h3 {
  font-size: 16px;
  font-weight: 600;
  color: #171717;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.card-type {
  font-size: 12px;
  color: #71717a;
  background: #f5f5f5;
  padding: 2px 8px;
  border-radius: 100px;
}
.card-actions {
  display: flex;
  gap: 4px;
}
.btn-icon {
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  border-radius: 6px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #71717a;
}
.btn-icon:hover {
  background: #f5f5f5;
}
.btn-icon.danger:hover {
  color: #ee0000;
  background: #f7d4d6;
}
.card-detail {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 13px;
  color: #a1a1aa;
}
.detail-tag {
  display: inline-block;
  background: #f5f5f5;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  color: #71717a;
}
.empty-tip {
  grid-column: 1 / -1;
  text-align: center;
  padding: 48px 24px;
  color: #a1a1aa;
  font-size: 14px;
}

/* 高级选项折叠区 */
.advanced-toggle {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 0;
  cursor: pointer;
  font-size: 13px;
  color: #71717a;
  border-top: 1px dashed #ebebeb;
  user-select: none;
  margin-bottom: 8px;
}
.advanced-toggle:hover {
  color: #171717;
}
.toggle-icon {
  font-size: 10px;
  transition: transform 0.2s;
}
.toggle-icon.expanded {
  transform: rotate(180deg);
}
.form-hint {
  font-size: 12px;
  color: #a1a1aa;
  margin-top: 4px;
  line-height: 1.4;
}
.param-required {
  color: #ef4444;
  font-size: 12px;
  font-weight: 500;
}
.param-optional {
  color: #a1a1aa;
  font-size: 12px;
}

.dialog-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 16px;
  border-top: 1px solid #f0f0f0;
  margin-top: 8px;
}
.dialog-footer-right {
  display: flex;
  gap: 8px;
}
.btn-cancel {
  padding: 6px 14px;
  background: #fff;
  color: #71717a;
  border: 1px solid #d4d4d8;
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
}
.btn-cancel:hover {
  border-color: #171717;
  color: #171717;
}
.btn-primary-sm {
  padding: 6px 14px;
  background: #171717;
  color: #fff;
  border: none;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
}
.btn-primary-sm:hover:not(:disabled) {
  background: #27272a;
}
.btn-primary-sm:disabled {
  background: #d4d4d8;
  cursor: not-allowed;
}

.test-tool-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.test-tool-name {
  font-size: 16px;
  font-weight: 600;
  color: #171717;
}
.test-tool-desc {
  font-size: 13px;
  color: #71717a;
}
.test-params-section {
  margin-top: 16px;
  margin-bottom: 16px;
}
.test-params-title {
  font-size: 13px;
  font-weight: 600;
  color: #171717;
  margin-bottom: 8px;
}
.test-params-hint {
  font-size: 13px;
  color: #a1a1aa;
  font-style: italic;
}
.test-params-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.test-params-table th {
  text-align: left;
  padding: 6px 12px;
  background: #f5f5f5;
  color: #52525b;
  font-weight: 600;
  border-bottom: 1px solid #e5e5e5;
}
.test-params-table td {
  padding: 6px 12px;
  border-bottom: 1px solid #f0f0f0;
  color: #171717;
}
.test-params-table code {
  background: #f5f5f5;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 12px;
  color: #0070f3;
}
.test-input-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}
.test-input-label {
  font-size: 13px;
  font-weight: 600;
  color: #171717;
}
.btn-text {
  background: none;
  border: none;
  color: #0070f3;
  font-size: 12px;
  cursor: pointer;
  padding: 2px 6px;
  border-radius: 4px;
}
.btn-text:hover {
  background: #f0f5ff;
}
.test-json-input {
  width: 100%;
  min-height: 120px;
  padding: 12px 16px;
  border: 1px solid #d4d4d8;
  border-radius: 8px;
  font-size: 13px;
  font-family: 'SF Mono', Monaco, Consolas, monospace;
  line-height: 1.6;
  resize: vertical;
  outline: none;
  transition: border-color 0.2s;
  background: #fafafa;
  color: #171717;
  box-sizing: border-box;
}
.test-json-input:focus {
  border-color: #0070f3;
  background: #fff;
}
.test-result-label {
  font-size: 13px;
  font-weight: 600;
  color: #171717;
  margin-bottom: 8px;
}
.test-result-content {
  background: #f5f5f5;
  border: 1px solid #e5e5e5;
  border-radius: 8px;
  padding: 12px 16px;
  font-size: 13px;
  color: #171717;
  max-height: 400px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
  font-family: 'SF Mono', Monaco, Consolas, monospace;
}

/* 系统工具卡片样式 */
.system-card {
  border-color: #0070f3;
  background: #f0f7ff;
}
.system-badge {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 100px;
  background: #0070f3;
  color: #fff;
  margin-left: 6px;
  vertical-align: middle;
}
.btn-icon-help {
  width: 24px;
  height: 24px;
  border: none;
  background: transparent;
  border-radius: 4px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #71717a;
  font-size: 14px;
}
.btn-icon-help:hover {
  background: #f5f5f5;
  color: #0070f3;
}

/* 帮助弹窗样式 */
.help-content {
  max-height: 60vh;
  overflow-y: auto;
}
.help-section {
  margin-bottom: 20px;
}
.help-section:last-child {
  margin-bottom: 0;
}
.help-section h4 {
  font-size: 15px;
  font-weight: 600;
  color: #171717;
  margin-bottom: 8px;
}
.help-section p {
  font-size: 13px;
  color: #52525b;
  line-height: 1.6;
  margin: 0;
}
.help-steps {
  margin-top: 12px;
}
.help-step {
  display: flex;
  gap: 10px;
  margin-bottom: 12px;
}
.step-num {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #171717;
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-top: 2px;
}
.help-step b {
  font-size: 13px;
  color: #171717;
}
.help-step p {
  font-size: 12px;
  color: #71717a;
  margin-top: 4px;
}
.code-block {
  background: #1e1e2e;
  color: #cdd6f4;
  padding: 8px 12px;
  border-radius: 6px;
  font-size: 12px;
  margin: 6px 0 0 0;
  overflow-x: auto;
}
.help-list {
  font-size: 13px;
  color: #52525b;
  line-height: 1.8;
  padding-left: 20px;
}
</style>
