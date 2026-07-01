<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">模型管理</h1>
        <p class="page-desc">管理 AI 模型提供商的 API 配置</p>
      </div>
      <div class="header-actions">
        <button class="btn-refresh" :disabled="refreshing" @click="handleRefreshCache">
          <SyncOutlined :class="{ spinning: refreshing }" /> 刷新缓存
        </button>
        <button class="btn-primary" @click="openDialog()">
          <PlusOutlined /> 新增提供商
        </button>
      </div>
    </div>

    <div class="provider-grid">
      <div v-for="p in list" :key="p.id" :class="['provider-card', { disabled: p.status?.code === 'disabled' || p.status === 'disabled' }]">
        <div class="card-top">
          <div class="card-icon">{{ p.name[0] }}</div>
          <div class="card-info">
            <h3>{{ p.name }}</h3>
            <span class="card-type">{{ p.type?.code || p.type }}</span>
          </div>
          <div class="card-actions">
            <a-switch
              :checked="p.status?.code === 'active' || p.status === 'active'"
              checked-children="启用"
              un-checked-children="禁用"
              size="small"
              @change="(checked) => handleToggleStatus(p, checked)"
            />
            <a-tooltip title="编辑">
              <button class="btn-icon" @click="openDialog(p)"><EditOutlined /></button>
            </a-tooltip>
            <a-tooltip title="删除">
              <button class="btn-icon danger" @click="handleDelete(p.id)"><DeleteOutlined /></button>
            </a-tooltip>
          </div>
        </div>
        <div class="card-detail">
          <span>API Key: {{ maskKey(p.apiKey) }}</span>
          <span v-if="p.baseUrl">URL: {{ p.baseUrl }}</span>
        </div>
        <div class="card-footer">
          <button class="btn-link" :disabled="p.status?.code === 'disabled' || p.status === 'disabled'" @click="openModelModal(p)">管理模型</button>
        </div>
      </div>
    </div>

    <!-- 新增/编辑弹窗 -->
    <a-modal v-model:open="dialogVisible" :title="form.id ? '编辑提供商' : '新增提供商'" :width="480" :footer="null" :maskClosable="false">
      <div class="provider-form-scroll">
        <a-form :model="form" :label-col="{ span: 6 }">
          <a-form-item label="名称" required>
            <a-input v-model:value="form.name" placeholder="如：通义千问" :maxlength="30" show-count />
          </a-form-item>
          <a-form-item label="类型" required>
          <a-select v-model:value="form.type" style="width: 100%" placeholder="选择提供商类型">
            <a-select-option v-for="t in providerTypes" :key="t.value" :value="t.value">
              {{ t.label }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="API Key">
          <a-input-password v-model:value="form.apiKey" placeholder="sk-..." />
        </a-form-item>
        <a-form-item label="Base URL">
          <a-input v-model:value="form.baseUrl" placeholder="可选" />
        </a-form-item>
        <a-form-item label="提供商预设">
          <button type="button" class="btn-preset-entry" @click="openPresetModal">选择预设</button>
          <div class="form-hint">从后端维护的预设中选择，一键覆盖基础配置</div>
        </a-form-item>
        <a-form-item label="默认模型">
          <a-input v-model:value="form.defaultModelId" :placeholder="defaultModelPlaceholder" />
          <div class="form-hint">用于连通性检查和未指定模型时的兜底模型</div>
        </a-form-item>

        <!-- 高级选项 -->
        <div class="advanced-toggle" @click="showAdvanced = !showAdvanced">
          <span>高级选项</span>
          <DownOutlined :class="['toggle-icon', { expanded: showAdvanced }]" />
        </div>
        <template v-if="showAdvanced">
          <a-form-item label="请求路径">
            <a-input v-model:value="form.completionsPath" placeholder="默认 /v1/chat/completions" />
            <div class="form-hint">OpenAI Chat Completions 路径</div>
          </a-form-item>
          <a-form-item label="模型列表URL">
            <a-input v-model:value="form.modelsEndpoint" placeholder="为空时使用默认地址" />
            <div class="form-hint">自定义获取模型列表的接口地址</div>
          </a-form-item>
          <a-form-item label="额外请求头">
            <JsonInput v-model="form.headersJson" placeholder='{"Authorization": "Bearer xxx"}' :rows="3" />
            <div class="form-hint">JSON 格式，为空时不添加额外请求头</div>
          </a-form-item>
          <a-form-item label="扩展配置">
            <JsonInput v-model="form.extraJson" placeholder='{"timeout": 30000}' :rows="3" />
            <div class="form-hint">JSON 格式的扩展配置</div>
          </a-form-item>
        </template>
        </a-form>
      </div>
      <div class="dialog-footer">
        <button class="btn-check" :disabled="checking" @click="handleCheck">
          {{ checking ? '检查中...' : '检查连通性' }}
        </button>
        <div class="dialog-footer-right">
          <button class="btn-cancel" @click="dialogVisible = false">取消</button>
          <button class="btn-primary-sm" :disabled="submitting" @click="handleSubmit">
            {{ submitting ? '提交中...' : '确定' }}
          </button>
        </div>
      </div>
    </a-modal>

    <!-- 提供商预设弹窗 -->
    <a-modal v-model:open="presetModalVisible" title="选择提供商预设" :width="760" :footer="null" :maskClosable="true">
      <div class="preset-modal-desc">系统预设一些 OpenAI-compatible 提供商模板，应用后会覆盖当前基础配置。</div>
      <div class="preset-list">
        <button
          v-for="preset in providerPresets"
          :key="preset.code"
          type="button"
          :class="['preset-card', { active: selectedPresetCode === preset.code }]"
          @click="selectedPresetCode = preset.code"
        >
          <div class="preset-card-header">
            <div class="preset-logo-box">
              <img
                v-if="preset.logo"
                :src="preset.logo"
                :alt="preset.name"
                :class="['preset-logo', { wide: preset.logo.startsWith('data:image') }]"
              />
              <div v-else class="preset-logo-fallback">{{ preset.name?.[0] }}</div>
            </div>
            <div class="preset-card-title-wrap">
              <div class="preset-card-title" :title="preset.name">{{ preset.name }}</div>
              <div class="preset-card-subtitle" :title="preset.defaultModelId || '需自行选择模型'">
                {{ preset.defaultModelId || '需自行选择模型' }}
              </div>
            </div>
            <span class="preset-type-tag">{{ preset.type?.desc || preset.type }}</span>
          </div>
          <div class="preset-card-desc" :title="preset.description">{{ preset.description }}</div>
          <div class="preset-card-meta">
            <span class="preset-meta-label">Base URL</span>
            <span class="preset-meta-value" :title="preset.baseUrl">{{ preset.baseUrl }}</span>
          </div>
        </button>
        <div v-if="providerPresets.length === 0" class="preset-empty">暂无可用预设</div>
      </div>
      <div class="preset-modal-footer">
        <button class="btn-cancel" @click="presetModalVisible = false">取消</button>
        <button v-if="selectedPreset" class="btn-primary-sm" @click="applySelectedPreset">应用预设</button>
      </div>
    </a-modal>

    <!-- 模型管理弹窗 -->
    <a-modal v-model:open="modelModalVisible" :title="`${currentProvider?.name || ''} - 模型管理`" :width="640" :footer="null" :maskClosable="false">
      <div class="model-modal-header">
        <span class="model-count">共 {{ modelList.length }} 个模型</span>
        <div class="model-modal-actions">
          <button class="btn-fetch" :disabled="fetching" @click="handleFetchModels">
            {{ fetching ? '拉取中...' : '联网拉取' }}
          </button>
          <button class="btn-primary-sm" @click="showAddModel = true">
            <PlusOutlined /> 手动添加
          </button>
        </div>
      </div>

      <!-- 手动添加模型表单 -->
      <div v-if="showAddModel" class="add-model-form">
        <a-form :model="modelForm" :label-col="{ span: 6 }" size="small">
          <a-form-item label="模型标识" required>
            <a-input v-model:value="modelForm.modelId" placeholder="如：qwen-max" />
          </a-form-item>
          <a-form-item label="显示名称" required>
            <a-input v-model:value="modelForm.name" placeholder="如：通义千问 Max" />
          </a-form-item>
          <a-form-item label="模型类型" required>
            <a-select v-model:value="modelForm.type" style="width: 100%">
              <a-select-option v-for="t in modelTypes" :key="t.value" :value="t.value">{{ t.label }}</a-select-option>
            </a-select>
          </a-form-item>
        </a-form>
        <div class="add-model-actions">
          <button class="btn-cancel" @click="showAddModel = false">取消</button>
          <button class="btn-primary-sm" :disabled="modelSubmitting" @click="handleAddModel">
            {{ modelSubmitting ? '添加中...' : '确认添加' }}
          </button>
        </div>
      </div>

      <!-- 模型列表 -->
      <div class="model-list" v-if="modelList.length > 0">
        <div v-for="m in modelList" :key="m.id" class="model-item">
          <div class="model-info">
            <a-tooltip :title="m.modelId" placement="topLeft">
              <span class="model-id ellipsis">{{ m.modelId }}</span>
            </a-tooltip>
            <a-tooltip :title="m.name" placement="topLeft">
              <span class="model-name ellipsis">{{ m.name }}</span>
            </a-tooltip>
            <span class="model-type-tag">{{ modelTypeText(m.type?.code || m.type) }}</span>
          </div>
          <a-tooltip title="删除">
            <button class="btn-icon danger" @click="handleDeleteModel(m.id)"><DeleteOutlined /></button>
          </a-tooltip>
        </div>
      </div>
      <div v-else-if="!showAddModel" class="model-empty">暂无模型，点击上方按钮添加</div>
    </a-modal>

    <!-- 联网拉取弹窗 -->
    <a-modal v-model:open="fetchModalVisible" :title="`${currentProvider?.name || ''} - 联网拉取`" :width="760" :footer="null" :maskClosable="false">
      <div class="fetch-tabs">
        <button :class="['fetch-tab', { active: fetchTab === 'available' }]" @click="fetchTab = 'available'">
          可添加 <span class="fetch-tab-count">{{ fetchedModels.length }}</span>
        </button>
        <button :class="['fetch-tab', { active: fetchTab === 'existing' }]" @click="fetchTab = 'existing'">
          已有模型 <span class="fetch-tab-count">{{ modelList.length }}</span>
        </button>
      </div>

      <!-- 搜索 + 类型筛选：上下布局，避免类型 Tab 挤压搜索框 -->
      <div class="fetch-filter-bar">
        <a-input
          v-model:value="fetchSearchText"
          class="fetch-search-input"
          placeholder="搜索模型 ID 或名称..."
          allow-clear
        >
          <template #prefix><SearchOutlined /></template>
        </a-input>
        <div class="fetch-type-tabs">
          <button
            v-for="t in currentFetchTabs"
            :key="t.value"
            :class="['type-tab', { active: fetchTypeFilter === t.value }]"
            @click="fetchTypeFilter = t.value"
          >{{ t.label }}{{ t.count > 0 ? ` (${t.count})` : '' }}</button>
        </div>
      </div>

      <!-- 可添加模型 -->
      <div v-if="fetchTab === 'available'" class="fetch-model-list">
        <a-checkbox-group v-model:value="selectedFetchedModels" class="fetch-model-grid">
          <div v-for="m in filteredFetchedModels" :key="m.modelId" class="fetch-model-item">
            <a-checkbox :value="m.modelId">
              <span class="fetch-model-id">{{ m.modelId }}</span>
              <span class="fetch-model-type">{{ modelTypeText(m.type) }}</span>
            </a-checkbox>
          </div>
          <div v-if="filteredFetchedModels.length === 0" class="fetch-empty">
            {{ fetchedModels.length === 0 ? '暂无可添加模型，请先点击拉取' : '无匹配模型' }}
          </div>
        </a-checkbox-group>
      </div>

      <!-- 已有模型 -->
      <div v-else class="fetch-model-list">
        <div v-for="m in filteredExistingModels" :key="m.id" class="fetch-model-item existing">
          <span class="fetch-model-id">{{ m.modelId }}</span>
          <span class="fetch-model-name">{{ m.name }}</span>
          <span class="fetch-model-type">{{ modelTypeText(m.type?.code || m.type) }}</span>
        </div>
        <div v-if="filteredExistingModels.length === 0" class="fetch-empty">无匹配模型</div>
      </div>

      <!-- 底部操作 -->
      <div class="fetch-modal-footer">
        <div class="fetch-footer-left">
          <button v-if="fetchTab === 'available'" class="btn-link" @click="selectAllFiltered">全选当前筛选</button>
        </div>
        <div class="fetch-footer-right">
          <button v-if="fetchTab === 'available'" class="btn-fetch" :disabled="fetching" @click="handleFetchModels">
            {{ fetching ? '拉取中...' : '重新拉取' }}
          </button>
          <button v-if="fetchTab === 'available'" class="btn-primary-sm" :disabled="selectedFetchedModels.length === 0 || modelSubmitting" @click="handleBatchAddModels">
            {{ modelSubmitting ? '添加中...' : `确认添加 (${selectedFetchedModels.length})` }}
          </button>
        </div>
      </div>
    </a-modal>

  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { PlusOutlined, EditOutlined, DeleteOutlined, SearchOutlined, DownOutlined, SyncOutlined } from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import { getModelProviders, createModelProvider, updateModelProvider, deleteModelProvider, checkModelProviderByForm, fetchProviderModels, refreshModelProviderCache, toggleProviderStatus, getProviderDefaultModel, getModelProviderPresets } from '../api/modelProvider'
import { getModelProviderTypes, getModelTypes } from '../api/enum'
import { getModelsByProvider, createModel, deleteModel } from '../api/model'
import JsonInput from '../components/JsonInput.vue'

const list = ref([])
const providerTypes = ref([])
const modelTypes = ref([])
const dialogVisible = ref(false)
const submitting = ref(false)
const checking = ref(false)
const refreshing = ref(false)
const form = reactive({ id: null, name: '', type: '', apiKey: '', baseUrl: '', defaultModelId: '', completionsPath: '', modelsEndpoint: '', headersJson: '{}', extraJson: '{}', config: '{}' })
const providerDefaultModelId = ref('')
const showAdvanced = ref(false)
const presetModalVisible = ref(false)
const providerPresets = ref([])
const selectedPresetCode = ref('')

// 模型管理
const modelModalVisible = ref(false)
const currentProvider = ref(null)
const modelList = ref([])
const showAddModel = ref(false)
const modelSubmitting = ref(false)
const modelForm = reactive({ modelId: '', name: '', type: 'llm' })

// 联网拉取
const fetching = ref(false)
const fetchModalVisible = ref(false)
const fetchTab = ref('available')
const fetchedModels = ref([])
const selectedFetchedModels = ref([])
const fetchSearchText = ref('')
const fetchTypeFilter = ref('all')

const filteredFetchedModels = computed(() => {
  let result = fetchedModels.value
  if (fetchTypeFilter.value !== 'all') {
    result = result.filter(m => m.type === fetchTypeFilter.value)
  }
  if (fetchSearchText.value) {
    const keyword = fetchSearchText.value.toLowerCase()
    result = result.filter(m =>
      m.modelId.toLowerCase().includes(keyword) || (m.name || '').toLowerCase().includes(keyword)
    )
  }
  return result
})

const filteredExistingModels = computed(() => {
  let result = modelList.value
  if (fetchTypeFilter.value !== 'all') {
    result = result.filter(m => (m.type?.code || m.type) === fetchTypeFilter.value)
  }
  if (fetchSearchText.value) {
    const keyword = fetchSearchText.value.toLowerCase()
    result = result.filter(m => m.modelId.toLowerCase().includes(keyword) || (m.name || '').toLowerCase().includes(keyword))
  }
  return result
})

const fetchTypeTabsCache = computed(() => {
  const counts = { all: fetchedModels.value.length }
  for (const m of fetchedModels.value) {
    counts[m.type] = (counts[m.type] || 0) + 1
  }
  const tabs = modelTypes.value.map(t => ({
    value: t.value, label: t.label, count: counts[t.value] || 0
  }))
  return [{ value: 'all', label: '全部', count: counts.all }, ...tabs]
      .filter(t => t.count > 0 || t.value === 'all')
})

const existingTypeTabs = computed(() => {
  const counts = { all: modelList.value.length }
  for (const m of modelList.value) {
    const t = m.type?.code || m.type
    counts[t] = (counts[t] || 0) + 1
  }
  const tabs = modelTypes.value.map(t => ({
    value: t.value, label: t.label, count: counts[t.value] || 0
  }))
  return [{ value: 'all', label: '全部', count: counts.all }, ...tabs]
      .filter(t => t.count > 0 || t.value === 'all')
})

const currentFetchTabs = computed(() => fetchTab.value === 'available' ? fetchTypeTabsCache.value : existingTypeTabs.value)
const selectedPreset = computed(() => providerPresets.value.find(p => p.code === selectedPresetCode.value))
const defaultModelPlaceholder = computed(() => providerDefaultModelId.value
    ? `不填则默认为${providerDefaultModelId.value}模型`
    : '不填则使用默认模型')

function selectAllFiltered() {
  selectedFetchedModels.value = filteredFetchedModels.value.map(m => m.modelId)
}

function resolveDefaultProviderType() {
  const preferred = providerTypes.value.find(t => t.value === 'DASHSCOPE')
  return preferred?.value || providerTypes.value[0]?.value || ''
}

async function loadProviderTypes() {
  try {
    const res = await getModelProviderTypes()
    providerTypes.value = res.data || []
    if (!form.type) {
      form.type = resolveDefaultProviderType()
    }
  } catch {
    providerTypes.value = []
  }
}

async function loadModelTypes() {
  try {
    const res = await getModelTypes()
    modelTypes.value = res.data || []
  } catch {
    modelTypes.value = []
  }
}

async function loadData() {
  const res = await getModelProviders({ pageNum: 1, pageSize: 50 })
  list.value = res.data.records || []
}

async function loadProviderPresets() {
  try {
    const res = await getModelProviderPresets()
    providerPresets.value = res.data || []
  } catch {
    providerPresets.value = []
  }
}

async function loadProviderDefaultModel(type) {
  providerDefaultModelId.value = ''
  if (!type) return
  try {
    const res = await getProviderDefaultModel(type)
    providerDefaultModelId.value = res.data || ''
  } catch {
    providerDefaultModelId.value = ''
  }
}

function parseJsonObject(str) {
  if (!str || !str.trim()) return {}
  try {
    const parsed = JSON.parse(str)
    return typeof parsed === 'object' && parsed !== null && !Array.isArray(parsed) ? parsed : {}
  } catch {
    return {}
  }
}

function getDefaultModelId(config) {
  return parseJsonObject(config).modelId || ''
}

function getCompletionsPath(config) {
  return parseJsonObject(config).completionsPath || ''
}

function buildProviderPayload() {
  const config = parseJsonObject(form.config)
  if (form.defaultModelId?.trim()) {
    config.modelId = form.defaultModelId.trim()
  } else {
    delete config.modelId
  }
  if (form.completionsPath?.trim()) {
    config.completionsPath = form.completionsPath.trim()
  } else {
    delete config.completionsPath
  }
  const { completionsPath, ...payload } = form
  return {
    ...payload,
    config: JSON.stringify(config),
  }
}

function openDialog(row) {
  if (row) {
    Object.assign(form, {
      id: row.id,
      name: row.name || '',
      type: row.type?.code || row.type,
      apiKey: row.apiKey || '',
      baseUrl: row.baseUrl || '',
      defaultModelId: getDefaultModelId(row.config),
      completionsPath: getCompletionsPath(row.config),
      modelsEndpoint: row.modelsEndpoint || '',
      headersJson: row.headersJson || '{}',
      extraJson: row.extraJson || '{}',
      config: row.config || '{}',
    })
    showAdvanced.value = false
  } else {
    Object.assign(form, {
      id: null,
      name: '',
      type: resolveDefaultProviderType(),
      apiKey: '',
      baseUrl: '',
      defaultModelId: '',
      completionsPath: '',
      modelsEndpoint: '',
      headersJson: '{}',
      extraJson: '{}',
      config: '{}',
    })
    showAdvanced.value = false
  }
  dialogVisible.value = true
  loadProviderDefaultModel(form.type)
}

function openPresetModal() {
  selectedPresetCode.value = providerPresets.value[0]?.code || ''
  presetModalVisible.value = true
}

function applySelectedPreset() {
  const preset = selectedPreset.value
  if (!preset) return
  Object.assign(form, {
    name: form.name || preset.name || '',
    type: preset.type?.code || preset.type || form.type,
    baseUrl: preset.baseUrl || '',
    defaultModelId: preset.defaultModelId || '',
    completionsPath: preset.completionsPath || '',
    modelsEndpoint: preset.modelsEndpoint || '',
    headersJson: preset.headersJson || '{}',
    extraJson: preset.extraJson || '{}',
  })
  showAdvanced.value = true
  presetModalVisible.value = false
  loadProviderDefaultModel(form.type)
}

async function handleSubmit() {
  if (!form.name.trim()) return message.warning('请输入名称')
  if (form.headersJson && !isValidJson(form.headersJson)) return message.warning('额外请求头 JSON 格式不正确')
  if (form.extraJson && !isValidJson(form.extraJson)) return message.warning('扩展配置 JSON 格式不正确')
  submitting.value = true
  try {
    const payload = buildProviderPayload()
    if (form.id) {
      await updateModelProvider(payload)
      message.success('更新成功')
    } else {
      await createModelProvider(payload)
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
    content: '删除后该模型提供商将无法恢复，关联的 Agent 将无法使用，是否继续？',
    okText: '确认删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      await deleteModelProvider(id)
      message.success('删除成功')
      loadData()
    },
  })
}

async function handleToggleStatus(provider, checked) {
  const status = checked ? 'active' : 'disabled'
  try {
    await toggleProviderStatus(provider.id, status)
    message.success(checked ? '已启用' : '已禁用')
    await loadData()
  } catch {
    // interceptor已处理错误提示
  }
}

async function handleCheck() {
  if (!form.apiKey?.trim() && form.type?.toUpperCase() !== 'OLLAMA') {
    message.warning('请先填写 API Key')
    return
  }
  checking.value = true
  try {
    const res = await checkModelProviderByForm({
      type: form.type,
      apiKey: form.apiKey,
      baseUrl: form.baseUrl,
      completionsPath: form.completionsPath,
      modelId: form.defaultModelId?.trim(),
    })
    message.success(res.data || '连接成功')
  } catch (e) {
    // interceptor已处理错误提示
  } finally {
    checking.value = false
  }
}

function maskKey(key) {
  if (!key || key.length < 10) return '***'
  return key.substring(0, 6) + '****' + key.substring(key.length - 4)
}

function isValidJson(str) {
  if (!str || !str.trim()) return true
  try {
    const parsed = JSON.parse(str)
    return typeof parsed === 'object' && parsed !== null
  } catch {
    return false
  }
}

// ========== 模型管理 ==========

async function openModelModal(provider) {
  currentProvider.value = provider
  showAddModel.value = false
  Object.assign(modelForm, { modelId: '', name: '', type: 'llm' })
  modelModalVisible.value = true
  await loadModels(provider.id)
}

async function loadModels(providerId) {
  const res = await getModelsByProvider(providerId)
  modelList.value = res.data || []
}

async function handleAddModel() {
  if (!modelForm.modelId.trim()) return message.warning('请输入模型标识')
  if (!modelForm.name.trim()) return message.warning('请输入显示名称')
  modelSubmitting.value = true
  try {
    await createModel({
      providerId: currentProvider.value.id,
      modelId: modelForm.modelId,
      name: modelForm.name,
      type: modelForm.type,
    })
    message.success('添加成功')
    showAddModel.value = false
    Object.assign(modelForm, { modelId: '', name: '', type: 'llm' })
    await loadModels(currentProvider.value.id)
  } finally {
    modelSubmitting.value = false
  }
}

function handleDeleteModel(id) {
  Modal.confirm({
    title: '确认删除',
    content: '删除后该模型将无法恢复，是否继续？',
    okText: '确认删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      await deleteModel(id)
      message.success('删除成功')
      await loadModels(currentProvider.value.id)
    },
  })
}

async function handleFetchModels() {
  fetching.value = true
  try {
    const res = await fetchProviderModels(currentProvider.value.id)
    fetchedModels.value = res.data || []
    selectedFetchedModels.value = []
    fetchSearchText.value = ''
    fetchTypeFilter.value = 'all'
    fetchTab.value = 'available'
    fetchModalVisible.value = true
    if (fetchedModels.value.length === 0) {
      message.info('该提供商下未找到可用模型')
    }
  } catch (e) {
    // interceptor已处理错误提示
  } finally {
    fetching.value = false
  }
}

async function handleBatchAddModels() {
  if (selectedFetchedModels.value.length === 0) return
  modelSubmitting.value = true
  try {
    // 建立 modelId → type 映射
    const typeMap = {}
    for (const m of fetchedModels.value) {
      typeMap[m.modelId] = m.type
    }
    let successCount = 0
    for (const modelId of selectedFetchedModels.value) {
      try {
        await createModel({
          providerId: currentProvider.value.id,
          modelId,
          name: modelId,
          type: typeMap[modelId] || 'llm',
        })
        successCount++
      } catch {
        // 跳过已存在的模型
      }
    }
    message.success(`成功添加 ${successCount} 个模型`)
    fetchModalVisible.value = false
    await loadModels(currentProvider.value.id)
  } finally {
    modelSubmitting.value = false
  }
}

function modelTypeText(type) {
  const found = modelTypes.value.find(t => t.value === type)
  return found?.label || type
}

async function handleRefreshCache() {
  refreshing.value = true
  try {
    await refreshModelProviderCache()
    message.success('缓存已刷新')
  } catch {
    // interceptor已处理错误提示
  } finally {
    refreshing.value = false
  }
}

watch(() => form.type, (type) => {
  if (dialogVisible.value) {
    loadProviderDefaultModel(type)
  }
})

onMounted(async () => {
  await Promise.all([loadProviderTypes(), loadModelTypes(), loadProviderPresets()])
  await loadData()
})
</script>

<style scoped>
.page {
  padding: var(--space-xl);
  padding-right: calc(var(--space-xl) + var(--scroll-content-gap));
  height: 100vh;
  overflow-y: auto;
  background: var(--color-canvas-soft);
  scrollbar-gutter: stable;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 32px;
}
.header-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}
.btn-refresh {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 20px;
  background: var(--color-canvas);
  color: var(--color-ink);
  border: 1px solid var(--color-hairline);
  border-radius: 100px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: border-color 0.15s;
}
.btn-refresh:hover:not(:disabled) {
  border-color: var(--color-link);
  color: var(--color-link);
}
.btn-refresh:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.spinning {
  animation: spin 1s linear infinite;
}
@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
.page-title {
  font-size: 24px;
  font-weight: 600;
  color: var(--color-ink);
  margin-bottom: 4px;
}
.page-desc {
  font-size: 14px;
  color: var(--color-mute);
}
.btn-primary {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 20px;
  background: var(--color-primary);
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

.provider-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}
.provider-card {
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
  border-radius: 12px;
  padding: 20px;
  display: flex;
  flex-direction: column;
  transition: opacity 0.2s, border-color 0.2s;
}
.provider-card.disabled {
  opacity: 0.6;
  border-color: var(--color-hairline);
}
.provider-card.disabled .card-icon {
  background: #a1a1aa;
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
  background: var(--color-primary);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 16px;
}
.card-info {
  flex: 1;
  min-width: 0;
}
.card-info h3 {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.card-type {
  font-size: 12px;
  color: var(--color-mute);
  background: var(--color-canvas-soft-2);
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
  color: var(--color-mute);
}
.btn-icon:hover {
  background: var(--color-canvas-soft-2);
}
.btn-icon.danger:hover {
  color: var(--color-error);
  background: var(--color-error-soft);
}
.card-detail {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 13px;
  color: var(--color-mute);
  margin-bottom: 12px;
  flex: 1;
}
.card-footer {
  border-top: 1px solid var(--color-hairline);
  padding-top: 12px;
}
.btn-link {
  background: none;
  border: none;
  color: var(--color-link);
  cursor: pointer;
  font-size: 13px;
  padding: 0;
}
.btn-link:hover {
  text-decoration: underline;
}
.btn-link:disabled {
  color: var(--color-mute);
  cursor: not-allowed;
}
.btn-link:disabled:hover {
  text-decoration: none;
}

/* 模型管理弹窗 */
.model-modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.model-count {
  font-size: 13px;
  color: var(--color-mute);
}
.add-model-form {
  background: var(--color-canvas-soft-2);
  border: 1px solid var(--color-hairline);
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 16px;
}
.add-model-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 8px;
}
.model-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 400px;
  overflow-y: auto;
}
.model-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  border: 1px solid var(--color-hairline);
  border-radius: 8px;
}
.model-item:hover {
  border-color: var(--color-hairline-strong);
}
.model-info {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  min-width: 0;
  overflow: hidden;
}
.model-info :deep(.ant-tooltip-wrapper) {
  min-width: 0;
  overflow: hidden;
}
.model-id {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-ink);
  font-family: monospace;
  max-width: 180px;
}
.model-name {
  font-size: 13px;
  color: var(--color-mute);
  max-width: 180px;
}
.ellipsis {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.model-type-tag {
  font-size: 11px;
  color: var(--color-mute);
  background: var(--color-canvas-soft-2);
  padding: 1px 6px;
  border-radius: 4px;
  flex-shrink: 0;
}
.model-empty {
  text-align: center;
  padding: 32px;
  color: var(--color-mute);
  font-size: 13px;
}
.model-modal-actions {
  display: flex;
  gap: 8px;
}
.btn-fetch {
  padding: 6px 14px;
  background: var(--color-canvas);
  color: var(--color-ink);
  border: 1px solid var(--color-hairline);
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
  transition: border-color 0.15s;
}
.btn-fetch:hover:not(:disabled) {
  border-color: var(--color-link);
  color: var(--color-link);
}
.btn-fetch:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 联网拉取弹窗 */
.fetch-tabs {
  display: flex;
  gap: 0;
  border-bottom: 1px solid var(--color-hairline);
  margin-bottom: 12px;
}
.fetch-tab {
  padding: 8px 16px;
  background: none;
  border: none;
  border-bottom: 2px solid transparent;
  font-size: 14px;
  color: var(--color-mute);
  cursor: pointer;
  transition: all 0.15s;
}
.fetch-tab:hover {
  color: var(--color-ink);
}
.fetch-tab.active {
  color: var(--color-ink);
  border-bottom-color: var(--color-ink);
  font-weight: 500;
}
.fetch-tab-count {
  font-size: 12px;
  color: var(--color-mute);
  margin-left: 4px;
}
.fetch-filter-bar {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 10px;
  margin-bottom: 12px;
}
.fetch-search-input {
  width: 100%;
}
.fetch-type-tabs {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.type-tab {
  padding: 3px 10px;
  border: 1px solid var(--color-hairline);
  border-radius: 100px;
  background: var(--color-canvas);
  font-size: 12px;
  cursor: pointer;
  color: var(--color-mute);
  transition: all 0.15s;
  white-space: nowrap;
}
.type-tab:hover {
  border-color: var(--color-link);
  color: var(--color-link);
}
.type-tab.active {
  background: var(--color-primary);
  border-color: var(--color-ink);
  color: #fff;
}
.fetch-model-list {
  max-height: 360px;
  overflow-y: auto;
  margin-bottom: 12px;
}
.fetch-model-list::-webkit-scrollbar {
  width: 4px;
}
.fetch-model-list::-webkit-scrollbar-thumb {
  background: var(--color-hairline-strong);
  border-radius: 2px;
}
.fetch-model-grid {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.fetch-model-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
  border-radius: 6px;
}
.fetch-model-item:hover {
  border-color: var(--color-hairline-strong);
}
.fetch-model-item.existing {
  background: var(--color-canvas-soft-2);
}
.fetch-model-id {
  font-family: monospace;
  font-size: 13px;
  color: var(--color-ink);
  flex-shrink: 0;
}
.fetch-model-name {
  font-size: 12px;
  color: var(--color-mute);
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.fetch-model-type {
  font-size: 11px;
  color: var(--color-mute);
  background: var(--color-canvas-soft-2);
  padding: 1px 6px;
  border-radius: 4px;
  flex-shrink: 0;
}
.fetch-empty {
  text-align: center;
  padding: 32px;
  color: var(--color-mute);
  font-size: 13px;
}
.fetch-modal-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 12px;
  border-top: 1px solid var(--color-hairline);
}
.fetch-footer-left,
.fetch-footer-right {
  display: flex;
  gap: 8px;
  align-items: center;
}

/* 提供商表单滚动区 */
.provider-form-scroll {
  max-height: min(68vh, 620px);
  overflow-y: auto;
  padding-right: 12px;
  margin-right: -4px;
}
.provider-form-scroll::-webkit-scrollbar {
  width: 6px;
}
.provider-form-scroll::-webkit-scrollbar-thumb {
  background: var(--color-hairline-strong);
  border-radius: 3px;
}
.provider-form-scroll::-webkit-scrollbar-track {
  background: transparent;
}

/* 弹窗底部 */
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
.btn-check {
  padding: 6px 14px;
  background: var(--color-canvas);
  color: var(--color-ink);
  border: 1px solid var(--color-hairline);
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
  transition: border-color 0.15s;
}
.btn-check:hover:not(:disabled) {
  border-color: var(--color-link);
  color: var(--color-link);
}
.btn-check:disabled {
  opacity: 0.5;
  cursor: not-allowed;
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

/* 高级选项 */
.advanced-toggle {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 0;
  cursor: pointer;
  font-size: 13px;
  color: var(--color-mute);
  user-select: none;
  border-top: 1px dashed var(--color-hairline);
  margin-top: 4px;
}
.advanced-toggle:hover {
  color: var(--color-link);
}
.toggle-icon {
  font-size: 10px;
  transition: transform 0.2s;
}
.toggle-icon.expanded {
  transform: rotate(180deg);
}
.btn-preset-entry {
  padding: 6px 14px;
  background: var(--color-canvas);
  color: var(--color-link);
  border: 1px solid var(--color-hairline);
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
}
.btn-preset-entry:hover {
  border-color: var(--color-link);
}
.preset-modal-desc {
  margin-bottom: 14px;
  color: var(--color-mute);
  font-size: 13px;
  line-height: 1.6;
}
.preset-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 12px;
  max-height: 58vh;
  overflow-y: auto;
  padding: 2px 14px 2px 2px;
  margin-right: -6px;
}
.preset-list::-webkit-scrollbar {
  width: 6px;
}
.preset-list::-webkit-scrollbar-thumb {
  background: var(--color-hairline-strong);
  border-radius: 3px;
}
.preset-list::-webkit-scrollbar-track {
  background: transparent;
}
.preset-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
  min-height: 150px;
  padding: 16px;
  background: linear-gradient(45deg, var(--color-canvas) 0%, var(--color-canvas-soft) 100%);
  border: 1px solid var(--color-hairline);
  border-radius: 10px;
  text-align: left;
  cursor: pointer;
  overflow: hidden;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, background 0.2s ease;
}
.preset-card:hover {
  border-color: var(--color-link);
  background: linear-gradient(45deg, var(--color-canvas) 0%, rgba(37, 99, 235, 0.06) 100%);
}
.preset-card.active {
  border-color: var(--color-link);
  box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.12);
  background: linear-gradient(45deg, var(--color-canvas) 0%, rgba(37, 99, 235, 0.08) 100%);
}
.preset-card-header {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}
.preset-logo-box {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  padding: 7px;
  background: var(--color-canvas-soft-2);
  border: 1px solid var(--color-hairline);
  border-radius: 8px;
  flex-shrink: 0;
  overflow: hidden;
}
.preset-logo {
  width: 24px;
  height: 24px;
  object-fit: contain;
}
.preset-logo.wide {
  width: 32px;
  height: 20px;
}
.preset-logo-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  color: var(--color-ink);
  font-weight: 600;
  font-size: 13px;
  flex-shrink: 0;
}
.preset-card-title-wrap {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.preset-card-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.preset-card-subtitle {
  margin-top: 2px;
  font-size: 12px;
  color: var(--color-mute);
  font-family: monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.preset-type-tag {
  flex-shrink: 0;
  padding: 2px 7px;
  color: var(--color-mute);
  background: var(--color-canvas-soft-2);
  border-radius: 999px;
  font-size: 11px;
  white-space: nowrap;
}
.preset-card-desc {
  min-height: 36px;
  font-size: 13px;
  line-height: 1.4;
  color: var(--color-mute);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.preset-card-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: auto;
  min-width: 0;
  font-size: 12px;
}
.preset-meta-label {
  flex-shrink: 0;
  color: var(--color-mute);
}
.preset-meta-value {
  flex: 1;
  min-width: 0;
  color: var(--color-ink);
  font-family: monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.preset-empty {
  grid-column: 1 / -1;
  padding: 32px;
  text-align: center;
  color: var(--color-mute);
  font-size: 13px;
}
.preset-modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 16px;
  margin-top: 16px;
  border-top: 1px solid var(--color-hairline);
}


.form-hint {
  font-size: 12px;
  color: var(--color-mute);
  margin-top: 2px;
}
.form-error {
  font-size: 12px;
  color: #dc2626;
  margin-top: 2px;
}
</style>
