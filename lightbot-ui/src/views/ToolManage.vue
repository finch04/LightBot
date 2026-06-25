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
        <a-select
          v-model:value="tagFilter"
          placeholder="标签筛选"
          allow-clear
          style="width: 120px"
        >
          <a-select-option v-for="t in allTags" :key="t" :value="t">{{ t }}</a-select-option>
        </a-select>
        <button class="btn-outline" @click="loadData" :disabled="loading">
          <ReloadOutlined :spin="loading" /> 刷新
        </button>
        <button class="btn-primary" @click="openDialog()">
          <PlusOutlined /> 新增工具
        </button>
      </div>
    </div>

    <a-spin :spinning="loading" style="min-height: 400px; display: block;">
    <div class="provider-grid">
      <EntityCard
        v-for="t in list"
        :key="t.id"
        type="tool"
        :name="t.displayName || t.name"
        :class="{ 'knowledge-card': (t.toolType?.code || t.toolType) === 'knowledge' }"
        @click="openDetail(t)"
      >
        <template #icon>
          <span v-if="(t.toolType?.code || t.toolType) === 'builtin'" class="type-badge badge-builtin">内置</span>
          <span v-else-if="(t.toolType?.code || t.toolType) === 'knowledge'" class="type-badge badge-knowledge">知识库</span>
          <span class="status-dot" :class="isDisabled(t) ? 'status-disabled' : 'status-active'"></span>
          {{ (t.displayName || t.name || '?')[0].toUpperCase() }}
        </template>
        <template #info>
          <a-tooltip :title="t.displayName || t.name">
            <h3>{{ t.displayName || t.name }}</h3>
          </a-tooltip>
          <span class="card-type">{{ toolTypeLabels[t.toolType?.code || t.toolType] || t.toolType }}</span>
        </template>
        <template #actions>
          <a-tooltip v-if="(t.toolType?.code || t.toolType) !== 'builtin' && (t.toolType?.code || t.toolType) !== 'knowledge'" title="删除">
            <button class="btn-icon danger" @click="handleDelete(t.id)"><DeleteOutlined /></button>
          </a-tooltip>
          <a-dropdown :trigger="['click']">
            <button class="btn-icon" @click.prevent><MoreOutlined /></button>
            <template #overlay>
              <a-menu>
                <a-menu-item v-if="(t.toolType?.code || t.toolType) !== 'knowledge'" @click="handleToggleEnabled(t)">
                  <CheckCircleOutlined v-if="!isDisabled(t)" style="color: #16a34a; margin-right: 6px" />
                  <CloseCircleOutlined v-else style="color: #a3a3a3; margin-right: 6px" />
                  {{ isDisabled(t) ? '启用' : '禁用' }}
                </a-menu-item>
                <a-menu-item @click="openTestDialog(t)">
                  <PlayCircleOutlined style="margin-right: 6px" /> 测试工具
                </a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
        </template>
        <div class="card-detail">
          <div class="card-tags">
            <span v-if="t.name" class="tag tag-identifier">{{ t.name }}</span>
            <span v-for="tag in parseTags(t.tags)" :key="tag" class="tag tag-label">{{ tag }}</span>
            <span v-if="t.endpointUrl" class="tag tag-endpoint">{{ truncateText(t.endpointUrl, 30) }}</span>
          </div>
          <a-tooltip v-if="t.description" :title="t.description" placement="topLeft" :overlay-style="{ maxWidth: '400px' }">
            <span class="card-desc">{{ truncateText(t.description, 50) }}</span>
          </a-tooltip>
        </div>
      </EntityCard>
      <div v-if="list.length === 0 && !loading" class="empty-tip">
        {{ searchText ? '没有匹配的工具' : '暂无工具，点击右上角新增' }}
      </div>
    </div>
    </a-spin>

    <!-- 新增/编辑弹窗 -->
    <a-modal v-model:open="dialogVisible" :title="form.id ? '编辑工具' : '新增工具'" :width="640" :footer="null" :maskClosable="false">
      <div class="dialog-scroll-body">
      <a-form :model="form" :label-col="{ span: 5 }">
        <a-form-item label="工具标识" required>
          <a-input v-model:value="form.name" placeholder="如：http_request（英文，唯一标识）（不超过30字）" :maxlength="30" show-count :disabled="form.toolType === 'builtin'" />
        </a-form-item>
        <a-form-item label="显示名称">
          <a-input v-model:value="form.displayName" placeholder="如：HTTP 请求（不超过30字）" :maxlength="30" show-count />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="form.description" :rows="2" placeholder="工具用途说明（不超过50字）" :maxlength="50" show-count />
        </a-form-item>
        <a-form-item label="标签">
          <a-select
            v-model:value="form.tags"
            mode="tags"
            :max-tag-count="3"
            placeholder="输入标签后回车（最多3个）"
            :token-separators="[',']"
            :options="tagSuggestions.map(t => ({ value: t }))"
          />
          <div class="form-hint">标签用于分类筛选，最多 3 个</div>
        </a-form-item>
        <a-form-item label="工具类型" required>
          <a-select v-model:value="form.toolType" style="width: 100%" :disabled="form.id && form.toolType === 'builtin'">
            <a-select-option value="api">API调用</a-select-option>
          </a-select>
          <div v-if="form.id && form.toolType === 'builtin'" class="param-hint">内置工具类型不可修改</div>
        </a-form-item>
        <!-- API 类型专属字段 -->
        <template v-if="form.toolType === 'api'">
          <a-form-item required>
            <template #label>
              端点地址
              <a-tooltip placement="topLeft">
                <template #title>工具被调用时请求的目标 URL，如 https://api.example.com/search</template>
                <QuestionCircleOutlined class="field-help-icon" />
              </a-tooltip>
            </template>
            <a-input v-model:value="form.endpointUrl" placeholder="API 端点 URL" />
          </a-form-item>
          <a-form-item>
            <template #label>
              认证类型
              <a-tooltip placement="topLeft">
                <template #title>请求端点时携带的认证方式，选择"无认证"则不附加任何凭证</template>
                <QuestionCircleOutlined class="field-help-icon" />
              </a-tooltip>
            </template>
            <a-select v-model:value="form.authType" style="width: 100%">
              <a-select-option value="none">无认证</a-select-option>
              <a-select-option value="api_key">API Key</a-select-option>
              <a-select-option value="oauth">OAuth</a-select-option>
              <a-select-option value="bearer">Bearer Token</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item v-if="form.authType && form.authType !== 'none'">
            <template #label>
              认证配置
              <a-tooltip placement="topLeft" overlayClassName="field-tooltip-wide">
                <template #title>
                  <div>认证所需的凭证信息，JSON 格式。</div>
                  <div class="tooltip-example-title">API Key 示例：</div>
                  <pre class="tooltip-example">{"apiKey": "sk-xxx"}</pre>
                  <div class="tooltip-example-title">Bearer Token 示例：</div>
                  <pre class="tooltip-example">{"token": "eyJhbGci..."}</pre>
                </template>
                <QuestionCircleOutlined class="field-help-icon" />
              </a-tooltip>
            </template>
            <JsonInput v-model="form.authConfig" :rows="2" placeholder='JSON 格式，如：{"apiKey":"xxx"}' />
          </a-form-item>
        </template>
        <!-- 高级选项折叠区 -->
        <div class="advanced-toggle" @click="showAdvanced = !showAdvanced">
          <span>高级选项</span>
          <RightOutlined :class="['toggle-icon', { expanded: showAdvanced }]" />
        </div>
        <template v-if="showAdvanced">
          <a-form-item>
            <template #label>
              输入Schema
              <a-tooltip placement="topLeft" overlayClassName="field-tooltip-wide">
                <template #title>
                  <div>定义工具的输入参数（JSON Schema 格式），供 Agent 理解参数含义。</div>
                  <div class="tooltip-example-title">示例：</div>
                  <pre class="tooltip-example">{
  "type": "object",
  "properties": {
    "keyword": {"type": "string", "description": "搜索关键词"},
    "limit": {"type": "integer", "description": "返回数量上限"}
  },
  "required": ["keyword"]
}</pre>
                </template>
                <QuestionCircleOutlined class="field-help-icon" />
              </a-tooltip>
            </template>
            <JsonInput v-model="form.inputSchema" :rows="4" placeholder='JSON Schema，如：{"type":"object","properties":{...}}' />
          </a-form-item>
          <a-form-item>
            <template #label>
              输出Schema
              <a-tooltip placement="topLeft" overlayClassName="field-tooltip-wide">
                <template #title>
                  <div>定义工具返回的 JSON 结构（JSON Schema 格式）。填写后详情页会展示字段说明表，帮助用户理解工具输出。</div>
                  <div class="tooltip-example-title">示例：</div>
                  <pre class="tooltip-example">{
  "type": "object",
  "properties": {
    "total": {"type": "integer", "description": "结果总数"},
    "items": {
      "type": "array",
      "description": "结果列表",
      "items": {"type": "object", "properties": {"id": {"type": "integer"}, "name": {"type": "string"}}}
    }
  }
}</pre>
                </template>
                <QuestionCircleOutlined class="field-help-icon" />
              </a-tooltip>
            </template>
            <JsonInput v-model="form.outputSchema" :rows="3" placeholder='JSON Schema，如：{"type":"object","properties":{"total":{"type":"integer","description":"结果总数"}}}' />
          </a-form-item>
          <a-form-item>
            <template #label>
              输出示例
              <a-tooltip placement="topLeft" overlayClassName="field-tooltip-wide">
                <template #title>
                  <div>工具返回的示例 JSON，用于在详情页展示工具的实际输出样例，帮助用户理解返回内容。</div>
                  <div class="tooltip-example-title">示例：</div>
                  <pre class="tooltip-example">{
  "total": 2,
  "items": [
    {"id": 1, "name": "文档A"},
    {"id": 2, "name": "文档B"}
  ]
}</pre>
                </template>
                <QuestionCircleOutlined class="field-help-icon" />
              </a-tooltip>
            </template>
            <JsonInput v-model="form.outputExample" :rows="4" placeholder='示例 JSON，如：{"total":2,"results":[...]}' />
          </a-form-item>
          <a-form-item label="扩展配置">
            <JsonInput v-model="form.config" :rows="2" placeholder="JSON 格式的扩展配置（可选）" />
          </a-form-item>
        </template>
      </a-form>
      </div>
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
    <a-modal v-model:open="testDialogVisible" :title="testToolName || '测试工具'" :width="680" :footer="null" :maskClosable="false">
      <a-spin :spinning="testLoading" tip="执行中...">
      <div class="dialog-scroll-body">
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
      <!-- 执行结果 -->
      <template v-if="testResult !== null">
        <a-divider />
        <div class="test-result">
          <div class="test-result-label">执行结果</div>
          <pre class="test-result-content" :class="{ 'is-json': isJsonResult(testResult) }">{{ formatTestResult(testResult) }}</pre>
        </div>
      </template>
      </div>
      </a-spin>
      <div class="dialog-footer">
        <div></div>
        <div class="dialog-footer-right">
          <button class="btn-cancel" @click="testDialogVisible = false">关闭</button>
          <button class="btn-primary-sm" :disabled="testLoading" @click="handleTest">
            {{ testLoading ? '执行中...' : '执行测试' }}
          </button>
        </div>
      </div>
    </a-modal>

    <!-- 工具详情弹窗 -->
    <a-modal
      v-model:open="detailVisible"
      :title="detailTool?.displayName || detailTool?.name || '工具详情'"
      :width="640"
      :footer="null"
      :maskClosable="false"
    >
      <div class="detail-section-container" v-if="detailTool">
        <div class="raw-toggle-bar">
          <button class="btn-text raw-toggle" @click="rawMode = !rawMode">
            <SwapOutlined /> {{ rawMode ? '格式化' : '原始格式' }}
          </button>
        </div>
        <!-- 格式化视图 -->
        <template v-if="!rawMode">
          <!-- 基本信息 -->
          <div class="detail-section">
            <div class="detail-section-header"><FileTextOutlined /> 基本信息</div>
            <div class="detail-info-grid">
              <div class="detail-info-item">
                <span class="detail-info-label">工具标识</span>
                <code class="detail-info-value detail-info-code">{{ detailTool.name }}</code>
              </div>
              <div class="detail-info-item">
                <span class="detail-info-label">显示名称</span>
                <span class="detail-info-value">{{ detailTool.displayName || '-' }}</span>
              </div>
              <div class="detail-info-item">
                <span class="detail-info-label">工具类型</span>
                <span class="detail-info-value">
                  <span class="tag" :style="{ background: typeColors[detailTool.toolType?.code || detailTool.toolType] + '15', color: typeColors[detailTool.toolType?.code || detailTool.toolType] }">
                    {{ toolTypeLabels[detailTool.toolType?.code || detailTool.toolType] || detailTool.toolType }}
                  </span>
                </span>
              </div>
              <div class="detail-info-item" v-if="detailTool.endpointUrl">
                <span class="detail-info-label">端点地址</span>
                <span class="detail-info-value">{{ detailTool.endpointUrl }}</span>
              </div>
            </div>
          </div>

          <!-- 描述 -->
          <div class="detail-section" v-if="detailTool.description">
            <div class="detail-section-header"><FileTextOutlined /> 描述</div>
            <div class="detail-desc">{{ detailTool.description }}</div>
          </div>

          <!-- 标签 -->
          <div class="detail-section">
            <div class="detail-section-header"><TagsOutlined /> 标签</div>
            <div class="detail-tags">
              <a-tag v-for="tag in parseTags(detailTool.tags)" :key="tag" color="blue">{{ tag }}</a-tag>
              <span v-if="parseTags(detailTool.tags).length === 0" class="detail-empty">暂无标签</span>
            </div>
          </div>

          <!-- 参数说明 -->
          <div class="detail-section" v-if="detailTool.inputSchema && detailTool.inputSchema !== '{}'">
            <div class="detail-section-header"><UnorderedListOutlined /> 参数说明</div>
            <a-table
              v-if="parseToolParams(detailTool.inputSchema).length > 0"
              :columns="paramColumns"
              :data-source="parseToolParams(detailTool.inputSchema)"
              :pagination="false"
              size="small"
              :scroll="{ y: 360 }"
            />
            <div v-else class="detail-empty">无可解析的参数</div>
          </div>

          <!-- 返回示例 -->
          <div class="detail-section" v-if="hasOutputExample(detailTool)">
            <div class="detail-section-header"><FileTextOutlined /> 返回示例</div>
            <!-- 字段说明表 -->
            <a-table
              v-if="parseOutputSchema(detailTool).length > 0"
              :columns="outputColumns"
              :data-source="parseOutputSchema(detailTool)"
              :pagination="false"
              size="small"
              :scroll="{ y: 360 }"
            />
            <!-- JSON 示例 -->
            <div v-if="formatOutputExample(detailTool)" class="detail-output-example">
              <div class="detail-output-example-title">返回示例 JSON</div>
              <pre class="detail-output-json">{{ formatOutputExample(detailTool) }}</pre>
            </div>
          </div>
        </template>

        <!-- 原始格式视图 -->
        <template v-else>
          <div class="detail-section">
            <div class="detail-section-header"><CodeOutlined /> inputSchema</div>
            <pre class="detail-raw-json">{{ formatJsonRaw(detailTool.inputSchema) }}</pre>
          </div>
          <div class="detail-section">
            <div class="detail-section-header"><CodeOutlined /> outputSchema</div>
            <pre class="detail-raw-json">{{ formatJsonRaw(detailTool.outputSchema) }}</pre>
          </div>
          <div class="detail-section">
            <div class="detail-section-header"><CodeOutlined /> outputExample</div>
            <pre class="detail-raw-json">{{ formatJsonRaw(detailTool.outputExample) }}</pre>
          </div>
          <div class="detail-section">
            <div class="detail-section-header"><CodeOutlined /> config</div>
            <pre class="detail-raw-json">{{ formatJsonRaw(detailTool.config) }}</pre>
          </div>
        </template>
      </div>

      <div class="dialog-footer">
        <div class="dialog-footer-left">
          <button v-if="(detailTool?.toolType?.code || detailTool?.toolType) !== 'builtin' && (detailTool?.toolType?.code || detailTool?.toolType) !== 'knowledge'" class="btn-cancel" @click="detailVisible = false; openDialog(detailTool)">
            <EditOutlined /> 编辑
          </button>
        </div>
        <div class="dialog-footer-right">
          <button class="btn-cancel" @click="detailVisible = false">关闭</button>
        </div>
      </div>
    </a-modal>

  </div>
</template>

<script setup>
defineProps({ hideHeader: Boolean })
import { ref, reactive, watch, onMounted, h } from 'vue'
import { PlusOutlined, EditOutlined, DeleteOutlined, SearchOutlined, ReloadOutlined, PlayCircleOutlined, TagsOutlined, FileTextOutlined, UnorderedListOutlined, MoreOutlined, CheckCircleOutlined, CloseCircleOutlined, QuestionCircleOutlined, SwapOutlined, CodeOutlined } from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import { getTools, createTool, updateTool, deleteTool, testTool, setToolEnabled } from '../api/tool'
import { getToolTypes } from '../api/enum'
import JsonInput from '../components/JsonInput.vue'
import EntityCard from '../components/EntityCard.vue'
import { truncateText } from '../utils/format'

function getPopupContainer() {
  return document.body
}

const toolTypeLabels = { builtin: '内置', knowledge: '知识库', api: 'API调用' }
const typeColors = { builtin: 'var(--color-primary)', knowledge: '#7c3aed', api: '#10b981' }

const list = ref([])
const loading = ref(false)
const searchText = ref('')
const toolTypeFilter = ref(undefined)
const toolTypeList = ref([])
const tagFilter = ref(undefined)
const allTags = ref([])
const tagSuggestions = ref([])
const dialogVisible = ref(false)
const submitting = ref(false)
const form = reactive({
  id: null, name: '', displayName: '', description: '',
  toolType: 'api', endpointUrl: '', authType: 'none',
  inputSchema: '{}', outputSchema: '{}', outputExample: '{}', authConfig: '{}', config: '{}',
  tags: [],
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
const detailVisible = ref(false)
const detailTool = ref(null)
const rawMode = ref(false)

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

function parseToolConfig(tool) {
  if (!tool?.config) return {}
  try {
    return typeof tool.config === 'string' ? JSON.parse(tool.config) : tool.config
  } catch {
    return {}
  }
}

function hasOutputExample(tool) {
  return (tool?.outputExample && tool.outputExample !== '{}') || (tool?.outputSchema && tool.outputSchema !== '{}')
}

function parseOutputSchema(tool) {
  if (!tool?.outputSchema || tool.outputSchema === '{}') return []
  try {
    const schema = typeof tool.outputSchema === 'string' ? JSON.parse(tool.outputSchema) : tool.outputSchema
    const properties = schema.properties || {}
    return Object.entries(properties).map(([name, prop]) => ({
      name,
      type: prop.type || 'string',
      desc: prop.description || '',
    }))
  } catch {
    return []
  }
}

function formatOutputExample(tool) {
  const raw = tool?.outputExample
  if (!raw || raw === '{}') return ''
  try {
    const parsed = typeof raw === 'string' ? JSON.parse(raw) : raw
    return JSON.stringify(parsed, null, 2)
  } catch {
    return raw
  }
}

function isJsonResult(result) {
  if (!result || typeof result !== 'string') return false
  try {
    const parsed = JSON.parse(result)
    return typeof parsed === 'object' && parsed !== null
  } catch {
    return false
  }
}

function formatTestResult(result) {
  if (!result || typeof result !== 'string') return result
  try {
    const parsed = JSON.parse(result)
    if (typeof parsed === 'object' && parsed !== null) {
      return JSON.stringify(parsed, null, 2)
    }
  } catch {}
  return result
}

function formatJsonRaw(val) {
  if (!val || val === '{}') return '{}'
  try {
    const parsed = typeof val === 'string' ? JSON.parse(val) : val
    return JSON.stringify(parsed, null, 2)
  } catch {
    return val
  }
}

async function loadData() {
  loading.value = true
  try {
    const params = { pageNum: 1, pageSize: 50 }
    if (searchText.value) params.keyword = searchText.value
    if (toolTypeFilter.value) params.toolType = toolTypeFilter.value
    if (tagFilter.value) params.tag = tagFilter.value
    const res = await getTools(params)
    list.value = res.data.records || []
    // 收集所有标签（用于筛选下拉）
    const tagSet = new Set()
    list.value.forEach(t => {
      const tags = parseTags(t.tags)
      tags.forEach(tag => tagSet.add(tag))
    })
    allTags.value = Array.from(tagSet).sort()
  } catch (e) {
    // interceptor handles error
  } finally {
    loading.value = false
  }
}

function parseTags(tags) {
  if (!tags) return []
  if (Array.isArray(tags)) return tags
  try {
    return JSON.parse(tags)
  } catch {
    return []
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

let searchDebounceTimer = null
watch(searchText, () => {
  clearTimeout(searchDebounceTimer)
  searchDebounceTimer = setTimeout(() => loadData(), 300)
})
watch(toolTypeFilter, () => loadData())
watch(tagFilter, () => loadData())
watch(allTags, (v) => { tagSuggestions.value = v })

function openDialog(row) {
  if (row) {
    const config = parseToolConfig(row)
    let outputExampleStr = '{}'
    if (row.outputExample && row.outputExample !== '{}') {
      try {
        const parsed = typeof row.outputExample === 'string' ? JSON.parse(row.outputExample) : row.outputExample
        outputExampleStr = JSON.stringify(parsed, null, 2)
      } catch {
        outputExampleStr = row.outputExample
      }
    }
    Object.assign(form, {
      ...row,
      toolType: row.toolType?.code || row.toolType || 'api',
      authType: row.authType?.code || row.authType || 'none',
      tags: parseTags(row.tags),
      outputExample: outputExampleStr,
      config: JSON.stringify(config),
    })
  } else {
    Object.assign(form, {
      id: null, name: '', displayName: '', description: '',
      toolType: 'api', endpointUrl: '', authType: 'none',
      inputSchema: '{}', outputSchema: '{}', outputExample: '{}', authConfig: '{}', config: '{}',
      tags: [],
    })
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!form.name.trim()) return message.warning('请输入工具标识')
  if (form.toolType === 'api' && !form.endpointUrl?.trim()) return message.warning('API 类型必须填写端点地址')
  submitting.value = true
  try {
    const data = {
      id: form.id,
      name: form.name,
      displayName: form.displayName,
      description: form.description,
      toolType: form.toolType,
      inputSchema: form.inputSchema,
      outputSchema: form.outputSchema,
      outputExample: form.outputExample,
      config: form.config,
      endpointUrl: form.endpointUrl,
      authType: form.authType,
      authConfig: form.authConfig,
      tags: JSON.stringify(form.tags || []),
      status: form.status,
    }
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

function openDetail(tool) {
  detailTool.value = tool
  detailVisible.value = true
}

function isDisabled(t) {
  const status = t.status?.code || t.status
  return status === 'disabled' || status === 'DISABLED'
}

async function handleToggleEnabled(tool) {
  const next = isDisabled(tool)
  await setToolEnabled(tool.id, next)
  message.success(next ? '已启用' : '已禁用')
  loadData()
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
  tagFilter.value = undefined
  loadData()
}

const paramColumns = [
  { title: '参数名', dataIndex: 'name', width: 140, customRender: ({ text }) => h('code', text) },
  { title: '类型', dataIndex: 'type', width: 80 },
  { title: '必填', dataIndex: 'required', width: 60, customRender: ({ text }) => text ? '是' : '否' },
  { title: '说明', dataIndex: 'desc' },
]

const outputColumns = [
  { title: '字段名', dataIndex: 'name', width: 140, customRender: ({ text }) => h('code', text) },
  { title: '类型', dataIndex: 'type', width: 80 },
  { title: '说明', dataIndex: 'desc' },
]

defineExpose({ openDialog, search, refresh })
</script>

<style scoped>

.provider-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}
.status-dot {
  position: absolute;
  bottom: -2px;
  right: -2px;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  border: 2px solid #fff;
  z-index: 1;
}
.status-active {
  background: #16a34a;
}
.status-disabled {
  background: #a3a3a3;
}
.card-type {
  font-size: 12px;
  color: var(--color-mute);
  background: var(--color-canvas-soft-2);
  padding: 2px 8px;
  border-radius: 100px;
}
.card-detail {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.card-desc {
  font-size: 13px;
  color: #a1a1aa;
  line-height: 1.5;
}
.card-tags {
  display: flex;
  gap: 6px;
  margin-top: 4px;
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
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tag-identifier {
  background: #ecfdf5;
  color: #059669;
  border: 1px solid #d1fae5;
  font-family: 'SF Mono', Monaco, Consolas, monospace;
}
.tag-label {
  background: #eff6ff;
  color: #2563eb;
  border: 1px solid #dbeafe;
}
.tag-endpoint {
  background: #f5f3ff;
  color: #7c3aed;
  border: 1px solid #ede9fe;
  max-width: 200px;
}
.empty-tip {
  grid-column: 1 / -1;
  text-align: center;
  padding: 48px 24px;
  color: #a1a1aa;
  font-size: 14px;
}
.param-hint {
  font-size: 12px;
  color: #a1a1aa;
  margin-top: 4px;
}

/* 高级选项折叠区 */
.advanced-toggle {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 0;
  cursor: pointer;
  font-size: 13px;
  color: var(--color-mute);
  border-top: 1px dashed #ebebeb;
  user-select: none;
  margin-bottom: 8px;
}
.advanced-toggle:hover {
  color: var(--color-primary);
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
.field-help-icon {
  margin-left: 4px;
  color: #a1a1aa;
  font-size: 14px;
  cursor: help;
  &:hover { color: #666; }
}
.dialog-scroll-body {
  max-height: 60vh;
  overflow-y: auto;
  padding-right: 8px;
}
.tooltip-example-title {
  margin-top: 8px;
  font-weight: 600;
  font-size: 12px;
}
.tooltip-example {
  margin: 4px 0 0;
  padding: 6px 8px;
  background: rgba(255,255,255,0.15);
  border-radius: 4px;
  font-size: 11px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
  font-family: 'SF Mono', Monaco, Consolas, monospace;
}
:deep(.field-tooltip-wide) {
  max-width: 420px;
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
  color: var(--color-mute);
  border: 1px solid #d4d4d8;
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
}
.btn-cancel:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}
.btn-primary-sm {
  padding: 6px 14px;
  background: var(--color-primary);
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

.test-params-section {
  margin-top: 16px;
  margin-bottom: 16px;
}
.test-params-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-primary);
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
  background: var(--color-canvas-soft-2);
  color: #52525b;
  font-weight: 600;
  border-bottom: 1px solid #e5e5e5;
}
.test-params-table td {
  padding: 6px 12px;
  border-bottom: 1px solid #f0f0f0;
  color: var(--color-primary);
}
.test-params-table code {
  background: var(--color-canvas-soft-2);
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 12px;
  color: var(--color-link);
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
  color: var(--color-primary);
}
.btn-text {
  background: none;
  border: none;
  color: var(--color-link);
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
  background: var(--color-canvas-soft);
  color: var(--color-primary);
  box-sizing: border-box;
}
.test-json-input:focus {
  border-color: var(--color-link);
  background: #fff;
}
.test-result-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-primary);
  margin-bottom: 8px;
}
.test-result-content {
  background: var(--color-canvas-soft-2);
  border: 1px solid #e5e5e5;
  border-radius: 8px;
  padding: 12px 16px;
  font-size: 13px;
  color: var(--color-primary);
  max-height: 400px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
  font-family: 'SF Mono', Monaco, Consolas, monospace;
}
.test-result-content.is-json {
  background: #1e1e1e;
  color: #d4d4d4;
  border-color: #333;
}

/* 工具类型徽章 */
.type-badge {
  position: absolute;
  top: -4px;
  right: -4px;
  font-size: 10px;
  padding: 1px 4px;
  border-radius: 4px;
  z-index: 1;
  color: #fff;
}
.badge-builtin {
  background: var(--color-primary);
}
.badge-knowledge {
  background: #7c3aed;
}
.knowledge-card {
  border-color: #7c3aed;
  background: #f5f3ff;
}

/* 工具详情弹窗样式 */
.detail-section-container {
  max-height: 60vh;
  overflow-y: auto;
  padding-right: 8px;
}
.raw-toggle-bar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 8px;
}
.raw-toggle {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--color-link);
  cursor: pointer;
  border: none;
  background: none;
  padding: 2px 6px;
  border-radius: 4px;
}
.raw-toggle:hover {
  background: #f0f5ff;
}
.detail-raw-json {
  background: #1e1e1e;
  color: #d4d4d4;
  border-radius: 6px;
  padding: 12px 16px;
  font-size: 13px;
  line-height: 1.6;
  overflow-x: auto;
  white-space: pre;
  margin: 0;
}
.detail-section {
  margin-bottom: 20px;

  :deep(.ant-table) {
    table-layout: fixed;
  }
  :deep(.ant-table-cell) {
    white-space: normal;
    word-break: break-word;
  }
  :deep(.ant-table-thead .ant-table-cell) {
    white-space: nowrap;
  }
}
.detail-section:last-child {
  margin-bottom: 0;
}
.detail-section-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-primary);
  margin-bottom: 10px;
  padding-bottom: 6px;
  border-bottom: 1px solid #f0f0f0;
}
.detail-info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px 24px;
}
.detail-info-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.detail-info-label {
  font-size: 12px;
  color: #a1a1aa;
  white-space: nowrap;
}
.detail-info-value {
  font-size: 13px;
  color: var(--color-primary);
}
.detail-info-code {
  font-family: 'SF Mono', Monaco, Consolas, monospace;
  background: var(--color-canvas-soft-2);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 12px;
  color: #059669;
}
.detail-desc {
  font-size: 13px;
  color: #52525b;
  line-height: 1.6;
  white-space: pre-wrap;
}
.detail-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.detail-empty {
  font-size: 13px;
  color: #a1a1aa;
  font-style: italic;
}
.detail-output-example {
  margin-top: 12px;
}
.detail-output-example-title {
  font-size: 12px;
  font-weight: 600;
  color: #52525b;
  margin-bottom: 6px;
}
.detail-output-json {
  background: #1e1e1e;
  color: #d4d4d4;
  padding: 12px 16px;
  border-radius: 8px;
  font-size: 12px;
  line-height: 1.6;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  overflow-x: auto;
  margin: 0;
  max-height: 360px;
  overflow-y: auto;
}
.dialog-footer-left {
  display: flex;
  gap: 8px;
}
</style>
